package com.astro.api.chat.service;

public class ChatDeliveryUnavailableException extends RuntimeException {
    public ChatDeliveryUnavailableException(Throwable cause) {
        super(cause);
    }
}
