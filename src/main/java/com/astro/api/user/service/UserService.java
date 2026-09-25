package com.astro.api.user.service;

import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.user.dto.request.EmailVerificationRequestDto;
import com.astro.api.user.dto.request.UserActivationRequestDto;
import com.astro.api.user.dto.request.AccessKeyVerificationRequestDto;
import com.astro.api.user.dto.response.IdentificatedUserResponseDto;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    public UserService(UserRepository userRepository, StringRedisTemplate redisTemplate){
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    public Optional<UserStatus> findStatusByFirebaseUid(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid).map(User::getStatus);
    }

    public IdentificatedUserResponseDto findTypeAndStatusByEmail(EmailVerificationRequestDto dto) {
        User user = userRepository.findByEmail(dto.email())
                                  .orElseThrow(() -> new ResourceNotFoundException("Este e-mail não está cadastrado no Astro."));

        return new IdentificatedUserResponseDto(
                user.getType(),
                user.getStatus()
        );
    }

    public void activateCollaborator(UserActivationRequestDto dto) {
        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador não encontrado"));

        user.setFirebaseUid(dto.firebaseUid());
        userRepository.save(user);
    }

    public boolean verifyAccessKey(AccessKeyVerificationRequestDto dto) {
        String accountType = userRepository.findAccountTypeByEmail(dto.email());

        if (accountType == null) {
            return false;
        }

        accountType = accountType.toLowerCase(Locale.ROOT);

        if (!"workspace".equals(accountType) && !"colaborador".equals(accountType)) {
            return false;
        }

        String redisKey = "processamento:email:%s:access_key:%s".formatted(accountType, dto.email());
        String storedAccessKey = redisTemplate.opsForValue().get(redisKey);

        return dto.accessKey().equals(storedAccessKey);
    }
}
