package com.astro.api.conformity.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.conformity.model.ConformityImportRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses XLS/XLSX into validated rows without querying or writing business data.
 * Component registration makes this stateless reader injectable into ConformityImportService.
 * Authorization and per-row transactions belong to the services, not this reader.
 */
@Component
public class ConformitySpreadsheetReader {
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final List<String> HEADERS = List.of("cpf", "nr", "data de validade");
    private static final Pattern NR = Pattern.compile("(?i)(?:NR[\\s-]*)?([0-9]+)");
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public List<ConformityImportRow> read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("A planilha é obrigatória");
        if (file.getSize() > MAX_SIZE) throw new MaxUploadSizeExceededException(MAX_SIZE);
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) throw new BusinessException("Envie um arquivo XLS ou XLSX");
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) throw new BusinessException("A planilha não possui uma aba");
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) throw new BusinessException("A planilha deve conter CPF, NR e Data de validade");
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("pt-BR"));
            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : header) {
                rejectFormula(cell);
                String column = normalized(formatter.formatCellValue(cell));
                if (!column.isEmpty() && columns.putIfAbsent(column, cell.getColumnIndex()) != null) {
                    throw new BusinessException("Cabeçalho duplicado: " + column);
                }
            }
            if (!columns.keySet().containsAll(HEADERS)) throw new BusinessException("A planilha deve conter CPF, NR e Data de validade");
            List<ConformityImportRow> rows = new ArrayList<>();
            for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                Cell cpf = row.getCell(columns.get("cpf")); Cell nr = row.getCell(columns.get("nr"));
                Cell expiresAt = row.getCell(columns.get("data de validade"));
                if (blank(cpf) && blank(nr) && blank(expiresAt)) continue;
                String cpfText = formatter.formatCellValue(cpf).trim(); String nrText = formatter.formatCellValue(nr).trim();
                try {
                    rejectFormula(cpf); rejectFormula(nr); rejectFormula(expiresAt);
                    rows.add(new ConformityImportRow(index + 1, cpfText, nrText, cpf(cpf), nr(nr), date(expiresAt), null));
                } catch (RuntimeException error) {
                    rows.add(new ConformityImportRow(index + 1, cpfText, nrText, null, null, null,
                            error instanceof BusinessException ? error.getMessage() : "Valor inválido na linha"));
                }
            }
            if (rows.isEmpty()) throw new BusinessException("A planilha não possui linhas para importar");
            return rows;
        } catch (BusinessException | MaxUploadSizeExceededException error) {
            throw error;
        } catch (IOException | RuntimeException error) {
            throw new BusinessException("Não foi possível ler a planilha XLS/XLSX enviada");
        }
    }

    private static boolean blank(Cell cell) {
        return cell == null || cell.getCellType() == CellType.BLANK
                || (cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank());
    }
    private static void rejectFormula(Cell cell) {
        if (cell != null && cell.getCellType() == CellType.FORMULA) throw new BusinessException("Fórmulas não são permitidas");
        if (cell != null && cell.getCellType() == CellType.ERROR) throw new BusinessException("Célula contém erro do Excel");
    }
    private static String normalized(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
    private static String cpf(Cell cell) {
        if (blank(cell)) throw new BusinessException("CPF é obrigatório");
        String digits;
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (!Double.isFinite(value) || value < 0 || value != Math.rint(value) || value > 99999999999L) {
                throw new BusinessException("CPF deve conter 11 dígitos");
            }
            digits = String.format(Locale.ROOT, "%011d", (long) value);
        } else if (cell.getCellType() == CellType.STRING) {
            String text = cell.getStringCellValue().trim();
            if (!text.matches("[0-9.\\-\\s]+")) throw new BusinessException("CPF deve conter 11 dígitos");
            digits = text.replaceAll("[^0-9]", "");
        } else throw new BusinessException("CPF inválido");
        if (digits.length() != 11) throw new BusinessException("CPF deve conter 11 dígitos");
        return digits;
    }
    private static Integer nr(Cell cell) {
        if (blank(cell)) throw new BusinessException("NR é obrigatória");
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (!Double.isFinite(value) || value <= 0 || value != Math.rint(value) || value > Integer.MAX_VALUE) {
                throw new BusinessException("NR deve ser um inteiro positivo");
            }
            return (int) value;
        }
        if (cell.getCellType() != CellType.STRING) throw new BusinessException("NR inválida");
        Matcher matcher = NR.matcher(cell.getStringCellValue().trim());
        if (!matcher.matches()) throw new BusinessException("Informe o código da NR, como 10 ou NR 10");
        try {
            int value = Integer.parseInt(matcher.group(1));
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) { throw new BusinessException("NR deve ser um inteiro positivo"); }
    }
    private static LocalDate date(Cell cell) {
        if (blank(cell)) throw new BusinessException("Data de validade é obrigatória na importação");
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
                && DateUtil.isValidExcelDate(cell.getNumericCellValue())) return cell.getLocalDateTimeCellValue().toLocalDate();
        if (cell.getCellType() == CellType.STRING) {
            String value = cell.getStringCellValue().trim();
            try { return LocalDate.parse(value, BR_DATE); }
            catch (java.time.format.DateTimeParseException ignored) {
                try { return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE); }
                catch (java.time.format.DateTimeParseException error) { /* Report a stable row error below. */ }
            }
        }
        throw new BusinessException("Data de validade inválida; use uma data Excel, dd/MM/yyyy ou yyyy-MM-dd");
    }
}
