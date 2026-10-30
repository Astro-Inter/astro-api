package com.astro.api.chat.push;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatPushDispatcherTest {
    private final ChatPushOutbox outbox = mock(ChatPushOutbox.class);
    private final ChatPushEligibility eligibility = mock(ChatPushEligibility.class);
    private final HttpClient client = mock(HttpClient.class);
    private final ChatPushSettings settings = new ChatPushSettings(true,
            "https://push.example/internal/chat-messages", "test-secret-".repeat(4));

    private Document queued() {
        return new Document("_id", "message").append("payload", new Document("recipientId", "2")).append("attempts", 2);
    }

    @Test void disabledAndUnsafeDestinationsNeverClaimEvents() {
        new ChatPushDispatcher(new ChatPushSettings(false, "", ""), outbox, eligibility, client).dispatch();
        new ChatPushDispatcher(new ChatPushSettings(true, "http://push.example", settings.secret()), outbox, eligibility, client).dispatch();
        verifyNoInteractions(outbox, client);
        assertFalse(settings.toString().contains(settings.secret()));
    }

    @Test void durableAcknowledgmentCompletesAndAuthenticatesRequest() throws Exception {
        when(outbox.claim(any(), any())).thenReturn(queued(), null);
        when(eligibility.allows(any())).thenReturn(true);
        HttpResponse<Void> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(202);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        new ChatPushDispatcher(settings, outbox, eligibility, client).dispatch();
        verify(outbox).complete(eq("message"), anyString());
        verify(outbox, never()).retry(any(), any(), any());
        org.mockito.ArgumentCaptor<HttpRequest> request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("Bearer " + settings.secret(), request.getValue().headers().firstValue("Authorization").orElseThrow());
        assertEquals("POST", request.getValue().method());
    }

    @Test void failurePreservesEventAndUsesBoundedProgressiveWait() throws Exception {
        when(outbox.claim(any(), any())).thenReturn(queued(), null);
        when(eligibility.allows(any())).thenReturn(true);
        when(client.send(any(), any(HttpResponse.BodyHandler.class))).thenThrow(new java.io.IOException());
        Instant before = Instant.now();
        new ChatPushDispatcher(settings, outbox, eligibility, client).dispatch();
        verify(outbox).retry(eq("message"), anyString(), argThat(time -> !time.isBefore(before.plusSeconds(2))));
        verify(outbox, never()).complete(any(), any());
        assertEquals(1, ChatPushDispatcher.delay(1));
        assertEquals(2, ChatPushDispatcher.delay(2));
        assertEquals(300, ChatPushDispatcher.delay(100));
    }

    @Test void revokedAccessDiscardsPreviewWithoutCallingExternalService() {
        when(outbox.claim(any(), any())).thenReturn(queued(), null);
        when(eligibility.allows(any())).thenReturn(false);
        new ChatPushDispatcher(settings, outbox, eligibility, client).dispatch();
        verify(outbox).complete(eq("message"), anyString());
        verifyNoInteractions(client);
    }
}
