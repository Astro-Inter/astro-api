package com.astro.api.unit.service;

import com.astro.api.common.exception.ConflictException;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.mapper.UnitMapper;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.model.UnitAddress;
import com.astro.api.unit.repository.UnitAddressRepository;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.workspace.model.Workspace;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UnitService {
    private final UnitRepository unitRepository;
    private final UnitAddressRepository unitAddressRepository;
    private final UnitMapper unitMapper;

    public UnitService(UnitRepository unitRepository, UnitAddressRepository unitAddressRepository, UnitMapper unitMapper) {
        this.unitRepository = unitRepository;
        this.unitAddressRepository = unitAddressRepository;
        this.unitMapper = unitMapper;
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
