package com.astro.api.unit.service;

import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.model.UnitCoordinates;
import com.astro.api.unit.model.UnitAddress;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class UnitGeolocationService {
    private final GeoapifyClient client;

    public UnitGeolocationService(GeoapifyClient client) {
        this.client = client;
    }

    public void updateGeolocation(UnitAddress address, UnitRequest request, boolean addressChanged) {
        Optional<UnitCoordinates> coordinates;
        try {
            coordinates = client.geocode(request);
        } catch (IllegalStateException exception) {
            coordinates = Optional.empty();
        }
        if (coordinates.isPresent()) {
            address.geolocation = coordinates.get();
        } else if (addressChanged) {
            address.geolocation = null;
        }
    }
}
