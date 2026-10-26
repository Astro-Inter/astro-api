package com.astro.api.chat.mapper;

import com.astro.api.chat.dto.response.ChatMessageResponse;
import com.astro.api.chat.model.ChatMessageDocument;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageMapper {
    public List<ChatMessageResponse> toResponses(String conversationId, List<ChatMessageDocument> messages) {
        return messages.stream()
                .map(message -> new ChatMessageResponse(message.id(), conversationId, message.senderId(),
                        message.content(), message.replyToMessageId(), message.createdAt()))
                .toList();
    }
}
