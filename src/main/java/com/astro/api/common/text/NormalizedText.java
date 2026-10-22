package com.astro.api.common.text;

import java.text.Normalizer;
import java.util.Locale;

public final class NormalizedText {
    private NormalizedText() { }

    public static String key(String value) {
        return Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
