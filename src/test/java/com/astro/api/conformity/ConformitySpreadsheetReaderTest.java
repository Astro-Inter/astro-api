package com.astro.api.conformity;

import com.astro.api.conformity.service.ConformitySpreadsheetReader;
import com.astro.api.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.*;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConformitySpreadsheetReaderTest {
    final ConformitySpreadsheetReader reader = new ConformitySpreadsheetReader();
    @Test void acceptsBothExcelFormatsAndPreservesLeadingZeroesAndRealDates() throws Exception {
        for (boolean xls : new boolean[]{false, true}) {
            var result = reader.read(ConformityTestSpreadsheets.file(xls,
                    new Object[]{"000.000.000-02", "NR 10", "31/12/2030"},
                    new Object[]{2D, 11D, LocalDate.of(2031,1,1)},
                    new Object[]{"00000000003", "12", "2032-02-29"}));
            assertEquals(3, result.size());
            assertEquals("00000000002", result.get(0).cpf()); assertEquals("00000000002", result.get(1).cpf());
            assertEquals(10, result.get(0).nrId()); assertEquals(LocalDate.of(2031,1,1), result.get(1).expiresAt());
            assertNull(result.get(2).error());
        }
    }
    @Test void rejectsInvalidRowsAndFormulasWithoutDiscardingTheValidOnes() throws Exception {
        var result = reader.read(ConformityTestSpreadsheets.file(false,
                new Object[]{"00000000002", 10, "31/02/2030"},
                new Object[]{"00000000002", 10, null},
                new Object[]{"=1+1", 10, "2030-01-01"},
                new Object[]{"00000000002", "=10", "2030-01-01"},
                new Object[]{"00000000002", 10, "=TODAY()"},
                new Object[]{"abc", 10, "2030-01-01"},
                new Object[]{"00000000002", 1.5, "2030-01-01"},
                new Object[]{null, null, null},
                new Object[]{"00000000002", 10, "2030-01-01"}));
        assertEquals(8, result.size());
        for (int index = 0; index < 7; index++) assertNotNull(result.get(index).error());
        assertEquals(10, result.getLast().row()); assertNull(result.getLast().error());
        assertTrue(result.get(2).error().contains("Fórmulas"));
    }
    @Test void rejectsEmptyInvalidAndOversizedFilesBeforeParsing() throws Exception {
        assertThrows(BusinessException.class, () -> reader.read(null));
        assertThrows(BusinessException.class, () -> reader.read(new MockMultipartFile("file", new byte[0])));
        assertThrows(BusinessException.class, () -> reader.read(new MockMultipartFile("file", "file.csv", "text/csv", "CPF,NR".getBytes())));
        assertThrows(BusinessException.class, () -> reader.read(new MockMultipartFile("file", "file.xlsx", "application/octet-stream", "invalid".getBytes())));
        assertThrows(BusinessException.class, () -> reader.read(ConformityTestSpreadsheets.file(false)));
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.getSize()).thenReturn(10L * 1024 * 1024 + 1);
        assertThrows(MaxUploadSizeExceededException.class, () -> reader.read(oversized));
        verify(oversized, never()).getInputStream();
    }
    @Test void validatesMissingDuplicateAndFormulaHeaders() throws Exception {
        for (String secondHeader : new String[]{"CPF", "other", "=1+1"}) {
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
                var row = workbook.createSheet().createRow(0); row.createCell(0).setCellValue("CPF");
                if (secondHeader.startsWith("=")) row.createCell(1).setCellFormula("1+1");
                else row.createCell(1).setCellValue(secondHeader);
                row.createCell(2).setCellValue("Data de validade");
                var output = new java.io.ByteArrayOutputStream(); workbook.write(output);
                assertThrows(BusinessException.class, () -> reader.read(new MockMultipartFile("file", "file.xlsx", "application/octet-stream", output.toByteArray())));
            }
        }
    }

    @Test void acceptsAFileExactlyAtTheTenMegabyteLimit() throws Exception {
        var file = spy(ConformityTestSpreadsheets.file(false, new Object[]{"00000000002", 10, "2030-01-01"}));
        when(file.getSize()).thenReturn(10L*1024*1024);
        assertEquals(1, reader.read(file).size());
    }
}
