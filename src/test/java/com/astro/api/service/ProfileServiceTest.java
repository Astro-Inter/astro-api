package com.astro.api.service;

import com.astro.api.role.model.Role;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.storage.R2StorageService;
import com.astro.api.conformidade.model.NrDocument;
import com.astro.api.conformidade.repository.NrDocumentRepository;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.dto.response.UserProfileResponse;
import com.astro.api.user.mapper.UserMapper;
import com.astro.api.user.model.User;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.repository.UserProfilePhotoRepository;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.repository.UserNrValidityProjection;
import com.astro.api.user.service.UserService;
import com.astro.api.user.service.UserAvatarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    private UserRepository userRepository;
    private NrDocumentRepository nrDocumentRepository;
    private UserProfilePhotoRepository userProfilePhotoRepository;
    private UserService userService;
    private R2StorageService r2StorageService;
    private UserAvatarService avatars;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        nrDocumentRepository = mock(NrDocumentRepository.class);
        userProfilePhotoRepository = mock(UserProfilePhotoRepository.class);
        r2StorageService = mock(R2StorageService.class);
        avatars = mock(UserAvatarService.class);
        userService = new UserService(userRepository, mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                nrDocumentRepository, userProfilePhotoRepository, new UserMapper(), r2StorageService, avatars);
    }

    @Test
    void shouldReturnAuthenticatedUsersProfileAndActiveNrsFromTheirRole() {
        User user = userWithRole();
        NrDocument nr = new NrDocument();
        nr.setId(35);
        nr.setDescription("Trabalho em altura");
        nr.setObjective("Proteger trabalhadores contra quedas");
        nr.setApplicability("Atividades acima de dois metros");

        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(user));
        when(avatars.findUrl(20L))
                .thenReturn("https://r2.example.com/astro/usuarios/20/foto-perfil.jpg?X-Amz-Signature=signature");
        when(userRepository.findNrValiditiesByRoleIdAndUserId(10L, 20L))
                .thenReturn(List.of(projection(35, LocalDate.of(2027, 10, 3))));
        when(nrDocumentRepository.findAllById(any())).thenReturn(List.of(nr));

        UserProfileResponse profile = userService.findProfileByFirebaseUid("firebase-uid");

        assertEquals("Bruno Baptista", profile.nome());
        assertEquals("Engenheiro de Segurança", profile.cargo());
        assertEquals("Osasco", profile.unidade());
        assertEquals("PRESENCIAL", profile.modalidade());
        assertEquals("bruno@astro.com", profile.email());
        assertEquals("https://r2.example.com/astro/usuarios/20/foto-perfil.jpg?X-Amz-Signature=signature",
                profile.profilePhotoUrl());
        verify(avatars).findUrl(20L);
        verifyNoInteractions(r2StorageService, userProfilePhotoRepository);
        assertEquals(List.of(new UserProfileResponse.NrProfileResponse(
                35,
                LocalDate.of(2027, 10, 3),
                "Trabalho em altura",
                "Proteger trabalhadores contra quedas",
                "Atividades acima de dois metros"
        )), profile.nrs());
    }

    @Test
    void shouldReturnNoNrsWhenUserHasNoRole() {
        User user = new User();
        user.setName("Bruno Baptista");
        user.setEmail("bruno@astro.com");
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(user));

        UserProfileResponse profile = userService.findProfileByFirebaseUid("firebase-uid");

        assertEquals(List.of(), profile.nrs());
        assertNull(profile.profilePhotoUrl());
        verifyNoInteractions(nrDocumentRepository);
    }

    @Test
    void shouldFailWhenTokenDoesNotBelongToAUser() {
        when(userRepository.findByFirebaseUid("admin-uid")).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> userService.findProfileByFirebaseUid("admin-uid"));

        assertEquals("Usuário não encontrado", exception.getMessage());
        verify(userRepository).findByFirebaseUid("admin-uid");
        verifyNoInteractions(nrDocumentRepository, userProfilePhotoRepository, r2StorageService, avatars);
    }

    private User userWithRole() {
        Role role = new Role();
        role.setId(10L);
        role.setName("Engenheiro de Segurança");

        Unit unit = new Unit();
        unit.name = "Osasco";

        User user = new User();
        user.setId(20L);
        user.setName("Bruno Baptista");
        user.setEmail("bruno@astro.com");
        user.setWorkModel(WorkModel.PRESENCIAL);
        user.setRole(role);
        user.setUnit(unit);
        return user;
    }

    private UserNrValidityProjection projection(Integer nrId, LocalDate validity) {
        return new UserNrValidityProjection() {
            @Override
            public Integer getNrId() {
                return nrId;
            }

            @Override
            public LocalDate getValidity() {
                return validity;
            }
        };
    }
}
