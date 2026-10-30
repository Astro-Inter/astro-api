package com.astro.api.chat.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.auth.security.Role;
import com.astro.api.chat.dto.response.ChatLastMessageResponse;
import com.astro.api.chat.dto.response.ChatMessagePageResponse;
import com.astro.api.chat.dto.response.ChatParticipantResponse;
import com.astro.api.chat.dto.response.ChatResponse;
import com.astro.api.chat.dto.response.ChatSendResponse;
import com.astro.api.chat.dto.request.ChatSendRequest;
import com.astro.api.chat.model.ChatMessageDocument;
import com.astro.api.chat.model.ChatMessageIds;
import com.astro.api.chat.model.ChatTypingTarget;
import com.astro.api.chat.model.ConversationDocument;
import com.astro.api.chat.mapper.ChatMessageMapper;
import com.astro.api.chat.mapper.ChatPushEventMapper;
import com.astro.api.chat.push.ChatPushParticipants;
import com.astro.api.chat.push.ChatPushSettings;
import com.astro.api.chat.push.ChatPushOutbox;
import com.astro.api.chat.repository.ChatMongoRepository;
import com.astro.api.chat.repository.LastMessageProjection;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.user.mapper.UserAvatarMapper;
import com.astro.api.user.repository.UserChatProjection;
import com.astro.api.user.repository.ChatSendUserProjection;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.UserAvatarService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.Objects;
import com.mongodb.MongoException;
import com.astro.api.chat.security.ChatPrincipal;
import com.astro.api.user.model.UserStatus;

@Service
public class ChatService {
    private static final DefaultRedisScript<Long> INCREMENT_UNREAD_ONCE = new DefaultRedisScript<>(
            "if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return 0 end; "
                    + "local count = redis.call('INCR', KEYS[1]); "
                    + "redis.call('SADD', KEYS[2], ARGV[1]); return count", Long.class);
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private final UserRepository userRepository;
    private final ChatMongoRepository chatMongoRepository;
    private final StringRedisTemplate redisTemplate;
    private final UserAvatarService userAvatarService;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessagePublisher messagePublisher;
    private final ChatPushSettings pushSettings;
    private final ChatPushOutbox pushOutbox;
    private static final String UNREAD_PROCESSED_KEY_PREFIX = "chat:unread:processed:";

    public ChatService(UserRepository userRepository, ChatMongoRepository chatMongoRepository,
                       StringRedisTemplate redisTemplate, UserAvatarService userAvatarService,
                       ChatMessageMapper chatMessageMapper, ChatMessagePublisher messagePublisher,
                       ChatPushSettings pushSettings, ChatPushOutbox pushOutbox) {
        this.userRepository = userRepository;
        this.chatMongoRepository = chatMongoRepository;
        this.redisTemplate = redisTemplate;
        this.userAvatarService = userAvatarService;
        this.chatMessageMapper = chatMessageMapper;
        this.messagePublisher = messagePublisher;
        this.pushSettings = pushSettings;
        this.pushOutbox = pushOutbox;
    }

    @Transactional(readOnly = true)
    public ChatMessagePageResponse findMessages(AuthenticatedUser authenticatedUser, String conversationId,
                                               int limit, String before) {
        if (limit < 1 || limit > 100) {
            throw new BusinessException("O limite deve estar entre 1 e 100");
        }
        UUID conversationUuid = parseUuid(conversationId, "conversationId");
        String normalizedConversationId = conversationUuid.toString();
        authorizeConversation(authenticatedUser, normalizedConversationId);

        MessageCursor cursor = before == null ? null : decodeCursor(before, normalizedConversationId);
        List<ChatMessageDocument> fetched = chatMongoRepository.findMessagesBefore(
                normalizedConversationId, cursor == null ? null : cursor.date(),
                cursor == null ? null : cursor.messageId(), limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<ChatMessageDocument> page = new ArrayList<>(fetched.subList(0, Math.min(limit, fetched.size())));
        String nextCursor = null;
        if (hasMore && !page.isEmpty()) {
            ChatMessageDocument oldest = page.getLast();
            nextCursor = encodeCursor(normalizedConversationId, oldest.createdAt(), oldest.id());
        }
        Collections.reverse(page);
        return new ChatMessagePageResponse(chatMessageMapper.toResponses(normalizedConversationId, page),
                nextCursor, hasMore);
    }

    private UUID parseUuid(String value, String field) {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return uuid;
        } catch (RuntimeException exception) {
            throw new BusinessException("Identificador inválido: " + field);
        }
    }

