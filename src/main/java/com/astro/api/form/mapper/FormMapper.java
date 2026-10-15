package com.astro.api.form.mapper;

import com.astro.api.form.dto.response.FormSummaryResponse;
import com.astro.api.form.dto.response.FormDetailsResponse;
import com.astro.api.form.dto.response.FormDetailsResponse.QuestionResponse;
import com.astro.api.form.dto.response.FormDetailsResponse.OptionResponse;
import com.astro.api.form.model.Form;
import org.springframework.stereotype.Component;

@Component
public class FormMapper {
    public static final java.time.ZoneId FORM_ZONE = java.time.ZoneId.of("America/Sao_Paulo");

    public FormSummaryResponse toSummary(Form form) {
        return new FormSummaryResponse(form.getId(), form.getTitle(), form.getDescription(),
                form.getQuestions() == null ? 0 : form.getQuestions().size(), form.getStatus());
    }

    public FormDetailsResponse toDetails(Form form) {
        return new FormDetailsResponse(form.getId(), form.getWorkspaceId(), form.getSenderManagerId(), form.getManagerId(),
                form.getTitle(), form.getDescription(), form.getNr(), form.getUnitId(),
                form.getDueDate() == null ? null : form.getDueDate().atZone(FORM_ZONE).toLocalDate(),
                form.getStatus(), form.getCreatedAt(), form.getUpdatedAt(), form.getRespondedAt(),
                form.getQuestions() == null ? java.util.List.of() : form.getQuestions().stream().map(this::toQuestion).toList());
    }

    private QuestionResponse toQuestion(Form.FormQuestion question) {
        return new QuestionResponse(question.getId(), question.getType(), question.getQuestion(),
                Boolean.TRUE.equals(question.getRequired()), question.getMultiple(),
                question.getOptions() == null ? java.util.List.of() : question.getOptions().stream()
                        .map(option -> new OptionResponse(option.getId(), option.getText(), Boolean.TRUE.equals(option.getOther()))).toList(),
                question.getMaxFiles(), question.getMaxFileSizeMb(),
                question.getAnswer() == null ? java.util.List.of() : java.util.List.copyOf(question.getAnswer()));
    }
}
