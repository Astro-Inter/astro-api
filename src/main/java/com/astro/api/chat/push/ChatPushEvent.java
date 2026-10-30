package com.astro.api.chat.push;

import java.time.Instant;

public record ChatPushEvent(int version, String messageId, String conversationId, String senderId,
                            String recipientId, String workspaceId, String recipientUid,
                            String senderName, String preview, Instant createdAt) { }
