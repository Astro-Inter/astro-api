package com.astro.api.form.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FormReviewStatus {
    APPROVED("approved", "APROVADO"), REJECTED("rejected", "REPROVADO");
    private final String value;
    private final String storedValue;
    FormReviewStatus(String value, String storedValue) { this.value = value; this.storedValue = storedValue; }
    @JsonValue public String value() { return value; }
    public String storedValue() { return storedValue; }
    @JsonCreator public static FormReviewStatus fromValue(String value) {
        for (FormReviewStatus status : values()) if (status.value.equals(value)) return status;
        throw new IllegalArgumentException("Decisão de análise inválida");
    }
    public static FormReviewStatus fromStoredValue(String value) {
        for (FormReviewStatus status : values()) if (status.storedValue.equals(value)) return status;
        throw new IllegalArgumentException("Decisão de análise armazenada inválida");
    }
}
