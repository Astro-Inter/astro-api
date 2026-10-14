package com.astro.api.conformity.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.conformity.dto.response.ConformityResponse;
import com.astro.api.conformity.mapper.ConformityMapper;
import com.astro.api.conformity.repository.ConformityRepository;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ConformityService {
    private final ConformityRepository repository;
    private final UserRepository userRepository;
    private final ConformityMapper mapper;

    public ConformityService(ConformityRepository repository, UserRepository userRepository, ConformityMapper mapper) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ConformityResponse> findAll(AuthenticatedUser principal) {
        User manager = requireManager(principal);
        return repository.findAllInScope(principal.firebaseUid(), manager.getUnit().workspace.id)
                .stream().map(mapper::toResponse).toList();
    }

    private User requireManager(AuthenticatedUser principal) {
        if (principal == null || principal.firebaseUid() == null || principal.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Gestor autenticado necessário");
        }
        User manager = userRepository.findByFirebaseUid(principal.firebaseUid())
                .orElseThrow(() -> new AccessDeniedException("Gestor ativo necessário"));
        if (manager.getStatus() != UserStatus.ATIVO
                || (manager.getType() != UserType.GESTOR && manager.getType() != UserType.GESTOR_WORKSPACE)
                || principal.role() == null || !manager.getType().name().equals(principal.role().name())
                || manager.getUnit() == null || manager.getUnit().workspace == null) {
            throw new AccessDeniedException("Gestor ativo com workspace necessário");
        }
        return manager;
    }
}
