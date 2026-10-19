package com.astro.api.user.service;

import com.astro.api.common.exception.SpreadsheetImportException;
import com.astro.api.common.model.SpreadsheetImportError;
import com.astro.api.common.text.NormalizedText;
import com.astro.api.role.model.Role;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.mapper.CollaboratorMapper;
import com.astro.api.user.model.CollaboratorImportData;
import com.astro.api.user.model.CollaboratorImportPlan.Change;
import com.astro.api.user.model.CollaboratorImportPlan;
import com.astro.api.user.model.CollaboratorImportRow;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserType;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.repository.CollaboratorRepository;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollaboratorImportService {
    private final UserRepository userRepository;
    private final Validator validator;
    private final CollaboratorMapper collaboratorMapper;
    private final CollaboratorRepository collaboratorRepository;
    private final UserBatchService userBatchService;

    public CollaboratorImportService(UserRepository userRepository, Validator validator,
            CollaboratorMapper collaboratorMapper, CollaboratorRepository collaboratorRepository,
            UserBatchService userBatchService) {
        this.userRepository = userRepository;
        this.validator = validator;
        this.collaboratorMapper = collaboratorMapper;
        this.collaboratorRepository = collaboratorRepository;
        this.userBatchService = userBatchService;
    }

    public CollaboratorImportPlan plan(Workspace workspace, List<CollaboratorImportRow> rows, Map<String, Unit> unitsByName,
                     Map<String, Role> rolesByName) {
        Set<String> cpfs = new HashSet<>();
        Set<String> emails = new HashSet<>();
        for (CollaboratorImportRow row : rows) {
            cpfs.add(normalizeCpf(row.cpf()));
            emails.add(normalizeEmail(row.email()));
        }
        List<User> identities = userRepository.findImportIdentitiesForUpdate(cpfs, emails);
        Map<String, User> usersByCpf = new HashMap<>();
        Map<String, User> usersByEmail = new HashMap<>();
        for (User user : identities) {
            if (user.getCpf() != null) usersByCpf.put(user.getCpf(), user);
            usersByEmail.put(normalizeEmail(user.getEmail()), user);
        }
        List<Change> changes = new ArrayList<>();
        List<SpreadsheetImportError> errors = new ArrayList<>();
        List<SpreadsheetImportError> ignored = new ArrayList<>();
        Map<String, List<String>> rowsByCpf = new HashMap<>();
        Set<String> seenEmails = new HashSet<>();
        int unchanged = 0;
        for (CollaboratorImportRow row : rows) {
            String cpf = normalizeCpf(row.cpf());
            String email = normalizeEmail(row.email());
            User existing = usersByCpf.get(cpf);
            User emailOwner = usersByEmail.get(email);
            User matchedUser = existing != null ? existing : emailOwner;
            if (matchedUser != null && matchedUser.getType() != UserType.COLABORADOR && inWorkspace(matchedUser, workspace)) {
                ignored.add(new SpreadsheetImportError(row.number(), "tipo", "Gestor mantido sem alterações"));
                continue;
            }
            WorkModel workModel = parseWorkModel(row.workModel());
            CollaboratorImportData data = collaboratorMapper.toImportData(row, workModel);
            if (row.role() == null || row.role().trim().length() < 2 || row.role().trim().length() > 255) {
                errors.add(new SpreadsheetImportError(row.number(), "cargo", "O cargo deve ter entre 2 e 255 caracteres"));
                continue;
            }
            Set<ConstraintViolation<CollaboratorImportData>> violations = validator.validate(data);
            if (!violations.isEmpty()) {
                violations.stream().sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                        .forEach(violation -> errors.add(new SpreadsheetImportError(row.number(),
                                spreadsheetField(violation.getPropertyPath().toString()), violation.getMessage())));
                continue;
            }
            List<String> rowValues = List.of(data.name(), cpf, email, NormalizedText.key(row.unit()),
                    NormalizedText.key(row.role()), workModel.name());
            List<String> previousRowValues = rowsByCpf.putIfAbsent(cpf, rowValues);
            if (previousRowValues != null) {
                if (previousRowValues.equals(rowValues)) {
                    ignored.add(new SpreadsheetImportError(row.number(), "cpf", "Linha idêntica repetida"));
                } else {
                    errors.add(new SpreadsheetImportError(row.number(), "cpf", "CPF repetido com informações diferentes"));
                }
                continue;
            }
            if (!seenEmails.add(email)) {
                errors.add(new SpreadsheetImportError(row.number(), "email", "E-mail repetido para pessoas diferentes"));
                continue;
            }
            if (existing != null && !inWorkspace(existing, workspace)) {
                errors.add(new SpreadsheetImportError(row.number(), "cpf", "CPF indisponível para este workspace"));
                continue;
            }
            if (emailOwner != null && (existing == null || !Objects.equals(emailOwner.getId(), existing.getId()))) {
                errors.add(new SpreadsheetImportError(row.number(), "email", "E-mail vinculado a outra conta"));
                continue;
            }
            Unit unit = unitsByName.get(NormalizedText.key(row.unit()));
            Role role = rolesByName.get(NormalizedText.key(row.role()));
            if (unit == null) {
                errors.add(new SpreadsheetImportError(row.number(), "unidade",
                        "Informe o endereço completo da nova unidade: " + row.unit()));
                continue;
            }
            if (!unit.isActive && hasNewUnit(existing, unit)) {
                errors.add(new SpreadsheetImportError(row.number(), "unidade", "Unidade inativa para nova vinculação"));
                continue;
            }
            if (role == null || (!role.isActive() && hasNewRole(existing, role))) {
                errors.add(new SpreadsheetImportError(row.number(), "cargo", "Cargo inválido ou inativo para nova vinculação"));
                continue;
            }
            boolean hasChanges = existing == null || !Objects.equals(existing.getName(), data.name())
                    || !normalizeEmail(existing.getEmail()).equals(email) || existing.getWorkModel() != workModel
                    || hasNewUnit(existing, unit) || hasNewRole(existing, role);
            if (hasChanges) changes.add(new Change(row.number(), data, existing, unit, role));
            else unchanged++;
        }
        if (!errors.isEmpty()) throw new SpreadsheetImportException(errors);
        return new CollaboratorImportPlan(List.copyOf(changes), unchanged, List.copyOf(ignored));
    }

    public List<User> persist(CollaboratorImportPlan plan) {
        List<User> changedUsers = new ArrayList<>();
        List<User> newCollaborators = new ArrayList<>();
        List<User> updatedCollaborators = new ArrayList<>();
        for (Change change : plan.changes()) {
            User user = change.existingCollaborator();
            if (user == null) {
                user = collaboratorMapper.toNewUser(change.data(), change.role(), change.unit());
                newCollaborators.add(user);
            } else {
                collaboratorMapper.applyImportUpdate(user, change.data(), change.role(), change.unit());
                updatedCollaborators.add(user);
            }
            changedUsers.add(user);
        }
        if (!updatedCollaborators.isEmpty()) {
            userRepository.saveAllAndFlush(updatedCollaborators);
        }
        if (!newCollaborators.isEmpty()) {
            userBatchService.saveAllPreRegistered(newCollaborators);
        }
        return changedUsers;
    }

    private boolean inWorkspace(User user, Workspace workspace) {
        return user.getUnit() != null && user.getUnit().workspace != null
                && Objects.equals(workspace.id, user.getUnit().workspace.id);
    }

    private String normalizeCpf(String value) {
        return value == null ? "" : value.trim().replace(".", "").replace("-", "");
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private WorkModel parseWorkModel(String value) {
        try { return WorkModel.valueOf(NormalizedText.key(value).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private String spreadsheetField(String field) {
        return switch (field) {
            case "name" -> "nome";
            case "workModel" -> "modalidade";
            default -> field;
        };
    }

    public void validateNewLinks(CollaboratorImportPlan plan, Workspace workspace) {
        List<SpreadsheetImportError> errors = new ArrayList<>();
        for (Change change : plan.changes()) {
            User existing = change.existingCollaborator();
            if (hasNewUnit(existing, change.unit())
                    && !Boolean.TRUE.equals(collaboratorRepository.lockUnitActive(change.unit().id, workspace.id))) {
                errors.add(new SpreadsheetImportError(change.row(), "unidade", "Unidade inativa para nova vinculação"));
            }
            if (hasNewRole(existing, change.role())
                    && !Boolean.TRUE.equals(collaboratorRepository.lockRoleActive(change.role().getId(), workspace.id))) {
                errors.add(new SpreadsheetImportError(change.row(), "cargo", "Cargo inativo para nova vinculação"));
            }
        }
        if (!errors.isEmpty()) throw new SpreadsheetImportException(errors);
    }

    public CollaboratorImportPlan bindPersistedUnits(CollaboratorImportPlan plan, Map<Unit, Unit> persistedUnits) {
        List<Change> changes = new ArrayList<>();
        for (Change change : plan.changes()) {
            Unit persistedUnit = persistedUnits.getOrDefault(change.unit(), change.unit());
            changes.add(new Change(change.row(), change.data(), change.existingCollaborator(), persistedUnit, change.role()));
        }
        return new CollaboratorImportPlan(List.copyOf(changes), plan.unchanged(), plan.ignored());
    }

    private boolean hasNewUnit(User existing, Unit unit) {
        return existing == null || existing.getUnit() == null || unit.id == null
                || !Objects.equals(existing.getUnit().id, unit.id);
    }

    private boolean hasNewRole(User existing, Role role) {
        return existing == null || existing.getRole() == null || role.getId() == null
                || !Objects.equals(existing.getRole().getId(), role.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deactivateAfterEmailFailure(Long userId, String firebaseUid) {
        if (collaboratorRepository.deactivateAfterEmailFailure(userId, firebaseUid) != 1) {
            throw new IllegalStateException("Não foi possível conter a identidade inconsistente");
        }
    }
}
