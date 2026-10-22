package com.astro.api.unit.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.text.NormalizedText;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.mapper.UnitMapper;
import com.astro.api.unit.mapper.UnitResponseMapper;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.model.UnitAddress;
import com.astro.api.unit.repository.UnitAddressRepository;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import com.astro.api.workspace.service.WorkspaceAccessService;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnitSettingsService {
    private final WorkspaceAccessService workspaceAccessService;
    private final UnitRepository unitRepository;
    private final UnitAddressRepository unitAddressRepository;
    private final UnitMapper unitMapper;
    private final UnitResponseMapper unitResponseMapper;
    private final UnitService unitService;
    private final UserRepository userRepository;

    public UnitSettingsService(WorkspaceAccessService workspaceAccessService, UnitRepository unitRepository,
                               UnitAddressRepository unitAddressRepository, UnitMapper unitMapper,
                               UnitResponseMapper unitResponseMapper, UnitService unitService,
                               UserRepository userRepository) {
        this.workspaceAccessService = workspaceAccessService;
        this.unitRepository = unitRepository;
        this.unitAddressRepository = unitAddressRepository;
        this.unitMapper = unitMapper;
        this.unitResponseMapper = unitResponseMapper;
        this.unitService = unitService;
        this.userRepository = userRepository;
    }

    @Transactional
    public UnitResponse create(UnitRequest body, AuthenticatedUser user) {
        Workspace workspace = workspaceAccessService.requireWorkspace(user, true);
        Unit unit = createInWorkspace(workspace, body);
        return buildUnitResponse(unit);
    }

    @Transactional
    public UnitResponse update(Long unitId, UnitRequest body, AuthenticatedUser user) {
        Workspace workspace = workspaceAccessService.requireWorkspace(user, true);
        if (unitId == null || unitId <= 0) throw new BusinessException("O identificador da unidade deve ser positivo");
        Unit unit = unitRepository.findByIdAndWorkspace_Id(unitId, workspace.id)
                .orElseThrow(() -> new ResourceNotFoundException("Unidade não encontrada no workspace"));
        ensureNameAvailable(workspace, body.name(), unitId);
        unitMapper.applyUpdate(body, unit);
        unitAddressRepository.save(unit.address);
        unitRepository.save(unit);
        return buildUnitResponse(unit);
    }

    @Transactional
    public Unit createInWorkspace(Workspace workspace, UnitRequest body) {
        ensureNameAvailable(workspace, body.name(), null);
        Unit unit = unitRepository.save(unitMapper.toUnit(body, workspace));
        UnitAddress address = unitAddressRepository.save(unitMapper.toAddress(body, unit));
        unit.address = address;
        return unit;
    }

    private void ensureNameAvailable(Workspace workspace, String name, Long excludedId) {
        String key = NormalizedText.key(name);
        boolean exists = unitRepository.findByWorkspace(workspace).stream()
                .anyMatch(unit -> !Objects.equals(unit.id, excludedId)
                        && key.equals(NormalizedText.key(unit.name)));
        if (exists) throw new ConflictException("Já existe uma unidade com este nome no workspace");
    }

    private UnitResponse buildUnitResponse(Unit unit) {
        long collaboratorCount = userRepository.countByUnit_IdAndType(unit.id, UserType.COLABORADOR);
        return unitResponseMapper.toResponse(unit, collaboratorCount);
    }

    @Transactional(readOnly = true)
    public Map<String, Unit> resolveForImport(Workspace workspace, List<UnitRequest> requests) {
        Map<String, Unit> unitsByName = new LinkedHashMap<>();
        List<Unit> existingUnits = unitRepository.findByWorkspace(workspace);
        for (Unit unit : existingUnits) {
            if (unitsByName.putIfAbsent(NormalizedText.key(unit.name), unit) != null) {
                throw new ConflictException("Existem unidades com nomes ambíguos no workspace");
            }
        }
        Set<String> suppliedNames = new HashSet<>();
        for (UnitRequest request : requests) {
            String key = NormalizedText.key(request.name());
            if (!suppliedNames.add(key)) {
                throw new ConflictException("Há unidades repetidas nos dados complementares");
            }
            unitsByName.computeIfAbsent(key, name -> unitMapper.toUnit(request, workspace));
        }
        return unitsByName;
    }

    @Transactional
    public Map<Unit, Unit> persistNewForImport(Workspace workspace, List<UnitRequest> requests,
            Map<String, Unit> resolvedUnits, Collection<Unit> referencedUnits) {
        Map<String, UnitRequest> requestsByName = new LinkedHashMap<>();
        requests.forEach(request -> requestsByName.put(NormalizedText.key(request.name()), request));
        Set<Unit> selectedUnits = new LinkedHashSet<>(referencedUnits);
        requestsByName.keySet().forEach(key -> selectedUnits.add(resolvedUnits.get(key)));
        List<Unit> newUnits = selectedUnits.stream().filter(unit -> unit.id == null).toList();
        if (newUnits.isEmpty()) {
            return Map.of();
        }
        List<UnitRequest> newUnitRequests = newUnits.stream()
                .map(unit -> requestsByName.get(NormalizedText.key(unit.name)))
                .toList();
        List<Unit> savedUnits = unitService.createForWorkspace(workspace, newUnitRequests);
        Map<String, Unit> savedUnitsByName = new LinkedHashMap<>();
        savedUnits.forEach(unit -> savedUnitsByName.put(NormalizedText.key(unit.name), unit));
        Map<Unit, Unit> persistedUnits = new IdentityHashMap<>();
        newUnits.forEach(unit -> persistedUnits.put(unit, savedUnitsByName.get(NormalizedText.key(unit.name))));
        return persistedUnits;
    }
}
