package com.astro.api.user.service;

import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.storage.R2StorageService;
import com.astro.api.conformidade.repository.NrDocumentRepository;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;

class UserProfilePhotoServiceTest {

    private UserRepository userRepository;
    private UserProfilePhotoRepository userProfilePhotoRepository;
    private UserService userService;
    private R2StorageService storage;
    private MultipartFile file;
    private UserAvatarService avatars;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userProfilePhotoRepository = mock(UserProfilePhotoRepository.class);
        storage = mock(R2StorageService.class);
        avatars = mock(UserAvatarService.class);
        file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", new byte[]{1});
        when(storage.uploadProfilePhoto(20L, file)).thenReturn("usuarios/20/foto-nova.jpg");
        userService = new UserService(
                userRepository,
                mock(StringRedisTemplate.class),
                mock(NrDocumentRepository.class),
                userProfilePhotoRepository,
                new UserMapper(), storage, avatars);
    }

    @Test
    void shouldSavePhotoPathAssociatedWithAuthenticatedUser() {
        User user = new User();
        user.setId(20L);
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(user));
        when(userProfilePhotoRepository.findById(20L)).thenReturn(Optional.empty());

        userService.updateProfilePhoto("firebase-uid", file);

        org.mockito.ArgumentCaptor<UserProfilePhoto> photoCaptor =
                org.mockito.ArgumentCaptor.forClass(UserProfilePhoto.class);
        verify(userProfilePhotoRepository).saveAndFlush(photoCaptor.capture());
        verify(storage).uploadProfilePhoto(20L, file);
        verify(storage, never()).deleteObject(any());
        assertEquals(20L, photoCaptor.getValue().getUserId());
        assertEquals("usuarios/20/foto-nova.jpg", photoCaptor.getValue().getObjectPath());
        verify(avatars).invalidate(20L);
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

        userService.updateProfilePhoto("firebase-uid", file);

        verify(userProfilePhotoRepository).saveAndFlush(existingPhoto);
        assertEquals("usuarios/20/foto-nova.jpg", existingPhoto.getObjectPath());
        var order = inOrder(userProfilePhotoRepository, storage, avatars);
        order.verify(storage).uploadProfilePhoto(20L, file);
        order.verify(userProfilePhotoRepository).saveAndFlush(existingPhoto);
        order.verify(avatars).invalidate(20L);
        order.verify(storage).deleteObject("usuarios/20/foto-antiga.jpg");
    }

    @Test
    void shouldFailWhenTokenDoesNotBelongToAUser() {
        when(userRepository.findByFirebaseUid("unknown-uid")).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> userService.updateProfilePhoto("unknown-uid", file));

        assertEquals("Usuário não encontrado", exception.getMessage());
        verifyNoInteractions(userProfilePhotoRepository, storage, avatars);
    }

    @Test
    void shouldNotPersistPhotoWhenUploadFails() {
        User user = new User();
        user.setId(20L);
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(user));
        when(storage.uploadProfilePhoto(20L, file)).thenThrow(new IllegalStateException("R2 indisponível"));

        assertThrows(IllegalStateException.class, () -> userService.updateProfilePhoto("firebase-uid", file));
        verify(userProfilePhotoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(avatars);
    }

    @Test
    void shouldReportInvalidationFailureWithoutDeletingThePreviouslyCachedPhotoObject() {
        User user = new User();
        user.setId(20L);
        UserProfilePhoto previous = new UserProfilePhoto();
        previous.setUserId(20L);
        previous.setObjectPath("usuarios/20/foto-antiga.jpg");
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(user));
        when(userProfilePhotoRepository.findById(20L)).thenReturn(Optional.of(previous));
        var failure = new org.springframework.data.redis.RedisConnectionFailureException("Redis indisponível");
        doThrow(failure).when(avatars).invalidate(20L);

        assertEquals(failure, assertThrows(org.springframework.data.redis.RedisConnectionFailureException.class,
                () -> userService.updateProfilePhoto("firebase-uid", file)));

        verify(userProfilePhotoRepository).saveAndFlush(previous);
        verify(storage, never()).deleteObject(any());
    }

    @Test
    void shouldRemoveUploadedObjectWhenDatabaseFails() {
        User user = new User();
        user.setId(20L);
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(user));
        UserProfilePhoto previousPhoto = new UserProfilePhoto();
        previousPhoto.setUserId(20L);
        previousPhoto.setObjectPath("usuarios/20/foto-antiga.jpg");
        when(userProfilePhotoRepository.findById(20L)).thenReturn(Optional.of(previousPhoto));
        doThrow(new IllegalStateException("Banco indisponível"))
                .when(userProfilePhotoRepository).saveAndFlush(any());

        assertThrows(IllegalStateException.class, () -> userService.updateProfilePhoto("firebase-uid", file));
        verify(storage).deleteObject("usuarios/20/foto-nova.jpg");
        verify(storage, never()).deleteObject("usuarios/20/foto-antiga.jpg");
        verifyNoInteractions(avatars);
    }

    @Test
    void shouldPreserveDatabaseFailureWhenCleanupAlsoFails() {
        User user = new User();
        user.setId(20L);
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(user));
        IllegalStateException databaseFailure = new IllegalStateException("Banco indisponível");
        doThrow(databaseFailure).when(userProfilePhotoRepository).saveAndFlush(any());
        doThrow(new IllegalStateException("R2 indisponível"))
                .when(storage).deleteObject("usuarios/20/foto-nova.jpg");

        assertEquals(databaseFailure, assertThrows(IllegalStateException.class,
                () -> userService.updateProfilePhoto("firebase-uid", file)));
        assertEquals(1, databaseFailure.getSuppressed().length);
    }
}
