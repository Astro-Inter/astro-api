package com.astro.api.user.service;

import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.user.dto.request.EmailVerificationRequestDto;
import com.astro.api.user.dto.response.IdentificatedUserResponseDto;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    public Optional<UserStatus> findStatusByFirebaseUid(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid).map(User::getStatus);
    }

    public IdentificatedUserResponseDto findTypeAndStatusByEmail(EmailVerificationRequestDto dto) {
        User user = userRepository.findByEmail(dto.email())
                                  .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        return new IdentificatedUserResponseDto(
                user.getType(),
                user.getStatus()
        );
    }
}
