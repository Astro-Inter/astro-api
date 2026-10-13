package com.astro.api.manager.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.manager.dto.response.ManagerSummaryResponse;
import com.astro.api.manager.dto.response.ManagerDetailsResponse;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.manager.mapper.ManagerMapper;
import com.astro.api.manager.repository.ManagerRepository;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ManagerService {
    private final ManagerRepository repository;
    private final UserRepository userRepository;
    private final ManagerMapper mapper;

    public ManagerService(ManagerRepository repository, UserRepository userRepository, ManagerMapper mapper) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ManagerSummaryResponse> findAll(AuthenticatedUser principal) {
        Long workspaceId = requireWorkspaceManager(principal).getUnit().workspace.id;
        return repository.findAllInWorkspace(workspaceId).stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ManagerDetailsResponse findById(Long managerId, AuthenticatedUser principal) {
        Long workspaceId = requireWorkspaceManager(principal).getUnit().workspace.id;
        checkId(managerId);
        return repository.findDetailsInWorkspace(managerId, workspaceId).map(mapper::toDetails)
                .orElseThrow(() -> new ResourceNotFoundException("Gestor não encontrado"));
    }

    private void checkId(Long userId) {
        if (userId == null || userId <= 0) throw new BusinessException("O identificador do usuário deve ser positivo");
    }

    private User requireWorkspaceManager(AuthenticatedUser principal) {
        if (principal == null || principal.role() != Role.GESTOR_WORKSPACE
                || principal.firebaseUid() == null || principal.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Gestor de workspace autenticado necessário");
        }
        return userRepository.findByFirebaseUid(principal.firebaseUid())
                .filter(user -> user.getType() == UserType.GESTOR_WORKSPACE && user.getStatus() == UserStatus.ATIVO
                        && user.getUnit() != null && user.getUnit().workspace != null)
                .orElseThrow(() -> new AccessDeniedException("Gestor de workspace ativo necessário"));
    }
}
