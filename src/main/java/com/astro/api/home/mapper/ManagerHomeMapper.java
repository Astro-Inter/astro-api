package com.astro.api.home.mapper;

import com.astro.api.home.dto.response.ManagerHomeDatabaseResponse;
import com.astro.api.home.dto.response.PendingValidationResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class ManagerHomeMapper {
    public List<PendingValidationResponse> toPendingValidationResponses(
            List<ManagerHomeDatabaseResponse.ManagerPendingValidationDatabaseResponse> validations,
            Set<String> viewedValidationIds) {
        return validations.stream()
                .map(validation -> new PendingValidationResponse(
                        validation.id(), validation.participantId(), validation.eventId(),
                        validation.collaboratorName(), validation.eventTitle(),
                        viewedValidationIds.contains(String.valueOf(validation.id()))))
                .toList();
    }
}
