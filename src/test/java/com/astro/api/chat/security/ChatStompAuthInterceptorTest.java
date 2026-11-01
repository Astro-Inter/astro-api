package com.astro.api.chat.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.astro.api.auth.security.Role;
import com.astro.api.auth.service.FirebaseTokenService;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.ChatConnectionProjection;
import com.astro.api.user.repository.UserRepository;
import com.google.firebase.ErrorCode;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

class ChatStompAuthInterceptorTest {
    private FirebaseTokenService firebaseTokenService;
    private UserRepository userRepository;
    private ChatStompAuthInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        firebaseTokenService = mock(FirebaseTokenService.class);
        userRepository = mock(UserRepository.class);
        interceptor = new ChatStompAuthInterceptor(firebaseTokenService, userRepository);
        channel = mock(MessageChannel.class);
    }

    @Test
    void authenticatesConnectAndAssociatesInternalCollaboratorPrincipal() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("firebase-uid");
        when(firebaseTokenService.verify("valid-id-token")).thenReturn(token);
        ChatConnectionProjection connection = connection(UserType.GESTOR_WORKSPACE, UserStatus.ATIVO, true);
        when(userRepository.findChatConnectionByFirebaseUid("firebase-uid"))
                .thenReturn(Optional.of(connection));

        Message<?> message = frame(StompCommand.CONNECT, "Bearer valid-id-token", null, null);
        Message<?> returned = interceptor.preSend(message, channel);
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(returned, StompHeaderAccessor.class);

        ChatPrincipal principal = (ChatPrincipal) accessor.getUser();
        assertEquals("25", principal.getName());
        assertEquals(7L, principal.workspaceId());
        assertEquals(Role.GESTOR_WORKSPACE, principal.role());
        assertEquals("firebase-uid", principal.firebaseUid());
        verify(userRepository).findChatConnectionByFirebaseUid("firebase-uid");
    }

    @Test
    void rejectsMissingAndMalformedAuthorization() {
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "Basic abc", null, null), channel));
    }

    @Test
    void rejectsInvalidAndExpiredFirebaseTokens() throws Exception {
        for (AuthErrorCode error : new AuthErrorCode[]{AuthErrorCode.INVALID_ID_TOKEN, AuthErrorCode.EXPIRED_ID_TOKEN}) {
            FirebaseAuthException exception = new FirebaseAuthException(
                    ErrorCode.UNAUTHENTICATED, "invalid token", null, null, error);
            doThrow(exception).when(firebaseTokenService).verify("invalid-id-token");
            assertThrows(AccessDeniedException.class,
                    () -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer invalid-id-token", null, null), channel));
        }
    }

    @Test
    void allowsEachOperationalUserType() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("firebase-uid");
        when(firebaseTokenService.verify("valid-id-token")).thenReturn(token);
        for (UserType type : new UserType[]{UserType.COLABORADOR, UserType.GESTOR, UserType.GESTOR_WORKSPACE}) {
            ChatConnectionProjection connection = connection(type, UserStatus.ATIVO, true);
            when(userRepository.findChatConnectionByFirebaseUid("firebase-uid")).thenReturn(Optional.of(connection));
            Message<?> connected = interceptor.preSend(
                    frame(StompCommand.CONNECT, "Bearer valid-id-token", null, null), channel);
            ChatPrincipal principal = (ChatPrincipal) StompHeaderAccessor.getAccessor(connected, StompHeaderAccessor.class).getUser();
            assertEquals(type.name(), principal.role().name());
        }
    }

    @Test
    void deniesAccountsWithoutInternalIdentityOrDisabledStatus() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("firebase-uid");
        when(firebaseTokenService.verify("valid-id-token")).thenReturn(token);
        when(userRepository.findChatConnectionByFirebaseUid("firebase-uid")).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer valid-id-token", null, null), channel));

        ChatConnectionProjection disabled = connection(UserType.COLABORADOR, UserStatus.DESATIVADO, true);
        when(userRepository.findChatConnectionByFirebaseUid("firebase-uid"))
                .thenReturn(Optional.of(disabled));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer valid-id-token", null, null), channel));
    }

    @Test
    void permitsOnlyPrivateMessagesSubscriptionForAuthenticatedSession() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("firebase-uid");
        when(firebaseTokenService.verify("valid-id-token")).thenReturn(token);
        ChatConnectionProjection connection = connection(UserType.COLABORADOR, UserStatus.ATIVO, true);
        when(userRepository.findChatConnectionByFirebaseUid("firebase-uid"))
                .thenReturn(Optional.of(connection));
        Message<?> connected = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer valid-id-token", null, null), channel);
        Object user = StompHeaderAccessor.getAccessor(connected, StompHeaderAccessor.class).getUser();

        Message<?> subscription = frame(StompCommand.SUBSCRIBE, null, "/user/queue/messages", user);
        assertSame(subscription, interceptor.preSend(subscription, channel));
        Message<?> errors = frame(StompCommand.SUBSCRIBE, null, "/user/queue/errors", user);
        assertSame(errors, interceptor.preSend(errors, channel));

        for (String destination : new String[]{"/queue/messages", "/topic/messages", "/user/25/queue/messages"}) {
            assertThrows(AccessDeniedException.class,
                    () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, destination, user), channel));
        }
    }

    @Test
    void acceptsOnlyChatSendAndRejectsFramesWithoutAuthenticatedSession() {
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/user/queue/messages", null), channel));
        ChatPrincipal principal = new ChatPrincipal(25L, 7L, Role.COLABORADOR, "firebase-uid");
        Message<?> send = frame(StompCommand.SEND, null, "/app/chat.send", principal);
        assertSame(send, interceptor.preSend(send, channel));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(frame(StompCommand.SEND, null, "/app/other", principal), channel));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(frame(StompCommand.SEND, null, "/queue/messages", principal), channel));
    }

    @Test
    void preservesTechnicalFirebaseFailuresAsServerErrors() throws Exception {
        FirebaseAuthException exception = new FirebaseAuthException(
                ErrorCode.UNAVAILABLE, "Firebase temporarily unavailable", null, null,
                AuthErrorCode.CERTIFICATE_FETCH_FAILED);
        doThrow(exception).when(firebaseTokenService).verify("temporary-failure");
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer temporary-failure", null, null), channel));
    }

    @Test
    void allowsOnlyAuthenticatedTypingDestinations() {
        ChatPrincipal principal = new ChatPrincipal(25L, 7L, Role.GESTOR_WORKSPACE, "uid");
        interceptor.preSend(frame(StompCommand.SEND, null, "/app/chat.typing", principal), channel);
        interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/user/queue/typing", principal), channel);
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(frame(StompCommand.SEND, null, "/app/chat.typing", null), channel));
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/queue/typing", principal), channel));
    }

    private static ChatConnectionProjection connection(UserType type, UserStatus status, boolean unitActive) {
        ChatConnectionProjection projection = mock(ChatConnectionProjection.class);
        when(projection.getId()).thenReturn(25L);
        when(projection.getWorkspaceId()).thenReturn(7L);
        when(projection.getType()).thenReturn(type);
        when(projection.getStatus()).thenReturn(status);
        when(projection.getUnitActive()).thenReturn(unitActive);
        return projection;
    }

    private static Message<byte[]> frame(StompCommand command, String authorization, String destination, Object user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setDestination(destination);
        accessor.setUser((java.security.Principal) user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
