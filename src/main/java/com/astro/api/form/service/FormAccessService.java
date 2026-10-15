package com.astro.api.form.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormScope;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormAccessService {
    private final UserRepository users;

    public FormAccessService(UserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public FormScope requireManager(AuthenticatedUser principal) {
        if (principal == null || principal.firebaseUid() == null || principal.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Gestor autenticado necessário");
        }
        User user = users.findByFirebaseUid(principal.firebaseUid())
                .orElseThrow(() -> new AccessDeniedException("Gestor ativo necessário"));
        if (user.getId() == null || user.getStatus() != UserStatus.ATIVO
                || (user.getType() != UserType.GESTOR && user.getType() != UserType.GESTOR_WORKSPACE)
                || principal.role() == null || !user.getType().name().equals(principal.role().name())
                || user.getUnit() == null || !user.getUnit().isActive
                || user.getUnit().workspace == null || user.getUnit().workspace.id == null) {
            throw new AccessDeniedException("Gestor ativo com unidade e workspace válidos necessário");
        }
        return new FormScope(user.getId(), user.getUnit().workspace.id, user.getType() == UserType.GESTOR_WORKSPACE);
    }

    public void requireManagement(Form form, FormScope scope) {
        if (!scope.workspaceId().equals(form.getWorkspaceId())
                || (!scope.workspaceManager() && !scope.managerId().equals(form.getSenderManagerId()))) {
            throw new AccessDeniedException("Somente o remetente ou o gestor do workspace pode gerenciar o formulário");
        }
    }
}
