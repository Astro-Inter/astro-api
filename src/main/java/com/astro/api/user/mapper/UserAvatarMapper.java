package com.astro.api.user.mapper;

import java.util.Locale;

public final class UserAvatarMapper {
    private UserAvatarMapper() { }

    public static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] parts = name.strip().split("\\s+");
        String first = new String(Character.toChars(parts[0].codePointAt(0)));
        String last = parts.length > 1 ? new String(Character.toChars(parts[parts.length - 1].codePointAt(0))) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }
}
