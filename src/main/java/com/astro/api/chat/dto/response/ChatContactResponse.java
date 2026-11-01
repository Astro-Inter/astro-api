package com.astro.api.chat.dto.response;

import com.astro.api.user.model.UserType;
import io.swagger.v3.oas.annotations.media.Schema;

public record ChatContactResponse(Long id, String name, UserType role,
                                  @Schema(description = "URL temporária da foto; null quando ausente", nullable = true)
                                  String profilePhotoUrl) { }