    @Transactional(readOnly = true)
    public void markAsRead(AuthenticatedUser authenticatedUser, String conversationId) {
        String normalizedConversationId = parseUuid(conversationId, "conversationId").toString();
        UserChatProjection currentUser = authorizeConversation(authenticatedUser, normalizedConversationId);
        redisTemplate.delete(unreadKey(normalizedConversationId, currentUser.getId()));
    }

    private UserChatProjection authorizeConversation(AuthenticatedUser authenticatedUser, String conversationId) {
        UserChatProjection currentUser = authorizeCurrentUser(authenticatedUser);
        ConversationDocument conversation = chatMongoRepository.findConversation(conversationId);
        if (conversation == null) {
            throw new ResourceNotFoundException("Conversa não encontrada");
        }
        List<Long> participants = conversation.participants();
        if (participants == null || participants.size() != 2 || participants.get(0) == null
                || participants.get(1) == null || participants.get(0).equals(participants.get(1))) {
            throw new ResourceNotFoundException("Conversa não encontrada");
        }
        if (!participants.contains(currentUser.getId())) {
            throw new AccessDeniedException("Usuário não participa desta conversa");
        }
        List<UserChatProjection> participantUsers = userRepository.findChatUsersByIds(participants);
        if (participantUsers.size() != 2 || participantUsers.stream()
                .anyMatch(user -> !currentUser.getWorkspaceId().equals(user.getWorkspaceId()))) {
            throw new AccessDeniedException("Conversa fora do Workspace autorizado");
        }
        return currentUser;
    }

