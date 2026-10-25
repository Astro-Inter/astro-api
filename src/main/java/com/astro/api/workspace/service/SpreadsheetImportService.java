package com.astro.api.workspace.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.model.SpreadsheetImportMessage;
import com.astro.api.common.text.NormalizedText;
import com.astro.api.common.text.PersonalDataNormalizer;
import com.astro.api.user.mapper.CollaboratorMapper;
import com.astro.api.user.model.CollaboratorImportData;
import com.astro.api.user.service.CollaboratorSpreadsheetValidator;
import com.astro.api.role.model.Role;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.CollaboratorImportRow;
import com.astro.api.user.model.User;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.CollaboratorSpreadsheetReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SpreadsheetImportService {
    private final UserRepository userRepository;
    private final CollaboratorSpreadsheetReader collaboratorSpreadsheetReader;
    private final CollaboratorMapper collaboratorMapper;
    private final CollaboratorSpreadsheetValidator spreadsheetValidator;

    public SpreadsheetImportService(UserRepository userRepository, CollaboratorSpreadsheetReader collaboratorSpreadsheetReader,
            CollaboratorMapper collaboratorMapper, CollaboratorSpreadsheetValidator spreadsheetValidator) {
        this.userRepository = userRepository;
        this.collaboratorSpreadsheetReader = collaboratorSpreadsheetReader;
        this.collaboratorMapper = collaboratorMapper;
        this.spreadsheetValidator = spreadsheetValidator;
    }

    public List<CollaboratorImportRow> read(MultipartFile file) {
        return collaboratorSpreadsheetReader.read(file, 5L * 1024 * 1024);
    }

    public CollaboratorImportRow findManagerRow(List<CollaboratorImportRow> rows, String managerEmail) {
        List<CollaboratorImportRow> managerRows = rows.stream()
                .filter(row -> PersonalDataNormalizer.email(row.email()).equals(PersonalDataNormalizer.email(managerEmail)))
                .toList();
        if (managerRows.isEmpty()) {
            throw new BusinessException("Planilha: o gestor do workspace deve constar em uma linha com o e-mail informado no cadastro");
        }
        if (managerRows.size() > 1) {
            throw new BusinessException("Planilha: o e-mail do gestor aparece em mais de uma linha");
        }
        CollaboratorImportRow managerRow = managerRows.getFirst();
        if (!notBlank(managerRow.name())) {
            throw new BusinessException("Planilha: o nome do gestor é obrigatório");
        }
        if (!notBlank(managerRow.unit())) {
            throw new BusinessException("Planilha: a unidade do gestor é obrigatória");
        }
        if (!notBlank(managerRow.role())) {
            throw new BusinessException("Planilha: o cargo do gestor é obrigatório");
        }
        return managerRow;
    }

    public Unit findManagerUnit(CollaboratorImportRow managerRow, List<Unit> units) {
        return units.stream()
                .filter(unit -> NormalizedText.key(unit.name).equals(NormalizedText.key(managerRow.unit())))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Planilha: a unidade informada para o gestor não existe no workspace"));
    }

    public Role findManagerRole(CollaboratorImportRow managerRow, Map<String, Role> rolesByName) {
        return rolesByName.get(NormalizedText.key(managerRow.role()));
    }

    public ImportProcessingResult validateForRegistration(List<CollaboratorImportRow> rows, List<UnitRequest> unitRequests,
                                                           CollaboratorImportRow managerRow) {
        List<Unit> units = unitRequests.stream().map(request -> {
            Unit unit = new Unit();
            unit.name = request.name();
            return unit;
        }).toList();
        Map<String, Role> rolesByName = new HashMap<>();
        rows.stream().map(CollaboratorImportRow::role).filter(this::notBlank).forEach(name -> {
            Role role = new Role();
            role.setName(name.trim());
            role.setActive(true);
            rolesByName.putIfAbsent(NormalizedText.key(name), role);
        });
        findManagerUnit(managerRow, units);
        findManagerRole(managerRow, rolesByName);
        return validateAndBuild(rows, units, managerRow, rolesByName);
    }

    public void bindPersistedDependencies(List<User> users, List<Unit> units, Map<String, Role> rolesByName) {
        Map<String, Unit> unitsByName = new HashMap<>();
        units.forEach(unit -> unitsByName.put(NormalizedText.key(unit.name), unit));
        users.forEach(user -> {
            user.setUnit(unitsByName.get(NormalizedText.key(user.getUnit().name)));
            user.setRole(rolesByName.get(NormalizedText.key(user.getRole().getName())));
        });
    }

    public ImportProcessingResult validateAndBuild(List<CollaboratorImportRow> rows, List<Unit> units,
                                                    CollaboratorImportRow managerRow, Map<String, Role> rolesByName) {
        List<CollaboratorImportRow> collaboratorRows = rows.stream().filter(row -> row != managerRow).toList();
        Set<String> emails = collaboratorRows.stream().map(CollaboratorImportRow::email)
                .filter(this::notBlank).map(PersonalDataNormalizer::email).collect(Collectors.toSet());
        Set<String> cpfs = rows.stream().map(CollaboratorImportRow::cpf)
                .filter(this::notBlank).map(PersonalDataNormalizer::cpfDigits).collect(Collectors.toSet());
        Set<String> existingEmails = emails.isEmpty() ? Set.of() : userRepository.findByEmailInIgnoreCase(emails).stream()
                .map(User::getEmail)
                .map(PersonalDataNormalizer::email)
                .collect(Collectors.toSet());
        Set<String> existingCpfs = cpfs.isEmpty() ? Set.of() : userRepository.findByCpfIn(cpfs).stream()
                .map(User::getCpf)
                .collect(Collectors.toSet());
        Map<String, Unit> unitsByName = new HashMap<>();
        units.forEach(unit -> unitsByName.put(NormalizedText.key(unit.name), unit));
        Set<String> emailsInSpreadsheet = new HashSet<>(Set.of(PersonalDataNormalizer.email(managerRow.email())));
        Set<String> cpfsInSpreadsheet = new HashSet<>();
        if (notBlank(managerRow.cpf())) {
            cpfsInSpreadsheet.add(PersonalDataNormalizer.cpfDigits(managerRow.cpf()));
        }
        List<User> validUsers = new ArrayList<>();
        List<SpreadsheetImportMessage> errors = new ArrayList<>();
        validateManagerPersonalData(managerRow, existingCpfs, errors);

        for (CollaboratorImportRow row : collaboratorRows) {
            CollaboratorImportData data = collaboratorMapper.toRegistrationData(row);
            String normalizedEmail = data.email();
            String normalizedCpf = data.cpf();
            Optional<SpreadsheetImportMessage> personalDataError = spreadsheetValidator.validateForRegistration(data, row.number());
            if (personalDataError.isPresent()) {
                errors.add(personalDataError.get());
                continue;
            }
            if (!emailsInSpreadsheet.add(normalizedEmail)) { error(errors, row, "email", "E-mail duplicado na planilha"); continue; }
            if (!cpfsInSpreadsheet.add(normalizedCpf)) { error(errors, row, "cpf", "CPF duplicado na planilha"); continue; }
            if (existingEmails.contains(normalizedEmail)) { error(errors, row, "email", "E-mail já cadastrado"); continue; }
            if (existingCpfs.contains(normalizedCpf)) { error(errors, row, "cpf", "CPF já cadastrado"); continue; }
            Unit unit = unitsByName.get(NormalizedText.key(row.unit()));
            if (unit == null) { error(errors, row, "unidade", "Unidade não encontrada entre as unidades do workspace"); continue; }
            Role role = rolesByName.get(NormalizedText.key(row.role()));
            if (role == null || !role.isActive()) { error(errors, row, "cargo", "Cargo inexistente ou inativo para o workspace"); continue; }
            WorkModel workModel = data.workModel();
            if (workModel == null) { error(errors, row, "modalidade", "Modalidade inválida"); continue; }
            User user = collaboratorMapper.toNewUser(data, role, unit);
            validUsers.add(user);
        }
        return new ImportProcessingResult(validUsers, errors);
    }

    private void validateManagerPersonalData(CollaboratorImportRow row, Set<String> existingCpfs,
                                             List<SpreadsheetImportMessage> errors) {
        CollaboratorImportData data = collaboratorMapper.toRegistrationData(row);

        if (notBlank(row.cpf())) {
            Optional<SpreadsheetImportMessage> cpfError = spreadsheetValidator.validateCpfForRegistration(data, row.number());
            if (cpfError.isPresent()) {
                errors.add(cpfError.get());
            } else if (existingCpfs.contains(data.cpf())) {
                error(errors, row, "cpf", "CPF já cadastrado");
            }
        }

        if (notBlank(row.workModel()) && data.workModel() == null) {
            error(errors, row, "modalidade", "Modalidade inválida");
        }
    }

    private void error(List<SpreadsheetImportMessage> errors, CollaboratorImportRow row, String field, String message) { errors.add(new SpreadsheetImportMessage(row.number(), field, message)); }
    private boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }

    public record ImportProcessingResult(List<User> validUsers, List<SpreadsheetImportMessage> errors) { }
}
