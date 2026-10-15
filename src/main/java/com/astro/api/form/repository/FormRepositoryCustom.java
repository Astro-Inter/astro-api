package com.astro.api.form.repository;

import com.astro.api.form.model.Form;
import com.astro.api.form.model.FormScope;
import com.astro.api.form.model.FormStatus;

import java.util.List;
import java.util.Optional;

public interface FormRepositoryCustom {
    Listing findAll(FormScope scope, String search, FormStatus status, int page, int size);
    Optional<Form> findById(String id, FormScope scope);
    Optional<Form> update(Form form, FormScope scope, Long expectedVersion);
    boolean delete(Form form, FormScope scope);
    Optional<Form> publish(Form form, FormScope scope);

    record Listing(List<Form> forms, long total) {}
}
