package com.astro.api.conformity.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.*;
import com.astro.api.conformity.dto.request.*;
import com.astro.api.conformity.dto.response.UnitConformityResponse;
import com.astro.api.conformity.mapper.UnitConformityMapper;
import com.astro.api.conformity.model.Conformity;
import com.astro.api.conformity.model.NrCatalog;
import com.astro.api.conformity.repository.*;
import com.astro.api.user.model.*;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.unit.model.Unit;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnitConformityService {
    private final ConformityRepository writes;
    private final UnitConformityRepository repository;
    private final UserRepository users;
    private final NrCatalogRepository nrs;
    private final UnitConformityMapper mapper;

    public UnitConformityService(ConformityRepository writes, UnitConformityRepository repository,
            UserRepository users, NrCatalogRepository nrs, UnitConformityMapper mapper) {
        this.writes = writes;
        this.repository = repository;
        this.users = users;
        this.nrs = nrs;
        this.mapper = mapper;
    }
    @Transactional(readOnly = true)
    public List<UnitConformityResponse> findAll(AuthenticatedUser principal) {
        Long workspaceId = workspace(principal);
        List<UnitConformityProjection> conformities = repository.findAllInScope(workspaceId, principal.firebaseUid());
        List<UnitConformityResponse> response = conformities.stream().map(mapper::toResponse).toList();
        return response;
    }
    @Transactional(readOnly = true)
    public UnitConformityResponse findById(Long id, AuthenticatedUser principal) {
        return details(id, workspace(principal), principal);
    }
    @Transactional
    public UnitConformityResponse create(CreateUnitConformityRequest request, AuthenticatedUser principal) {
        Long workspaceId = workspace(principal);
        writes.lockWrites();
        Unit unit = unit(request.unitId(), workspaceId, principal);
        if (!unit.isActive) {
            throw new BusinessException("A unidade deve estar ativa");
        }
        requireNr(request.nrId(), true);
        conflict(request.unitId(), request.nrId(), null);
        Conformity saved = repository.saveAndFlush(mapper.toManual(request));
        return details(saved.getId(), workspaceId, principal);
    }
    @Transactional
    public UnitConformityResponse update(Long id, UpdateConformityRequest request, AuthenticatedUser principal) {
        Long workspaceId = workspace(principal);
        writes.lockWrites();
        Conformity record = record(id, workspaceId, principal);
        unit(record.getUnitId(), workspaceId, principal);
        boolean changed = !record.getNrId().equals(request.nrId());
        if (changed && record.getFormResponseId() != null) {
            throw new BusinessException("A NR deve corresponder ao formulário aprovado");
        }
        requireNr(request.nrId(), changed);
        if (changed && record.isApplicable()) {
            conflict(record.getUnitId(), request.nrId(), id);
        }
        mapper.applyUpdate(record, request);
        repository.saveAndFlush(record);
        return details(id, workspaceId, principal);
    }
    @Transactional
    public UnitConformityResponse changeApplicability(Long id, ConformityApplicabilityRequest request, AuthenticatedUser principal) {
        Long workspaceId = workspace(principal);
        writes.lockWrites();
        Conformity record = record(id, workspaceId, principal);
        Unit unit = unit(record.getUnitId(), workspaceId, principal);
        if (request.applicable() != record.isApplicable()) {
            if (request.applicable()) {
                if (!unit.isActive) {
                    throw new BusinessException("A unidade deve estar ativa");
                }
                requireNr(record.getNrId(), true);
                conflict(record.getUnitId(), record.getNrId(), id);
            }
            record.setApplicable(request.applicable());
            repository.saveAndFlush(record);
        }
        return details(id, workspaceId, principal);
    }
    private Long workspace(AuthenticatedUser principal) {
        if (principal == null || principal.firebaseUid() == null) {
            throw new AccessDeniedException("Gestor autenticado necessário");
        }
        User user = users.findByFirebaseUid(principal.firebaseUid()).orElseThrow(() -> new AccessDeniedException("Gestor ativo necessário"));
        if (user.getStatus() != UserStatus.ATIVO || (user.getType() != UserType.GESTOR && user.getType() != UserType.GESTOR_WORKSPACE)
                || principal.role() == null || !user.getType().name().equals(principal.role().name())
                || user.getUnit() == null || user.getUnit().workspace == null) {
            throw new AccessDeniedException("Gestor ativo com workspace necessário");
        }
        return user.getUnit().workspace.id;
    }
    private Unit unit(Long id, Long workspaceId, AuthenticatedUser principal) {
        return repository.findUnitInScope(id, workspaceId, principal.firebaseUid()).orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada"));
    }
    private Conformity record(Long id, Long workspaceId, AuthenticatedUser principal) {
        return repository.findRecordInScope(id, workspaceId, principal.firebaseUid()).orElseThrow(() -> new ResourceNotFoundException("Conformidade não encontrada"));
    }
    private UnitConformityResponse details(Long id, Long workspaceId, AuthenticatedUser principal) {
        return repository.findDetailsInScope(id, workspaceId, principal.firebaseUid()).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Conformidade não encontrada"));
    }
    private void requireNr(Integer id, boolean current) {
        NrCatalog nr = nrs.findLockedByCodeNr(id).orElseThrow(() -> new ResourceNotFoundException("NR não encontrada"));
        if (current && nr.isRevoked()) {
            throw new BusinessException("A NR não pode estar revogada");
        }
    }
    private void conflict(Long unitId, Integer nrId, Long excludedId) {
        if (repository.hasApplicableConflict(unitId, nrId, excludedId)) {
            throw new ConflictException("Já existe uma conformidade aplicável para esta unidade e NR");
        }
    }
}
