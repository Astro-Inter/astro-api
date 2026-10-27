package com.astro.api.unit.service;

import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.mapper.UnitMapper;
import com.astro.api.unit.mapper.UnitResponseMapper;
import com.astro.api.unit.model.Unit;
import com.astro.api.unit.model.UnitAddress;
import com.astro.api.unit.model.UnitCoordinates;
import com.astro.api.unit.repository.UnitAddressRepository;
import com.astro.api.unit.repository.UnitNrCatalogRepository;
import com.astro.api.unit.repository.UnitRepository;
import com.astro.api.workspace.model.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnitRegistrationGeolocationTest {
    @Test
    void geocodesEachRegistrationAddressAndKeepsTheBatchWhenOneLookupFails() {
        UnitRepository units = mock(UnitRepository.class);
        UnitAddressRepository addresses = mock(UnitAddressRepository.class);
        GeoapifyClient client = mock(GeoapifyClient.class);
        UnitService service = new UnitService(units, addresses, new UnitMapper(), new UnitResponseMapper(),
                mock(UnitNrCatalogRepository.class), JsonMapper.builder().build(),
                new UnitGeolocationService(client));
        Workspace workspace = new Workspace();
        UnitRequest first = new UnitRequest("Matriz", "01310100", "SP", "São Paulo", "Bela Vista",
                "Avenida Paulista", "1000", null);
        UnitRequest second = new UnitRequest("Filial", "20040020", "RJ", "Rio de Janeiro", "Centro",
                "Rua A", "10", null);
        when(units.saveAll(anyList())).thenAnswer(call -> {
            List<Unit> saved = call.getArgument(0);
            AtomicLong id = new AtomicLong(12);
            saved.forEach(unit -> unit.id = id.getAndIncrement());
            return saved;
        });
        when(client.geocode(first)).thenReturn(Optional.of(new UnitCoordinates(-46.6, -23.5)));
        when(client.geocode(second)).thenThrow(new IllegalStateException("Indisponível"));

        assertEquals(2, service.createForWorkspace(workspace, List.of(first, second)).size());
        verify(addresses).saveAll(argThat(batch -> {
            var saved = (List<UnitAddress>) batch;
            return saved.size() == 2
                    && new UnitCoordinates(-46.6, -23.5).equals(saved.getFirst().geolocation)
                    && saved.getFirst().unit.id == 12L && saved.get(1).unit.id == 13L
                    && saved.get(1).geolocation == null;
        }));
    }
}
