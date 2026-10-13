package com.astro.api.collaborator.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.collaborator.dto.response.CollaboratorSummaryResponse;
import com.astro.api.collaborator.mapper.CollaboratorMapper;
import com.astro.api.collaborator.repository.CollaboratorRepository;
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

    public CollaboratorService(CollaboratorRepository repository, UserRepository users, CollaboratorMapper mapper) {
        this.repository = repository;
        this.users = users;
        this.mapper = mapper;
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
}
