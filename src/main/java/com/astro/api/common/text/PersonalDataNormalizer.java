package com.astro.api.common.text;

import java.util.Locale;

public final class PersonalDataNormalizer {
    private PersonalDataNormalizer() { }

    public static String name(String value) {
        return value == null ? "" : value.trim();
    }

    public static String cpf(String value) {
        return name(value).replace(".", "").replace("-", "");
    }

    public static String email(String value) {
        return name(value).toLowerCase(Locale.ROOT);
    }

    // Limpeza usada pelos fluxos que já removem todos os caracteres não numéricos.
    public static String cpfDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
