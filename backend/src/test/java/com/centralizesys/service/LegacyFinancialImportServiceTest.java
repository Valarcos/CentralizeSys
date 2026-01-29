package com.centralizesys.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegacyFinancialImportServiceTest {

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private NamedParameterJdbcTemplate namedJdbcTemplate;

    @Mock
    private BackupService backupService;

    @InjectMocks
    private LegacyFinancialImportService service;

    @Test
    @DisplayName("Should process valid file correctly")
    void testImportValidFile() throws IOException {
        // Setup Mocks
        when(backupService.createCheckpoint(anyString(), anyLong())).thenReturn("checkpoint.zip");

        // Mock DB Insert (Venta) to return an ID
        // Mock DB Insert (Venta) to return an ID
        doAnswer(invocation -> {
            KeyHolder holder = invocation.getArgument(2);
            if (holder instanceof org.springframework.jdbc.support.GeneratedKeyHolder) {
                ((org.springframework.jdbc.support.GeneratedKeyHolder) holder).getKeyList()
                        .add(java.util.Map.of("id", 123L));
            }
            return 1;
        }).when(namedJdbcTemplate).update(contains("INSERT INTO ventas"), any(MapSqlParameterSource.class),
                any(KeyHolder.class));

        // Create temporary Excel file
        File tempFile = File.createTempFile("test_import", ".xlsx");
        tempFile.deleteOnExit();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("ENERO");

            // Row 0: Year hint
            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("2024");

            // Row 5: Headers
            Row header = sheet.createRow(5);
            header.createCell(0).setCellValue("DÍA");
            header.createCell(1).setCellValue("CLIENTE");
            header.createCell(2).setCellValue("ART");
            header.createCell(3).setCellValue("DESCRIPCIÓN");
            header.createCell(4).setCellValue("CANT");
            header.createCell(5).setCellValue("P. UNITARIO");
            header.createCell(6).setCellValue("COSTO");
            header.createCell(7).setCellValue("TOTAL VENTA");

            // Row 6: Data Row
            Row data = sheet.createRow(6);
            data.createCell(0).setCellValue("1");
            data.createCell(1).setCellValue("Juan Perez");
            data.createCell(2).setCellValue("C001");
            data.createCell(3).setCellValue("Producto Test");
            data.createCell(4).setCellValue(2.0);
            data.createCell(5).setCellValue(10.0);
            data.createCell(6).setCellValue(5.0);
            data.createCell(7).setCellValue(20.0);

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                wb.write(fos);
            }
        }

        // Execute
        String result = service.importLegacyFile(tempFile.getAbsolutePath());
        System.out.println("DEBUG: Import Result = " + result);

        // Verify
        assertTrue(result.contains("Import Complete"), "Should complete successfully");

        // Check what auditoria received
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrarAccion(eq(0L), eq("IMPORT_LEGACY_FIN"), statusCaptor.capture());
        String status = statusCaptor.getValue();
        System.out.println("DEBUG: Auditoria Status = " + status);
        assertTrue(status.contains("Imported Sales: 1"), "Should report 1 imported sale. Actual: " + status);

        // Verify DB calls
        verify(namedJdbcTemplate, atLeastOnce()).update(contains("INSERT INTO ventas"),
                any(MapSqlParameterSource.class), any(KeyHolder.class));
        verify(namedJdbcTemplate, atLeastOnce()).batchUpdate(contains("INSERT INTO detalles_venta"),
                any(MapSqlParameterSource[].class));
    }

    @Test
    @DisplayName("Should skip skipped rows due to validation")
    void testImportInvalidRows() throws IOException {
        // Setup Mocks
        when(backupService.createCheckpoint(anyString(), anyLong())).thenReturn("checkpoint.zip");

        File tempFile = File.createTempFile("test_invalid", ".xlsx");
        tempFile.deleteOnExit();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("FEBRERO");

            // Headers
            Row header = sheet.createRow(4);
            header.createCell(0).setCellValue("CLIENTE");
            header.createCell(1).setCellValue("ART");
            header.createCell(2).setCellValue("DESCRIPCIÓN");
            header.createCell(3).setCellValue("CANT");
            header.createCell(4).setCellValue("COSTO");
            header.createCell(5).setCellValue("P. UNITARIO");

            // Invalid Row: Short client name
            Row r1 = sheet.createRow(5);
            r1.createCell(0).setCellValue("A"); // Too short

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                wb.write(fos);
            }
        }

        // Execute
        service.importLegacyFile(tempFile.getAbsolutePath());

        // Verify NO DB calls (sales skipped)
        verify(namedJdbcTemplate, never()).update(contains("INSERT INTO ventas"), any(MapSqlParameterSource.class),
                any(KeyHolder.class));
    }
}
