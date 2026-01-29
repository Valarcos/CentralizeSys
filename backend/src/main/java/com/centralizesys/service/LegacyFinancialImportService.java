package com.centralizesys.service;

import org.apache.poi.ss.usermodel.*;
import com.centralizesys.exception.LegacyImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Service
public class LegacyFinancialImportService {

    private static final Logger log = LoggerFactory.getLogger(LegacyFinancialImportService.class);
    private static final double TOLERANCE = 0.05;
    private static final String LOG_FILE_PATH = "data/import_errors.log";

    // Headers
    private static final String HDR_DIA = "DÍA";
    private static final String HDR_CLIENTE = "CLIENTE";
    private static final String HDR_ART = "ART";
    private static final String HDR_DESC = "DESCRIPCIÓN";
    private static final String HDR_CANT = "CANT";
    private static final String HDR_PRECIO = "P. UNITARIO";
    private static final String HDR_COSTO = "COSTO";
    private static final String HDR_TOTAL = "TOTAL VENTA";
    private static final String HDR_PAGO = "MODO DE PAGO";

    private final AuditoriaService auditoriaService;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final BackupService backupService;

    public LegacyFinancialImportService(AuditoriaService auditoriaService,
                                        NamedParameterJdbcTemplate namedJdbcTemplate,
                                        BackupService backupService) {
        this.auditoriaService = auditoriaService;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.backupService = backupService;
    }

    /**
     * Main Entry Point for Legacy Import.
     * Orchestrates the resilience checkpoint and workbook processing.
     */
    @Transactional
    public String importLegacyFile(String filePath) {
        // 0. Resilience Checkpoint
        // TODO: SPRING_SECURITY_MIGRATION - [Context: One-time system import, so UserID 0 is acceptable]
        String checkpointFile = backupService.createCheckpoint("legacy_import", 0L);
        log.info("Created resilience checkpoint: {}", checkpointFile);

        log.info("Starting Legacy Import from: {}", filePath);

        List<String> errorLog = new ArrayList<>();
        int importedSales = 0;
        int skippedRows = 0;

        try (Workbook workbook = WorkbookFactory.create(new File(filePath))) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter();
            ParsingContext parsingCtx = new ParsingContext(formatter, evaluator);

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (shouldSkipSheet(sheet))
                    continue;

                log.info("Processing Sheet: {}", sheet.getSheetName());

                ImportStats stats = processSheet(sheet, parsingCtx, errorLog);
                importedSales += stats.importedSales;
                skippedRows += stats.skippedRows;
            }

