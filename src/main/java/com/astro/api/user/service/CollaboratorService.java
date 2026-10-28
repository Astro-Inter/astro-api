package com.astro.api.user.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.user.dto.response.CollaboratorSummaryResponse;
import com.astro.api.user.dto.response.CollaboratorPendingCountResponse;
import com.astro.api.user.mapper.CollaboratorMapper;
import com.astro.api.user.repository.CollaboratorRepository;
import com.astro.api.user.repository.CollaboratorPendingCountProjection;
import com.astro.api.user.repository.CollaboratorSummaryProjection;
import com.astro.api.user.dto.response.CollaboratorDetailsResponse;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.user.dto.request.CreateCollaboratorRequest;
import com.astro.api.role.model.Role;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.workspace.event.PreRegisteredCollaboratorsCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import com.astro.api.auth.service.FirebaseIdentityService;
import com.astro.api.user.dto.request.UpdateCollaboratorRequest;
import com.astro.api.user.dto.request.CollaboratorStatusRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class CollaboratorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CollaboratorService.class);
    private final CollaboratorRepository collaboratorRepository;
    private final UserRepository userRepository;
    private final CollaboratorMapper collaboratorMapper;
    private final RoleRepository roleRepository;
    private final UnitRepository unitRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FirebaseIdentityService firebaseIdentityService;
    private final CollaboratorStatusPolicy collaboratorStatusPolicy;
    private final TransactionTemplate transactionTemplate;
    private final UserAvatarService userAvatarService;

    public CollaboratorService(CollaboratorRepository collaboratorRepository, UserRepository userRepository,
                               CollaboratorMapper collaboratorMapper, RoleRepository roleRepository,
                               UnitRepository unitRepository, ApplicationEventPublisher eventPublisher,
                               FirebaseIdentityService firebaseIdentityService,
                               CollaboratorStatusPolicy collaboratorStatusPolicy,
                               PlatformTransactionManager transactionManager, UserAvatarService userAvatarService) {
        this.collaboratorRepository = collaboratorRepository;
        this.userRepository = userRepository;
        this.collaboratorMapper = collaboratorMapper;
        this.roleRepository = roleRepository;
        this.unitRepository = unitRepository;
        this.eventPublisher = eventPublisher;
        this.firebaseIdentityService = firebaseIdentityService;
        this.collaboratorStatusPolicy = collaboratorStatusPolicy;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.userAvatarService = userAvatarService;
    }

    @Transactional(readOnly = true)
    public List<CollaboratorSummaryResponse> findAll(AuthenticatedUser principal) {
        return findAll(principal, false);
    }

    @Transactional(readOnly = true)
    public List<CollaboratorSummaryResponse> findAll(AuthenticatedUser principal, boolean excludeSelf) {
        User manager = requireManager(principal);
        List<CollaboratorSummaryProjection> rows = collaboratorRepository.findAllInScope(principal.firebaseUid(), manager.getUnit().workspace.id)
                .stream().filter(row -> !excludeSelf || !row.getId().equals(manager.getId())).toList();
        Map<Long, String> urls = userAvatarService.findUrls(rows.stream().map(row -> row.getId()).toList());
        return rows.stream().map(row -> collaboratorMapper.toSummary(row, urls.get(row.getId()),
                canManage(manager, row.getId(), row.getUserType()))).toList();
    }

    @Transactional(readOnly = true)
    public List<CollaboratorPendingCountResponse> findPendingCount(AuthenticatedUser principal) {
        requireManager(principal);
        List<CollaboratorPendingCountProjection> rows = collaboratorRepository.findPendingCountInScope(
                principal.firebaseUid());
        Map<Long, String> urls = userAvatarService.findUrls(rows.stream()
                .map(CollaboratorPendingCountProjection::getId).toList());
        return rows.stream().map(row -> collaboratorMapper.toPendingCount(row, urls.get(row.getId()))).toList();
    }

    private User requireManager(AuthenticatedUser principal) {
        if (principal == null || principal.firebaseUid() == null || principal.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Gestor autenticado necessário");
        }
        User manager = userRepository.findByFirebaseUid(principal.firebaseUid())
                .orElseThrow(() -> new AccessDeniedException("Gestor ativo necessário"));
        if (manager.getStatus() != UserStatus.ATIVO
                || (manager.getType() != UserType.GESTOR && manager.getType() != UserType.GESTOR_WORKSPACE)
                || principal.role() == null || !manager.getType().name().equals(principal.role().name())
                || manager.getUnit() == null || manager.getUnit().workspace == null) {
            throw new AccessDeniedException("Gestor ativo com workspace necessário");
        }
        return manager;
    }

    @Transactional(readOnly = true)
    public CollaboratorDetailsResponse findById(Long collaboratorId, AuthenticatedUser principal) {
        User manager = requireManager(principal);
        checkId(collaboratorId);
        return collaboratorRepository.findDetailsInScope(collaboratorId, principal.firebaseUid(), manager.getUnit().workspace.id)
                .map(row -> collaboratorMapper.toDetails(row, userAvatarService.findUrl(row.getId()),
                        canManage(manager, row.getId(), row.getUserType())))
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador não encontrado"));
    }

    private void checkId(Long collaboratorId) {
        if (collaboratorId == null || collaboratorId <= 0) {
            throw new BusinessException("O identificador do colaborador deve ser positivo");
        }
    }

    @Transactional
    public CollaboratorDetailsResponse create(CreateCollaboratorRequest body, AuthenticatedUser principal) {
        User manager = requireManager(principal);
        Long workspaceId = manager.getUnit().workspace.id;
        Role role = requireRole(body.roleId(), workspaceId, true);
        Unit unit = requireUnit(body.unitId(), workspaceId, principal.firebaseUid(), true);
        checkConflicts(body.cpf(), body.email(), null);
        User saved = persist(collaboratorMapper.toNewUser(body, role, unit));
        eventPublisher.publishEvent(new PreRegisteredCollaboratorsCreatedEvent(List.of(saved.getEmail())));
        return collaboratorMapper.toDetails(saved, null, true);
    }

    private Role requireRole(Long roleId, Long workspaceId, boolean requireActive) {
        Role role = roleRepository.findById(roleId).filter(value -> value.getWorkspace() != null
                        && workspaceId.equals(value.getWorkspace().id))
                .orElseThrow(() -> new ResourceNotFoundException("Cargo não encontrado no workspace"));
        if (requireActive && !role.isActive()) {
            throw new BusinessException("O cargo deve estar ativo para uma nova vinculação");
        }
        return role;
    }

    private Unit requireUnit(Long unitId, Long workspaceId, String firebaseUid, boolean requireActive) {
        Unit unit = unitRepository.findById(unitId).filter(value -> value.workspace != null && workspaceId.equals(value.workspace.id))
                .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada no workspace"));
        if (!collaboratorRepository.canAccessUnit(firebaseUid, unitId)) {
            throw new AccessDeniedException("Sem permissão sobre a unidade de destino");
        }
        if (requireActive && !unit.isActive) {
            throw new BusinessException("A unidade deve estar ativa para uma nova vinculação");
        }
        return unit;
    }

    private void checkConflicts(String cpf, String email, Long excludedId) {
        if (collaboratorRepository.cpfExists(cpf, excludedId)) {
            throw new ConflictException("Já existe um usuário com este CPF");
        }
        if (collaboratorRepository.emailExists(email, excludedId)) {
            throw new ConflictException("Já existe um usuário com este e-mail");
        }
    }

    private User persist(User user) {
        try {
            return collaboratorRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                    if ("uq_usuario_cpf".equals(violation.getConstraintName())) {
                        throw new ConflictException("Já existe um usuário com este CPF");
                    }
                    if ("uq_usuario_email".equals(violation.getConstraintName())) {
                        throw new ConflictException("Já existe um usuário com este e-mail");
                    }
                }
            }
            throw exception;
        }
    }

    public CollaboratorDetailsResponse update(Long collaboratorId, UpdateCollaboratorRequest body,
                                              AuthenticatedUser principal) {
        // The template includes the PostgreSQL commit, so compensation also covers commit failures.
        EmailChange[] emailChange = new EmailChange[1];
        try {
            return transactionTemplate.execute(transaction -> {
                User manager = requireManager(principal);
                User collaborator = requireCollaborator(collaboratorId, principal, manager);
                Long workspaceId = manager.getUnit().workspace.id;
                Role role = requireRole(body.roleId(), workspaceId, !body.roleId().equals(collaborator.getRole().getId()));
                Unit unit = requireUnit(body.unitId(), workspaceId, principal.firebaseUid(), !body.unitId().equals(collaborator.getUnit().id));
                checkConflicts(body.cpf(), body.email(), collaboratorId);
                String previousEmail = collaborator.getEmail();
                collaboratorMapper.applyUpdate(collaborator, body, role, unit);
                validateStatus(collaborator, body.status(), workspaceId);
                collaborator.setStatus(body.status());
                User saved = persist(collaborator);
                if (saved.getFirebaseUid() != null && !saved.getFirebaseUid().isBlank()
                        && !body.email().equals(previousEmail)) {
                    FirebaseIdentityService.EmailState previous = firebaseIdentityService.readEmailState(saved.getFirebaseUid());
                    if (previous.email() == null || !previousEmail.equalsIgnoreCase(previous.email())) {
                        throw new ConflictException("O e-mail da autenticação precisa ser reconciliado antes da edição");
                    }
                    emailChange[0] = new EmailChange(saved.getId(), saved.getFirebaseUid(), previous);
                    firebaseIdentityService.updateEmail(saved.getFirebaseUid(), body.email());
                }
                return collaboratorMapper.toDetails(saved, userAvatarService.findUrl(saved.getId()), true);
            });
        } catch (RuntimeException exception) {
            EmailChange change = emailChange[0];
            if (change != null && !(exception instanceof ConflictException)) {
                compensateEmail(change, exception);
            }
            throw exception;
        }
    }

    private User requireCollaborator(Long collaboratorId, AuthenticatedUser principal, User manager) {
        checkId(collaboratorId);
        User collaborator = collaboratorRepository.findForUpdateInScope(collaboratorId, principal.firebaseUid(), manager.getUnit().workspace.id)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador não encontrado"));
        if (!canManage(manager, collaborator.getId(), collaborator.getType() == null ? null : collaborator.getType().name())) {
            throw new AccessDeniedException("Somente o gestor do workspace pode administrar outros gestores");
        }
        return collaborator;
    }

    private boolean canManage(User manager, Long targetId, String targetType) {
        return targetType != null && (manager.getType() == UserType.GESTOR_WORKSPACE
                || UserType.COLABORADOR.name().equals(targetType)
                || (UserType.GESTOR.name().equals(targetType) && manager.getId().equals(targetId)));
    }

    private void compensateEmail(EmailChange change, RuntimeException original) {
        try {
            firebaseIdentityService.restoreEmail(change.firebaseUid(), change.previous());
        } catch (RuntimeException compensationFailure) {
            original.addSuppressed(compensationFailure);
            // Contain inconsistent identities using the existing per-request status authorization.
            try {
                transactionTemplate.executeWithoutResult(transaction -> {
                    if (collaboratorRepository.deactivateAfterEmailFailure(change.collaboratorId(), change.firebaseUid()) != 1) {
                        throw new IllegalStateException("Colaborador não encontrado para contenção da falha");
                    }
                });
            } catch (RuntimeException containmentFailure) {
                original.addSuppressed(containmentFailure);
            }
            LOGGER.error("Falha na compensação de e-mail do colaborador {}; reconciliação operacional necessária",
                    change.collaboratorId(), original);
            throw new IllegalStateException("Falha de sincronização da autenticação do colaborador", original);
        }
    }

    private record EmailChange(Long collaboratorId, String firebaseUid, FirebaseIdentityService.EmailState previous) { }

    @Transactional
    public CollaboratorDetailsResponse changeStatus(Long collaboratorId, CollaboratorStatusRequest body,
                                                    AuthenticatedUser principal) {
        User manager = requireManager(principal);
        User collaborator = requireCollaborator(collaboratorId, principal, manager);
        UserStatus desired = UserStatus.valueOf(body.status().name());
        validateStatus(collaborator, desired, manager.getUnit().workspace.id);
        if (desired != collaborator.getStatus()) {
            collaborator.setStatus(desired);
            persist(collaborator);
        }
        return collaboratorMapper.toDetails(collaborator, userAvatarService.findUrl(collaborator.getId()), true);
    }

    private void validateStatus(User collaborator, UserStatus desired, Long workspaceId) {
        collaboratorStatusPolicy.validate(collaborator, desired);
        if (desired == UserStatus.ATIVO && collaborator.getStatus() != UserStatus.ATIVO) {
            // Fresh scalar reads avoid stale JPA entities and serialize activation against link deactivation.
            Boolean roleActive = collaboratorRepository.lockRoleActive(collaborator.getRole().getId(), workspaceId);
            Boolean unitActive = collaboratorRepository.lockUnitActive(collaborator.getUnit().id, workspaceId);
            if (!Boolean.TRUE.equals(roleActive) || !Boolean.TRUE.equals(unitActive)) {
                throw new BusinessException("Cargo e unidade devem estar ativos no workspace para ativar o colaborador");
            }
        }
    }
}
