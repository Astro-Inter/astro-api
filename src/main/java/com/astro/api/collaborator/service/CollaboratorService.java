package com.astro.api.collaborator.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.collaborator.dto.response.CollaboratorSummaryResponse;
import com.astro.api.collaborator.mapper.CollaboratorMapper;
import com.astro.api.collaborator.repository.CollaboratorRepository;
import com.astro.api.collaborator.dto.response.CollaboratorDetailsResponse;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.collaborator.dto.request.CreateCollaboratorRequest;
import com.astro.api.role.model.Role;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.workspace.event.PreRegisteredCollaboratorsCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CollaboratorService {
    private final CollaboratorRepository repository;
    private final UserRepository users;
    private final CollaboratorMapper mapper;
    private final RoleRepository roles;
    private final UnitRepository units;
    private final ApplicationEventPublisher events;

    public CollaboratorService(CollaboratorRepository repository, UserRepository users, CollaboratorMapper mapper,
                               RoleRepository roles, UnitRepository units, ApplicationEventPublisher events) {
        this.repository = repository;
        this.users = users;
        this.mapper = mapper;
        this.roles = roles;
        this.units = units;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<CollaboratorSummaryResponse> findAll(AuthenticatedUser principal) {
        User manager = requireManager(principal);
        return repository.findAllInScope(principal.firebaseUid(), manager.getUnit().workspace.id)
                .stream().map(mapper::toSummary).toList();
    }

    private User requireManager(AuthenticatedUser principal) {
        if (principal == null || principal.firebaseUid() == null || principal.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Gestor autenticado necessário");
        }
        User manager = users.findByFirebaseUid(principal.firebaseUid())
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
        return repository.findDetailsInScope(collaboratorId, principal.firebaseUid(), manager.getUnit().workspace.id)
                .map(mapper::toDetails)
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
        User saved = persist(mapper.toNewUser(body, role, unit));
        events.publishEvent(new PreRegisteredCollaboratorsCreatedEvent(List.of(saved.getEmail())));
        return mapper.toDetails(saved);
    }

    private Role requireRole(Long roleId, Long workspaceId, boolean requireActive) {
        Role role = roles.findById(roleId).filter(value -> value.getWorkspace() != null
                        && workspaceId.equals(value.getWorkspace().id))
                .orElseThrow(() -> new ResourceNotFoundException("Cargo não encontrado no workspace"));
        if (requireActive && !role.isActive()) {
            throw new BusinessException("O cargo deve estar ativo para uma nova vinculação");
        }
        return role;
    }

    private Unit requireUnit(Long unitId, Long workspaceId, String firebaseUid, boolean requireActive) {
        Unit unit = units.findById(unitId).filter(value -> value.workspace != null && workspaceId.equals(value.workspace.id))
                .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada no workspace"));
        if (!repository.canAccessUnit(firebaseUid, unitId)) {
            throw new AccessDeniedException("Sem permissão sobre a unidade de destino");
        }
        if (requireActive && !unit.isActive) {
            throw new BusinessException("A unidade deve estar ativa para uma nova vinculação");
        }
        return unit;
    }

    private void checkConflicts(String cpf, String email, Long excludedId) {
        if (repository.cpfExists(cpf, excludedId)) {
            throw new ConflictException("Já existe um usuário com este CPF");
        }
        if (repository.emailExists(email, excludedId)) {
            throw new ConflictException("Já existe um usuário com este e-mail");
        }
    }

    private User persist(User user) {
        try {
            return repository.saveAndFlush(user);
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
}
