package com.astro.api.service;

import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.storage.R2StorageService;
import com.astro.api.conformity.repository.NrDocumentRepository;
import com.astro.api.user.mapper.UserMapper;
import com.astro.api.user.dto.request.EmailVerificationRequestDto;
import com.astro.api.user.dto.request.UserActivationRequestDto;
import com.astro.api.user.dto.request.AccessKeyVerificationRequestDto;
import com.astro.api.user.dto.response.IdentificatedUserResponseDto;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.repository.UserProfilePhotoRepository;
import com.astro.api.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private NrDocumentRepository nrDocumentRepository;
    private UserProfilePhotoRepository userProfilePhotoRepository;
    private UserMapper userMapper;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        nrDocumentRepository = mock(NrDocumentRepository.class);
        userProfilePhotoRepository = mock(UserProfilePhotoRepository.class);
        userMapper = mock(UserMapper.class);
        userService = new UserService(userRepository, redisTemplate, nrDocumentRepository, userProfilePhotoRepository, userMapper,
                mock(R2StorageService.class), mock(com.astro.api.user.service.UserAvatarService.class));
        ReflectionTestUtils.setField(userService, "workspaceAccessTokenPrefix", "workspace:");
        ReflectionTestUtils.setField(userService, "collaboratorAccessTokenPrefix", "collaborator:");
    }

    @Test
    void shouldReturnStatusWhenFirebaseUidExists() {
        User user = new User();
        user.setStatus(UserStatus.ATIVO);

        when(userRepository.findByFirebaseUid("firebase-uid"))
                .thenReturn(Optional.of(user));

        Optional<UserStatus> status = userService.findStatusByFirebaseUid("firebase-uid");

        assertEquals(Optional.of(UserStatus.ATIVO), status);
        verify(userRepository).findByFirebaseUid("firebase-uid");
    }

    @Test
    void shouldReturnEmptyWhenFirebaseUidDoesNotExist() {
        when(userRepository.findByFirebaseUid("inexistente"))
                .thenReturn(Optional.empty());

        Optional<UserStatus> status = userService.findStatusByFirebaseUid("inexistente");

        assertFalse(status.isPresent());
        verify(userRepository).findByFirebaseUid("inexistente");
    }

    @Test
    void shouldReturnTypeAndStatusWhenEmailExists() {
        EmailVerificationRequestDto dto = new EmailVerificationRequestDto("colaborador@astro.com");
        User user = new User();
        user.setType(UserType.COLABORADOR);
        user.setStatus(UserStatus.PRE_CADASTRADO);

        when(userRepository.findByEmail(dto.email()))
                .thenReturn(Optional.of(user));

        IdentificatedUserResponseDto response = userService.findTypeAndStatusByEmail(dto);

        assertEquals(new IdentificatedUserResponseDto(UserType.COLABORADOR, UserStatus.PRE_CADASTRADO), response);
        verify(userRepository).findByEmail(dto.email());
    }

    @Test
    void shouldThrowWhenEmailDoesNotBelongToAnyUser() {
        EmailVerificationRequestDto dto = new EmailVerificationRequestDto("inexistente@astro.com");

        when(userRepository.findByEmail(dto.email()))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.findTypeAndStatusByEmail(dto)
        );

        assertEquals("Este e-mail não está cadastrado no Astro.", exception.getMessage());
        verify(userRepository).findByEmail(dto.email());
    }

    @Test
    void shouldUpdateFirebaseUidWhenActivatingCollaborator() {
        UserActivationRequestDto dto = new UserActivationRequestDto("colaborador@astro.com", "novo-firebase-uid");
        User user = new User();
        user.setFirebaseUid("uid-anterior");
        user.setStatus(UserStatus.PRE_CADASTRADO);

        when(userRepository.findByEmail(dto.email()))
                .thenReturn(Optional.of(user));

        userService.activateCollaborator(dto);

        assertEquals(dto.firebaseUid(), user.getFirebaseUid());
        assertEquals(UserStatus.PRE_CADASTRADO, user.getStatus());
        verify(userRepository).findByEmail(dto.email());
        verify(userRepository).save(user);
    }

    @Test
    void shouldThrowWhenActivatingCollaboratorThatDoesNotExist() {
        UserActivationRequestDto dto = new UserActivationRequestDto("inexistente@astro.com", "novo-firebase-uid");

        when(userRepository.findByEmail(dto.email()))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> userService.activateCollaborator(dto)
        );

        assertEquals("Colaborador não encontrado", exception.getMessage());
        verify(userRepository).findByEmail(dto.email());
        verifyNoMoreInteractions(userRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"123456", "654321"})
    void shouldVerifyWorkspaceKeyWithoutQueryingUsers(String storedKey) {
        AccessKeyVerificationRequestDto dto = new AccessKeyVerificationRequestDto("  GESTOR@Astro.com  ", "123456");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("workspace:gestor@astro.com")).thenReturn(storedKey);

        assertEquals("123456".equals(storedKey), userService.verifyAccessKey(dto, UserType.GESTOR_WORKSPACE));
        verify(valueOperations).get("workspace:gestor@astro.com");
        verifyNoInteractions(userRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"123456", "654321"})
    void shouldVerifyPreRegisteredCollaboratorKey(String storedKey) {
        AccessKeyVerificationRequestDto dto = new AccessKeyVerificationRequestDto("  COLABORADOR@Astro.com  ", "123456");
        User user = collaborator(UserStatus.PRE_CADASTRADO);
        when(userRepository.findByEmailIgnoreCase("colaborador@astro.com")).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("collaborator:colaborador@astro.com")).thenReturn(storedKey);

        assertEquals("123456".equals(storedKey), userService.verifyAccessKey(dto, UserType.COLABORADOR));
        verify(userRepository).findByEmailIgnoreCase("colaborador@astro.com");
        verifyNoMoreInteractions(userRepository);
        verify(valueOperations).get("collaborator:colaborador@astro.com");
    }

    @Test
    void shouldRejectMissingCollaboratorBeforeReadingRedis() {
        AccessKeyVerificationRequestDto dto = new AccessKeyVerificationRequestDto("inexistente@astro.com", "123456");
        when(userRepository.findByEmailIgnoreCase(dto.email())).thenReturn(Optional.empty());

        assertFalse(userService.verifyAccessKey(dto, UserType.COLABORADOR));
        verifyNoInteractions(redisTemplate);
    }

    @ParameterizedTest
    @EnumSource(value = UserType.class, names = {"GESTOR", "GESTOR_WORKSPACE"})
    void shouldRejectOtherAccountTypesInCollaboratorFlow(UserType type) {
        User user = collaborator(UserStatus.PRE_CADASTRADO);
        user.setType(type);
        when(userRepository.findByEmailIgnoreCase("user@astro.com")).thenReturn(Optional.of(user));

        assertFalse(userService.verifyAccessKey(new AccessKeyVerificationRequestDto("user@astro.com", "123456"), UserType.COLABORADOR));
        verifyNoInteractions(redisTemplate);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"ATIVO", "DESATIVADO"})
    void shouldRejectCollaboratorOutsidePreRegistration(UserStatus status) {
        when(userRepository.findByEmailIgnoreCase("user@astro.com")).thenReturn(Optional.of(collaborator(status)));

        assertFalse(userService.verifyAccessKey(new AccessKeyVerificationRequestDto("user@astro.com", "123456"), UserType.COLABORADOR));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void shouldRejectManagerFlowWithoutReadingDatabaseOrRedis() {
        assertFalse(userService.verifyAccessKey(new AccessKeyVerificationRequestDto("gestor@astro.com", "123456"), UserType.GESTOR));
        verifyNoInteractions(userRepository, redisTemplate);
    }

    @Test
    void shouldNotAcceptCodesFromTheOtherFlow() {
        AccessKeyVerificationRequestDto dto = new AccessKeyVerificationRequestDto("user@astro.com", "654321");
        when(userRepository.findByEmailIgnoreCase(dto.email())).thenReturn(Optional.of(collaborator(UserStatus.PRE_CADASTRADO)));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("workspace:user@astro.com")).thenReturn("654321");
        when(valueOperations.get("collaborator:user@astro.com")).thenReturn("123456");

        assertFalse(userService.verifyAccessKey(dto, UserType.COLABORADOR));
        AccessKeyVerificationRequestDto collaboratorCode = new AccessKeyVerificationRequestDto(dto.email(), "123456");
        assertFalse(userService.verifyAccessKey(collaboratorCode, UserType.GESTOR_WORKSPACE));
    }

    private User collaborator(UserStatus status) {
        User user = new User();
        user.setType(UserType.COLABORADOR);
        user.setStatus(status);
        return user;
    }
}
