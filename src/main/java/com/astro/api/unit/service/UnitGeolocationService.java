package com.astro.api.unit.service;

import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.model.UnitCoordinates;
import com.astro.api.unit.repository.UnitAddressRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnitGeolocationService {
    private final GeoapifyClient client;
    private final UnitAddressRepository addresses;

    public UnitGeolocationService(GeoapifyClient client, UnitAddressRepository addresses) {
        this.client = client;
        this.addresses = addresses;
    }

    @Transactional
    public void updateGeolocation(Long unitId, UnitRequest request, boolean addressChanged) {
        Optional<UnitCoordinates> coordinates;
        try {
            coordinates = client.geocode(request);
        } catch (IllegalStateException exception) {
            coordinates = Optional.empty();
        }
        if (coordinates.isPresent()) {
            UnitCoordinates location = coordinates.get();
            if (addresses.updateGeolocation(unitId, location.longitude(), location.latitude()) != 1) {
                throw new IllegalStateException("Endereço da unidade não encontrado para salvar a geolocalização");
            }
        } else if (addressChanged) {
            addresses.clearGeolocation(unitId);
        }
    }
}
