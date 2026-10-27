package com.astro.api.unit.mapper;

import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.dto.response.GeoapifyResponse;
import com.astro.api.unit.model.UnitCoordinates;
import java.net.URI;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GeoapifyMapper {
    public URI toSearchUri(UnitRequest request, String apiKey) {
        return UriComponentsBuilder.fromUriString("https://api.geoapify.com/v1/geocode/search")
                .queryParam("text", "{address}")
                .queryParam("filter", "countrycode:br")
                .queryParam("lang", "pt")
                .queryParam("limit", 1)
                .queryParam("format", "json")
                .queryParam("apiKey", "{apiKey}")
                .encode()
                .buildAndExpand(request.street() + ", " + request.number() + ", " + request.neighborhood()
                        + ", " + request.city() + ", " + request.state() + ", " + request.cep() + ", Brasil", apiKey)
                .toUri();
    }

    public Optional<UnitCoordinates> toCoordinates(GeoapifyResponse response) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return Optional.empty();
        }
        GeoapifyResponse.Result result = response.results().getFirst();
        if (result == null || result.lon() == null || result.lat() == null) {
            return Optional.empty();
        }
        return Optional.of(new UnitCoordinates(result.lon(), result.lat()));
    }
}
