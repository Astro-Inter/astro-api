package com.astro.api.home.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.viewed.ViewedService;
import com.astro.api.form.model.Form;
import com.astro.api.form.service.FormService;
import com.astro.api.home.dto.response.HomeResponse;
import com.astro.api.home.dto.response.ManagerHomeDatabaseResponse;
import com.astro.api.home.dto.response.ManagerHomeResponse;
import com.astro.api.home.dto.response.PendingFormResponse;
import com.astro.api.home.dto.response.PendingValidationResponse;
import com.astro.api.home.repository.HomeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
public class HomeService {
    private static final String VALIDATIONS_RESOURCE = "validations";
    private static final String FORMS_RESOURCE = "forms";

    private final HomeRepository homeRepository;
    private final JsonMapper objectMapper;
    private final FormService formService;
    private final ViewedService viewedService;

    public HomeService(HomeRepository homeRepository, JsonMapper objectMapper,
                       FormService formService, ViewedService viewedService) {
        this.homeRepository = homeRepository;
        this.objectMapper = objectMapper;
        this.formService = formService;
        this.viewedService = viewedService;
    }

    @Transactional(readOnly = true)
    public HomeResponse loadHome(AuthenticatedUser user) {
        if (user == null || user.firebaseUid() == null || user.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Usuário autenticado necessário para consultar a Home");
        }
        String homeJson = homeRepository.loadHome(user.firebaseUid());
        if (homeJson == null) {
            throw new AccessDeniedException("Gestor ativo sem acesso à Home do workspace");
        }
        try {
            HomeResponse response = objectMapper.readValue(homeJson, HomeResponse.class);
            if (response == null || response.userName() == null || response.statistics() == null) {
                throw new IllegalStateException("Dados da Home retornados pelo banco são inválidos");
            }
            return response;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível interpretar os dados da Home", exception);
        }
    }

    @Transactional(readOnly = true)
    public ManagerHomeResponse loadManagerHome(AuthenticatedUser user) {
        String firebaseUid = authenticatedUid(user);
        ManagerHomeDatabaseResponse databaseHome = readManagerDatabaseHome(homeRepository.loadManagerHome(firebaseUid));
        Long managerId = homeRepository.findByFirebaseUid(firebaseUid)
                .map(id -> id.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Gestor não encontrado"));

        List<Form> forms = formService.findTop3PendingForManager(managerId);
        List<ManagerHomeDatabaseResponse.ManagerPendingValidationDatabaseResponse> validations =
                databaseHome.pendingValidations() == null ? List.of() : databaseHome.pendingValidations();
        Set<String> viewedValidations = viewedService.findViewed(managerId, VALIDATIONS_RESOURCE,
                validations.stream().map(ManagerHomeDatabaseResponse.ManagerPendingValidationDatabaseResponse::id).toList());
        Set<String> viewedForms = viewedService.findViewed(managerId, FORMS_RESOURCE,
                forms.stream().map(Form::getId).toList());

        return new ManagerHomeResponse(
                databaseHome.username(), databaseHome.workspaceName(),
                validations.stream().map(validation -> new PendingValidationResponse(
                        validation.id(), validation.collaboratorName(), validation.eventTitle(),
                        viewedValidations.contains(String.valueOf(validation.id())))).toList(),
                forms.stream().map(form -> new PendingFormResponse(
                        form.getId(), form.getTitle(), daysLeft(form.getDueDate()),
                        viewedForms.contains(String.valueOf(form.getId())))).toList(),
                databaseHome.indicators());
    }

    static Long daysLeft(Instant dueDate, LocalDate currentDate, ZoneId zoneId) {
        return dueDate == null ? null : ChronoUnit.DAYS.between(currentDate, dueDate.atZone(zoneId).toLocalDate());
    }

    private Long daysLeft(Instant dueDate) {
        ZoneId zoneId = ZoneId.systemDefault();
        return daysLeft(dueDate, LocalDate.now(zoneId), zoneId);
    }

    private String authenticatedUid(AuthenticatedUser user) {
        if (user == null || user.firebaseUid() == null || user.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Usuário autenticado necessário para consultar a Home");
        }
        return user.firebaseUid();
    }

    private ManagerHomeDatabaseResponse readManagerDatabaseHome(String json) {
        if (json == null) {
            throw new ResourceNotFoundException("Gestor inexistente, inativo ou sem permissão para acessar a Home");
        }
        try {
            ManagerHomeDatabaseResponse response = objectMapper.readValue(json, ManagerHomeDatabaseResponse.class);
            if (response == null || response.username() == null || response.workspaceName() == null
                    || response.indicators() == null) {
                throw new IllegalStateException("Dados da Home do gestor retornados pelo banco são inválidos");
            }
            return response;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível interpretar os dados da Home do gestor", exception);
        }
    }
}
