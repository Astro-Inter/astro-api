package com.astro.api.service;

import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.user.dto.request.EmailVerificationRequestDto;
import com.astro.api.user.dto.request.UserActivationRequestDto;
import com.astro.api.user.dto.request.AccessKeyVerificationRequestDto;
import com.astro.api.user.dto.response.IdentificatedUserResponseDto;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        userService = new UserService(userRepository, redisTemplate);
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

    @Test
    void shouldReturnTrueWhenAccessKeyMatchesRedisValue() {
        AccessKeyVerificationRequestDto dto = new AccessKeyVerificationRequestDto("colaborador@astro.com", "123456");
        String redisKey = "processamento:email:colaborador:access_key:colaborador@astro.com";

        when(userRepository.findAccountTypeByEmail(dto.email())).thenReturn("colaborador");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(redisKey)).thenReturn("123456");

        boolean isValid = userService.verifyAccessKey(dto);

        assertEquals(true, isValid);
        verify(userRepository).findAccountTypeByEmail(dto.email());
        verify(redisTemplate).opsForValue();
        verify(valueOperations).get(redisKey);
    }

    @Test
    void shouldReturnFalseWhenAccessKeyDoesNotMatchRedisValue() {
        AccessKeyVerificationRequestDto dto = new AccessKeyVerificationRequestDto("workspace@astro.com", "123456");
        String redisKey = "processamento:email:workspace:access_key:workspace@astro.com";

        when(userRepository.findAccountTypeByEmail(dto.email())).thenReturn("workspace");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(redisKey)).thenReturn("654321");

        boolean isValid = userService.verifyAccessKey(dto);

        assertEquals(false, isValid);
        verify(valueOperations).get(redisKey);
    }

    @Test
    void shouldReturnFalseWhenAccountTypeIsInvalid() {
        AccessKeyVerificationRequestDto dto = new AccessKeyVerificationRequestDto("inexistente@astro.com", "123456");

        when(userRepository.findAccountTypeByEmail(dto.email())).thenReturn(null);

        boolean isValid = userService.verifyAccessKey(dto);

        assertEquals(false, isValid);
        verify(userRepository).findAccountTypeByEmail(dto.email());
        verifyNoInteractions(redisTemplate);
    }
}
