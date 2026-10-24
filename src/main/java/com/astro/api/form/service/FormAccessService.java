package com.astro.api.form.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormScope;
import com.astro.api.form.model.FormReadFilter;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.model.FormWriteCondition;

import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormAccessService {
    private final UserRepository userRepository;

    public FormAccessService(UserRepository userRepository) { this.userRepository = userRepository; }

    @Transactional(readOnly = true)
    public FormScope requireManager(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.firebaseUid() == null || authenticatedUser.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Gestor autenticado necessário");
        }
        User user = userRepository.findByFirebaseUid(authenticatedUser.firebaseUid())
                .orElseThrow(() -> new AccessDeniedException("Gestor ativo necessário"));
        if (user.getId() == null || user.getStatus() != UserStatus.ATIVO
                || (user.getType() != UserType.GESTOR && user.getType() != UserType.GESTOR_WORKSPACE)
                || authenticatedUser.role() == null || !user.getType().name().equals(authenticatedUser.role().name())
                || user.getUnit() == null || !user.getUnit().isActive
                || user.getUnit().workspace == null || user.getUnit().workspace.id == null) {
            throw new AccessDeniedException("Gestor ativo com unidade e workspace válidos necessário");
        }
        return new FormScope(user.getId(), user.getUnit().workspace.id, user.getType() == UserType.GESTOR_WORKSPACE);
    }

    public FormReadFilter readFilter(FormScope formScope) {
        if (formScope.workspaceManager()) {
            return new FormReadFilter(formScope.workspaceId(), null, null, List.of());
        }
        return new FormReadFilter(formScope.workspaceId(), formScope.managerId(), formScope.managerId(),
                List.of(FormStatus.PENDING, FormStatus.COMPLETED));
    }

    public FormReadFilter recipientFilter(FormScope formScope) {
        return new FormReadFilter(formScope.workspaceId(), null, formScope.managerId(),
                List.of(FormStatus.PENDING, FormStatus.COMPLETED));
    }

    public FormWriteCondition writeCondition(Form form, FormScope formScope) {
        Long senderManagerId = formScope.workspaceManager() ? null : formScope.managerId();
        return new FormWriteCondition(form.getId(), formScope.workspaceId(), senderManagerId,
                form.getVersion(), form.getStatus());
    }

    public void requireManagement(Form form, FormScope formScope) {
        if (!formScope.workspaceId().equals(form.getWorkspaceId())
                || (!formScope.workspaceManager() && !formScope.managerId().equals(form.getSenderManagerId()))) {
            throw new AccessDeniedException("Somente o remetente ou o gestor do workspace pode gerenciar o formulário");
        }
    }
}
