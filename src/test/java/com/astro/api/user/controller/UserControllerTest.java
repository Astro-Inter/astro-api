package com.astro.api.user.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.user.dto.request.ProfilePhotoRequest;
import com.astro.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserControllerTest {

    @Test
    void shouldUpdatePhotoUsingAuthenticatedUsersFirebaseUid() {
        UserService userService = mock(UserService.class);
        UserController controller = new UserController(userService);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                "firebase-uid",
                "colaborador@astro.com",
                Role.COLABORADOR);
        ProfilePhotoRequest request = new ProfilePhotoRequest("usuarios/20/foto-perfil.jpg");

        ResponseEntity<Void> response = controller.updateProfilePhoto(authenticatedUser, request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(userService).updateProfilePhoto("firebase-uid", request);
    }
}
