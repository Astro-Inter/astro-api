package com.astro.api.chat.dto.response;

import com.astro.api.user.model.UserType;

public record ChatContactResponse(Long id, String name, UserType role) { }
