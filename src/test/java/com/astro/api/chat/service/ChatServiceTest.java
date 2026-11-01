package com.astro.api.chat.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.chat.dto.response.ChatResponse;
import com.astro.api.chat.mapper.ChatMessageMapper;
import com.astro.api.chat.model.ChatMessageDocument;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

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
        service = new ChatService(users, mongo, redis, avatars, new ChatMessageMapper(),
                mock(ChatMessagePublisher.class), mock(com.astro.api.chat.push.ChatPushSettings.class),
                mock(com.astro.api.chat.push.ChatPushOutbox.class));
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
        when(values.multiGet(List.of("conversa:nao_lidas:conv-new:1", "conversa:nao_lidas:conv-old:1")))
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

    @Test
    void pagesMessagesUsingCompositeCursorAndReturnsChronologicalOrder() {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        String oldestId = "10000000-0000-4000-8000-000000000001";
        String middleId = "10000000-0000-4000-8000-000000000002";
        String newestId = "10000000-0000-4000-8000-000000000003";
        Instant date = Instant.parse("2026-10-21T10:00:00Z");
        when(mongo.findConversation(conversationId)).thenReturn(new ConversationDocument(conversationId, List.of(1L, 2L)));
        UserChatProjection ana = user(2L, "Ana", UserType.GESTOR, 10L);
        when(users.findChatUsersByIds(List.of(1L, 2L))).thenReturn(List.of(current, ana));
        when(mongo.findMessagesBefore(conversationId, null, null, 3)).thenReturn(List.of(
                message(newestId, conversationId, date.plusSeconds(2)),
                new ChatMessageDocument(middleId, conversationId, 2L, "reply", oldestId, date.plusSeconds(1)),
                message(oldestId, conversationId, date)));
        when(mongo.findMessagesBefore(conversationId, date.plusSeconds(1), middleId, 3)).thenReturn(List.of(
                message("10000000-0000-4000-8000-000000000000", conversationId, date.minusSeconds(1))));

        var first = service.findMessages(authenticatedUser(), conversationId, 2, null);
        var second = service.findMessages(authenticatedUser(), conversationId, 2, first.nextCursor());

        assertEquals(List.of(middleId, newestId), first.messages().stream().map(m -> m.id()).toList());
        assertEquals(oldestId, first.messages().getFirst().replyToMessageId());
        assertEquals(true, first.hasMore());
        assertEquals(false, second.hasMore());
        assertEquals(null, second.nextCursor());
        assertEquals(List.of("10000000-0000-4000-8000-000000000000"),
                second.messages().stream().map(m -> m.id()).toList());
        verify(mongo).findMessagesBefore(conversationId, date.plusSeconds(1), middleId, 3);
        verify(redis, never()).opsForValue();
    }

    @Test
    void rejectsMalformedCursorAndOutOfRangeLimit() {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        when(mongo.findConversation(conversationId)).thenReturn(new ConversationDocument(conversationId, List.of(1L, 2L)));
        UserChatProjection ana = user(2L, "Ana", UserType.GESTOR, 10L);
        when(users.findChatUsersByIds(List.of(1L, 2L))).thenReturn(List.of(current, ana));
        assertThrows(com.astro.api.common.exception.BusinessException.class,
                () -> service.findMessages(authenticatedUser(), conversationId, 101, null));
        assertThrows(com.astro.api.common.exception.BusinessException.class,
                () -> service.findMessages(authenticatedUser(), conversationId, 30, "bad-cursor"));
    }

    @Test
    void rejectsNonParticipantsAndCrossWorkspaceConversations() {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        when(mongo.findConversation(conversationId)).thenReturn(new ConversationDocument(conversationId, List.of(2L, 3L)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.findMessages(authenticatedUser(), conversationId, 30, null));

        when(mongo.findConversation(conversationId)).thenReturn(new ConversationDocument(conversationId, List.of(1L, 2L)));
        UserChatProjection foreign = user(2L, "Ana", UserType.GESTOR, 20L);
        when(users.findChatUsersByIds(List.of(1L, 2L))).thenReturn(List.of(current, foreign));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.findMessages(authenticatedUser(), conversationId, 30, null));
        verify(redis, never()).opsForValue();
    }

    @Test
    void handlesEmptyHistoryAndRequestsAtMostLimitPlusOne() {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        when(mongo.findConversation(conversationId)).thenReturn(new ConversationDocument(conversationId, List.of(1L, 2L)));
        UserChatProjection ana = user(2L, "Ana", UserType.GESTOR, 10L);
        when(users.findChatUsersByIds(List.of(1L, 2L))).thenReturn(List.of(current, ana));
        when(mongo.findMessagesBefore(conversationId, null, null, 101)).thenReturn(List.of(
                message("10000000-0000-4000-8000-000000000001", conversationId, Instant.parse("2026-10-21T10:00:00Z"))));
        when(mongo.findMessagesBefore(conversationId, null, null, 2)).thenReturn(List.of());

        var maximumPage = service.findMessages(authenticatedUser(), conversationId, 100, null);
        var emptyPage = service.findMessages(authenticatedUser(), conversationId, 1, null);

        assertEquals(1, maximumPage.messages().size());
        assertEquals(false, maximumPage.hasMore());
        assertEquals(List.of(), emptyPage.messages());
        assertEquals(null, emptyPage.nextCursor());
        assertEquals(false, emptyPage.hasMore());
        verify(mongo).findMessagesBefore(conversationId, null, null, 101);
        verify(mongo).findMessagesBefore(conversationId, null, null, 2);
    }

    @Test
    void returnsNotFoundWhenConversationDoesNotExist() {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        when(mongo.findConversation(conversationId)).thenReturn(null);

        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,
                () -> service.findMessages(authenticatedUser(), conversationId, 30, null));
        verify(mongo, never()).findMessagesBefore(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void marksAuthorizedConversationReadByDeletingOnlyAuthenticatedUsersKey() {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        UserChatProjection ana = user(2L, "Ana", UserType.GESTOR, 10L);
        when(mongo.findConversation(conversationId)).thenReturn(
                new ConversationDocument(conversationId, List.of(1L, 2L)));
        when(users.findChatUsersByIds(List.of(1L, 2L)))
                .thenReturn(List.of(current, ana));

        service.markAsRead(authenticatedUser(), conversationId);

        verify(redis).delete("conversa:nao_lidas:" + conversationId + ":1");
        verify(redis, never()).delete("conversa:nao_lidas:" + conversationId + ":2");
        verify(mongo, never()).findMessagesBefore(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt());
        var order = inOrder(users, mongo, redis);
        order.verify(users).findChatUserByFirebaseUid("uid");
        order.verify(mongo).findConversation(conversationId);
        order.verify(users).findChatUsersByIds(List.of(1L, 2L));
        order.verify(redis).delete("conversa:nao_lidas:" + conversationId + ":1");
    }

    @Test
    void repeatedReadRequestsDeleteTheSameKeyAndRemainIdempotent() {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        UserChatProjection ana = user(2L, "Ana", UserType.GESTOR, 10L);
        when(mongo.findConversation(conversationId)).thenReturn(
                new ConversationDocument(conversationId, List.of(1L, 2L)));
        when(users.findChatUsersByIds(List.of(1L, 2L)))
                .thenReturn(List.of(current, ana));

        service.markAsRead(authenticatedUser(), conversationId);
        service.markAsRead(authenticatedUser(), conversationId);

        verify(redis, times(2)).delete("conversa:nao_lidas:" + conversationId + ":1");
    }

    @Test
    void doesNotDeleteUnreadKeyForInvalidMissingOrUnauthorizedConversation() {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        assertThrows(com.astro.api.common.exception.BusinessException.class,
                () -> service.markAsRead(authenticatedUser(), "invalid"));

        when(mongo.findConversation(conversationId)).thenReturn(null);
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,
                () -> service.markAsRead(authenticatedUser(), conversationId));

        when(mongo.findConversation(conversationId)).thenReturn(
                new ConversationDocument(conversationId, List.of(2L, 3L)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.markAsRead(authenticatedUser(), conversationId));

        when(mongo.findConversation(conversationId)).thenReturn(
                new ConversationDocument(conversationId, List.of(1L, 2L)));
        UserChatProjection foreign = user(2L, "Ana", UserType.GESTOR, 20L);
        when(users.findChatUsersByIds(List.of(1L, 2L)))
                .thenReturn(List.of(current, foreign));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.markAsRead(authenticatedUser(), conversationId));
        verify(redis, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsAdminWithoutDeletingUnreadKey() {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.markAsRead(new AuthenticatedUser("uid", "admin@example.com", Role.ADMIN), conversationId));
        verify(redis, never()).delete(org.mockito.ArgumentMatchers.anyString());
        verify(mongo, never()).findConversation(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void propagatesRedisFailureInsteadOfReportingSuccessfulRead() {
        String conversationId = "11111111-2222-4333-8444-555555555555";
        UserChatProjection ana = user(2L, "Ana", UserType.GESTOR, 10L);
        when(mongo.findConversation(conversationId)).thenReturn(
                new ConversationDocument(conversationId, List.of(1L, 2L)));
        when(users.findChatUsersByIds(List.of(1L, 2L)))
                .thenReturn(List.of(current, ana));
        when(redis.delete("conversa:nao_lidas:" + conversationId + ":1"))
                .thenThrow(new IllegalStateException("Redis indisponível"));

        assertThrows(IllegalStateException.class,
                () -> service.markAsRead(authenticatedUser(), conversationId));
    }

    private ChatMessageDocument message(String id, String conversationId, Instant date) {
        return new ChatMessageDocument(id, conversationId, 1L, "mensagem " + id,
                null, date);
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
        when(projection.getStatus()).thenReturn(com.astro.api.user.model.UserStatus.ATIVO);
        when(projection.getUnitActive()).thenReturn(true);
        return projection;
    }
}
