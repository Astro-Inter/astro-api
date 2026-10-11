package com.astro.api.user.service;

import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.storage.R2StorageService;
import com.astro.api.conformidade.model.NrDocument;
import com.astro.api.conformidade.repository.NrDocumentRepository;
import com.astro.api.user.dto.request.EmailVerificationRequestDto;
import com.astro.api.user.dto.request.UserActivationRequestDto;
import com.astro.api.user.dto.request.AccessKeyVerificationRequestDto;
import com.astro.api.user.dto.response.IdentificatedUserResponseDto;
import com.astro.api.user.dto.response.UserProfileResponse;
import com.astro.api.user.mapper.UserMapper;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserProfilePhoto;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.repository.UserNrValidityProjection;
import com.astro.api.user.repository.UserProfilePhotoRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final NrDocumentRepository nrDocumentRepository;
    private final UserProfilePhotoRepository userProfilePhotoRepository;
    private final UserMapper userMapper;
    private final R2StorageService r2StorageService;

    public UserService(
            UserRepository userRepository,
            StringRedisTemplate redisTemplate,
            NrDocumentRepository nrDocumentRepository,
            UserProfilePhotoRepository userProfilePhotoRepository,
            UserMapper userMapper,
            R2StorageService r2StorageService) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.nrDocumentRepository = nrDocumentRepository;
        this.userProfilePhotoRepository = userProfilePhotoRepository;
        this.userMapper = userMapper;
        this.r2StorageService = r2StorageService;
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

    public UserProfileResponse findProfileByFirebaseUid(String firebaseUid) {
        User user = userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        String profilePhotoPath = userProfilePhotoRepository.findById(user.getId())
                .map(UserProfilePhoto::getObjectPath)
                .orElse(null);
        String profilePhotoUrl = r2StorageService.getDownloadUrl(profilePhotoPath);
        return userMapper.toProfile(user, profilePhotoUrl, findNrs(user));
    }

    public void updateProfilePhoto(String firebaseUid, MultipartFile file) {
        User user = userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        UserProfilePhoto profilePhoto = userProfilePhotoRepository.findById(user.getId())
                .orElseGet(UserProfilePhoto::new);
        String previousObjectPath = profilePhoto.getObjectPath();
        String objectPath = r2StorageService.uploadProfilePhoto(user.getId(), file);
        profilePhoto.setUserId(user.getId());
        profilePhoto.setObjectPath(objectPath);
        try {
            userProfilePhotoRepository.saveAndFlush(profilePhoto);
        } catch (RuntimeException exception) {
            try {
                r2StorageService.deleteObject(objectPath);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
        if (previousObjectPath != null && !previousObjectPath.isBlank()
                && !previousObjectPath.equals(objectPath)) {
            r2StorageService.deleteObject(previousObjectPath);
        }
    }

    private List<UserProfileResponse.NrProfileResponse> findNrs(User user) {
        if (user.getCargo() == null) {
            return List.of();
        }

        List<UserNrValidityProjection> nrsByCargo = userRepository
                .findNrValiditiesByCargoIdAndUserId(user.getCargo().getId(), user.getId());
        if (nrsByCargo.isEmpty()) {
            return List.of();
        }

        Map<Integer, NrDocument> documentsById = nrDocumentRepository.findAllById(
                        nrsByCargo.stream().map(UserNrValidityProjection::getNrId).toList())
                .stream()
                .collect(Collectors.toMap(NrDocument::getId, Function.identity()));

        return nrsByCargo.stream()
                .map(nr -> toNrProfile(nr, documentsById.get(nr.getNrId())))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private UserProfileResponse.NrProfileResponse toNrProfile(
            UserNrValidityProjection nr,
            NrDocument document) {
        if (document == null || document.isRevoked()) {
            return null;
        }

        return userMapper.toNrProfile(document, nr.getValidity());
    }
}
