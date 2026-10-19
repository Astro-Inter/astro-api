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

    public UnitSettingsService(WorkspaceAccessService workspaceAccessService, UnitRepository unitRepository,
                               UnitAddressRepository unitAddressRepository, UnitMapper unitMapper, UnitResponseMapper unitResponseMapper) {
        this.workspaceAccessService = workspaceAccessService;
        this.unitRepository = unitRepository;
        this.unitAddressRepository = unitAddressRepository;
        this.unitMapper = unitMapper;
        this.unitResponseMapper = unitResponseMapper;
    }

    @Transactional
    public UnitResponse create(UnitRequest body, AuthenticatedUser user) {
        Workspace workspace = workspaceAccessService.requireWorkspace(user, true);
        Unit unit = createInWorkspace(workspace, body);
        return buildUnitResponse(unit);
    }

    @Transactional
    public Unit createInWorkspace(Workspace workspace, UnitRequest body) {
        ensureNameAvailable(workspace, body.name(), null);
        Unit unit = unitRepository.saveAndFlush(unitMapper.toUnit(body, workspace));
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
        long collaboratorCount = unitRepository.countCollaborators(unit.id);
        return unitResponseMapper.toResponse(unit, collaboratorCount);
    }

}
