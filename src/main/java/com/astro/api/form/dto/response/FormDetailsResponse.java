package com.astro.api.form.dto.response;

import com.astro.api.form.model.FormStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FormDetailsResponse(
        String id,
        Long workspaceId,
        Long senderManagerId,
        Long managerId,
        String title,
        String description,
        Integer nrCode,
        Long unitId,
        LocalDate dueDate,
        FormStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant respondedAt,
        List<QuestionResponse> questions,
        UnitLocationResponse unitLocation,
        FormAppearanceResponse appearance
) {
    public record UnitLocationResponse(Double latitude, Double longitude) {}

    public record QuestionResponse(
            Long id,
            String type,
            String question,
            boolean required,
            Boolean multiple,
            List<OptionResponse> options,
            Integer maxFiles,
            Integer maxFileSizeMb,
            List<Object> answer
    ) {}

    public record OptionResponse(
            Long id,
            String text,
            boolean other
    ) {}
}
