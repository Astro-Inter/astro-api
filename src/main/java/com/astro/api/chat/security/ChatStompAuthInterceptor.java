package com.astro.api.chat.security;

import com.astro.api.auth.security.Role;
import com.astro.api.auth.service.FirebaseTokenService;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.ChatConnectionProjection;
import com.astro.api.user.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class ChatStompAuthInterceptor implements ChannelInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStompAuthInterceptor.class);
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)^Bearer ([^\\s]+)$");
    private static final String PRIVATE_MESSAGES_DESTINATION = "/user/queue/messages";
    private static final Set<String> INVALID_TOKEN_ERRORS = Set.of(
            "INVALID_ID_TOKEN", "EXPIRED_ID_TOKEN", "REVOKED_ID_TOKEN", "USER_DISABLED", "USER_NOT_FOUND");

    private final FirebaseTokenService firebaseTokenService;
    private final UserRepository userRepository;

    public ChatStompAuthInterceptor(FirebaseTokenService firebaseTokenService, UserRepository userRepository) {
        this.firebaseTokenService = firebaseTokenService;
        this.userRepository = userRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            throw denied("Frame STOMP inválido");
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            authenticate(accessor);
            return message;
        }
        if (command == StompCommand.DISCONNECT) {
            return message;
        }

        ChatPrincipal principal = authenticatedPrincipal(accessor);
        if (command == StompCommand.SUBSCRIBE) {
            if (PRIVATE_MESSAGES_DESTINATION.equals(accessor.getDestination())) {
                return message;
            }
            LOGGER.warn("Assinatura STOMP negada para colaborador {} e destino {}", principal.getName(), accessor.getDestination());
            throw denied("Destino de assinatura não autorizado");
        }

        if (command == StompCommand.UNSUBSCRIBE || command == StompCommand.ACK || command == StompCommand.NACK) {
            return message;
        }

        if (command == StompCommand.SEND) {
            throw denied("Envio de mensagens ainda não está disponível");
        }

        throw denied("Frame STOMP não autorizado");
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        Matcher matcher = authorization == null ? null : BEARER_TOKEN.matcher(authorization);
        if (matcher == null || !matcher.matches()) {
            LOGGER.warn("Conexão STOMP rejeitada: cabeçalho Authorization ausente ou malformado");
            throw denied("Autenticação STOMP inválida");
        }

        FirebaseToken firebaseToken;
        try {
            firebaseToken = firebaseTokenService.verify(matcher.group(1));
        } catch (FirebaseAuthException exception) {
            String errorCode = exception.getAuthErrorCode() == null ? "" : exception.getAuthErrorCode().name();
            if (INVALID_TOKEN_ERRORS.contains(errorCode.toUpperCase(Locale.ROOT))) {
                LOGGER.warn("Conexão STOMP rejeitada: token Firebase inválido ou expirado");
                throw denied("Autenticação STOMP inválida");
            }
            LOGGER.error("Falha técnica ao validar token Firebase para conexão STOMP (código={})", errorCode);
            throw new MessagingException("Falha ao autenticar conexão STOMP", exception);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Conexão STOMP rejeitada: token Firebase malformado");
            throw denied("Autenticação STOMP inválida");
        }

        Optional<ChatConnectionProjection> account;
        try {
            account = userRepository.findChatConnectionByFirebaseUid(firebaseToken.getUid());
        } catch (DataAccessException exception) {
            LOGGER.error("Falha técnica ao resolver identidade PostgreSQL da conexão STOMP", exception);
            throw new MessagingException("Falha ao autenticar conexão STOMP", exception);
        }
        if (account.isEmpty() || !isEligible(account.get())) {
            LOGGER.warn("Conexão STOMP rejeitada: usuário sem acesso elegível ao Chat");
            throw denied("Usuário sem acesso ao Chat");
        }

        ChatConnectionProjection collaborator = account.get();
        Role role = toSecurityRole(collaborator.getType());
        if (role == null || role == Role.ADMIN) {
            LOGGER.warn("Conexão STOMP rejeitada: perfil não participa do Chat operacional");
            throw denied("Usuário sem acesso ao Chat");
        }

        ChatPrincipal principal = new ChatPrincipal(collaborator.getId(), collaborator.getWorkspaceId(), role, firebaseToken.getUid());
        accessor.setUser(principal);
        LOGGER.info("Conexão STOMP autenticada para colaborador {} no workspace {}", principal.getName(), principal.workspaceId());
    }

    private static boolean isEligible(ChatConnectionProjection collaborator) {
        return collaborator.getId() != null
                && collaborator.getWorkspaceId() != null
                && collaborator.getStatus() == UserStatus.ATIVO
                && Boolean.TRUE.equals(collaborator.getUnitActive());
    }

    private static Role toSecurityRole(UserType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case COLABORADOR -> Role.COLABORADOR;
            case GESTOR -> Role.GESTOR;
            case GESTOR_WORKSPACE -> Role.GESTOR_WORKSPACE;
        };
    }

    private static ChatPrincipal authenticatedPrincipal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof ChatPrincipal principal) {
            return principal;
        }
        throw denied("Sessão STOMP não autenticada");
    }

    private static AccessDeniedException denied(String message) {
        return new AccessDeniedException(message);
    }
}
