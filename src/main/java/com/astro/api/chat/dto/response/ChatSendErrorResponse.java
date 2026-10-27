package com.astro.api.chat.dto.response;

public record ChatSendErrorResponse(String clientMessageId, String code, String message) { }
