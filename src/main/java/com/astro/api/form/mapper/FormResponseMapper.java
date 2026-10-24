package com.astro.api.form.mapper;

import com.astro.api.form.dto.response.*;
import com.astro.api.common.storage.R2StorageService.PresignedDownload;
import com.astro.api.form.model.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FormResponseMapper {
    private final FormMapper formMapper;
    public FormResponseMapper(FormMapper formMapper) { this.formMapper = formMapper; }

    public FormReceivedPageResponse toPage(List<Form> forms, int page, int size, long total) {
        return new FormReceivedPageResponse(forms.stream().map(form -> new FormReceivedPageResponse.Item(
                form.getId(), form.getTitle(), form.getDescription(), form.getNrCode(), form.getUnitId(),
                form.getSenderManagerId(), form.getDueDate() == null ? null : form.getDueDate().atZone(FormMapper.FORM_ZONE).toLocalDate(),
                form.getCreatedAt(), form.getRespondedAt(), form.getQuestions() == null ? 0 : form.getQuestions().size(),
                form.getStatus(), reviewStatus(form))).toList(), page, size, total, (total + size - 1) / size);
    }

    public FormResponseDetailsResponse toDetails(Form form, String senderName, String recipientName,
                                                 String unitName, String reviewerName, boolean canReview, FormLocationPolicy locationPolicy) {
        FormReview review = form.getReview();
        return new FormResponseDetailsResponse(formMapper.toDetails(form), senderName, recipientName, unitName,
                new FormResponseDetailsResponse.LocationPolicyResponse(locationPolicy.captureRequired(),
                        locationPolicy.enforceUnitRadius(), locationPolicy.radiusMeters(), locationPolicy.maxReadingAgeSeconds()),
                form.getLocations() == null ? List.of() : form.getLocations().stream().map(location ->
                        new FormResponseDetailsResponse.LocationReadingResponse(location.latitude(), location.longitude(),
                                location.accuracyMeters(), location.recordedAt())).toList(),
                new FormResponseDetailsResponse.ReviewResponse(reviewStatus(form),
                        review == null ? null : review.reviewerManagerId(), reviewerName,
                        review == null ? null : review.reviewedAt(), review == null ? null : review.description()), canReview, toLocationCheck(form.getLocationCheck()));
    }

    private FormResponseDetailsResponse.LocationCheckResponse toLocationCheck(FormLocationCheck locationCheck) {
        if (locationCheck == null) return null;
        FormLocationReading reading = locationCheck.reading();
        FormResponseDetailsResponse.LocationReadingResponse readingResponse = new FormResponseDetailsResponse.LocationReadingResponse(
                reading.latitude(), reading.longitude(), reading.accuracyMeters(), reading.recordedAt());
        return new FormResponseDetailsResponse.LocationCheckResponse(locationCheck.unitId(), locationCheck.unitLatitude(),
                locationCheck.unitLongitude(), locationCheck.radiusMeters(), locationCheck.distanceMeters(), readingResponse);
    }

    public FormImageDownloadResponse toImageDownload(FormImage image, PresignedDownload imageDownload) {
        return new FormImageDownloadResponse(image.id(), imageDownload.url(), imageDownload.expiresAt());
    }

    public String reviewStatus(Form form) {
        if (form.getReview() != null) return form.getReview().status().value();
        return form.getStatus() == FormStatus.COMPLETED ? "awaiting_review" : "awaiting_response";
    }
}