    private String encodeCursor(String conversationId, Instant date, String messageId) {
        parseUuid(messageId, "messageId");
        String payload = "v1|%s|%d|%s".formatted(conversationId, date.toEpochMilli(), messageId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private MessageCursor decodeCursor(String encoded, String conversationId) {
        try {
            if (encoded.length() > 256) throw new IllegalArgumentException();
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(encoded)) {
                throw new IllegalArgumentException();
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 4 || !"v1".equals(parts[0]) || !conversationId.equals(parts[1])) {
                throw new IllegalArgumentException();
            }
            long epochMillis = Long.parseLong(parts[2]);
            parseUuid(parts[3], "cursor");
            return new MessageCursor(Instant.ofEpochMilli(epochMillis), parts[3]);
        } catch (RuntimeException exception) {
            throw new BusinessException("Cursor inválido");
        }
    }

    private record MessageCursor(Instant date, String messageId) { }

    @Transactional(readOnly = true)
    public List<ChatResponse> findChats(AuthenticatedUser authenticatedUser) {
        UserChatProjection currentUser = authorizeCurrentUser(authenticatedUser);

        List<ConversationDocument> conversations = chatMongoRepository.findConversationsFor(currentUser.getId());
        if (conversations.isEmpty()) {
            return List.of();
        }

        Map<String, Long> otherParticipantByConversation = new LinkedHashMap<>();
        Set<Long> participantIds = new LinkedHashSet<>();
        for (ConversationDocument conversation : conversations) {
            if (conversation.id() == null || conversation.participants() == null
                    || conversation.participants().size() != 2
                    || conversation.participants().stream().filter(currentUser.getId()::equals).count() != 1) {
                continue;
            }
            Long otherId = conversation.participants().stream()
                    .filter(id -> !currentUser.getId().equals(id)).findFirst().orElse(null);
            if (otherId != null) {
                otherParticipantByConversation.put(conversation.id(), otherId);
                participantIds.add(otherId);
            }
        }
        if (otherParticipantByConversation.isEmpty()) {
            return List.of();
        }

        Map<Long, UserChatProjection> participants = new HashMap<>();
        userRepository.findChatUsersByIds(participantIds).forEach(user -> {
            if (user.getWorkspaceId() != null && user.getWorkspaceId().equals(currentUser.getWorkspaceId())) {
                participants.put(user.getId(), user);
            }
        });
        otherParticipantByConversation.entrySet().removeIf(entry -> !participants.containsKey(entry.getValue()));
        if (otherParticipantByConversation.isEmpty()) {
            return List.of();
        }

        List<LastMessageProjection> lastMessages = chatMongoRepository.findLatestMessages(
                otherParticipantByConversation.keySet());
        Map<String, LastMessageProjection> messageByConversation = new HashMap<>();
        lastMessages.forEach(message -> {
            if (message.conversationId() != null && message.date() != null) {
                messageByConversation.put(message.conversationId(), message);
            }
        });
        List<LastMessageProjection> includedMessages = otherParticipantByConversation.keySet().stream()
                .map(messageByConversation::get).filter(message -> message != null)
                .sorted(Comparator.comparing(LastMessageProjection::date).reversed()
                        .thenComparing(LastMessageProjection::conversationId, Comparator.reverseOrder()))
                .toList();
        if (includedMessages.isEmpty()) {
            return List.of();
        }

        Set<Long> includedParticipantIds = includedMessages.stream()
                .map(message -> otherParticipantByConversation.get(message.conversationId()))
                .filter(id -> id != null && participants.containsKey(id))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> profilePhotoUrls = userAvatarService.findUrls(includedParticipantIds);

        List<String> keys = includedMessages.stream()
                .map(message -> unreadKey(message.conversationId(), currentUser.getId())).toList();
        List<String> counts = redisTemplate.opsForValue().multiGet(keys);
        Map<String, Integer> unreadByConversation = new HashMap<>();
        for (int index = 0; index < includedMessages.size(); index++) {
            String count = counts == null || index >= counts.size() ? null : counts.get(index);
            unreadByConversation.put(includedMessages.get(index).conversationId(), parseCount(count));
        }

        List<ChatResponse> response = new ArrayList<>(includedMessages.size());
        for (LastMessageProjection message : includedMessages) {
            UserChatProjection participant = participants.get(
                    otherParticipantByConversation.get(message.conversationId()));
            if (participant == null) continue;
            response.add(new ChatResponse(message.conversationId(),
                    new ChatParticipantResponse(participant.getId(), participant.getName(), participant.getRole(),
                            profilePhotoUrls.get(participant.getId()), UserAvatarMapper.initials(participant.getName())),
                    new ChatLastMessageResponse(message.senderId(), message.content()),
                    unreadByConversation.getOrDefault(message.conversationId(), 0)));
        }
        return List.copyOf(response);
    }

    private String unreadKey(String conversationId, Long userId) {
        return "chat:unread:%s:%d".formatted(conversationId, userId);
    }

    private int parseCount(String count) {
        if (count == null) return 0;
        try {
            int value = Integer.parseInt(count);
            if (value < 0) throw new IllegalStateException("Contador Redis de mensagens não lidas inválido");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Contador Redis de mensagens não lidas inválido", exception);
        }
    }

    public ChatSendResponse send(ChatPrincipal principal, ChatSendRequest request) {
        validatePayload(request);
        Long senderId = principal.collaboratorId();
        String conversationId = normalizeOptionalUuid(request.getConversationId(), "conversationId");
        String replyId = normalizeOptionalUuid(request.getReplyToMessageId(), "replyToMessageId");
        String clientMessageId = normalizeOptionalUuid(request.getClientMessageId(), "clientMessageId");
        Long recipientId = resolveRecipient(principal, request.getRecipientId(), conversationId);
        ChatPushParticipants pushParticipants = validateParticipants(principal, recipientId);
        String selectedConversationId = conversationId;
        ChatMessageDocument message;
        try {
            message = persistSend(senderId, recipientId, selectedConversationId,
                    request.getContent(), replyId, clientMessageId, pushParticipants);
        } catch (MongoException exception) {
            if (exception.getCode() != 11000 && exception.getCode() != 112) throw exception;
            message = recoverOrRetry(senderId, recipientId, selectedConversationId,
                    request.getContent(), replyId, clientMessageId, pushParticipants);
        }
        String unreadKey = unreadKey(message.conversationId(), recipientId);
        String processedKey = UNREAD_PROCESSED_KEY_PREFIX + message.conversationId();
        Long incremented;
        try {
            incremented = redisTemplate.execute(INCREMENT_UNREAD_ONCE,
                    List.of(unreadKey, processedKey), message.id());
        } catch (DataAccessException exception) {
            throw new ChatUnreadCounterException(exception);
        }
        if (incremented == null) throw new ChatUnreadCounterException(null);
        ChatSendResponse response = new ChatSendResponse(clientMessageId, message.id(), message.conversationId(),
                message.senderId(), message.content(), message.replyToMessageId(), message.createdAt());
        messagePublisher.publish(recipientId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public ChatTypingTarget authorizeTyping(ChatPrincipal principal, Long recipientId, String conversationId) {
        String normalizedId = normalizeOptionalUuid(conversationId, "conversationId");
        Long recipient = resolveRecipient(principal, recipientId, normalizedId);
        validateParticipants(principal, recipient);
        if (normalizedId == null) {
            ConversationDocument existing = chatMongoRepository.findConversationByPair(principal.collaboratorId(), recipient);
            if (existing != null) normalizedId = existing.id();
            else normalizedId = ChatMessageIds.forConversation(principal.collaboratorId(), recipient);
        }
        return new ChatTypingTarget(normalizedId, recipient);
    }

    private Long resolveRecipient(ChatPrincipal principal, Long recipientId, String conversationId) {
        Long senderId = principal.collaboratorId();
        if (conversationId != null) {
            ConversationDocument conversation = chatMongoRepository.findConversation(conversationId);
            if (conversation == null) throw new ResourceNotFoundException("Conversa não encontrada");
            if (conversation.participants() == null || conversation.participants().size() != 2
                    || conversation.participants().stream().anyMatch(Objects::isNull)
                    || conversation.participants().getFirst().equals(conversation.participants().getLast())
                    || !conversation.participants().contains(senderId)) {
                throw new AccessDeniedException("Usuário não participa desta conversa");
            }
            recipientId = conversation.participants().stream().filter(id -> !senderId.equals(id)).findFirst()
                    .orElseThrow(() -> new AccessDeniedException("Conversa inválida"));
        }
        return recipientId;
    }

    private UserChatProjection authorizeCurrentUser(AuthenticatedUser authenticatedUser) {
        String uid = authenticatedUser == null ? null : authenticatedUser.firebaseUid();
        if (uid == null || uid.isBlank()) {
            throw new AccessDeniedException("Usuário autenticado necessário para acessar o Chat");
        }
        if (authenticatedUser.role() == null || authenticatedUser.role() == Role.ADMIN) {
            throw new AccessDeniedException("Usuários ADMIN não participam do Chat operacional");
        }
        UserChatProjection currentUser = userRepository.findChatUserByFirebaseUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador não encontrado"));
        if (currentUser.getStatus() != UserStatus.ATIVO || !Boolean.TRUE.equals(currentUser.getUnitActive())) {
            throw new AccessDeniedException("Usuário sem acesso ativo ao Chat");
        }
        if (currentUser.getWorkspaceId() == null) {
            throw new AccessDeniedException("Colaborador autenticado sem Workspace associado");
        }
        if (currentUser.getRole() == null || !authenticatedUser.role().name().equals(currentUser.getRole().name())) {
            throw new AccessDeniedException("Perfil autenticado não corresponde ao cadastro atual");
        }
        return currentUser;
    }

    private ChatMessageDocument persistSend(Long senderId, Long recipientId, String requestedConversationId,
                                           String content, String replyId, String clientMessageId, ChatPushParticipants pushParticipants) {
        return chatMongoRepository.inTransaction(session -> {
            ChatMessageDocument duplicate = chatMongoRepository.findMessage(session, senderId, clientMessageId);
            ConversationDocument conversation = requestedConversationId == null
                    ? chatMongoRepository.findConversationByPair(session, senderId, recipientId)
                    : chatMongoRepository.findConversationById(session, requestedConversationId);
            if (conversation == null) {
                if (requestedConversationId != null) throw new ResourceNotFoundException("Conversa não encontrada");
                conversation = new ConversationDocument(ChatMessageIds.forConversation(senderId, recipientId),
                        List.of(senderId, recipientId));
                chatMongoRepository.insertConversation(session, conversation);
            }
            if (conversation.participants() == null || conversation.participants().size() != 2
                    || !conversation.participants().contains(senderId)
                    || !conversation.participants().contains(recipientId)) {
                throw new AccessDeniedException("Usuário não participa desta conversa");
            }
            if (duplicate != null) {
                if (!sameOperation(duplicate, conversation.id(), content, replyId)) {
                    throw new BusinessException("clientMessageId já utilizado para outro envio");
                }
                return duplicate;
            }
            if (replyId != null && chatMongoRepository.findMessage(session, conversation.id(), replyId) == null) {
                throw new ResourceNotFoundException("Mensagem respondida não encontrada nesta conversa");
            }
            ChatMessageDocument message = new ChatMessageDocument(ChatMessageIds.forMessage(senderId, clientMessageId), conversation.id(),
                    senderId, content, replyId, Instant.now());
            chatMongoRepository.insertMessage(session, message);
            if (pushSettings.enabled() && pushParticipants.recipient().getFirebaseUid() != null
                    && !pushParticipants.recipient().getFirebaseUid().isBlank()) {
                pushOutbox.insert(session, ChatPushEventMapper.from(message, pushParticipants));
            }
            return message;
        });
    }

    private ChatMessageDocument recoverOrRetry(Long senderId, Long recipientId,
                                               String requestedConversationId, String content, String replyId,
                                               String clientMessageId, ChatPushParticipants pushParticipants) {
        ChatMessageDocument existing = chatMongoRepository.findMessageByIdempotency(senderId, clientMessageId);
        if (existing != null) {
            ConversationDocument existingConversation = chatMongoRepository.findConversationByPair(
                    senderId, recipientId);
            if (existingConversation == null || !existing.conversationId().equals(existingConversation.id())
                    || !sameOperation(existing, requestedConversationId == null
                    ? existing.conversationId() : requestedConversationId, content, replyId)) {
                throw new BusinessException("clientMessageId já utilizado para outro envio");
            }
            return existing;
        }
        try {
            return persistSend(senderId, recipientId, requestedConversationId,
                    content, replyId, clientMessageId, pushParticipants);
        } catch (MongoException retryException) {
            ChatMessageDocument raced = chatMongoRepository.findMessageByIdempotency(senderId, clientMessageId);
            ConversationDocument racedConversation = raced == null ? null
                    : chatMongoRepository.findConversationByPair(senderId, recipientId);
            if (raced != null && racedConversation != null && raced.conversationId().equals(racedConversation.id())
                    && sameOperation(raced, requestedConversationId == null
                    ? raced.conversationId() : requestedConversationId, content, replyId)) return raced;
            throw retryException;
        }
    }

    private static boolean sameOperation(ChatMessageDocument message, String conversationId,
                                         String content, String replyId) {
        return Objects.equals(message.conversationId(), conversationId)
                && Objects.equals(message.content(), content)
                && Objects.equals(message.replyToMessageId(), replyId);
    }

    private ChatPushParticipants validateParticipants(ChatPrincipal principal, Long recipientId) {
        if (recipientId == null || recipientId <= 0) throw new BusinessException("recipientId inválido");
        if (principal == null || principal.collaboratorId() == null || principal.collaboratorId().equals(recipientId)) {
            throw new BusinessException("Não é permitido enviar mensagem para si mesmo");
        }
        List<ChatSendUserProjection> users = userRepository.findChatSendUsers(principal.firebaseUid(),
                List.of(principal.collaboratorId(), recipientId));
        ChatSendUserProjection sender = users.stream().filter(user -> principal.collaboratorId().equals(user.getId()))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Remetente não encontrado"));
        ChatSendUserProjection recipient = users.stream().filter(user -> recipientId.equals(user.getId()))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Destinatário não encontrado"));
        if (!Boolean.TRUE.equals(sender.getAuthenticatedSender())
                || sender.getStatus() != UserStatus.ATIVO || !Boolean.TRUE.equals(sender.getUnitActive())
                || sender.getWorkspaceId() == null || sender.getType() == null
                || principal.role() == null || !principal.role().name().equals(sender.getType().name())) {
            throw new AccessDeniedException("Remetente sem acesso atual ao Chat");
        }
        if (recipient.getStatus() != UserStatus.ATIVO || !Boolean.TRUE.equals(recipient.getUnitActive())
                || recipient.getWorkspaceId() == null) {
            throw new AccessDeniedException("Destinatário sem acesso atual ao Chat");
        }
        if (!sender.getWorkspaceId().equals(recipient.getWorkspaceId())) {
            throw new AccessDeniedException("Usuários pertencem a Workspaces diferentes");
        }
        if (!ChatCommunicationPolicy.canCommunicate(sender.getType(), recipient.getType())) {
            throw new AccessDeniedException("Comunicação entre estes perfis não é permitida");
        }
        return new ChatPushParticipants(sender, recipient);
    }

    private static void validatePayload(ChatSendRequest request) {
        if (request == null) throw new BusinessException("Payload inválido");
        boolean hasRecipient = request.getRecipientId() != null;
        boolean hasConversation = request.getConversationId() != null;
        if (hasRecipient == hasConversation) throw new BusinessException("Informe recipientId ou conversationId");
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new BusinessException("content não pode ser vazio");
        }
        if (request.getContent().length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("content excede o limite de 4000 caracteres");
        }
        if (request.getClientMessageId() == null) throw new BusinessException("clientMessageId é obrigatório");
        normalizeOptionalUuid(request.getClientMessageId(), "clientMessageId");
    }

    private static String normalizeOptionalUuid(String value, String field) {
        if (value == null) return null;
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return uuid.toString();
        } catch (RuntimeException exception) {
            throw new BusinessException("Identificador inválido: " + field);
        }
    }

}
