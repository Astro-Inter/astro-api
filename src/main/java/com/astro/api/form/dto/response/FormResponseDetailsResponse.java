package com.astro.api.form.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record FormResponseDetailsResponse(FormDetailsResponse form, String senderName, String recipientName,
        String unitName, LocationPolicyResponse locationPolicy, List<LocationReadingResponse> locations,
        ReviewResponse review, boolean canReview) {
    public record LocationPolicyResponse(boolean captureRequired, boolean enforceUnitRadius) {}
    public record LocationReadingResponse(Double latitude, Double longitude, Double accuracyMeters, Instant recordedAt) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReviewResponse(String status, Long reviewerManagerId, String reviewerName, Instant reviewedAt, String description) {}
}
