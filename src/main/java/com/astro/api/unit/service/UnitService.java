package com.astro.api.unit.service;

import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.unit.dto.request.UpdateUnitNrsRequest;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.dto.response.UnitNrResponse;
import com.astro.api.unit.dto.response.UnitNrOptionResponse;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.mapper.UnitMapper;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.model.UnitAddress;
import com.astro.api.unit.repository.UnitAddressRepository;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.unit.repository.UnitManagementRepository;
import com.astro.api.unit.repository.UnitNrCatalogRepository;
import com.astro.api.workspace.model.Workspace;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.util.stream.Collectors;

import java.util.*;

@Service
public class UnitService {
    private final UnitRepository unitRepository;
    private final UnitAddressRepository unitAddressRepository;
    private final UnitMapper unitMapper;
    private final UnitManagementRepository managementRepository;
    private final UnitNrCatalogRepository nrCatalogRepository;
    private final JsonMapper objectMapper;

    public UnitService(UnitRepository unitRepository, UnitAddressRepository unitAddressRepository, UnitMapper unitMapper,
                       UnitManagementRepository managementRepository, UnitNrCatalogRepository nrCatalogRepository,
                       JsonMapper objectMapper) {
        this.unitRepository = unitRepository;
        this.unitAddressRepository = unitAddressRepository;
        this.unitMapper = unitMapper;
        this.managementRepository = managementRepository;
        this.nrCatalogRepository = nrCatalogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> findAll(AuthenticatedUser user) {
        return managementRepository.findAll(authenticatedUid(user));
    }

    @Transactional
    public boolean toggleStatus(Long unitId, AuthenticatedUser user) {
        String firebaseUid = authenticatedUid(user);
        checkUnitId(unitId);
        return managementRepository.toggleStatus(firebaseUid, unitId);
    }

    @Transactional(readOnly = true)
    public List<UnitNrResponse> findNrs(Long unitId, AuthenticatedUser user) {
        String firebaseUid = authenticatedUid(user);
        checkUnitId(unitId);
        return managementRepository.findNrs(firebaseUid, unitId);
    }

    @Transactional(readOnly = true)
    public List<UnitNrOptionResponse> findNrOptions(Long unitId, AuthenticatedUser user) {
        Set<Integer> associatedCodes = findNrs(unitId, user).stream()
                .map(UnitNrResponse::nr).collect(Collectors.toSet());
        return nrCatalogRepository.findCompanyNrs().stream()
                .map(nr -> new UnitNrOptionResponse(nr.getId(), nr.getDescription(), associatedCodes.contains(nr.getId())))
                .sorted(Comparator.comparing(UnitNrOptionResponse::nr))
                .toList();
    }

    @Transactional
    public void updateNrs(Long unitId, UpdateUnitNrsRequest body, AuthenticatedUser user) {
        String firebaseUid = authenticatedUid(user);
        checkUnitId(unitId);
        Set<Integer> codes = new HashSet<>();
        for (var nr : body.nrs()) {
            if (!codes.add(nr.nr())) {
                throw new BusinessException("A lista não pode repetir códigos de NR");
            }
        }
        try {
            managementRepository.updateNrs(firebaseUid, unitId, objectMapper.writeValueAsString(body.nrs()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível serializar a lista de NRs", exception);
        }
    }

    private String authenticatedUid(AuthenticatedUser user) {
        if (user == null || user.firebaseUid() == null || user.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Usuário autenticado necessário para consultar unidades");
        }
        return user.firebaseUid();
    }

    private void checkUnitId(Long unitId) {
        if (unitId == null || unitId <= 0) {
            throw new BusinessException("O identificador da unidade deve ser positivo");
        }
    }

    public List<Unit> createForWorkspace(Workspace workspace, List<UnitRequest> requests) {
        Map<String, UnitRequest> requestsByNormalizedName = new LinkedHashMap<>();
        for (UnitRequest request : requests) {
            String normalizedName = normalize(request.name());
            if (requestsByNormalizedName.putIfAbsent(normalizedName, request) != null) {
                throw new ConflictException("Há unidades duplicadas na requisição: " + request.name());
            }
        }

        List<String> names = requests.stream().map(UnitRequest::name).toList();
        if (!unitRepository.findByWorkspaceAndNameIn(workspace, names).isEmpty()) {
            throw new ConflictException("Já existe uma unidade com um dos nomes informados");
        }

        List<Unit> units = requests.stream().map(request -> unitMapper.toUnit(request, workspace)).toList();
        List<Unit> savedUnits = unitRepository.saveAll(units);

        List<UnitAddress> addresses = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            UnitRequest request = requests.get(index);
            addresses.add(unitMapper.toAddress(request, savedUnits.get(index)));
        }
        unitAddressRepository.saveAll(addresses);
        return savedUnits;
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
