package com.astro.api.conformity.dto.response;

import java.time.LocalDate;

public record UnitConformityResponse(Long id, String type, Long unitId, String unitName,
        Integer nrId, String nrTitle, LocalDate expiresAt, String origin, boolean applicable,
        String formResponseId, String status) {}
