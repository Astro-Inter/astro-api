package com.astro.api.chat.mapper;

import com.astro.api.chat.dto.response.ChatContactResponse;
import com.astro.api.user.repository.ChatContactProjection;
import org.springframework.stereotype.Component;

@Component
public class ChatContactMapper {
    public ChatContactResponse toResponse(ChatContactProjection projection, String profilePhotoUrl) {
        return new ChatContactResponse(projection.getId(), projection.getName(), projection.getRole(), profilePhotoUrl);
    }
}
