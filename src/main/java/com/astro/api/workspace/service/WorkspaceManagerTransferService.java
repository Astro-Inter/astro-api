package com.astro.api.workspace.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.manager.repository.ManagerRepository;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.dto.request.WorkspaceManagerTransferRequest;
import com.astro.api.workspace.dto.response.WorkspaceManagerTransferResponse;
import com.astro.api.workspace.mapper.WorkspaceMapper;
import com.astro.api.workspace.model.Workspace;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceManagerTransferService {
    private final WorkspaceAccessService workspaceAccessService;
    private final UserRepository userRepository;
    private final ManagerRepository managerRepository;
    private final WorkspaceMapper workspaceMapper;

    public WorkspaceManagerTransferService(WorkspaceAccessService workspaceAccessService, UserRepository userRepository,
            ManagerRepository managerRepository, WorkspaceMapper workspaceMapper) {
        this.workspaceAccessService = workspaceAccessService;
        this.userRepository = userRepository;
        this.managerRepository = managerRepository;
        this.workspaceMapper = workspaceMapper;
    }

    @Transactional
    public WorkspaceManagerTransferResponse transfer(WorkspaceManagerTransferRequest body, AuthenticatedUser principal) {
        Workspace workspace = workspaceAccessService.requireWorkspace(principal, true);
        validateRequest(body);
        User previousManager = userRepository.findByFirebaseUid(principal.firebaseUid())
                .orElseThrow(() -> new AccessDeniedException("Gestor de workspace ativo necessário"));
        if (body.newManagerId().equals(previousManager.getId())) {
            throw new BusinessException("Escolha outro gestor para assumir o workspace");
        }
        User newManager = managerRepository.findUserForUpdateInWorkspace(body.newManagerId(), workspace.id)
                .orElseThrow(() -> new ResourceNotFoundException("Gestor não encontrado no workspace"));
        validateNewManager(newManager);
        if (userRepository.countByUnit_WorkspaceAndType(workspace, UserType.GESTOR_WORKSPACE) != 1) {
            throw new ConflictException("O workspace deve ter um único gestor responsável para realizar a transferência");
        }

        demotePreviousManager(previousManager);
        promoteNewManager(newManager);
        return workspaceMapper.toManagerTransferResponse(workspace, previousManager, newManager);
    }

    private void validateRequest(WorkspaceManagerTransferRequest body) {
        if (body == null || body.newManagerId() == null || body.newManagerId() <= 0) {
            throw new BusinessException("O identificador do novo gestor deve ser positivo");
        }
    }

    private void validateNewManager(User newManager) {
        if (newManager.getType() != UserType.GESTOR) {
            throw new ConflictException("Somente um gestor pode assumir a responsabilidade pelo workspace");
        }
        if (newManager.getStatus() != UserStatus.ATIVO || newManager.getFirebaseUid() == null
                || newManager.getFirebaseUid().isBlank()) {
            throw new BusinessException("O novo gestor deve estar ativo e ter autenticação concluída");
        }
    }

    private void demotePreviousManager(User previousManager) {
        // A autorização pode ter sido lida antes da espera pelo bloqueio do workspace.
        int changedUsers = userRepository.updateTypeIfCurrent(previousManager.getId(),
                UserType.GESTOR_WORKSPACE, UserType.GESTOR, UserStatus.ATIVO);
        if (changedUsers != 1) {
            throw new AccessDeniedException("A conta não é mais o gestor ativo responsável pelo workspace");
        }
    }

    private void promoteNewManager(User newManager) {
        int changedUsers = userRepository.updateTypeIfCurrent(newManager.getId(),
                UserType.GESTOR, UserType.GESTOR_WORKSPACE, UserStatus.ATIVO);
        if (changedUsers != 1) {
            throw new ConflictException("O novo gestor não está mais disponível para a transferência");
        }
    }
}
