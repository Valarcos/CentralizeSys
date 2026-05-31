package com.centralizesys.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assertions;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                (holder).getKeyList()
                        .add(java.util.Map.of("id", 123L));
            }
            return 1;
        }).when(namedJdbcTemplate).update(contains("INSERT INTO ventas"), any(MapSqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));

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
                any(MapSqlParameterSource.class), any(KeyHolder.class), any(String[].class));
        verify(namedJdbcTemplate, atLeastOnce()).batchUpdate(contains("INSERT INTO detalles_venta"),
                any(MapSqlParameterSource[].class));
    }

    @Test
    @DisplayName("Should accept short client names (e.g. TO)")
    void testImportShortClientName() throws IOException {
        // Setup Mocks
        when(backupService.createCheckpoint(anyString(), anyLong())).thenReturn("checkpoint.zip");

        File tempFile = File.createTempFile("test_short_name", ".xlsx");
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
            header.createCell(7).setCellValue("TOTAL VENTA");

            // Invalid Row: Numeric client name "12345"
            Row rNumeric = sheet.createRow(5);
            rNumeric.createCell(0).setCellValue("12345"); // Invalid
            rNumeric.createCell(1).setCellValue("X");

            // Valid Row: Short client name "TO"
            Row r1 = sheet.createRow(6);
            r1.createCell(0).setCellValue("TO"); // Now Valid
            r1.createCell(1).setCellValue("A001");
            r1.createCell(2).setCellValue("Item");
            r1.createCell(3).setCellValue(1.0);
            r1.createCell(4).setCellValue(50.0);
            r1.createCell(5).setCellValue(100.0);
            r1.createCell(7).setCellValue(100.0);

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                wb.write(fos);
            }
        }

        // Execute
        service.importLegacyFile(tempFile.getAbsolutePath());

        // Verify DB calls (sales SHOULD be inserted)
        verify(namedJdbcTemplate, atLeastOnce()).update(contains("INSERT INTO ventas"),
                any(MapSqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));
    }

    @Test
    @DisplayName("Should skip sheets marked as summary (e.g. RESUMEN)")
    void testImportSkippedSheets() throws IOException {
        // Setup Mocks
        when(backupService.createCheckpoint(anyString(), anyLong())).thenReturn("checkpoint.zip");

        File tempFile = File.createTempFile("test_skip_sheet", ".xlsx");
        tempFile.deleteOnExit();

        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("RESUMEN 2024");
            wb.createSheet("TOTALES");

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                wb.write(fos);
            }
        }

        // Execute
        service.importLegacyFile(tempFile.getAbsolutePath());

        // Verify NO DB calls (nothing imported)
        verify(namedJdbcTemplate, never()).update(contains("INSERT INTO ventas"), any(MapSqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));
    }

    @Test
    @DisplayName("Should skip orphan rows (items before any client)")
    void testImportOrphanRows() throws IOException {
        when(backupService.createCheckpoint(anyString(), anyLong())).thenReturn("checkpoint.zip");
        File tempFile = File.createTempFile("test_orphan", ".xlsx");
        tempFile.deleteOnExit();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("ENERO");
            // Headers
            createValidHeaderRow(sheet, 0);

            // Orphan Row
            Row rOrphan = sheet.createRow(1);
            rOrphan.createCell(1).setCellValue("ORPHAN");
            rOrphan.createCell(2).setCellValue("Orphan Item");
            rOrphan.createCell(3).setCellValue(1.0);
            rOrphan.createCell(4).setCellValue(10.0);
            rOrphan.createCell(5).setCellValue(20.0);

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                wb.write(fos);
            }
        }

        service.importLegacyFile(tempFile.getAbsolutePath());

        // Verify NO DB calls
        verify(namedJdbcTemplate, never()).update(contains("INSERT INTO ventas"), any(MapSqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));
    }

    @Test
    @DisplayName("Should skip invalid items (Zero Cost, Short Desc)")
    void testImportInvalidItems() throws IOException {
        when(backupService.createCheckpoint(anyString(), anyLong())).thenReturn("checkpoint.zip");
        File tempFile = File.createTempFile("test_invalid_items", ".xlsx");
        tempFile.deleteOnExit();

        // No stubbing for INSERT INTO ventas needed, as we expect it NEVER to be
        // called.

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("ENERO");
            createValidHeaderRow(sheet, 0);

            // Client
            Row rClient = sheet.createRow(1);
            rClient.createCell(0).setCellValue("Client A");

            // Invalid Cost
            Row rInvalidCost = sheet.createRow(2);
            rInvalidCost.createCell(1).setCellValue("INV");
            rInvalidCost.createCell(2).setCellValue("Zero Cost");
            rInvalidCost.createCell(3).setCellValue(1.0);
            rInvalidCost.createCell(4).setCellValue(0.0); // Fail
            rInvalidCost.createCell(5).setCellValue(10.0);

            // Invalid Desc
            Row rShortDesc = sheet.createRow(3);
            rShortDesc.createCell(1).setCellValue("SHORT");
            rShortDesc.createCell(2).setCellValue("A"); // Fail
            rShortDesc.createCell(3).setCellValue(1.0);
            rShortDesc.createCell(4).setCellValue(10.0);
            rShortDesc.createCell(5).setCellValue(10.0);

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                wb.write(fos);
            }
        }

        service.importLegacyFile(tempFile.getAbsolutePath());

        // Verify correct header calls were made but NO items batch inserted
        // Since ALL items were invalid, the sale has 0 items and thus should NOT be
        // persisted.
        verify(namedJdbcTemplate, never()).update(contains("INSERT INTO ventas"), any(MapSqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));

        // Ensure batchUpdate for details was NOT called or called with empty list?
        // Service logic: persistSale returns early if items.isEmpty() -> insertDetails
        // not called.
        // So we expect insertDetails NOT to be called.
        verify(namedJdbcTemplate, never()).batchUpdate(contains("INSERT INTO detalles_venta"),
                any(MapSqlParameterSource[].class));
    }

    @Test
    @DisplayName("Should recover price from Total/Quantity if missing")
    void testImportPriceRecovery() throws IOException {
        when(backupService.createCheckpoint(anyString(), anyLong())).thenReturn("checkpoint.zip");
        File tempFile = File.createTempFile("test_price_recovery", ".xlsx");
        tempFile.deleteOnExit();

        // Mock DB Insert to return ID
        doAnswer(invocation -> {
            ((org.springframework.jdbc.support.GeneratedKeyHolder) invocation.getArgument(2)).getKeyList()
                    .add(java.util.Map.of("id", 999L));
            return 1;
        }).when(namedJdbcTemplate).update(contains("INSERT INTO ventas"), any(MapSqlParameterSource.class),
                any(KeyHolder.class), any(String[].class));

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("ENERO");
            // Test robustness: Shift header to Row 2 to ensure service finds it dynamically
            createValidHeaderRow(sheet, 2);

            // Client
            Row rClient = sheet.createRow(3);
            rClient.createCell(0).setCellValue("Client B");

            // Item with Missing Price
            Row rNoPrice = sheet.createRow(4);
            rNoPrice.createCell(1).setCellValue("REC");
            rNoPrice.createCell(2).setCellValue("Recovered Item");
            rNoPrice.createCell(3).setCellValue(2.0); // Qty
            rNoPrice.createCell(4).setCellValue(10.0); // Cost
            // P. UNITARIO missing
            rNoPrice.createCell(7).setCellValue(100.0); // Total

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                wb.write(fos);
            }
        }

        service.importLegacyFile(tempFile.getAbsolutePath());

        // Verify Price was recovered (100 / 2 = 50)
        ArgumentCaptor<MapSqlParameterSource[]> batchCaptor = ArgumentCaptor.forClass(MapSqlParameterSource[].class);
        verify(namedJdbcTemplate).batchUpdate(contains("INSERT INTO detalles_venta"), batchCaptor.capture());

        MapSqlParameterSource[] items = batchCaptor.getValue();
        assertEquals(1, items.length);
        Double price = (Double) items[0].getValue("prec");
        // Safe unboxing check
        if (price == null) {
            throw new AssertionError("Recovered price should not be null");
        }
        assertEquals(50.0, price, 0.01, "Price should be calculated from Total/Qty");
    }

    // Helper to reduce boilerplate
    private void createValidHeaderRow(Sheet sheet, int rowNum) {
        Row header = sheet.createRow(rowNum);
        header.createCell(0).setCellValue("CLIENTE");
        header.createCell(1).setCellValue("ART");
        header.createCell(2).setCellValue("DESCRIPCIÓN");
        header.createCell(3).setCellValue("CANT");
        header.createCell(4).setCellValue("COSTO");
        header.createCell(5).setCellValue("P. UNITARIO");
        header.createCell(7).setCellValue("TOTAL VENTA");
    }

    @Test
    @DisplayName("Should detect and import Year 2026 correctly")
    void testImportYear2026() throws IOException {
        // Setup Mocks
        when(backupService.createCheckpoint(anyString(), anyLong())).thenReturn("checkpoint.zip");

        File tempFile = File.createTempFile("test_2026", ".xlsx");
        tempFile.deleteOnExit();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("ENERO");

            // Row 0: Explicit 2026 Year Header
            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("2026");

            // Headers
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("DÍA");
            header.createCell(1).setCellValue("CLIENTE");
            header.createCell(2).setCellValue("ART");
            header.createCell(3).setCellValue("DESCRIPCIÓN");
            header.createCell(4).setCellValue("CANT");
            header.createCell(5).setCellValue("P. UNITARIO");
            header.createCell(6).setCellValue("COSTO");
            header.createCell(7).setCellValue("TOTAL VENTA");

            // Data Row
            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("1");
            data.createCell(1).setCellValue("Client 2026");
            data.createCell(2).setCellValue("A");
            data.createCell(3).setCellValue("Item 2026");
            data.createCell(4).setCellValue(1.0);
            data.createCell(5).setCellValue(10.0);
            data.createCell(6).setCellValue(5.0);
            data.createCell(7).setCellValue(10.0);

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                wb.write(fos);
            }
        }

        // Execute
        service.importLegacyFile(tempFile.getAbsolutePath());

        // Verify correct Date parsing (Year 2026)
        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(namedJdbcTemplate, atLeastOnce()).update(contains("INSERT INTO ventas"), captor.capture(),
                any(KeyHolder.class), any(String[].class));

        MapSqlParameterSource params = captor.getValue();
        String dateStr = (String) params.getValue("fecha");
        Assertions.assertNotNull(dateStr);
        assertTrue(dateStr.startsWith("2026-"), "Date should start with 2026. Actual: " + dateStr);
    }
}
