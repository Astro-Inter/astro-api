package com.astro.api.user.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import org.springframework.mock.web.MockMultipartFile;
import com.astro.api.common.handler.GlobalExceptionHandler;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;
import com.astro.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserControllerTest {

    @Test
    void shouldReceiveMultipartFileInPutRequest() throws Exception {
        UserService service = mock(UserService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UserController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var user = new AuthenticatedUser("firebase-uid", "colaborador@astro.com", Role.COLABORADOR);
        var file = new MockMultipartFile("file", "foto.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        try {
            mvc.perform(multipart(HttpMethod.PUT, "/user/me/profile-photo").file(file))
                    .andExpect(status().isNoContent());
            verify(service).updateProfilePhoto("firebase-uid", file);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldRejectRequestWithoutFilePart() throws Exception {
        UserService service = mock(UserService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new UserController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(multipart(HttpMethod.PUT, "/user/me/profile-photo"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void shouldUpdatePhotoUsingAuthenticatedUsersFirebaseUid() {
        UserService userService = mock(UserService.class);
        UserController controller = new UserController(userService);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                "firebase-uid",
                "colaborador@astro.com",
                Role.COLABORADOR);
        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        ResponseEntity<Void> response = controller.updateProfilePhoto(authenticatedUser, file);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(userService).updateProfilePhoto("firebase-uid", file);
    }
}
