package com.astro.api.auth.service;

import com.astro.api.auth.repository.AuthRepository;
import com.astro.api.auth.security.Role;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthorizationService {

    private final AuthRepository authRepository;
    private final UserService userService;

    public AuthorizationService(AuthRepository authRepository, UserService userService) {
        this.authRepository = authRepository;
        this.userService = userService;
    }

    public Optional<Role> getRole(String firebaseUid) {
        String accessLevel = authRepository.findAccessLevel(firebaseUid);

        if (accessLevel.equals("SEM_ACESSO")) {
            return Optional.empty();
        }

        if (accessLevel.equals(Role.ADMIN.name())) {
            return Optional.of(Role.ADMIN);
        }

        Optional<UserStatus> status = userService.findStatusByFirebaseUid(firebaseUid);

        if (status.isEmpty() || status.get() != UserStatus.ATIVO){
            return Optional.empty();
        }

        return Optional.of(Role.valueOf(accessLevel));
    }
}
