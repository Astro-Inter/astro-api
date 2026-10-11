package com.astro.api.cargo.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.cargo.dto.response.CargoNrOptionResponse;
import com.astro.api.cargo.dto.response.CargoNrResponse;
import com.astro.api.cargo.dto.response.CargoResponse;
import com.astro.api.cargo.model.Cargo;
import com.astro.api.cargo.repository.CargoManagementRepository;
import com.astro.api.cargo.repository.CargoRepository;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.conformidade.repository.NrDocumentRepository;
import com.astro.api.workspace.model.Workspace;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CargoService {
    private final CargoRepository cargoRepository;
    private final CargoManagementRepository managementRepository;
    private final NrDocumentRepository nrDocuments;

    public CargoService(CargoRepository cargoRepository, CargoManagementRepository managementRepository,
                        NrDocumentRepository nrDocuments) {
        this.cargoRepository = cargoRepository;
        this.managementRepository = managementRepository;
        this.nrDocuments = nrDocuments;
    }

    @Transactional(readOnly = true)
    public List<CargoResponse> findAll(String uid, AuthenticatedUser user) {
        checkIdentity(uid, user);
        return managementRepository.findAll(uid);
    }

    @Transactional(readOnly = true)
    public List<CargoNrResponse> findNrs(String uid, Long cargoId, AuthenticatedUser user) {
        checkIdentity(uid, user);
        checkCargoId(cargoId);
        return managementRepository.findNrs(uid, cargoId);
    }

    @Transactional(readOnly = true)
    public List<CargoNrOptionResponse> findNrOptions(String uid, Long cargoId, AuthenticatedUser user) {
        Set<Integer> linked = findNrs(uid, cargoId, user).stream()
                .map(CargoNrResponse::codigoNr).collect(Collectors.toSet());
        return nrDocuments.findByUsability("Colaborador").stream()
                .map(nr -> new CargoNrOptionResponse(nr.getId(), nr.getDescription(), linked.contains(nr.getId())))
                .sorted(Comparator.comparing(CargoNrOptionResponse::nr))
                .toList();
    }

    private void checkIdentity(String uid, AuthenticatedUser user) {
        if (uid == null || uid.isBlank()) {
            throw new BusinessException("firebase_uid é obrigatório");
        }
        if (user == null || !uid.equals(user.firebaseUid())) {
            throw new AccessDeniedException("firebase_uid deve corresponder ao usuário autenticado");
        }
    }

    private void checkCargoId(Long cargoId) {
        if (cargoId == null || cargoId <= 0) {
            throw new BusinessException("id_cargo deve ser positivo");
        }
    }

    public Map<String, Cargo> createForWorkspace(Workspace workspace, Collection<String> names) {
        Map<String, String> namesByNormalizedValue = new LinkedHashMap<>();
        names.stream()
                .filter(this::hasText)
                .forEach(name -> namesByNormalizedValue.putIfAbsent(normalize(name), name.trim()));

        List<Cargo> cargos = namesByNormalizedValue.values().stream()
                .map(name -> create(workspace, name))
                .toList();
        List<Cargo> savedCargos = cargoRepository.saveAll(cargos);

        Map<String, Cargo> cargosByName = new LinkedHashMap<>();
        savedCargos.forEach(cargo -> cargosByName.put(normalize(cargo.getName()), cargo));
        return cargosByName;
    }

    private Cargo create(Workspace workspace, String name) {
        Cargo cargo = new Cargo();
        cargo.setName(name);
        cargo.setActive(true);
        cargo.setWorkspace(workspace);
        return cargo;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
