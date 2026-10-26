package com.astro.api.chat.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.chat.dto.response.ChatLastMessageResponse;
import com.astro.api.chat.dto.response.ChatMessagePageResponse;
import com.astro.api.chat.dto.response.ChatParticipantResponse;
import com.astro.api.chat.dto.response.ChatResponse;
import com.astro.api.chat.model.ChatMessageDocument;
import com.astro.api.chat.model.ConversationDocument;
import com.astro.api.chat.mapper.ChatMessageMapper;
import com.astro.api.chat.repository.ChatMongoRepository;
import com.astro.api.chat.repository.LastMessageProjection;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.user.mapper.UserAvatarMapper;
import com.astro.api.user.repository.UserChatProjection;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.UserAvatarService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class ChatService {
    private final UserRepository userRepository;
    private final ChatMongoRepository chatMongoRepository;
    private final StringRedisTemplate redisTemplate;
    private final UserAvatarService userAvatarService;
    private final ChatMessageMapper chatMessageMapper;

    public ChatService(UserRepository userRepository, ChatMongoRepository chatMongoRepository,
                       StringRedisTemplate redisTemplate, UserAvatarService userAvatarService,
                       ChatMessageMapper chatMessageMapper) {
        this.userRepository = userRepository;
        this.chatMongoRepository = chatMongoRepository;
        this.redisTemplate = redisTemplate;
        this.userAvatarService = userAvatarService;
        this.chatMessageMapper = chatMessageMapper;
    }

    @Transactional(readOnly = true)
    public ChatMessagePageResponse findMessages(AuthenticatedUser authenticatedUser, String conversationId,
                                               int limit, String before) {
        String uid = authenticatedUser == null ? null : authenticatedUser.firebaseUid();
        if (uid == null || uid.isBlank()) {
            throw new AccessDeniedException("Usuário autenticado necessário para consultar mensagens");
        }
        if (limit < 1 || limit > 100) {
            throw new BusinessException("O limite deve estar entre 1 e 100");
        }
        UUID conversationUuid = parseUuid(conversationId, "conversationId");
        String normalizedConversationId = conversationUuid.toString();

        UserChatProjection currentUser = userRepository.findChatUserByFirebaseUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador não encontrado"));
        if (currentUser.getWorkspaceId() == null) {
            throw new AccessDeniedException("Colaborador autenticado sem Workspace associado");
        }

        ConversationDocument conversation = chatMongoRepository.findConversation(normalizedConversationId);
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
        String uid = authenticatedUser == null ? null : authenticatedUser.firebaseUid();
        if (uid == null || uid.isBlank()) {
            throw new AccessDeniedException("Usuário autenticado necessário para consultar conversas");
        }
        UserChatProjection currentUser = userRepository.findChatUserByFirebaseUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador não encontrado"));
        if (currentUser.getWorkspaceId() == null) {
            throw new AccessDeniedException("Colaborador autenticado sem Workspace associado");
        }

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
}
