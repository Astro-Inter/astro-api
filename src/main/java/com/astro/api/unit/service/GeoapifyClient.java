package com.astro.api.unit.service;

import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.dto.response.GeoapifyResponse;
import com.astro.api.unit.mapper.GeoapifyMapper;
import com.astro.api.unit.model.UnitCoordinates;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GeoapifyClient {
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final GeoapifyMapper mapper;
    private final String apiKey;

    public GeoapifyClient(@Qualifier("geocodingHttpClient") HttpClient httpClient,
                         JsonMapper jsonMapper, GeoapifyMapper mapper,
                         @Value("${geoapify.api-key:}") String apiKey) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.mapper = mapper;
        this.apiKey = apiKey;
    }

    public Optional<UnitCoordinates> geocode(UnitRequest address) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Configure GEOAPIFY_API_KEY para localizar endereços");
        }
        HttpRequest request = HttpRequest.newBuilder(mapper.toSearchUri(address, apiKey))
                .timeout(Duration.ofSeconds(4))
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Serviço de geocodificação indisponível");
            }
            return mapper.toCoordinates(jsonMapper.readValue(response.body(), GeoapifyResponse.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consulta de geocodificação interrompida");
        } catch (IOException | tools.jackson.core.JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("Não foi possível consultar a geolocalização");
        }
    }
}
