package com.astro.api.unit.service;

import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.model.UnitCoordinates;
import com.astro.api.unit.repository.UnitAddressRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnitGeolocationServiceTest {
    private final GeoapifyClient client = mock(GeoapifyClient.class);
    private final UnitAddressRepository addresses = mock(UnitAddressRepository.class);
    private final UnitGeolocationService service = new UnitGeolocationService(client, addresses);
    private final UnitRequest request = new UnitRequest("Matriz", "01310100", "SP", "São Paulo",
            "Bela Vista", "Avenida Paulista", "1000", null);

    @Test
    void skipsCoordinatesWhenNotFoundOrUnavailable() {
        when(client.geocode(request)).thenReturn(Optional.empty());
        service.updateGeolocation(12L, request, false);
        when(client.geocode(request)).thenThrow(new IllegalStateException("Indisponível"));
        service.updateGeolocation(12L, request, false);
        verifyNoInteractions(addresses);
    }

    @Test
    void clearsStaleCoordinatesOnChangedAddressButPreservesThemOnUnchangedAddress() {
        when(client.geocode(request)).thenThrow(new IllegalStateException("Indisponível"));
        service.updateGeolocation(12L, request, true);
        service.updateGeolocation(13L, request, false);
        verify(addresses).clearGeolocation(12L);
        verifyNoMoreInteractions(addresses);
    }

    @Test
    void propagatesDatabaseFailureToRollBackTheAddressTransaction() {
        when(client.geocode(request)).thenReturn(Optional.of(new UnitCoordinates(-46.6, -23.5)));
        when(addresses.updateGeolocation(12L, -46.6, -23.5))
                .thenThrow(new DataAccessResourceFailureException("Banco indisponível"));
        assertThrows(DataAccessResourceFailureException.class,
                () -> service.updateGeolocation(12L, request, false));
    }

    @Test
    void detectsMissingAddressInsteadOfReportingPersistenceSuccess() {
        when(client.geocode(request)).thenReturn(Optional.of(new UnitCoordinates(-46.6, -23.5)));
        assertThrows(IllegalStateException.class, () -> service.updateGeolocation(12L, request, false));
    }
}
