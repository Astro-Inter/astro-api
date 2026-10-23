package com.astro.api.chat.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.chat.dto.response.ChatResponse;
import com.astro.api.chat.model.ConversationDocument;
import com.astro.api.chat.repository.ChatMongoRepository;
import com.astro.api.chat.repository.LastMessageProjection;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserChatProjection;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.UserAvatarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {
    private UserRepository users;
    private ChatMongoRepository mongo;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private UserAvatarService avatars;
    private ChatService service;
    private UserChatProjection current;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        users = mock(UserRepository.class);
        mongo = mock(ChatMongoRepository.class);
        redis = mock(StringRedisTemplate.class);
        avatars = mock(UserAvatarService.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        current = user(1L, "João", UserType.COLABORADOR, 10L);
        when(users.findChatUserByFirebaseUid("uid")).thenReturn(Optional.of(current));
        service = new ChatService(users, mongo, redis, avatars);
    }

    @Test
    void returnsEmptyWithoutCallingRedisWhenThereAreNoConversations() {
        when(mongo.findConversationsFor(1L)).thenReturn(List.of());

        assertEquals(List.of(), service.findChats(authenticatedUser()));
        verify(values, never()).multiGet(anyCollection());
    }

    @Test
    void batchesParticipantsAndUnreadCountsAndSortsByLatestMessage() {
        when(mongo.findConversationsFor(1L)).thenReturn(List.of(
                new ConversationDocument("conv-old", List.of(1L, 2L)),
                new ConversationDocument("conv-new", List.of(1L, 3L))));
        UserChatProjection ana = user(2L, "Ana Lima", UserType.GESTOR, 10L);
        UserChatProjection carlos = user(3L, "Carlos Souza", UserType.COLABORADOR, 10L);
        when(users.findChatUsersByIds(anyCollection())).thenReturn(List.of(ana, carlos));
        when(mongo.findLatestMessages(anyCollection())).thenReturn(List.of(
                new LastMessageProjection("conv-old", 1L, "Obrigado!",
                        Instant.parse("2026-10-20T10:00:00Z")),
                new LastMessageProjection("conv-new", 3L, "Concluí",
                        Instant.parse("2026-10-21T10:00:00Z"))));
        when(values.multiGet(List.of("chat:unread:conv-new:1", "chat:unread:conv-old:1")))
                .thenReturn(java.util.Arrays.asList("3", null));
        when(avatars.findUrls(anyCollection())).thenReturn(java.util.Map.of(2L, "https://cdn.example/ana.jpg"));

        List<ChatResponse> result = service.findChats(authenticatedUser());

        assertEquals(List.of("conv-new", "conv-old"), result.stream().map(ChatResponse::id).toList());
        assertEquals(3, result.getFirst().unreadCount());
        assertEquals(0, result.get(1).unreadCount());
        assertEquals(3L, result.getFirst().participant().id());
        assertEquals(UserType.COLABORADOR, result.getFirst().participant().role());
        assertEquals("https://cdn.example/ana.jpg", result.get(1).participant().profilePhotoUrl());
        assertEquals("CS", result.getFirst().participant().avatarInitials());
        assertEquals("Concluí", result.getFirst().lastMessage().content());
        assertEquals(1L, result.get(1).lastMessage().senderId());
        verify(users).findChatUsersByIds(anyCollection());
        verify(mongo).findLatestMessages(anyCollection());
    }

    @Test
    void excludesCrossWorkspaceParticipantsAndConversationsWithoutMessages() {
        when(mongo.findConversationsFor(1L)).thenReturn(List.of(
                new ConversationDocument("foreign", List.of(1L, 2L)),
                new ConversationDocument("no-message", List.of(1L, 3L))));
        UserChatProjection foreignParticipant = user(2L, "Outra workspace", UserType.GESTOR, 20L);
        UserChatProjection localParticipant = user(3L, "Sem mensagem", UserType.COLABORADOR, 10L);
        when(users.findChatUsersByIds(anyCollection())).thenReturn(List.of(foreignParticipant, localParticipant));
        when(mongo.findLatestMessages(anyCollection())).thenReturn(List.of());

        assertEquals(List.of(), service.findChats(authenticatedUser()));
        verify(values, never()).multiGet(anyCollection());
    }

    @Test
    void propagatesRedisFailuresInsteadOfReturningZero() {
        when(mongo.findConversationsFor(1L)).thenReturn(List.of(
                new ConversationDocument("conv", List.of(1L, 2L))));
        UserChatProjection ana = user(2L, "Ana Lima", UserType.GESTOR, 10L);
        when(users.findChatUsersByIds(anyCollection())).thenReturn(List.of(ana));
        when(mongo.findLatestMessages(anyCollection())).thenReturn(List.of(
                new LastMessageProjection("conv", 2L, "Oi", Instant.parse("2026-10-21T10:00:00Z"))));
        when(values.multiGet(anyCollection())).thenThrow(new IllegalStateException("Redis indisponível"));

        assertThrows(IllegalStateException.class, () -> service.findChats(authenticatedUser()));
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser("uid", "joao@example.com", Role.COLABORADOR);
    }

    private UserChatProjection user(Long id, String name, UserType role, Long workspaceId) {
        UserChatProjection projection = mock(UserChatProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getName()).thenReturn(name);
        when(projection.getRole()).thenReturn(role);
        when(projection.getWorkspaceId()).thenReturn(workspaceId);
        return projection;
    }
}
