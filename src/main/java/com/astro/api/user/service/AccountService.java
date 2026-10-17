package com.astro.api.user.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.user.dto.response.AccountSummaryResponse;
import com.astro.api.user.dto.response.AccountDetailsResponse;
import com.astro.api.user.mapper.AccountMapper;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final UserAvatarService avatars;
    private final AccountMapper mapper;

    public AccountService(UserRepository users, UserAvatarService avatars, AccountMapper mapper) {
        this.users = users;
        this.avatars = avatars;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public AccountSummaryResponse findSummary(AuthenticatedUser principal) {
        User user = requireAccount(principal);
        return mapper.toSummary(user, avatars.findUrl(user.getId()));
    }

    @Transactional(readOnly = true)
    public AccountDetailsResponse findDetails(AuthenticatedUser principal) {
        User user = requireAccount(principal);
        return mapper.toDetails(user, avatars.findUrl(user.getId()));
    }

    private User requireAccount(AuthenticatedUser principal) {
        requirePrincipal(principal);
        User user = users.findByFirebaseUid(principal.firebaseUid())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        validateAccount(user, principal);
        return user;
    }

    private void requirePrincipal(AuthenticatedUser principal) {
        if (principal == null || principal.firebaseUid() == null || principal.firebaseUid().isBlank()
                || (principal.role() != Role.GESTOR && principal.role() != Role.GESTOR_WORKSPACE)) {
            throw new AccessDeniedException("Gestor autenticado necessário");
        }
    }

    private void validateAccount(User user, AuthenticatedUser principal) {
        if (user.getStatus() != UserStatus.ATIVO || user.getType() == null
                || !user.getType().name().equals(principal.role().name())
                || user.getUnit() == null || user.getUnit().workspace == null
                || user.getUnit().workspace.id == null) {
            throw new AccessDeniedException("Gestor ativo com workspace necessário");
        }
        if (user.getRole() != null && (user.getRole().getWorkspace() == null
                || !user.getUnit().workspace.id.equals(user.getRole().getWorkspace().id))) {
            throw new AccessDeniedException("Cargo fora do workspace da conta");
        }
    }
}
