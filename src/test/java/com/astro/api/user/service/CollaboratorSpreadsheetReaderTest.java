package com.astro.api.user.service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CollaboratorSpreadsheetReaderTest {
    @Test
    void readsTheSixColumnsFromBothExcelFormatsAndEnforcesTheSeparateImportLimit() throws Exception {
        for (Workbook workbook : List.of(
                new XSSFWorkbook(), new HSSFWorkbook())) {
            try (workbook; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                Sheet sheet = workbook.createSheet();
                Row header = sheet.createRow(0);
                List<String> names = List.of("Nome", "CPF", "Email", "Unidade", "Cargo", "Modalidade");
                List<String> values = List.of("Ana", "52998224725", "ana@astro.com", "Filial", "Dev", "Híbrido");
                Row row = sheet.createRow(1);
                for (int index = 0; index < names.size(); index++) {
                    header.createCell(index).setCellValue(names.get(index));
                    row.createCell(index).setCellValue(values.get(index));
                }
                workbook.write(bytes);
                MockMultipartFile file = new MockMultipartFile("file", bytes.toByteArray());
                CollaboratorSpreadsheetReader spreadsheetReader = new CollaboratorSpreadsheetReader();
                assertEquals("Híbrido", spreadsheetReader.read(file, 10L * 1024 * 1024).getFirst().workModel());
                assertEquals(2, spreadsheetReader.read(file, 10L * 1024 * 1024).getFirst().number());
            }
        }
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        CollaboratorSpreadsheetReader spreadsheetReader = new CollaboratorSpreadsheetReader();
        assertThrows(
                MaxUploadSizeExceededException.class,
                () -> spreadsheetReader.read(oversized, 10L * 1024 * 1024));
        verify(oversized, never()).getInputStream();
    }

}
