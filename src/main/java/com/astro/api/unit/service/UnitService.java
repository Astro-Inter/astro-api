package com.astro.api.unit.service;

import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.unit.dto.request.UpdateUnitNrsRequest;
import com.astro.api.unit.dto.response.UnitResponse;
import com.astro.api.unit.dto.response.UnitNrResponse;
import com.astro.api.unit.dto.response.UnitNrOptionResponse;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.mapper.UnitMapper;
import com.astro.api.unit.mapper.UnitResponseMapper;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.model.UnitAddress;
import com.astro.api.unit.repository.UnitAddressRepository;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.unit.repository.UnitNrCatalogRepository;
import com.astro.api.workspace.model.Workspace;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.sql.SQLException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import java.util.*;

@Service
public class UnitService {
    private final UnitRepository unitRepository;
    private final UnitAddressRepository unitAddressRepository;
    private final UnitMapper unitMapper;
    private final UnitResponseMapper responseMapper;
    private final UnitNrCatalogRepository nrCatalogRepository;
    private final JsonMapper objectMapper;
    private final UnitGeolocationService geolocationService;

    public UnitService(UnitRepository unitRepository, UnitAddressRepository unitAddressRepository, UnitMapper unitMapper,
                       UnitResponseMapper responseMapper, UnitNrCatalogRepository nrCatalogRepository,
                       JsonMapper objectMapper, UnitGeolocationService geolocationService) {
        this.unitRepository = unitRepository;
        this.unitAddressRepository = unitAddressRepository;
        this.unitMapper = unitMapper;
        this.responseMapper = responseMapper;
        this.nrCatalogRepository = nrCatalogRepository;
        this.objectMapper = objectMapper;
        this.geolocationService = geolocationService;
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> findAll(AuthenticatedUser user) {
        String firebaseUid = authenticatedUid(user);
        return executeDatabaseOperation(() -> unitRepository.findAllByFirebaseUid(firebaseUid)).stream()
                .map(responseMapper::toResponse).toList();
    }

    @Transactional
    public boolean toggleStatus(Long unitId, AuthenticatedUser user) {
        String firebaseUid = authenticatedUid(user);
        checkUnitId(unitId);
        Boolean status = executeDatabaseOperation(() -> unitRepository.toggleStatus(firebaseUid, unitId));
        if (status == null) {
            throw new IllegalStateException("A função de status da unidade não retornou um booleano");
        }
        return status;
    }

    @Transactional(readOnly = true)
    public List<UnitNrResponse> findNrs(Long unitId, AuthenticatedUser user) {
        String firebaseUid = authenticatedUid(user);
        checkUnitId(unitId);
        return executeDatabaseOperation(() -> unitRepository.findNrs(firebaseUid, unitId)).stream()
                .map(responseMapper::toNrResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UnitNrOptionResponse> findNrOptions(Long unitId, AuthenticatedUser user) {
        Set<Integer> associatedCodes = findNrs(unitId, user).stream()
                .map(UnitNrResponse::nr).collect(Collectors.toSet());
        return nrCatalogRepository.findCompanyNrs().stream()
                .map(nr -> responseMapper.toNrOptionResponse(nr, associatedCodes.contains(nr.getId())))
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
            String nrsJson = objectMapper.writeValueAsString(body.nrs());
            executeDatabaseOperation(() -> {
                unitRepository.updateNrs(firebaseUid, unitId, nrsJson);
                return null;
            });
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível serializar a lista de NRs", exception);
        }
    }

    private <T> T executeDatabaseOperation(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql) {
                    if ("22023".equals(sql.getSQLState())) {
                        throw new BusinessException("Dados inválidos para a operação de unidade ou NR inválida");
                    }
                    if ("42501".equals(sql.getSQLState())) {
                        throw new AccessDeniedException("Gestor ativo sem acesso à operação");
                    }
                    if ("P0002".equals(sql.getSQLState())) {
                        throw new ResourceNotFoundException("Unidade não encontrada no workspace do gestor");
                    }
                }
            }
            throw exception;
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

    @Transactional
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
        for (int index = 0; index < requests.size(); index++) {
            geolocationService.updateGeolocation(savedUnits.get(index).id, requests.get(index), false);
        }
        return savedUnits;
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
