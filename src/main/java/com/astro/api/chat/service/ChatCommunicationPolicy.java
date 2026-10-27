package com.astro.api.chat.service;

import com.astro.api.user.model.UserType;
import java.util.List;

public final class ChatCommunicationPolicy {
    private static final List<UserType> MANAGERS = List.of(UserType.GESTOR, UserType.GESTOR_WORKSPACE);
    private static final List<UserType> ALL = List.of(UserType.COLABORADOR, UserType.GESTOR,
            UserType.GESTOR_WORKSPACE);

    private ChatCommunicationPolicy() { }

    public static List<UserType> allowedRecipients(UserType sender) {
        return sender == UserType.COLABORADOR ? MANAGERS : ALL;
    }

    public static boolean canCommunicate(UserType sender, UserType recipient) {
        return sender != null && recipient != null && allowedRecipients(sender).contains(recipient);
    }
}
