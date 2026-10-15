package com.astro.api.form.model;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Arrays;

public enum FormQuestionType {
    TEXT("texto"), OPTION("alternativa"), PHOTO("foto"), DATE("data");

    private final String value;
    FormQuestionType(String value) { this.value = value; }
    @JsonValue public String value() { return value; }

    @JsonCreator
    public static FormQuestionType fromValue(String value) {
        return Arrays.stream(values()).filter(type -> type.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de pergunta inválido"));
    }
}
