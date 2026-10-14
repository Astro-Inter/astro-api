package com.astro.api.form.service;

import com.astro.api.form.model.Form;
import com.astro.api.form.repository.FormRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FormService {
    private final FormRepository formRepository;

    public FormService(FormRepository formRepository) {
        this.formRepository = formRepository;
    }

    public List<Form> findTop3PendingForManager(Long managerId) {
        return formRepository.findTop3PendingByManagerId(managerId);
    }
}
