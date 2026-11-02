package com.astro.api.auth.service;

import com.astro.api.auth.repository.AuthRepository;
import com.astro.api.user.service.UserService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class AuthorizationServiceTest {
    @Test
    void registersUidWithoutLookingUpInternalUserId() {
        AuthRepository repository = mock(AuthRepository.class);
        UserService users = mock(UserService.class);
        AuthorizationService service = new AuthorizationService(repository, users);

        service.registerDailyAccess("firebase-uid");

        verify(repository).registerDailyAccess("firebase-uid");
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(users);
    }
}
