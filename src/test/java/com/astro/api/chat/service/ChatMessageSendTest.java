package com.astro.api.chat.service;

import com.astro.api.auth.security.Role;
import com.astro.api.chat.dto.request.ChatSendRequest;
import com.astro.api.chat.mapper.ChatMessageMapper;
import com.astro.api.chat.model.ChatMessageDocument;
import com.astro.api.chat.model.ChatMessageIds;
import com.astro.api.chat.model.ConversationDocument;
import com.astro.api.chat.repository.ChatMongoRepository;
import com.astro.api.chat.security.ChatPrincipal;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.ChatSendUserProjection;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.UserAvatarService;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageSendTest {
    private final UserRepository users = mock(UserRepository.class);
    private final ChatMongoRepository mongo = mock(ChatMongoRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final UserAvatarService avatars = mock(UserAvatarService.class);
    private final ChatMessagePublisher messagePublisher = mock(ChatMessagePublisher.class);
    private final ChatPrincipal sender = new ChatPrincipal(1L, 10L, Role.COLABORADOR, "uid-1");
    private final ChatService service = new ChatService(users, mongo, redis, avatars,
            new ChatMessageMapper(), messagePublisher);

    @BeforeEach
    void setUp() {
        ChatSendUserProjection senderUser = participant(1L, UserType.COLABORADOR, 10L);
        ChatSendUserProjection recipientUser = participant(2L, UserType.GESTOR, 10L);
        when(users.findChatSendUsers(eq("uid-1"), anyList())).thenReturn(List.of(senderUser, recipientUser));
        doAnswer(invocation -> ((Function<?, ?>) invocation.getArgument(0)).apply(null))
                .when(mongo).inTransaction(any());
        when(redis.execute(any(), anyList(), any())).thenReturn(1L);
    }

    @Test
    void createsConversationAndPersistsMessageBeforeRedisAndPrivateDelivery() {
        ChatSendRequest request = request(2L, "Olá", "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        when(mongo.findConversationByPair(null, 1L, 2L)).thenReturn(null);
        when(mongo.findMessage(null, 1L, request.getClientMessageId())).thenReturn(null);

        var response = service.send(sender, request);

        assertEquals(request.getClientMessageId(), response.clientMessageId());
        assertEquals(1L, response.senderId());
        assertEquals(ChatMessageIds.forConversation(1L, 2L), response.conversationId());
        assertEquals(ChatMessageIds.forMessage(1L, request.getClientMessageId()), response.id());
        verify(mongo).insertConversation(any(), any(ConversationDocument.class));
        verify(mongo).insertMessage(any(), any(ChatMessageDocument.class));
        verify(redis).execute(any(), eq(List.of(
                "chat:unread:" + response.conversationId() + ":2",
                "chat:unread:processed:" + response.conversationId())), eq(response.id()));
        verify(messagePublisher).publish(2L, response);
    }

    @Test
    void idempotentRetryReusesMessageAndRunsRedisDedupScriptAgain() {
        ChatSendRequest request = request(2L, "Olá", "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        ConversationDocument conversation = new ConversationDocument("11111111-2222-4333-8444-555555555555",
                List.of(1L, 2L));
        ChatMessageDocument original = new ChatMessageDocument(
                ChatMessageIds.forMessage(1L, request.getClientMessageId()),
                conversation.id(), 1L, "Olá", null, Instant.parse("2026-10-26T10:00:00Z"));
        when(mongo.findConversationByPair(null, 1L, 2L)).thenReturn(conversation);
        when(mongo.findMessage(null, 1L, request.getClientMessageId())).thenReturn(original);

        var response = service.send(sender, request);

        assertEquals(original.id(), response.id());
        verify(mongo, never()).insertMessage(any(), any());
        verify(redis).execute(any(), anyList(), eq(original.id()));
    }

    @Test
    void rejectsDisallowedRolesWithoutTouchingMongoOrRedis() {
        ChatSendUserProjection senderUser = participant(1L, UserType.COLABORADOR, 10L);
        ChatSendUserProjection peerUser = participant(2L, UserType.COLABORADOR, 10L);
        when(users.findChatSendUsers(eq("uid-1"), anyList())).thenReturn(List.of(senderUser, peerUser));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.send(sender, request(2L, "Olá", "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")));
        verify(mongo, never()).inTransaction(any());
        verify(redis, never()).execute(any(), anyList(), any());
    }

    @Test
    void requiresExactlyOneConversationSelectorAndValidText() {
        ChatSendRequest request = request(2L, "  \n", "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee");
        assertThrows(com.astro.api.common.exception.BusinessException.class, () -> service.send(sender, request));
        verify(users, never()).findChatSendUsers(any(), anyList());
        verify(mongo, never()).inTransaction(any());
    }

    private static ChatSendRequest request(Long recipientId, String content, String clientMessageId) {
        ChatSendRequest request = new ChatSendRequest();
        request.setRecipientId(recipientId);
        request.setContent(content);
        request.setClientMessageId(clientMessageId);
        return request;
    }

    private static ChatSendUserProjection participant(Long id, UserType type, Long workspaceId) {
        ChatSendUserProjection result = mock(ChatSendUserProjection.class);
        when(result.getId()).thenReturn(id);
        when(result.getType()).thenReturn(type);
        when(result.getStatus()).thenReturn(UserStatus.ATIVO);
        when(result.getUnitActive()).thenReturn(true);
        when(result.getWorkspaceId()).thenReturn(workspaceId);
        when(result.getAuthenticatedSender()).thenReturn(id == 1L);
        return result;
    }
}
