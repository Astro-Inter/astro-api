package com.astro.api.conformity;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

final class ConformityTestSpreadsheets {
    static MockMultipartFile file(boolean xls, Object[]... rows) throws Exception {
        try (Workbook workbook = xls ? new HSSFWorkbook() : new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("CPF"); header.createCell(1).setCellValue("NR");
            header.createCell(2).setCellValue("Data de validade");
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
            for (int index = 0; index < rows.length; index++) {
                Row row = sheet.createRow(index + 1);
                for (int column = 0; column < rows[index].length; column++) {
                    Object value = rows[index][column]; if (value == null) continue;
                    Cell cell = row.createCell(column);
                    if (value instanceof LocalDate date) { cell.setCellValue(date.atStartOfDay()); cell.setCellStyle(dateStyle); }
                    else if (value instanceof Number number) cell.setCellValue(number.doubleValue());
                    else if (value.toString().startsWith("=")) cell.setCellFormula(value.toString().substring(1));
                    else cell.setCellValue(value.toString());
                }
            }
            var output = new ByteArrayOutputStream(); workbook.write(output);
            return new MockMultipartFile("file", xls ? "test.xls" : "test.xlsx", "application/octet-stream", output.toByteArray());
        }
    }
}
