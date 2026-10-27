package com.astro.api.chat.service;

public class ChatSendException extends RuntimeException {
    private final String clientMessageId;
    private final String code;

    public ChatSendException(String clientMessageId, String code, String message, Throwable cause) {
        super(message, cause);
        this.clientMessageId = clientMessageId;
        this.code = code;
    }

    public String clientMessageId() { return clientMessageId; }
    public String code() { return code; }
}
