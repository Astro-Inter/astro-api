package com.astro.api.form.dto.response;

import java.util.List;

public record FormPageResponse(List<FormSummaryResponse> items, int page, int size,
                               long totalElements, long totalPages) {}
