package com.astro.api.user.service;

import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.conformidade.repository.NrDocumentRepository;
import com.astro.api.user.dto.request.ProfilePhotoRequest;
import com.astro.api.user.mapper.UserMapper;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserProfilePhoto;
import com.astro.api.user.repository.UserProfilePhotoRepository;
import com.astro.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserProfilePhotoServiceTest {

    private UserRepository userRepository;
    private UserProfilePhotoRepository userProfilePhotoRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userProfilePhotoRepository = mock(UserProfilePhotoRepository.class);
        userService = new UserService(
                userRepository,
                mock(StringRedisTemplate.class),
                mock(NrDocumentRepository.class),
                userProfilePhotoRepository,
                new UserMapper());
    }

    @Test
    void shouldSavePhotoPathAssociatedWithAuthenticatedUser() {
        User user = new User();
        user.setId(20L);
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(user));
        when(userProfilePhotoRepository.findById(20L)).thenReturn(Optional.empty());

        userService.updateProfilePhoto("firebase-uid", new ProfilePhotoRequest("usuarios/20/foto-perfil.jpg"));

        org.mockito.ArgumentCaptor<UserProfilePhoto> photoCaptor =
                org.mockito.ArgumentCaptor.forClass(UserProfilePhoto.class);
        verify(userProfilePhotoRepository).save(photoCaptor.capture());
        assertEquals(20L, photoCaptor.getValue().getUserId());
        assertEquals("usuarios/20/foto-perfil.jpg", photoCaptor.getValue().getObjectPath());
    }

    @Test
    void shouldReplacePathWhenAuthenticatedUserAlreadyHasProfilePhoto() {
        User user = new User();
        user.setId(20L);
        UserProfilePhoto existingPhoto = new UserProfilePhoto();
        existingPhoto.setUserId(20L);
        existingPhoto.setObjectPath("usuarios/20/foto-antiga.jpg");
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(user));
        when(userProfilePhotoRepository.findById(20L)).thenReturn(Optional.of(existingPhoto));

        userService.updateProfilePhoto("firebase-uid", new ProfilePhotoRequest("usuarios/20/foto-nova.jpg"));

        verify(userProfilePhotoRepository).save(existingPhoto);
        assertEquals("usuarios/20/foto-nova.jpg", existingPhoto.getObjectPath());
    }

    @Test
    void shouldFailWhenTokenDoesNotBelongToAUser() {
        when(userRepository.findByFirebaseUid("unknown-uid")).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> userService.updateProfilePhoto("unknown-uid", new ProfilePhotoRequest("foto.jpg")));

        assertEquals("Usuário não encontrado", exception.getMessage());
        verifyNoInteractions(userProfilePhotoRepository);
    }
}
