package com.astro.api.form.strategy;

import com.astro.api.form.dto.request.FormQuestionRequest;
import com.astro.api.form.model.FormQuestionType;

public interface FormQuestionStrategy {
    FormQuestionType supportedType();
    void validate(FormQuestionRequest question, FormValidationMode mode);
}
