package com.astro.api.workspace.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import com.astro.api.workspace.repository.WorkspaceRepository;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceAccessService {
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

    public WorkspaceAccessService(UserRepository userRepository, WorkspaceRepository workspaceRepository) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
    }

    public Workspace requireWorkspace(AuthenticatedUser principal, boolean forUpdate) {
        if (principal == null || principal.role() != Role.GESTOR_WORKSPACE
                || principal.firebaseUid() == null || principal.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Gestor de workspace autenticado necessário");
        }
        User workspaceManager = userRepository.findByFirebaseUid(principal.firebaseUid())
                .filter(user -> user.getType() == UserType.GESTOR_WORKSPACE
                        && user.getStatus() == UserStatus.ATIVO
                        && user.getUnit() != null && user.getUnit().workspace != null)
                .orElseThrow(() -> new AccessDeniedException("Gestor de workspace ativo necessário"));
        Long workspaceId = workspaceManager.getUnit().workspace.id;
        Optional<Workspace> workspace = forUpdate
                ? workspaceRepository.findWithLockById(workspaceId)
                : workspaceRepository.findById(workspaceId);
        return workspace.orElseThrow(() -> new AccessDeniedException("Workspace não disponível"));
    }
}
