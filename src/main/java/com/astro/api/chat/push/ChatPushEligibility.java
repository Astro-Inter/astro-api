package com.astro.api.chat.push;

import com.astro.api.chat.service.ChatCommunicationPolicy;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.repository.ChatSendUserProjection;
import com.astro.api.user.repository.UserRepository;
import java.util.List;
import com.astro.api.chat.model.ChatPushEvent;
import org.springframework.stereotype.Component;

@Component
public class ChatPushEligibility {
    private final UserRepository users;

    public ChatPushEligibility(UserRepository users) { this.users = users; }

    public boolean allows(ChatPushEvent event) {
        Long senderId = event.senderId();
        Long recipientId = event.recipientId();
        Long workspaceId = event.workspaceId();
        List<ChatSendUserProjection> participants = users.findChatSendUsers(event.recipientUid(),
                List.of(senderId, recipientId));
        ChatSendUserProjection sender = participants.stream().filter(user -> senderId.equals(user.getId())).findFirst().orElse(null);
        ChatSendUserProjection recipient = participants.stream().filter(user -> recipientId.equals(user.getId())).findFirst().orElse(null);
        return active(sender, workspaceId) && active(recipient, workspaceId)
                && event.recipientUid().equals(recipient.getFirebaseUid())
                && ChatCommunicationPolicy.canCommunicate(sender.getType(), recipient.getType());
    }

    private static boolean active(ChatSendUserProjection user, Long workspaceId) {
        return user != null && user.getStatus() == UserStatus.ATIVO
                && Boolean.TRUE.equals(user.getUnitActive()) && workspaceId.equals(user.getWorkspaceId());
    }
}
