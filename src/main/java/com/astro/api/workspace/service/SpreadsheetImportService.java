package com.astro.api.workspace.service;

import com.astro.api.cargo.model.Cargo;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.dto.response.ImportErrorResponse;
import com.astro.api.workspace.model.Workspace;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

@Service
public class SpreadsheetImportService {
    private static final List<String> REQUIRED_HEADERS = List.of("nome", "cpf", "email", "unidade", "cargo", "modalidade");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final UserRepository userRepository;

    public SpreadsheetImportService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<SpreadsheetRow> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("A planilha de colaboradores é obrigatória");
        }
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new BusinessException("A planilha não possui linhas para importar");
            }
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("pt-BR"));
            Row header = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> columns = columns(header, formatter);
            for (String requiredHeader : REQUIRED_HEADERS) {
                if (!columns.containsKey(requiredHeader)) {
                    throw new BusinessException("A planilha deve conter a coluna '" + requiredHeader + "'");
                }
            }
            List<SpreadsheetRow> rows = new ArrayList<>();
            for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || isBlankRow(row, formatter)) continue;
                rows.add(new SpreadsheetRow(index + 1,
                        value(row, columns.get("nome"), formatter), value(row, columns.get("cpf"), formatter),
                        value(row, columns.get("email"), formatter), value(row, columns.get("unidade"), formatter),
                        value(row, columns.get("cargo"), formatter), value(row, columns.get("modalidade"), formatter)));
            }
            return rows;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException("Não foi possível ler a planilha XLS/XLSX enviada");
        }
    }

    public SpreadsheetRow findManagerRow(List<SpreadsheetRow> rows, String managerEmail) {
        List<SpreadsheetRow> managerRows = rows.stream()
                .filter(row -> email(row.email()).equals(email(managerEmail)))
                .toList();
        if (managerRows.isEmpty()) {
            throw new BusinessException("Planilha: o gestor do workspace deve constar em uma linha com o e-mail informado no cadastro");
        }
        if (managerRows.size() > 1) {
            throw new BusinessException("Planilha: o e-mail do gestor aparece em mais de uma linha");
        }
        SpreadsheetRow managerRow = managerRows.getFirst();
        if (!notBlank(managerRow.name())) {
            throw new BusinessException("Planilha: o nome do gestor é obrigatório");
        }
        if (!notBlank(managerRow.unit())) {
            throw new BusinessException("Planilha: a unidade do gestor é obrigatória");
        }
        if (!notBlank(managerRow.cargo())) {
            throw new BusinessException("Planilha: o cargo do gestor é obrigatório");
        }
        return managerRow;
    }

    public Unit findManagerUnit(SpreadsheetRow managerRow, List<Unit> units) {
        return units.stream()
                .filter(unit -> normalized(unit.name).equals(normalized(managerRow.unit())))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Planilha: a unidade informada para o gestor não existe no workspace"));
    }

    public Cargo findManagerCargo(SpreadsheetRow managerRow, Map<String, Cargo> cargosByName) {
        return cargosByName.get(normalized(managerRow.cargo()));
    }

    public ImportProcessingResult validateAndBuild(List<SpreadsheetRow> rows, Workspace workspace, List<Unit> units,
                                                    SpreadsheetRow managerRow, Map<String, Cargo> cargosByName) {
        List<SpreadsheetRow> collaboratorRows = rows.stream().filter(row -> row != managerRow).toList();
        Set<String> emails = collaboratorRows.stream().map(SpreadsheetRow::email).filter(this::notBlank).map(this::email).collect(Collectors.toSet());
        Set<String> cpfs = collaboratorRows.stream().map(SpreadsheetRow::cpf).filter(this::notBlank).map(this::cpf).collect(Collectors.toSet());
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
        List<ImportErrorResponse> errors = new ArrayList<>();
        for (SpreadsheetRow row : collaboratorRows) {
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
            Cargo cargo = cargosByName.get(normalized(row.cargo()));
            if (cargo == null || !cargo.isActive()) { error(errors, row, "cargo", "Cargo inexistente ou inativo para o workspace"); continue; }
            WorkModel workModel = workModel(row.workModel());
            if (workModel == null) { error(errors, row, "modalidade", "Modalidade inválida"); continue; }
            User user = new User();
            user.setName(row.name().trim()); user.setEmail(normalizedEmail); user.setCpf(normalizedCpf);
            user.setType(UserType.COLABORADOR); user.setStatus(UserStatus.PRE_CADASTRADO);
            user.setWorkModel(workModel); user.setUnit(unit); user.setCargo(cargo); user.setCreatedAt(Instant.now());
            validUsers.add(user);
        }
        return new ImportProcessingResult(validUsers, errors);
    }

    private Map<String, Integer> columns(Row header, DataFormatter formatter) {
        Map<String, Integer> result = new HashMap<>();
        for (Cell cell : header) result.put(normalized(formatter.formatCellValue(cell)), cell.getColumnIndex());
        return result;
    }
    private String value(Row row, int column, DataFormatter formatter) { Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL); return cell == null ? "" : formatter.formatCellValue(cell).trim(); }
    private boolean isBlankRow(Row row, DataFormatter formatter) { for (Cell cell : row) if (notBlank(formatter.formatCellValue(cell))) return false; return true; }
    private void error(List<ImportErrorResponse> errors, SpreadsheetRow row, String field, String message) { errors.add(new ImportErrorResponse(row.number(), field, message)); }
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

    public record SpreadsheetRow(int number, String name, String cpf, String email, String unit, String cargo, String workModel) { }
    public record ImportProcessingResult(List<User> validUsers, List<ImportErrorResponse> errors) { }
}
