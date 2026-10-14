package com.astro.api.manager.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.manager.dto.response.ManagerSummaryResponse;
import com.astro.api.manager.dto.response.ManagerDetailsResponse;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.manager.dto.request.PromoteManagerRequest;
import com.astro.api.manager.dto.request.UpdateManagerRequest;
import com.astro.api.manager.dto.request.ManagerStatusRequest;
import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.role.model.Role;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.repository.UnitRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.astro.api.manager.mapper.ManagerMapper;
import com.astro.api.manager.repository.ManagerRepository;
import com.astro.api.manager.repository.ManagerSummaryProjection;
import com.astro.api.user.service.UserAvatarService;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class ManagerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagerService.class);
    private final ManagerRepository managerRepository;
    private final UserRepository userRepository;
    private final ManagerMapper managerMapper;
    private final RoleRepository roleRepository;
    private final UnitRepository unitRepository;
    private final FirebaseIdentityService firebaseIdentityService;
    private final ManagerStatusPolicy statusPolicy;
    private final TransactionTemplate transactionTemplate;
    private final UserAvatarService userAvatarService;

    public ManagerService(ManagerRepository managerRepository, UserRepository userRepository, ManagerMapper managerMapper,
                          RoleRepository roleRepository, UnitRepository unitRepository,
                          FirebaseIdentityService firebaseIdentityService, ManagerStatusPolicy statusPolicy,
                          PlatformTransactionManager transactionManager, UserAvatarService userAvatarService) {
        this.managerRepository = managerRepository;
        this.userRepository = userRepository;
        this.managerMapper = managerMapper;
        this.roleRepository = roleRepository;
        this.unitRepository = unitRepository;
        this.firebaseIdentityService = firebaseIdentityService;
        this.statusPolicy = statusPolicy;
        this.userAvatarService = userAvatarService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public List<ManagerSummaryResponse> findAll(AuthenticatedUser principal) {
        Long workspaceId = requireWorkspaceManager(principal).getUnit().workspace.id;
        List<ManagerSummaryProjection> rows = managerRepository.findAllInWorkspace(workspaceId);
        Map<Long, String> urls = userAvatarService.findUrls(rows.stream().map(ManagerSummaryProjection::getId).toList());
        return rows.stream().map(row -> managerMapper.toSummary(row, urls.get(row.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ManagerDetailsResponse findById(Long managerId, AuthenticatedUser principal) {
        Long workspaceId = requireWorkspaceManager(principal).getUnit().workspace.id;
        checkId(managerId);
        return managerRepository.findDetailsInWorkspace(managerId, workspaceId)
                .map(row -> managerMapper.toDetails(row, userAvatarService.findUrl(row.getId())))
                .orElseThrow(() -> new ResourceNotFoundException("Gestor não encontrado"));
    }

    private void checkId(Long userId) {
        if (userId == null || userId <= 0) throw new BusinessException("O identificador do usuário deve ser positivo");
    }

    @Transactional
    public ManagerDetailsResponse promote(PromoteManagerRequest body, AuthenticatedUser principal) {
        Long workspaceId = requireWorkspaceManager(principal).getUnit().workspace.id;
        checkId(body.collaboratorId());
        User collaborator = managerRepository.findUserForUpdateInWorkspace(body.collaboratorId(), workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador não encontrado"));
        if (collaborator.getType() != UserType.COLABORADOR) {
            throw new ConflictException("Somente colaboradores podem ser promovidos a gestor");
        }
        if (collaborator.getStatus() != UserStatus.ATIVO || collaborator.getFirebaseUid() == null
                || collaborator.getFirebaseUid().isBlank()) {
            throw new BusinessException("O colaborador deve estar ativo e ter autenticação concluída");
        }
        requireActiveLinks(collaborator, workspaceId);
        collaborator.setType(UserType.GESTOR);
        collaborator.setStatus(body.status().toUserStatus());
        User saved = managerRepository.saveAndFlush(collaborator);
        return managerMapper.toDetails(saved, userAvatarService.findUrl(saved.getId()));
    }

    private void requireActiveLinks(User user, Long workspaceId) {
        if (user.getRole() == null || user.getUnit() == null
                || !Boolean.TRUE.equals(managerRepository.lockRoleActive(user.getRole().getId(), workspaceId))
                || !Boolean.TRUE.equals(managerRepository.lockUnitActive(user.getUnit().id, workspaceId))) {
            throw new BusinessException("Cargo e unidade devem estar ativos no workspace");
        }
    }

    public ManagerDetailsResponse update(Long managerId, UpdateManagerRequest body, AuthenticatedUser principal) {
        EmailChange[] emailChange = new EmailChange[1];
        try {
            // Covers both flush and commit failures; PostgreSQL cannot roll back Firebase.
            return transactionTemplate.execute(transaction -> {
                Long workspaceId = requireWorkspaceManager(principal).getUnit().workspace.id;
                User manager = requireManager(managerId, workspaceId);
                Role role = roleRepository.findById(body.roleId())
                        .filter(value -> value.getWorkspace() != null && workspaceId.equals(value.getWorkspace().id))
                        .orElseThrow(() -> new ResourceNotFoundException("Cargo não encontrado no workspace"));
                Unit unit = unitRepository.findById(body.unitId())
                        .filter(value -> value.workspace != null && workspaceId.equals(value.workspace.id))
                        .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada no workspace"));
                boolean newRole = manager.getRole() == null || !body.roleId().equals(manager.getRole().getId());
                boolean newUnit = manager.getUnit() == null || !body.unitId().equals(manager.getUnit().id);
                if (newRole && !Boolean.TRUE.equals(managerRepository.lockRoleActive(body.roleId(), workspaceId))) {
                    throw new BusinessException("O cargo deve estar ativo para uma nova vinculação");
                }
                if (newUnit && !Boolean.TRUE.equals(managerRepository.lockUnitActive(body.unitId(), workspaceId))) {
                    throw new BusinessException("A unidade deve estar ativa para uma nova vinculação");
                }
                checkConflicts(body, managerId);
                String previousEmail = manager.getEmail();
                managerMapper.applyUpdate(manager, body, role, unit);
                applyStatus(manager, body.status(), workspaceId);
                User saved = persist(manager);
                if (saved.getFirebaseUid() != null && !saved.getFirebaseUid().isBlank() && !body.email().equals(previousEmail)) {
                    FirebaseIdentityService.EmailState previous = firebaseIdentityService.readEmailState(saved.getFirebaseUid());
                    if (previous.email() == null || !previousEmail.equalsIgnoreCase(previous.email())) {
                        throw new ConflictException("O e-mail da autenticação precisa ser reconciliado antes da edição");
                    }
                    emailChange[0] = new EmailChange(saved.getId(), saved.getFirebaseUid(), previous);
                    firebaseIdentityService.updateEmail(saved.getFirebaseUid(), body.email());
                }
                return managerMapper.toDetails(saved, userAvatarService.findUrl(saved.getId()));
            });
        } catch (RuntimeException exception) {
            if (emailChange[0] != null) compensateEmail(emailChange[0], exception);
            throw exception;
        }
    }

    private User requireManager(Long managerId, Long workspaceId) {
        checkId(managerId);
        return managerRepository.findUserForUpdateInWorkspace(managerId, workspaceId)
                .filter(user -> user.getType() == UserType.GESTOR)
                .orElseThrow(() -> new ResourceNotFoundException("Gestor não encontrado"));
    }

    @Transactional
    public ManagerDetailsResponse changeStatus(Long managerId, ManagerStatusRequest body, AuthenticatedUser principal) {
        Long workspaceId = requireWorkspaceManager(principal).getUnit().workspace.id;
        User manager = requireManager(managerId, workspaceId);
        UserStatus desired = body.status().toUserStatus();
        UserStatus previous = manager.getStatus();
        applyStatus(manager, desired, workspaceId);
        if (desired != previous) persist(manager);
        return managerMapper.toDetails(manager, userAvatarService.findUrl(manager.getId()));
    }

    private void applyStatus(User manager, UserStatus desired, Long workspaceId) {
        statusPolicy.validate(manager, desired);
        if (desired == UserStatus.ATIVO && manager.getStatus() != desired) requireActiveLinks(manager, workspaceId);
        manager.setStatus(desired);
    }

    private void checkConflicts(UpdateManagerRequest body, Long managerId) {
        if (managerRepository.cpfExists(body.cpf(), managerId)) throw new ConflictException("Já existe um usuário com este CPF");
        if (managerRepository.emailExists(body.email(), managerId)) throw new ConflictException("Já existe um usuário com este e-mail");
    }

    private User persist(User manager) {
        try { return managerRepository.saveAndFlush(manager); }
        catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                    if ("uq_usuario_cpf".equals(violation.getConstraintName())) throw new ConflictException("Já existe um usuário com este CPF");
                    if ("uq_usuario_email".equals(violation.getConstraintName())) throw new ConflictException("Já existe um usuário com este e-mail");
                }
            }
            throw exception;
        }
    }

    private void compensateEmail(EmailChange change, RuntimeException original) {
        try { firebaseIdentityService.restoreEmail(change.firebaseUid(), change.previous()); }
        catch (RuntimeException compensationFailure) {
            original.addSuppressed(compensationFailure);
            try {
                transactionTemplate.executeWithoutResult(transaction -> {
                    if (managerRepository.deactivateAfterEmailFailure(change.managerId(), change.firebaseUid()) != 1) {
                        throw new IllegalStateException("Gestor não encontrado para contenção da falha");
                    }
                });
            } catch (RuntimeException containmentFailure) { original.addSuppressed(containmentFailure); }
            LOGGER.error("Falha na compensação de e-mail do gestor {}; reconciliação operacional necessária", change.managerId(), original);
            throw new IllegalStateException("Falha de sincronização da autenticação do gestor", original);
        }
    }

    private User requireWorkspaceManager(AuthenticatedUser principal) {
        if (principal == null || principal.role() != com.astro.api.auth.security.Role.GESTOR_WORKSPACE
                || principal.firebaseUid() == null || principal.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Gestor de workspace autenticado necessário");
        }
        return userRepository.findByFirebaseUid(principal.firebaseUid())
                .filter(user -> user.getType() == UserType.GESTOR_WORKSPACE && user.getStatus() == UserStatus.ATIVO
                        && user.getUnit() != null && user.getUnit().workspace != null)
                .orElseThrow(() -> new AccessDeniedException("Gestor de workspace ativo necessário"));
    }
}
