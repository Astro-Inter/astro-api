package com.astro.api.unit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeoapifyResponse(List<Result> results) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(Double lon, Double lat) { }
}
