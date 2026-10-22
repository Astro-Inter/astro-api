package com.astro.api.chat.dto.response;

import com.astro.api.user.model.UserType;

public record ChatParticipantResponse(Long id, String name, UserType role) { }
