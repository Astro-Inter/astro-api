package com.astro.api.form.repository;

import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormResponseCondition;
import com.astro.api.form.model.FormReview;
import com.astro.api.form.model.FormReadFilter;
import com.astro.api.form.model.FormStatus;
import com.astro.api.form.model.FormWriteCondition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FormRepositoryCustom {
    Listing findAll(FormReadFilter readFilter, String search, FormStatus status, int page, int size);
    Optional<Form> findById(String formId, FormReadFilter readFilter);
    Optional<Form> update(Form form, FormWriteCondition writeCondition);
    boolean deleteIfUnanswered(FormWriteCondition writeCondition);
    Optional<Form> updateStatus(FormWriteCondition writeCondition, FormStatus targetStatus, Instant updatedAt);

    Listing findReceived(FormReadFilter readFilter, String search, Integer nrCode,
                         FormStatus status, int page, int size);
    Optional<Form> submitResponse(Form form, FormResponseCondition condition);
    Optional<Form> review(FormWriteCondition condition, FormReview review);

    record Listing(List<Form> forms, long total) {}
}
