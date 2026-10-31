package com.astro.api.form.dto.request;

import com.astro.api.form.model.FormStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public record FormRequest(
        @Size(max = 100, message = "{form.title.size}") String title,
        @Size(max = 240, message = "{form.description.size}") String description,
        @Positive(message = "{form.managerId.positive}") Long managerId,
        @Positive(message = "{form.nrCode.positive}") Integer nrCode,
        @Positive(message = "{form.unitId.positive}") Long unitId,
        LocalDate dueDate,
        FormStatus status,
        @Size(max = 100, message = "{form.questions.size}")
        List<@NotNull(message = "{form.question.item.notNull}") @Valid FormQuestionDefinitionRequest> questions,
        @Valid FormAppearanceRequest appearance
) {
    public FormRequest(String title, String description, Long managerId, Integer nrCode, Long unitId,
                       LocalDate dueDate, FormStatus status, List<FormQuestionDefinitionRequest> questions) {
        this(title, description, managerId, nrCode, unitId, dueDate, status, questions, null);
    }
}
