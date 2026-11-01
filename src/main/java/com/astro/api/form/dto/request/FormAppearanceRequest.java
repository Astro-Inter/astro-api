package com.astro.api.form.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FormAppearanceRequest(
        @NotBlank(message = "{form.appearance.icon}")
        @Pattern(regexp = "document|calendar|feedback|person-outline|security|check|building|collaborators|position|clock", message = "{form.appearance.icon}") String icon,
        @NotBlank(message = "{form.appearance.color}")
        @Pattern(regexp = "purple|pink|blue|green|teal|indigo|orange|rose|violet|slate", message = "{form.appearance.color}") String color) {}
