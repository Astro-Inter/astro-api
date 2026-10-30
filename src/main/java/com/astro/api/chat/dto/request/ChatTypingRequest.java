package com.astro.api.chat.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public class ChatTypingRequest {
    private Long recipientId;
    private String conversationId;
    private Boolean typing;

    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Boolean getTyping() { return typing; }
    public void setTyping(Boolean typing) { this.typing = typing; }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Campo não permitido: " + name);
    }
}
