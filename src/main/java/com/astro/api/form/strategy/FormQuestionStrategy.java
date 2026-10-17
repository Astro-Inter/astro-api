package com.astro.api.form.strategy;

import com.astro.api.form.dto.request.FormQuestionDefinitionRequest;
import com.astro.api.form.model.FormQuestionType;

public interface FormQuestionStrategy {
    FormQuestionType supportedType();
    void validate(FormQuestionDefinitionRequest question, FormValidationMode mode);
}
