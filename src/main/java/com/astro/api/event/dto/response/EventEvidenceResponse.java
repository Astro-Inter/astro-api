package com.astro.api.event.dto.response;

public record EventEvidenceResponse(
        Long evidenceId,
        String originalName,
        String url,
        String mimeType,
        Long sizeBytes
) {
}
