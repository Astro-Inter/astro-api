package com.astro.api.unit.service;

import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.mapper.GeoapifyMapper;
import com.astro.api.unit.model.UnitCoordinates;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeoapifyClientTest {
    private final HttpClient http = mock(HttpClient.class);
    private final GeoapifyClient client = new GeoapifyClient(http, JsonMapper.builder().build(),
            new GeoapifyMapper(), "test-key");
    private final UnitRequest address = new UnitRequest("Matriz", "01310100", "SP", "São Paulo",
            "Bela Vista", "Avenida Paulista & Rua A", "1000", "Sala privada");

    @Test
    void sendsEncodedBrazilianAddressAndMapsCoordinatesWithoutSwappingAxes() throws Exception {
        respond(200, "{\"results\":[{\"lon\":-46.6,\"lat\":-23.5,\"formatted\":\"Address\"}]}");
        assertEquals(new UnitCoordinates(-46.6, -23.5), client.geocode(address).orElseThrow());
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("api.geoapify.com", request.getValue().uri().getHost());
        assertEquals(Duration.ofSeconds(4), request.getValue().timeout().orElseThrow());
        String query = URLDecoder.decode(request.getValue().uri().getRawQuery(), StandardCharsets.UTF_8);
        assertTrue(query.contains("Avenida Paulista & Rua A, 1000, Bela Vista, São Paulo, SP, 01310100, Brasil"));
        assertTrue(query.contains("filter=countrycode:br"));
        assertTrue(query.contains("apiKey=test-key"));
        assertFalse(query.contains("Sala privada"));
        assertTrue(request.getValue().uri().getRawQuery().contains("%26"));
    }

    @Test
    void returnsEmptyWhenNoCoordinatesAreAvailable() throws Exception {
        for (String body : new String[]{"{\"results\":[]}", "{\"results\":[{\"lat\":-23.5}]}", "{}"}) {
            respond(200, body);
            assertTrue(client.geocode(address).isEmpty());
        }
    }

    @Test
    void rejectsInvalidResponsesWithoutExposingProviderBodyOrCredentials() throws Exception {
        for (String body : new String[]{"invalid test-key", "{\"results\":[{\"lon\":181,\"lat\":0}]}"}) {
            respond(200, body);
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.geocode(address));
            assertFalse(error.getMessage().contains("test-key"));
            assertNull(error.getCause());
        }
        respond(429, "quota test-key");
        assertThrows(IllegalStateException.class, () -> client.geocode(address));
    }

    @Test
    void handlesConnectionFailureAndRestoresInterruptedFlag() throws Exception {
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("test-key"));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.geocode(address));
        assertNull(error.getCause());
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException());
        try {
            assertThrows(IllegalStateException.class, () -> client.geocode(address));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsMissingKeyWithoutCallingProvider() {
        GeoapifyClient unconfigured = new GeoapifyClient(http, JsonMapper.builder().build(), new GeoapifyMapper(), "");
        assertThrows(IllegalStateException.class, () -> unconfigured.geocode(address));
        verifyNoInteractions(http);
    }

    private void respond(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }
}
