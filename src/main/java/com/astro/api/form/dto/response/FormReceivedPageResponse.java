package com.astro.api.form.dto.response;

import com.astro.api.form.model.FormStatus;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

public record FormReceivedPageResponse(List<Item> items, int page, int size, long totalElements, long totalPages) {
    public record Item(String id, String title, String description, Integer nrCode, Long unitId, Long senderManagerId,
            LocalDate dueDate, Instant createdAt, Instant respondedAt, int questionCount, FormStatus status, String reviewStatus, boolean viewed) {}
}
