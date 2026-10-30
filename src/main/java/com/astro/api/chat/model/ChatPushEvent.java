package com.astro.api.chat.model;

import java.time.Instant;

public record ChatPushEvent(int version, String messageId, String conversationId, Long senderId,
                            Long recipientId, Long workspaceId, String recipientUid,
                            String senderName, String preview, Instant createdAt) { }
