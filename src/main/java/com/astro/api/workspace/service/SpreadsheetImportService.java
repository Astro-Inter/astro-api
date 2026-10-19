package com.astro.api.workspace.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.model.SpreadsheetImportError;
import com.astro.api.role.model.Role;
import com.astro.api.unit.dto.request.UnitRequest;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.CollaboratorImportRow;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.user.service.CollaboratorSpreadsheetReader;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SpreadsheetImportService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final UserRepository userRepository;

    private final CollaboratorSpreadsheetReader collaboratorSpreadsheetReader;

    public SpreadsheetImportService(UserRepository userRepository, CollaboratorSpreadsheetReader collaboratorSpreadsheetReader) {
        this.userRepository = userRepository;
        this.collaboratorSpreadsheetReader = collaboratorSpreadsheetReader;
    }

    public List<CollaboratorImportRow> read(MultipartFile file) {
        return collaboratorSpreadsheetReader.read(file, 5L * 1024 * 1024);
    }

    public CollaboratorImportRow findManagerRow(List<CollaboratorImportRow> rows, String managerEmail) {
        List<CollaboratorImportRow> managerRows = rows.stream()
                .filter(row -> email(row.email()).equals(email(managerEmail)))
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
                .filter(unit -> normalized(unit.name).equals(normalized(managerRow.unit())))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Planilha: a unidade informada para o gestor não existe no workspace"));
    }

    public Role findManagerRole(CollaboratorImportRow managerRow, Map<String, Role> rolesByName) {
        return rolesByName.get(normalized(managerRow.role()));
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
            rolesByName.putIfAbsent(normalized(name), role);
        });
        findManagerUnit(managerRow, units);
        findManagerRole(managerRow, rolesByName);
        return validateAndBuild(rows, units, managerRow, rolesByName);
    }

    public void bindPersistedDependencies(List<User> users, List<Unit> units, Map<String, Role> rolesByName) {
        Map<String, Unit> unitsByName = new HashMap<>();
        units.forEach(unit -> unitsByName.put(normalized(unit.name), unit));
        users.forEach(user -> {
            user.setUnit(unitsByName.get(normalized(user.getUnit().name)));
            user.setRole(rolesByName.get(normalized(user.getRole().getName())));
        });
    }

    public ImportProcessingResult validateAndBuild(List<CollaboratorImportRow> rows, List<Unit> units,
                                                    CollaboratorImportRow managerRow, Map<String, Role> rolesByName) {
        List<CollaboratorImportRow> collaboratorRows = rows.stream().filter(row -> row != managerRow).toList();
        Set<String> emails = collaboratorRows.stream().map(CollaboratorImportRow::email).filter(this::notBlank).map(this::email).collect(Collectors.toSet());
        Set<String> cpfs = collaboratorRows.stream().map(CollaboratorImportRow::cpf).filter(this::notBlank).map(this::cpf).collect(Collectors.toSet());
        Set<String> existingEmails = emails.isEmpty() ? Set.of() : userRepository.findByEmailInIgnoreCase(emails).stream()
                .map(User::getEmail)
                .map(this::email)
                .collect(Collectors.toSet());
        Set<String> existingCpfs = cpfs.isEmpty() ? Set.of() : userRepository.findByCpfIn(cpfs).stream()
                .map(User::getCpf)
                .collect(Collectors.toSet());
        Map<String, Unit> unitsByName = new HashMap<>();
        units.forEach(unit -> unitsByName.put(normalized(unit.name), unit));
        Set<String> emailsInSpreadsheet = new HashSet<>(Set.of(email(managerRow.email())));
        Set<String> cpfsInSpreadsheet = new HashSet<>();
        if (notBlank(managerRow.cpf())) {
            cpfsInSpreadsheet.add(cpf(managerRow.cpf()));
        }
        List<User> validUsers = new ArrayList<>();
        List<SpreadsheetImportError> errors = new ArrayList<>();
        for (CollaboratorImportRow row : collaboratorRows) {
            String normalizedEmail = email(row.email());
            String normalizedCpf = cpf(row.cpf());
            if (!notBlank(row.name())) { error(errors, row, "nome", "Nome é obrigatório"); continue; }
            if (!EMAIL.matcher(normalizedEmail).matches()) { error(errors, row, "email", "E-mail inválido"); continue; }
            if (!isValidCpf(normalizedCpf)) { error(errors, row, "cpf", "CPF inválido"); continue; }
            if (!emailsInSpreadsheet.add(normalizedEmail)) { error(errors, row, "email", "E-mail duplicado na planilha"); continue; }
            if (!cpfsInSpreadsheet.add(normalizedCpf)) { error(errors, row, "cpf", "CPF duplicado na planilha"); continue; }
            if (existingEmails.contains(normalizedEmail)) { error(errors, row, "email", "E-mail já cadastrado"); continue; }
            if (existingCpfs.contains(normalizedCpf)) { error(errors, row, "cpf", "CPF já cadastrado"); continue; }
            Unit unit = unitsByName.get(normalized(row.unit()));
            if (unit == null) { error(errors, row, "unidade", "Unidade não encontrada entre as unidades do workspace"); continue; }
            Role role = rolesByName.get(normalized(row.role()));
            if (role == null || !role.isActive()) { error(errors, row, "cargo", "Cargo inexistente ou inativo para o workspace"); continue; }
            WorkModel workModel = workModel(row.workModel());
            if (workModel == null) { error(errors, row, "modalidade", "Modalidade inválida"); continue; }
            User user = new User();
            user.setName(row.name().trim()); user.setEmail(normalizedEmail); user.setCpf(normalizedCpf);
            user.setType(UserType.COLABORADOR); user.setStatus(UserStatus.PRE_CADASTRADO);
            user.setWorkModel(workModel); user.setUnit(unit); user.setRole(role); user.setCreatedAt(Instant.now());
            validUsers.add(user);
        }
        return new ImportProcessingResult(validUsers, errors);
    }

    private void error(List<SpreadsheetImportError> errors, CollaboratorImportRow row, String field, String message) { errors.add(new SpreadsheetImportError(row.number(), field, message)); }
    private boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }
    private String email(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private String cpf(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
    private String normalized(String value) { return Normalizer.normalize(value == null ? "" : value.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT); }
    private WorkModel workModel(String value) { try { return WorkModel.valueOf(normalized(value).toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { return null; } }
    private boolean isValidCpf(String cpf) {
        if (!cpf.matches("\\d{11}") || cpf.chars().distinct().count() == 1) return false;
        return digit(cpf, 9) == cpf.charAt(9) - '0' && digit(cpf, 10) == cpf.charAt(10) - '0';
    }
    private int digit(String cpf, int length) { int sum = 0; for (int i = 0; i < length; i++) sum += (cpf.charAt(i) - '0') * (length + 1 - i); int result = (sum * 10) % 11; return result == 10 ? 0 : result; }

    public record ImportProcessingResult(List<User> validUsers, List<SpreadsheetImportError> errors) { }
}
