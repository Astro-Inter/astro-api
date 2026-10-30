package com.astro.api.chat.push;

import com.astro.api.user.repository.ChatSendUserProjection;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.chat.mapper.ChatPushEventMapper;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatPushEligibilityTest {
    @Test void revalidatesStatusWorkspaceRoleAndRecipientIdentity() {
        UserRepository users = mock(UserRepository.class);
        ChatSendUserProjection sender = participant(1L, UserType.COLABORADOR);
        ChatSendUserProjection recipient = participant(2L, UserType.GESTOR);
        when(users.findChatSendUsers(eq("uid-2"), anyList())).thenReturn(List.of(sender, recipient));
        Document payload = new Document("senderId", "1").append("recipientId", "2")
                .append("workspaceId", "10").append("recipientUid", "uid-2");
        ChatPushEligibility guard = new ChatPushEligibility(users);
        assertTrue(guard.allows(payload));
        when(recipient.getFirebaseUid()).thenReturn("changed");
        assertFalse(guard.allows(payload));
        when(recipient.getFirebaseUid()).thenReturn("uid-2");
        when(recipient.getWorkspaceId()).thenReturn(11L);
        assertFalse(guard.allows(payload));
        when(recipient.getWorkspaceId()).thenReturn(10L);
        when(recipient.getUnitActive()).thenReturn(false);
        assertFalse(guard.allows(payload));
        when(recipient.getUnitActive()).thenReturn(true);
        when(recipient.getType()).thenReturn(UserType.COLABORADOR);
        assertFalse(guard.allows(payload));
        when(users.findChatSendUsers(any(), anyList())).thenReturn(List.of(sender));
        assertFalse(guard.allows(payload));
    }

    @Test void previewNormalizesWhitespaceAndPreservesUnicodeCodePoints() {
        assertEquals("Olá Astro", ChatPushEventMapper.truncate(" Olá\n Astro ", 160));
        String preview = ChatPushEventMapper.truncate("😀".repeat(170), 160);
        assertEquals(160, preview.codePointCount(0, preview.length()));
        assertTrue(preview.endsWith("…"));
    }

    private ChatSendUserProjection participant(Long id, UserType type) {
        ChatSendUserProjection user = mock(ChatSendUserProjection.class);
        when(user.getId()).thenReturn(id);
        when(user.getFirebaseUid()).thenReturn("uid-" + id);
        when(user.getType()).thenReturn(type);
        when(user.getStatus()).thenReturn(UserStatus.ATIVO);
        when(user.getWorkspaceId()).thenReturn(10L);
        when(user.getUnitActive()).thenReturn(true);
        return user;
    }
}
