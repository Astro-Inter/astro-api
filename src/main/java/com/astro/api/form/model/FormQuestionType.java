package com.astro.api.form.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FormQuestionType {
    TEXTO("texto"), ALTERNATIVA("alternativa"), FOTO("foto"), DATA("data");

    private final String value;
    FormQuestionType(String value) { this.value = value; }
    @JsonValue public String value() { return value; }
}
