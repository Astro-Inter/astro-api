package com.astro.api.form.dto.response;

import java.util.List;

public record FormRecipientsResponse(Long currentManagerId, List<RecipientResponse> items) {
    public record RecipientResponse(Long id, String name, String unitName) {}
}
