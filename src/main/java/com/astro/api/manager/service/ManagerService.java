package com.astro.api.manager.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.manager.dto.response.ManagerSummaryResponse;
import com.astro.api.manager.dto.response.ManagerDetailsResponse;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.manager.dto.request.PromoteManagerRequest;
import com.astro.api.manager.dto.request.UpdateManagerRequest;
import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.unit.repository.UnitRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.astro.api.manager.mapper.ManagerMapper;
import com.astro.api.manager.repository.ManagerRepository;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ManagerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagerService.class);
    private final ManagerRepository repository;
    private final UserRepository userRepository;
    private final ManagerMapper mapper;
    private final RoleRepository roleRepository;
    private final UnitRepository unitRepository;
    private final FirebaseIdentityService firebaseIdentityService;
    private final ManagerStatusPolicy statusPolicy;
    private final TransactionTemplate transactionTemplate;

    public ManagerService(ManagerRepository repository, UserRepository userRepository, ManagerMapper mapper,
                          RoleRepository roleRepository, UnitRepository unitRepository,
                          FirebaseIdentityService firebaseIdentityService, ManagerStatusPolicy statusPolicy,
                          PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.roleRepository = roleRepository;
        this.unitRepository = unitRepository;
        this.firebaseIdentityService = firebaseIdentityService;
        this.statusPolicy = statusPolicy;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public List<ManagerSummaryResponse> findAll(AuthenticatedUser principal) {
        Long workspaceId = requireWorkspaceManager(principal).getUnit().workspace.id;
        return repository.findAllInWorkspace(workspaceId).stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ManagerDetailsResponse findById(Long managerId, AuthenticatedUser principal) {
        Long workspaceId = requireWorkspaceManager(principal).getUnit().workspace.id;
        checkId(managerId);
        return repository.findDetailsInWorkspace(managerId, workspaceId).map(mapper::toDetails)
                .orElseThrow(() -> new ResourceNotFoundException("Gestor não encontrado"));
    }

    private void checkId(Long userId) {
        if (userId == null || userId <= 0) throw new BusinessException("O identificador do usuário deve ser positivo");
    }

    @Transactional
    public ManagerDetailsResponse promote(PromoteManagerRequest body, AuthenticatedUser principal) {
        Long workspaceId = requireWorkspaceManager(principal).getUnit().workspace.id;
        checkId(body.collaboratorId());
        User collaborator = repository.findUserForUpdateInWorkspace(body.collaboratorId(), workspaceId)
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
        return mapper.toDetails(repository.saveAndFlush(collaborator));
    }

    private void requireActiveLinks(User user, Long workspaceId) {
        if (user.getRole() == null || user.getUnit() == null
                || !Boolean.TRUE.equals(repository.lockRoleActive(user.getRole().getId(), workspaceId))
                || !Boolean.TRUE.equals(repository.lockUnitActive(user.getUnit().id, workspaceId))) {
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
                var role = roleRepository.findById(body.roleId())
                        .filter(value -> value.getWorkspace() != null && workspaceId.equals(value.getWorkspace().id))
                        .orElseThrow(() -> new ResourceNotFoundException("Cargo não encontrado no workspace"));
                var unit = unitRepository.findById(body.unitId())
                        .filter(value -> value.workspace != null && workspaceId.equals(value.workspace.id))
                        .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada no workspace"));
                boolean newRole = manager.getRole() == null || !body.roleId().equals(manager.getRole().getId());
                boolean newUnit = manager.getUnit() == null || !body.unitId().equals(manager.getUnit().id);
                if (newRole && !Boolean.TRUE.equals(repository.lockRoleActive(body.roleId(), workspaceId))) {
                    throw new BusinessException("O cargo deve estar ativo para uma nova vinculação");
                }
                if (newUnit && !Boolean.TRUE.equals(repository.lockUnitActive(body.unitId(), workspaceId))) {
                    throw new BusinessException("A unidade deve estar ativa para uma nova vinculação");
                }
                checkConflicts(body, managerId);
                String previousEmail = manager.getEmail();
                mapper.applyUpdate(manager, body, role, unit);
                applyStatus(manager, body.status(), workspaceId);
                User saved = persist(manager);
                if (saved.getFirebaseUid() != null && !saved.getFirebaseUid().isBlank() && !body.email().equals(previousEmail)) {
                    var previous = firebaseIdentityService.readEmailState(saved.getFirebaseUid());
                    if (previous.email() == null || !previousEmail.equalsIgnoreCase(previous.email())) {
                        throw new ConflictException("O e-mail da autenticação precisa ser reconciliado antes da edição");
                    }
                    emailChange[0] = new EmailChange(saved.getId(), saved.getFirebaseUid(), previous);
                    firebaseIdentityService.updateEmail(saved.getFirebaseUid(), body.email());
                }
                return mapper.toDetails(saved);
            });
        } catch (RuntimeException exception) {
            if (emailChange[0] != null) compensateEmail(emailChange[0], exception);
            throw exception;
        }
    }

    private User requireManager(Long managerId, Long workspaceId) {
        checkId(managerId);
        return repository.findUserForUpdateInWorkspace(managerId, workspaceId)
                .filter(user -> user.getType() == UserType.GESTOR)
                .orElseThrow(() -> new ResourceNotFoundException("Gestor não encontrado"));
    }

    private void applyStatus(User manager, UserStatus desired, Long workspaceId) {
        statusPolicy.validate(manager, desired);
        if (desired == UserStatus.ATIVO && manager.getStatus() != desired) requireActiveLinks(manager, workspaceId);
        manager.setStatus(desired);
    }

    private void checkConflicts(UpdateManagerRequest body, Long managerId) {
        if (repository.cpfExists(body.cpf(), managerId)) throw new ConflictException("Já existe um usuário com este CPF");
        if (repository.emailExists(body.email(), managerId)) throw new ConflictException("Já existe um usuário com este e-mail");
    }

    private User persist(User manager) {
        try { return repository.saveAndFlush(manager); }
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
                    if (repository.deactivateAfterEmailFailure(change.managerId(), change.firebaseUid()) != 1) {
                        throw new IllegalStateException("Gestor não encontrado para contenção da falha");
                    }
                });
            } catch (RuntimeException containmentFailure) { original.addSuppressed(containmentFailure); }
            LOGGER.error("Falha na compensação de e-mail do gestor {}; reconciliação operacional necessária", change.managerId(), original);
            throw new IllegalStateException("Falha de sincronização da autenticação do gestor", original);
        }
    }

    private record EmailChange(Long managerId, String firebaseUid, FirebaseIdentityService.EmailState previous) { }

    private User requireWorkspaceManager(AuthenticatedUser principal) {
        if (principal == null || principal.role() != Role.GESTOR_WORKSPACE
                || principal.firebaseUid() == null || principal.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Gestor de workspace autenticado necessário");
        }
        return userRepository.findByFirebaseUid(principal.firebaseUid())
                .filter(user -> user.getType() == UserType.GESTOR_WORKSPACE && user.getStatus() == UserStatus.ATIVO
                        && user.getUnit() != null && user.getUnit().workspace != null)
                .orElseThrow(() -> new AccessDeniedException("Gestor de workspace ativo necessário"));
    }
}
