package com.astro.api.chat.push;

import com.astro.api.user.repository.ChatSendUserProjection;

public record ChatPushParticipants(ChatSendUserProjection sender, ChatSendUserProjection recipient) { }
