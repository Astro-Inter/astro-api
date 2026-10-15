package com.astro.api.form.mapper;

import com.astro.api.form.dto.response.FormSummaryResponse;
import com.astro.api.form.model.Form;
import org.springframework.stereotype.Component;

@Component
public class FormMapper {
    public FormSummaryResponse toSummary(Form form) {
        return new FormSummaryResponse(form.getId(), form.getTitle(), form.getDescription(),
                form.getQuestions() == null ? 0 : form.getQuestions().size(), form.getStatus());
    }
}
