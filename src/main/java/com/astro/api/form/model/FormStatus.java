package com.astro.api.form.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum FormStatus {
    DRAFT("RASCUNHO"), PENDING("PENDENTE"), COMPLETED("CONCLUIDO");

    private final String value;

    FormStatus(String value) { this.value = value; }

    @JsonValue
    public String value() { return value; }

    @JsonCreator
    public static FormStatus fromValue(String value) {
        return Arrays.stream(values()).filter(status -> status.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Status de formulário inválido"));
    }
}
