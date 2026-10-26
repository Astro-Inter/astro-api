package com.astro.api.conformity.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.conformity.dto.request.CreateConformityRequest;
import com.astro.api.conformity.dto.request.UpdateConformityRequest;
import com.astro.api.conformity.dto.request.ConformityApplicabilityRequest;
import com.astro.api.conformity.model.Conformity;
import com.astro.api.conformity.model.NrCatalog;
import com.astro.api.conformity.repository.NrCatalogRepository;
import com.astro.api.conformity.repository.ConformityUserRepository;
import com.astro.api.conformity.dto.response.ConformityResponse;
import com.astro.api.conformity.mapper.ConformityMapper;
import com.astro.api.conformity.repository.ConformityRepository;
import com.astro.api.conformity.repository.ConformityProjection;
import com.astro.api.user.service.UserAvatarService;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ConformityService {
    private final ConformityRepository conformityRepository;
    private final UserRepository userRepository;
    private final ConformityMapper conformityMapper;
    private final ConformityUserRepository conformityUserRepository;
    private final NrCatalogRepository nrCatalogRepository;
    private final UserAvatarService userAvatarService;

    public ConformityService(ConformityRepository conformityRepository, UserRepository userRepository, ConformityMapper conformityMapper,
                             ConformityUserRepository conformityUserRepository, NrCatalogRepository nrCatalogRepository,
                             UserAvatarService userAvatarService) {
        this.conformityRepository = conformityRepository;
        this.userRepository = userRepository;
        this.conformityMapper = conformityMapper;
        this.conformityUserRepository = conformityUserRepository;
        this.nrCatalogRepository = nrCatalogRepository;
        this.userAvatarService = userAvatarService;
    }

    @Transactional(readOnly = true)
    public List<ConformityResponse> findAll(AuthenticatedUser principal) {
        User manager = requireManager(principal);
        List<ConformityProjection> conformities = conformityRepository.findAllInScope(principal.firebaseUid(), manager.getUnit().workspace.id);
        Map<Long, String> profilePhotoUrls = userAvatarService.findUrls(conformities.stream()
                .map(ConformityProjection::getCollaboratorId).toList());
        return conformities.stream().map(conformity -> conformityMapper.toResponse(conformity,
                profilePhotoUrls.get(conformity.getCollaboratorId()))).toList();
    }

    @Transactional(readOnly = true)
    public ConformityResponse findById(Long conformityId, AuthenticatedUser principal) {
        User manager = requireManager(principal);
        checkId(conformityId);
        return details(conformityId, principal, manager);
    }

    @Transactional
    public ConformityResponse create(CreateConformityRequest request, AuthenticatedUser principal) {
        User manager = requireManager(principal);
        conformityRepository.lockWrites();
        User collaborator = requireCollaborator(request.collaboratorId(), principal, manager);
        requireActiveCollaborator(collaborator);
        requireNr(request.nrId(), true);
        checkConflict(request.collaboratorId(), request.nrId(), null);
        Conformity saved = conformityRepository.saveAndFlush(conformityMapper.toNewManual(request));
        return details(saved.getId(), principal, manager);
    }

    private User requireCollaborator(Long collaboratorId, AuthenticatedUser principal, User manager) {
        return conformityUserRepository.findForUpdateInScope(collaboratorId, principal.firebaseUid(), manager.getUnit().workspace.id)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador não encontrado"));
    }

    @Transactional
    public ConformityResponse update(Long conformityId, UpdateConformityRequest request, AuthenticatedUser principal) {
        User manager = requireManager(principal);
        checkId(conformityId);
        conformityRepository.lockWrites();
        Conformity entity = requireRecord(conformityId, principal, manager);
        requireCollaborator(entity.getCollaboratorId(), principal, manager);
        boolean changedNr = !request.nrId().equals(entity.getNrId());
        requireNr(request.nrId(), changedNr);
        if (changedNr && entity.isApplicable()) checkConflict(entity.getCollaboratorId(), request.nrId(), conformityId);
        if (entity.getEventCompletionId() != null
                && !conformityRepository.completionMatchesNr(entity.getEventCompletionId(), entity.getCollaboratorId(), request.nrId())) {
            throw new BusinessException("A NR deve corresponder à conclusão do evento vinculada");
        }
        if (changedNr && entity.getEventCompletionId() == null && "CONCLUSAO_EVENTO".equals(entity.getOrigin())) {
            throw new BusinessException("A conclusão do evento deve estar vinculada para alterar a NR");
        }
        conformityMapper.applyUpdate(entity, request);
        conformityRepository.saveAndFlush(entity);
        return details(conformityId, principal, manager);
    }

    private Conformity requireRecord(Long conformityId, AuthenticatedUser principal, User manager) {
        return conformityRepository.findRecordInScope(conformityId, principal.firebaseUid(), manager.getUnit().workspace.id)
                .orElseThrow(() -> new ResourceNotFoundException("Conformidade não encontrada"));
    }

    @Transactional
    public ConformityResponse changeApplicability(Long conformityId, ConformityApplicabilityRequest request,
                                                 AuthenticatedUser principal) {
        User manager = requireManager(principal);
        checkId(conformityId);
        conformityRepository.lockWrites();
        Conformity entity = requireRecord(conformityId, principal, manager);
        User collaborator = requireCollaborator(entity.getCollaboratorId(), principal, manager);
        if (request.applicable() != entity.isApplicable()) {
            if (request.applicable()) {
                requireActiveCollaborator(collaborator);
                requireNr(entity.getNrId(), true);
                checkConflict(entity.getCollaboratorId(), entity.getNrId(), conformityId);
            }
            entity.setApplicable(request.applicable());
            conformityRepository.saveAndFlush(entity);
        }
        return details(conformityId, principal, manager);
    }

    private void requireActiveCollaborator(User collaborator) {
        if (collaborator.getStatus() != UserStatus.ATIVO) {
            throw new BusinessException("O colaborador deve estar ativo para incluir ou reativar uma conformidade");
        }
    }

    private void requireNr(Integer nrId, boolean requireCurrent) {
        NrCatalog nr = nrCatalogRepository.findLockedByCodeNr(nrId)
                .orElseThrow(() -> new ResourceNotFoundException("NR não encontrada"));
        if (requireCurrent && nr.isRevoked()) {
            throw new BusinessException("A NR não pode estar revogada para uma nova vinculação ou reativação");
        }
    }

    private void checkConflict(Long collaboratorId, Integer nrId, Long excludedId) {
        if (conformityRepository.hasApplicableConflict(collaboratorId, nrId, excludedId)) {
            throw new ConflictException("Já existe uma conformidade aplicável para este colaborador e NR");
        }
    }

    private ConformityResponse details(Long conformityId, AuthenticatedUser principal, User manager) {
        return conformityRepository.findDetailsInScope(conformityId, principal.firebaseUid(), manager.getUnit().workspace.id)
                .map(conformity -> conformityMapper.toResponse(conformity,
                        userAvatarService.findUrl(conformity.getCollaboratorId())))
                .orElseThrow(() -> new ResourceNotFoundException("Conformidade não encontrada"));
    }

    private void checkId(Long conformityId) {
        if (conformityId == null || conformityId <= 0) {
            throw new BusinessException("O identificador da conformidade deve ser positivo");
        }
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

    @Transactional(readOnly = true)
    public void authorizeImport(AuthenticatedUser principal) { requireManager(principal); }

    /** Called through the service proxy by the importer: each successful row commits independently. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean importRow(String cpf, Integer nrId, LocalDate expiresAt, AuthenticatedUser principal) {
        User manager = requireManager(principal);
        conformityRepository.lockWrites();
        Long collaboratorId = conformityUserRepository.findIdByCpfInScope(cpf, principal.firebaseUid(), manager.getUnit().workspace.id)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador não encontrado no escopo autorizado"));
        User collaborator = requireCollaborator(collaboratorId, principal, manager);
        Optional<Conformity> representative = conformityRepository.findApplicableRepresentative(collaboratorId, nrId, Limit.of(1));
        requireNr(nrId, representative.isEmpty());
        if (representative.isPresent()) {
            Conformity entity = representative.get();
            entity.setExpiresAt(expiresAt);
            conformityRepository.saveAndFlush(entity);
            return false;
        }
        requireActiveCollaborator(collaborator);
        checkConflict(collaboratorId, nrId, null);
        conformityRepository.saveAndFlush(conformityMapper.toNewManual(new CreateConformityRequest(collaboratorId, nrId, expiresAt)));
        return true;
    }
}
