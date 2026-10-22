package com.astro.api.user.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.text.NormalizedText;
import com.astro.api.user.model.CollaboratorImportRow;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

@Component
public class CollaboratorSpreadsheetReader {
    private static final List<String> REQUIRED_HEADERS = List.of("nome", "cpf", "email", "unidade", "cargo", "modalidade");

    public List<CollaboratorImportRow> read(MultipartFile file, long maxBytes) {
        if (file != null && file.getSize() > maxBytes) {
            throw new MaxUploadSizeExceededException(maxBytes);
        }
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
            List<CollaboratorImportRow> rows = new ArrayList<>();
            for (int index = header.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || isBlankRow(row, formatter)) continue;
                rows.add(new CollaboratorImportRow(index + 1,
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

    private Map<String, Integer> columns(Row header, DataFormatter formatter) {
        Map<String, Integer> columnIndexes = new HashMap<>();
        for (Cell cell : header) {
            String columnName = NormalizedText.key(formatter.formatCellValue(cell));
            columnIndexes.put(columnName, cell.getColumnIndex());
        }
        return columnIndexes;
    }

    private String value(Row row, int column, DataFormatter formatter) {
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        for (Cell cell : row) {
            if (!formatter.formatCellValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }
}
