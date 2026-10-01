package com.astro.api.cargo.service;

import com.astro.api.cargo.model.Cargo;
import com.astro.api.cargo.repository.CargoRepository;
import com.astro.api.workspace.model.Workspace;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CargoService {
    private final CargoRepository cargoRepository;

    public CargoService(CargoRepository cargoRepository) {
        this.cargoRepository = cargoRepository;
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