            writeErrorLog(errorLog);
            String status = String.format(
                    "Import Complete. Imported Sales: %d. Skipped/Error Rows: %d. See 'import_errors.log'.",
                    importedSales, skippedRows);
            auditoriaService.registrarAccion(0L, "IMPORT_LEGACY_FIN", status);
            return status;

        } catch (Exception e) {
            throw new LegacyImportException("Import Failed: " + e.getMessage(), e);
        }
    }

    private boolean shouldSkipSheet(Sheet sheet) {
        String sheetName = sheet.getSheetName().toUpperCase();
        return sheetName.contains("RESUMEN") || sheetName.contains("TOTAL");
    }

    /**
     * Processes a single sheet entirely, handling header detection and row
     * iteration.
     */
    private ImportStats processSheet(Sheet sheet, ParsingContext parsingCtx, List<String> errorLog) {
        String sheetName = sheet.getSheetName();
        ImportStats stats = new ImportStats();

        // 1. Detect Year
        int year = detectYear(sheet, parsingCtx);

        // 2. Find Headers
        Map<String, Integer> colMap = detectHeaders(sheet, parsingCtx);
        if (colMap.isEmpty()) {
            errorLog.add("Sheet '" + sheetName + "': HEADERS NOT FOUND. Skipped.");
            return stats;
        }

        // 3. Process Rows
        VentaCtx currentVenta = null;
        Integer headerRowIdx = colMap.get("_HEADER_ROW_INDEX");
        int startRow = headerRowIdx + 1;

        SheetContext sheetCtx = new SheetContext(sheetName, year, colMap);

        for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                continue;

            try {
                // Determine if this row starts a new sale, continues one, or is skipped
                currentVenta = processRow(row, currentVenta, sheetCtx, parsingCtx, errorLog, stats);
            } catch (Exception e) {
                errorLog.add(
                        String.format("Row %d Sheet '%s': Unexpected Error: %s", r + 1, sheetName, e.getMessage()));
                stats.skippedRows++;
            }
        }

        // Flush last sale
        if (currentVenta != null) {
            persistSale(currentVenta);
            stats.importedSales++;
        }

        return stats;
    }

    /**
     * Analyzes a single row to extract sale context or item details.
     * Manages the "current sale" state.
     */
    private VentaCtx processRow(Row row,
                                VentaCtx currentVenta,
                                SheetContext sheetCtx,
                                ParsingContext parsingCtx,
                                List<String> errorLog,
                                ImportStats stats) {

        String client = getStr(row, sheetCtx.colMap.get(HDR_CLIENTE), parsingCtx);
        String art = getStr(row, sheetCtx.colMap.get(HDR_ART), parsingCtx);
        String desc = getStr(row, sheetCtx.colMap.get(HDR_DESC), parsingCtx);

        if (client.isEmpty() && art.isEmpty() && desc.isEmpty())
            return currentVenta; // Skip empty rows

        // New Sale Start
        if (!client.isEmpty()) {
            if (isInvalidClient(client)) {
                errorLog.add(String.format("Row %d Sheet '%s': INVALID CLIENT NAME '%s'. Skipped Sale Group.",
                        row.getRowNum() + 1, sheetCtx.sheetName, client));
                return null; // Reset current sale
            }

            // Persistence: Flush previous sale
            if (currentVenta != null) {
                persistSale(currentVenta);
                stats.importedSales++;
            }

            // Start new sale context
            currentVenta = new VentaCtx();
            currentVenta.clientName = client;
            currentVenta.sheetName = sheetCtx.sheetName;
            currentVenta.rowNum = row.getRowNum() + 1;
            String dayStr = getStr(row, sheetCtx.colMap.get(HDR_DIA), parsingCtx);
            currentVenta.date = parseDate(sheetCtx.year, sheetCtx.sheetName, dayStr);

            // Payment Method Parsing
            String payStr = getStr(row, sheetCtx.colMap.get(HDR_PAGO), parsingCtx);
            currentVenta.paymentAcronym = parsePaymentMethod(payStr, errorLog, row.getRowNum() + 1, sheetCtx.sheetName);
        }

        // Item Parsing
        if (currentVenta == null) {
            errorLog.add(String.format("Row %d Sheet '%s': ORPHAN ROW (No Client Context). Skipped.",
                    row.getRowNum() + 1, sheetCtx.sheetName));
            stats.skippedRows++;
            return null;
        }

        if (!art.isEmpty() || !desc.isEmpty()) {
            ItemCtx item = parseItem(row, sheetCtx, parsingCtx, errorLog, row.getRowNum() + 1);
            if (item != null) {
                currentVenta.items.add(item);
            } else {
                stats.skippedRows++;
            }
        }

        return currentVenta;
    }

    // TODO: Check with client if these kinds of client names are valid for legacy import or not
    private boolean isInvalidClient(String client) {
        return client.length() < 3 || client.contains("XXX");
    }

    private String parsePaymentMethod(String raw, List<String> errs, int r, String sheet) {
        if (raw == null)
            return "E";
        String val = raw.trim().toUpperCase();

        if (val.isEmpty() || val.equals("-") || val.equals("_")) {
            return "E";
        }

        if (val.equals("TBMCLO") || val.equals("TBORO") || val.equals("TDORO")) {
            errs.add(String.format("Row %d %s: WARN - Legacy Payment Type '%s' mapped to 'E'.", r, sheet, val));
            return "E";
        }

        return val;
    }

    private Map<String, Integer> detectHeaders(Sheet sheet, ParsingContext ctx) {
        Map<String, Integer> map = new HashMap<>();
        Row headerRow = findHeaderRow(sheet, ctx);

        if (headerRow == null)
            return map;

        for (Cell c : headerRow) {
            String val = getStr(headerRow, c.getColumnIndex(), ctx).toUpperCase();
            mapHeaderColumn(map, val, c.getColumnIndex());
        }

        // Fallback or explicit check for Payment Method if not found via headers
        map.computeIfAbsent(HDR_PAGO, k -> 15);

        map.put("_HEADER_ROW_INDEX", headerRow.getRowNum());
        return map;
    }

    private Row findHeaderRow(Sheet sheet, ParsingContext ctx) {
        for (int r = 0; r <= 10; r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                continue;

            for (Cell c : row) {
                String val = getStr(row, c.getColumnIndex(), ctx).toUpperCase();
                if (val.contains(HDR_CLIENTE))
                    return row;
            }
        }
        return null;
    }

    private void mapHeaderColumn(Map<String, Integer> map, String val, int colIndex) {
        if (val.contains(HDR_CLIENTE))
            map.put(HDR_CLIENTE, colIndex);
        else if (val.contains("ART"))
            map.put(HDR_ART, colIndex);
        else if (val.contains("DESCRIP"))
            map.put(HDR_DESC, colIndex);
        else if (val.contains("CANT"))
            map.put(HDR_CANT, colIndex);
        else if (val.contains("UNITARIO") || val.contains("PRECIO"))
            map.put(HDR_PRECIO, colIndex);
        else if (val.contains(HDR_COSTO))
            map.put(HDR_COSTO, colIndex);
        else if (val.contains("TOTAL") && val.contains("VENTA"))
            map.put(HDR_TOTAL, colIndex);
        else if (val.contains("DÍA") || val.contains("DIA"))
            map.put(HDR_DIA, colIndex);
        else if (val.contains("MODO") || val.contains("PAGO"))
            map.put(HDR_PAGO, colIndex);
    }

    private ItemCtx parseItem(Row row, SheetContext sheetCtx, ParsingContext ctx, List<String> errs, int rNum) {
        ItemCtx item = new ItemCtx();
        item.art = getStr(row, sheetCtx.colMap.get(HDR_ART), ctx);
        item.desc = getStr(row, sheetCtx.colMap.get(HDR_DESC), ctx);

        if (item.desc.length() < 2) {
            errs.add(String.format("Row %d %s: INVALID DESCRIPTION (Too short). Failed.", rNum, sheetCtx.sheetName));
            return null;
        }

        item.costo = validateDouble(row, sheetCtx.colMap.get(HDR_COSTO), ctx.evaluator, rNum, sheetCtx.sheetName,
                HDR_COSTO, errs);
        if (item.costo == null)
            return null;

        item.cant = validateInteger(row, sheetCtx.colMap.get(HDR_CANT), ctx.evaluator, rNum, sheetCtx.sheetName,
                HDR_CANT, errs);
        if (item.cant == null)
            return null;

        item.price = getNum(row, sheetCtx.colMap.get(HDR_PRECIO), ctx.evaluator);
        item.lineTotal = getNum(row, sheetCtx.colMap.get(HDR_TOTAL), ctx.evaluator);

        reconcilePrices(item, errs, rNum, sheetCtx.sheetName);

        return (item.price == null) ? null : item;
    }

    private Double validateDouble(Row row, Integer colIdx, FormulaEvaluator e, int rNum, String sheet, String fieldName,
                                  List<String> errs) {
        Double val = getNum(row, colIdx, e);
        if (val == null || val <= 0) {
            errs.add(String.format("Row %d %s: Invalid %s (Empty, Text, or <= 0). Failed.", rNum, sheet, fieldName));
            return null;
        }
        return val;
    }

    private Integer validateInteger(Row row, Integer colIdx, FormulaEvaluator e, int rNum, String sheet,
                                    String fieldName,
                                    List<String> errs) {
        Double val = getNum(row, colIdx, e);
        if (val == null || val <= 0) {
            errs.add(String.format("Row %d %s: Invalid %s (Empty, Text, or <= 0). Failed.", rNum, sheet, fieldName));
            return null;
        }
        return val.intValue();
    }

    private void reconcilePrices(ItemCtx item, List<String> errs, int rNum, String sheet) {
        if (item.price == null) {
            if (item.lineTotal != null && item.cant != 0) {
                item.price = item.lineTotal / item.cant;
                errs.add(String.format("Row %d %s: WARN - Recovered Price from Total.", rNum, sheet));
            } else {
                errs.add(String.format("Row %d %s: Missing PRICE. Failed to extract from total and quantity.", rNum,
                        sheet));
                return;
            }
        }

        if (item.lineTotal == null) {
            item.lineTotal = item.price * item.cant;
            errs.add(String.format("Row %d %s: WARN - Recovered Total from Price.", rNum, sheet));
        }

        double calc = item.price * item.cant;
        if (Math.abs(calc - item.lineTotal) > TOLERANCE) {
            errs.add(String.format("Row %d %s: WARN - Math Mismatch (Calc: %.2f, Excel: %.2f). Using Excel Total.",
                    rNum, sheet, calc, item.lineTotal));
        }
    }

    private void persistSale(VentaCtx v) {
        if (v.items.isEmpty())
            return;

        Double finalTotal = v.items.stream().mapToDouble(i -> i.lineTotal).sum();

        // 1. Insert Venta
        // TODO: when refactoring project to include Spring Security, for this particular case, no userID is needed. 0 is fine
        //  because this is a one time import to start up the store's history in the new DB
        String sqlVenta = "INSERT INTO ventas (fecha, cliente_nombre, total_venta, usuario_id) VALUES (:fecha, :cliente, :total, 0)";
        MapSqlParameterSource pV = new MapSqlParameterSource()
                .addValue("fecha", v.date.toString())
                .addValue("cliente", v.clientName)
                .addValue("total", finalTotal);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update(sqlVenta, pV, keyHolder);
        Number ventaId = keyHolder.getKey();

        if (ventaId == null) {
            log.error("Failed to insert sale for client {}. No ID returned.", v.clientName);
            return;
        }

        // 2. Insert Details
        insertDetails(ventaId.longValue(), v.items);

        // 3. Payment
        insertPayment(ventaId.longValue(), finalTotal, v.paymentAcronym);
    }

    private void insertDetails(Long ventaId, List<ItemCtx> items) {
        String sqlDet = """
                INSERT INTO detalles_venta (venta_id, producto_id, codigo_snapshot, descripcion_snapshot,
                                            costo_snapshot, cantidad, precio_lista, precio_unitario, subtotal)
                VALUES (:vid, NULL, :cod, :desc, :costo, :cant, :prec, :prec, :sub)
                """;

        List<MapSqlParameterSource> batch = new ArrayList<>();
        for (ItemCtx i : items) {
            batch.add(new MapSqlParameterSource()
                    .addValue("vid", ventaId)
                    .addValue("cod", i.art.isEmpty() ? "SIN CODIGO" : i.art)
                    .addValue("desc", i.desc)
                    .addValue("costo", i.costo)
                    .addValue("cant", i.cant)
                    .addValue("prec", i.price)
                    .addValue("sub", i.lineTotal));
        }
        namedJdbcTemplate.batchUpdate(sqlDet, batch.toArray(new MapSqlParameterSource[0]));
    }

    private void insertPayment(Long ventaId, Double amount, String acronym) {
        String sqlPago = "INSERT INTO pagos_venta (venta_id, metodo_pago_id, monto) VALUES (:vid, (SELECT id FROM metodos_pago WHERE acronimo = :acr), :monto)";
        MapSqlParameterSource pP = new MapSqlParameterSource()
                .addValue("vid", ventaId)
                .addValue("acr", acronym)
                .addValue("monto", amount);
        namedJdbcTemplate.update(sqlPago, pP);
    }

    private int detectYear(Sheet sheet, ParsingContext ctx) {
        for (int i = 0; i <= 1; i++) {
            Row r = sheet.getRow(i);
            if (r == null)
                continue;
            for (Cell c : r) {
                try {
                    String val = ctx.formatter.formatCellValue(c, ctx.evaluator);
                    if (val.matches("20\\d{2}"))
                        return Integer.parseInt(val);
                } catch (Exception ignored) {
                    /* Safe to ignore */ }
            }
        }
        return 2025;
    }

    // --- Helpers and Utilities ---

    private void writeErrorLog(List<String> logs) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE_PATH, StandardCharsets.UTF_8))) {
            for (String l : logs)
                pw.println(l);
        } catch (Exception e) {
            log.error("Failed to write import log", e);
        }
    }

    private LocalDate parseDate(int year, String monthName, String dayStr) {
        try {
            int day = Integer.parseInt(dayStr.replaceAll("\\D", ""));
            int month = parseMonth(monthName);
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return LocalDate.of(year, parseMonth(monthName), 1);
        }
    }

    private int parseMonth(String name) {
        if (name == null)
            return 1;
        name = name.toUpperCase();
        if (name.contains("ENE"))
            return 1;
        if (name.contains("FEB"))
            return 2;
        if (name.contains("MAR"))
            return 3;
        if (name.contains("ABR"))
            return 4;
        if (name.contains("MAY"))
            return 5;
        if (name.contains("JUN"))
            return 6;
        if (name.contains("JUL"))
            return 7;
        if (name.contains("AGO"))
            return 8;
        if (name.contains("SET") || name.contains("SEP"))
            return 9;
        if (name.contains("OCT"))
            return 10;
        if (name.contains("NOV"))
            return 11;
        if (name.contains("DIC"))
            return 12;
        return 1;
    }

    private String getStr(Row r, Integer idx, ParsingContext ctx) {
        if (idx == null)
            return "";
        Cell c = r.getCell(idx);
        if (c == null)
            return "";
        try {
            return ctx.formatter != null ? ctx.formatter.formatCellValue(c, ctx.evaluator).trim()
                    : c.getStringCellValue().trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private Double getNum(Row r, Integer idx, FormulaEvaluator e) {
        if (idx == null)
            return null;
        Cell c = r.getCell(idx);
        if (c == null)
            return null;
        try {
            return c.getNumericCellValue();
        } catch (Exception ex) {
            try {
                if (c.getCellType() == CellType.FORMULA) {
                    return e.evaluate(c).getNumberValue();
                }
            } catch (Exception ex2) {
                try {
                    String s = c.getStringCellValue().replace("$", "").replace(",", "");
                    return Double.parseDouble(s);
                } catch (Exception ex3) {
                    return null;
                }
            }
        }
        return null;
    }

    // Inner State Classes
    private static class VentaCtx {
        String clientName;
        String sheetName;
        int rowNum;
        LocalDate date;
        String paymentAcronym = "E"; // Default to Cash
        List<ItemCtx> items = new ArrayList<>();
    }

    private static class ItemCtx {
        String art;
        String desc;
        Integer cant;
        Double price;
        Double costo;
        Double lineTotal;
    }

    private static class ImportStats {
        int importedSales = 0;
        int skippedRows = 0;
    }

    private static class ParsingContext {
        final DataFormatter formatter;
        final FormulaEvaluator evaluator;

        ParsingContext(DataFormatter f, FormulaEvaluator e) {
            this.formatter = f;
            this.evaluator = e;
        }
    }

    private static class SheetContext {
        final String sheetName;
        final int year;
        final Map<String, Integer> colMap;

        SheetContext(String n, int y, Map<String, Integer> c) {
            this.sheetName = n;
            this.year = y;
            this.colMap = c;
        }
    }
}
