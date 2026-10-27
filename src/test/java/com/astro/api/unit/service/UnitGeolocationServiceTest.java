package com.astro.api.unit.service;

import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.model.UnitCoordinates;
import com.astro.api.unit.model.UnitAddress;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnitGeolocationServiceTest {
    private final GeoapifyClient client = mock(GeoapifyClient.class);
    private final UnitGeolocationService service = new UnitGeolocationService(client);
    private final UnitRequest request = new UnitRequest("Matriz", "01310100", "SP", "São Paulo",
            "Bela Vista", "Avenida Paulista", "1000", null);

    @Test
    void skipsCoordinatesWhenNotFoundOrUnavailable() {
        UnitAddress address = new UnitAddress();
        when(client.geocode(request)).thenReturn(Optional.empty());
        service.updateGeolocation(address, request, false);
        assertNull(address.geolocation);
        when(client.geocode(request)).thenThrow(new IllegalStateException("Indisponível"));
        service.updateGeolocation(address, request, false);
        assertNull(address.geolocation);
    }

    @Test
    void clearsStaleCoordinatesOnChangedAddressButPreservesThemOnUnchangedAddress() {
        UnitCoordinates previous = new UnitCoordinates(-46.6, -23.5);
        UnitAddress changed = new UnitAddress();
        changed.geolocation = previous;
        UnitAddress unchanged = new UnitAddress();
        unchanged.geolocation = previous;
        when(client.geocode(request)).thenThrow(new IllegalStateException("Indisponível"));
        service.updateGeolocation(changed, request, true);
        service.updateGeolocation(unchanged, request, false);
        assertNull(changed.geolocation);
        assertEquals(previous, unchanged.geolocation);
    }

    @Test
    void replacesCoordinatesOnTheAddressEntityWhenGeocodingSucceeds() {
        UnitAddress address = new UnitAddress();
        address.geolocation = new UnitCoordinates(-43.2, -22.9);
        UnitCoordinates found = new UnitCoordinates(-46.6, -23.5);
        when(client.geocode(request)).thenReturn(Optional.of(found));
        service.updateGeolocation(address, request, true);
        assertEquals(found, address.geolocation);
    }
}
