package com.astro.api.form.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record FormResponseDetailsResponse(FormDetailsResponse form, String senderName, String recipientName,
        String unitName, LocationPolicyResponse locationPolicy, List<LocationReadingResponse> locations,
        ReviewResponse review, boolean canReview, LocationCheckResponse locationCheck) {
    public record LocationPolicyResponse(boolean captureRequired, boolean enforceUnitRadius,
                                         Integer radiusMeters, Integer maxReadingAgeSeconds) {}
    public record LocationCheckResponse(Long unitId, double unitLatitude, double unitLongitude, int radiusMeters,
                                        double distanceMeters, LocationReadingResponse reading) {}
    public record LocationReadingResponse(Double latitude, Double longitude, Double accuracyMeters, Instant recordedAt) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReviewResponse(String status, Long reviewerManagerId, String reviewerName, Instant reviewedAt, String description) {}
}
