package com.centralizesys.service;

import com.centralizesys.exception.InfrastructureException;
import com.centralizesys.model.dto.BackupFileDTO;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.repository.AuditoriaRepository;
import com.centralizesys.repository.CompraRepository;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.VentaRepository;
import com.centralizesys.security.SecurityUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter FMT_FILE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private static final String EXT_XLSX = ".xlsx";
    private static final String PREFIX_FILE = "centralizesys_";

    private static final Long RETENTION_DAYS_DAILY = 60L;
    private static final String FECHA = "Fecha";

    private final JdbcTemplate jdbcTemplate;
    private final AuditoriaService auditoriaService;
    private final ProductRepository productRepository;
    private final VentaRepository ventaRepository;
    private final CompraRepository compraRepository;
    private final DeudoresRepository deudoresRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final BackupPathStrategy pathStrategy;

    public BackupService(JdbcTemplate jdbcTemplate,
                         AuditoriaService auditoriaService,
                         ProductRepository productRepository,
                         VentaRepository ventaRepository,
                         CompraRepository compraRepository,
                         DeudoresRepository deudoresRepository,
                         AuditoriaRepository auditoriaRepository,
                         BackupPathStrategy pathStrategy) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditoriaService = auditoriaService;
        this.productRepository = productRepository;
        this.ventaRepository = ventaRepository;
        this.compraRepository = compraRepository;
        this.deudoresRepository = deudoresRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.pathStrategy = pathStrategy;
    }


    public enum BackupType {
        DAILY,
        MANUAL;

        public String getDirectory(BackupPathStrategy strategy) {
            return switch (this) {
                case DAILY -> strategy.getDailyDir();
                case MANUAL -> strategy.getManualDir();
            };
        }
    }

    public void performBackup(BackupType type) {
        performBackup(type, SecurityUtils.getAuthenticatedUserId());
    }

    public void performBackup(BackupType type, Long userId) {
        // Fallback or System User if null passed (though unlikely if called correctly)
        if (userId == null)
            userId = 0L;

        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        String timestamp = now.format(FMT_FILE);
        String prefix = type == BackupType.DAILY ? "daily_" : "manual_";

        String excelFileName = PREFIX_FILE + prefix + timestamp + EXT_XLSX;

        Path dirPath = Paths.get(type.getDirectory(pathStrategy));

        try {
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            Path fullExcelPath = dirPath.resolve(excelFileName).toAbsolutePath();

            // 1. PostgreSQL DB Backup (Deferred)
            log.warn("Database backup is deferred to the cloud migration phase (pg_dump implementation pending).");

            // 2. Excel Export
            exportToExcel(fullExcelPath.toString());

            // 3. Audit Success
            String message = String.format("Respaldo %s (Excel) completo.", type.name());
            auditoriaService.registrarAccion(userId, "BACKUP_EXITOSO", message);

        } catch (IOException | DataAccessException | IllegalArgumentException e) {
            auditoriaService.registrarAccion(userId, "BACKUP_FALLIDO", "Error: " + e.getMessage());
            throw new InfrastructureException("Backup failed: " + e.getMessage(), e);
        }
    }



    private void exportToExcel(String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {

            // --- STYLES ---
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle oddRowStyle = workbook.createCellStyle();
            oddRowStyle.setWrapText(true);

            CellStyle evenRowStyle = workbook.createCellStyle();
            evenRowStyle.setWrapText(true);
            evenRowStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            evenRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));
            currencyStyle.setWrapText(true);

            CellStyle currencyEvenStyle = workbook.createCellStyle();
            currencyEvenStyle.cloneStyleFrom(currencyStyle);
            currencyEvenStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            currencyEvenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // --- SHEETS ---
            populateProductsSheet(workbook, headerStyle, oddRowStyle, evenRowStyle, currencyStyle, currencyEvenStyle);
            populateSalesSheet(workbook, headerStyle, oddRowStyle, evenRowStyle, currencyStyle, currencyEvenStyle);
            populatePurchasesSheet(workbook, headerStyle, oddRowStyle, evenRowStyle, currencyStyle, currencyEvenStyle);
            populateDebtorsSheet(workbook, headerStyle, oddRowStyle, evenRowStyle, currencyStyle, currencyEvenStyle);
            populateStockSheet(workbook, headerStyle, oddRowStyle, evenRowStyle);
            populateAuditSheet(workbook, headerStyle, oddRowStyle, evenRowStyle);

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        }
    }

    private void createHeader(Sheet sheet, String[] headers, CellStyle style) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void createNumericCell(Row row, int col, Double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : 0.0);
        cell.setCellStyle(style);
    }

    private void createTextCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.toString());
        }
        cell.setCellStyle(style);
    }

    private String formatIsoDateForExcel(LocalDateTime dateTime) {
        if (dateTime == null)
            return "";
        try {
            return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd -- HH:mm:ss"));
        } catch (Exception e) {
            return dateTime.toString(); // Fallback
        }
    }

    // --- File Management APIs ---

    public List<com.centralizesys.model.dto.BackupFileDTO> listBackups() {
        List<BackupFileDTO> list = new ArrayList<>();
        list.addAll(scanDirectory(BackupType.DAILY));
        list.addAll(scanDirectory(BackupType.MANUAL));
        // Sort DESC
        list.sort((a, b) -> b.date().compareTo(a.date()));
        return list;
    }

    public File getBackupFile(String filename) throws FileNotFoundException {
        // Security: Prevent path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename");
        }

        // Search in Daily
        Path dailyPath = Paths.get(BackupType.DAILY.getDirectory(pathStrategy), filename);
        if (Files.exists(dailyPath))
            return dailyPath.toFile();

        // Search in Manual
        Path manualPath = Paths.get(BackupType.MANUAL.getDirectory(pathStrategy), filename);
        if (Files.exists(manualPath))
            return manualPath.toFile();

        throw new FileNotFoundException("File not found: " + filename);
    }

    private List<BackupFileDTO> scanDirectory(BackupType type) {
        Path dirPath = Paths.get(type.getDirectory(pathStrategy));
        if (!Files.exists(dirPath))
            return Collections.emptyList();

        try (Stream<Path> stream = Files.list(dirPath)) {
            return stream
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        // Returns TRUE or FALSE based on filename extension. If true, path object
                        // is included in the resulting stream.
                        return name.endsWith(EXT_XLSX);
                    })
                    .map(path -> mapPathToDto(path, type))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            log.error("Failed to scan directory: {}", type.name(), e);
            return Collections.emptyList();
        }
    }

    // Sonar: Extract complex lambda body
    private BackupFileDTO mapPathToDto(Path path, BackupType type) {
        String name = path.getFileName().toString();
        LocalDateTime date = parseDateFromFilename(name);
        if (date == null) {
            try {
                date = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()),
                        ZoneId.systemDefault());
            } catch (IOException e) {
                date = LocalDateTime.now(ZoneId.systemDefault()); // Fallback
            }
        }
        try {
            return new BackupFileDTO(
                    name,
                    date,
                    Files.size(path),
                    type.name() + "_EXCEL");
        } catch (IOException e) {
            return null;
        }
    }

    private LocalDateTime parseDateFromFilename(String name) {
        try {
            // Extract YYYYMMDD_HHmm
            int idx = name.lastIndexOf('_');
            if (idx == -1)
                return null;
            String timePart = name.substring(idx + 1, idx + 5);

            String temp = name.substring(0, idx);
            int idx2 = temp.lastIndexOf('_');
            if (idx2 == -1)
                return null;
            String datePart = temp.substring(idx2 + 1);

            return LocalDateTime.parse(datePart + "_" + timePart, FMT_FILE);
        } catch (Exception e) {
            return null;
        }
    }

    // --- Retention Logic (GFS Adapted) ---
    public void cleanupOldBackups() {
        Path dirPath = Paths.get(pathStrategy.getDailyDir());
        if (!Files.exists(dirPath))
            return;

        List<File> files;
        try (Stream<Path> stream = Files.list(dirPath)) {
            files = stream
                    .filter(p -> p.toString().endsWith(EXT_XLSX))
                    .map(Path::toFile)
                    .sorted(Comparator.comparingLong(File::lastModified))
                    .toList();
        } catch (IOException e) {
            log.warn("Cleanup failed to list files", e);
            return;
        }

        processRetentionPolicy(files);
    }

    private void processRetentionPolicy(List<File> files) {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        Set<Integer> yearsKept = new HashSet<>();

        // Process files older than retention period
        List<File> filesToDelete = files.stream()
                .filter(f -> {
                    LocalDateTime fileDate = parseDateFromFilename(f.getName());
                    return fileDate != null && ChronoUnit.DAYS.between(fileDate.atZone(ZoneId.systemDefault()), now.atZone(ZoneId.systemDefault())) > RETENTION_DAYS_DAILY;
                })

                .filter(f -> shouldDeleteArchivedFile(Objects.requireNonNull(parseDateFromFilename(f.getName())), now,
                        yearsKept))
                .toList();

        Long deletedCount = 0L;
        for (File f : filesToDelete) {
            try {
                Files.delete(f.toPath());
                deletedCount++;
            } catch (IOException e) {
                log.warn("Failed to delete old backup: {}", f.getName());
            }
        }

        if (deletedCount > 0) {
            auditoriaService.registrarAccion(0L, "BACKUP_CLEANUP", "Deleted " + deletedCount + " old backups.");
        }
    }

    private boolean shouldDeleteArchivedFile(LocalDateTime fileDate, LocalDateTime now, Set<Integer> yearsKept) {
        int fileYear = fileDate.getYear();
        int currentYear = now.getYear();

        if (fileYear < (currentYear - 1)) {
            if (!yearsKept.contains(fileYear)) {
                yearsKept.add(fileYear); // Keep First
                return false;
            }
            return true; // Delete subsequent
        } else {
            // BI-MONTHLY (Current or Previous Year)
            int dom = fileDate.getDayOfMonth();

            // RETENTION STRATEGY (Bi-Monthly + First of Year):
            // 1. Always keep the first backup encountered for the year (Oldest).
            // 2. Otherwise, keep only 1st and 15th of the month.
            if (!yearsKept.contains(fileYear)) {
                yearsKept.add(fileYear); // Mark year as having a "Start of Year" candidate kept
                return false; // DO NOT DELETE (Keep as 'First of Year' replacement)
            }

            // Normal Bi-Monthly retention for the rest
            return dom != 1 && dom != 15;
        }
    }


    public void removeMidDayBackup() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        String datePart = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String midDaySuffix = "_" + datePart + "_1300";

        Path dir = Paths.get(pathStrategy.getDailyDir());
        if (!Files.exists(dir))
            return;

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().contains(midDaySuffix))
                    .forEach(this::deleteMidDayBackup);
        } catch (IOException e) {
            log.warn("Mid-day cleanup failed", e);
        }
    }

    private void deleteMidDayBackup(Path p) {
        try {
            Files.delete(p);
            auditoriaService.registrarAccion(0L, "BACKUP_CLEANUP",
                    "Removed Mid-Day Backup: " + p.getFileName());
        } catch (IOException e) {
            log.warn("Failed to delete mid-day backup: {}", p);
        }
    }
    // --- Helper Methods for Excel Export ---

    private void populateProductsSheet(Workbook workbook, CellStyle headerStyle, CellStyle oddStyle,
                                       CellStyle evenStyle, CellStyle currOdd, CellStyle currEven) {
        Sheet sheet = workbook.createSheet("Productos");
        String[] headers = { "ID", "Código", "Descripción", "Costo", "Precio Mayorista", "Precio Minorista", "Stock" };
        createHeader(sheet, headers, headerStyle);

        sheet.setColumnWidth(0, 15 * 256); // ID
        sheet.setColumnWidth(1, 20 * 256); // Codigo
        sheet.setColumnWidth(2, 60 * 256); // Descripcion (Wide for wrapping)
        sheet.setColumnWidth(3, 15 * 256); // Costo
        sheet.setColumnWidth(4, 15 * 256); // Mayorista
        sheet.setColumnWidth(5, 15 * 256); // Minorista
        sheet.setColumnWidth(6, 12 * 256); // Stock

        List<Product> products = productRepository.findAll();
        int rowNum = 1;
        for (Product p : products) {
            Row row = sheet.createRow(rowNum++);
            boolean isEven = rowNum % 2 != 0; // rowNum was incremented, so odd rowNum variable means even data row
            CellStyle base = isEven ? evenStyle : oddStyle;
            CellStyle curr = isEven ? currEven : currOdd;

            createTextCell(row, 0, p.getId(), base);
            createTextCell(row, 1, p.getCodigo(), base);
            createTextCell(row, 2, p.getDescripcion(), base);
            createNumericCell(row, 3, p.getPrecioCosto(), curr);
            createNumericCell(row, 4, p.getPrecioMayorista(), curr);
            createNumericCell(row, 5, p.getPrecioMinorista(), curr);
            createTextCell(row, 6, p.getCantidadStock(), base);
        }
    }

    private void populateSalesSheet(Workbook workbook, CellStyle headerStyle, CellStyle oddStyle, CellStyle evenStyle,
                                    CellStyle currOdd, CellStyle currEven) {
        Sheet sheet = workbook.createSheet("Ventas");
        String[] headers = { "ID", FECHA, "Cliente", "Tipo Venta", "Desc. Global", "Total", "Usuario ID" };
        createHeader(sheet, headers, headerStyle);

        sheet.setColumnWidth(0, 15 * 256); // ID
        sheet.setColumnWidth(1, 25 * 256); // Fecha
        sheet.setColumnWidth(2, 40 * 256); // Cliente
        sheet.setColumnWidth(3, 15 * 256); // Tipo Venta
        sheet.setColumnWidth(4, 15 * 256); // Desc. Global
        sheet.setColumnWidth(5, 15 * 256); // Total
        sheet.setColumnWidth(6, 12 * 256); // Usuario ID

        List<Venta> sales = ventaRepository.findAll();
        int rowNum = 1;
        for (Venta v : sales) {
            Row row = sheet.createRow(rowNum++);
            boolean isEven = rowNum % 2 != 0;
            CellStyle base = isEven ? evenStyle : oddStyle;
            CellStyle curr = isEven ? currEven : currOdd;

            createTextCell(row, 0, v.getId(), base);
            createTextCell(row, 1, formatIsoDateForExcel(v.getFecha()), base);
            createTextCell(row, 2, v.getClienteNombre(), base);
            createTextCell(row, 3, v.getTipoVenta(), base);
            createNumericCell(row, 4, v.getDescuentoGlobal(), curr);
            createNumericCell(row, 5, v.getTotalVenta(), curr);
            createTextCell(row, 6, v.getUsuarioId(), base);
        }
    }

    private void populatePurchasesSheet(Workbook workbook, CellStyle headerStyle, CellStyle oddStyle,
                                        CellStyle evenStyle, CellStyle currOdd, CellStyle currEven) {
        Sheet sheet = workbook.createSheet("Compras");
        createHeader(sheet, new String[] { "ID", FECHA, "Proveedor", "Comprobante", "Total" }, headerStyle);

        sheet.setColumnWidth(0, 15 * 256); // ID
        sheet.setColumnWidth(1, 25 * 256); // Fecha
        sheet.setColumnWidth(2, 40 * 256); // Proveedor
        sheet.setColumnWidth(3, 20 * 256); // Comprobante
        sheet.setColumnWidth(4, 15 * 256); // Total

        int rowNum = 1;
        var compras = compraRepository.findAll();
        for (var c : compras) {
            Row row = sheet.createRow(rowNum++);
            boolean isEven = rowNum % 2 != 0;
            CellStyle base = isEven ? evenStyle : oddStyle;
            CellStyle curr = isEven ? currEven : currOdd;

            createTextCell(row, 0, c.getId(), base);
            createTextCell(row, 1, formatIsoDateForExcel(c.getFecha()), base);
            createTextCell(row, 2, c.getProveedor(), base);
            createTextCell(row, 3, c.getNroComprobante(), base);
            createNumericCell(row, 4, c.getTotalCompra(), curr);
        }
    }

    private void populateDebtorsSheet(Workbook workbook, CellStyle headerStyle, CellStyle oddStyle, CellStyle evenStyle,
                                      CellStyle currOdd, CellStyle currEven) {
        Sheet sheet = workbook.createSheet("Deudores");
        createHeader(sheet, new String[] { "ID", "Venta ID", "Cliente", "Monto Deuda", FECHA, "Estado" }, headerStyle);

        sheet.setColumnWidth(0, 15 * 256); // ID
        sheet.setColumnWidth(1, 15 * 256); // Venta ID
        sheet.setColumnWidth(2, 40 * 256); // Cliente
        sheet.setColumnWidth(3, 15 * 256); // Monto
        sheet.setColumnWidth(4, 25 * 256); // Fecha
        sheet.setColumnWidth(5, 15 * 256); // Estado

        int rowNum = 1;
        var deudores = deudoresRepository.findAll();
        for (var d : deudores) {
            Row row = sheet.createRow(rowNum++);
            boolean isEven = rowNum % 2 != 0;
            CellStyle base = isEven ? evenStyle : oddStyle;
            CellStyle curr = isEven ? currEven : currOdd;

            createTextCell(row, 0, d.getId(), base);
            createTextCell(row, 1, d.getVentaId(), base);
            createTextCell(row, 2, d.getClienteNombre(), base);
            createNumericCell(row, 3, d.getMontoDeuda(), curr);
            createTextCell(row, 4, formatIsoDateForExcel(d.getFechaDeuda()), base);
            createTextCell(row, 5, d.getEstado(), base);
        }
    }

    private void populateStockSheet(Workbook workbook, CellStyle headerStyle, CellStyle oddStyle, CellStyle evenStyle) {
        Sheet sheet = workbook.createSheet("Stock_Ubicacion");
        createHeader(sheet, new String[] { "Producto ID", "Código", "Descripción", "Cantidad", "Ubicación" },
                headerStyle);

        sheet.setColumnWidth(0, 15 * 256); // ID
        sheet.setColumnWidth(1, 20 * 256); // Codigo
        sheet.setColumnWidth(2, 60 * 256); // Descripcion
        sheet.setColumnWidth(3, 15 * 256); // Cantidad
        sheet.setColumnWidth(4, 30 * 256); // Ubicacion

        String sqlStock = """
                    SELECT p.id, p.codigo, p.descripcion, s.cantidad, u.nombre
                    FROM stock_por_ubicacion s
                    JOIN productos p ON s.producto_id = p.id
                    JOIN ubicaciones u ON s.ubicacion_id = u.id
                """;
        int rowNum = 1;
        var stockRows = jdbcTemplate.queryForList(sqlStock);
        for (var map : stockRows) {
            Row row = sheet.createRow(rowNum++);
            boolean isEven = rowNum % 2 != 0;
            CellStyle base = isEven ? evenStyle : oddStyle;

            createTextCell(row, 0, map.get("id"), base);
            createTextCell(row, 1, map.get("codigo"), base);
            createTextCell(row, 2, map.get("descripcion"), base);
            createTextCell(row, 3, map.get("cantidad"), base);
            createTextCell(row, 4, map.get("nombre"), base);
        }
    }

    private void populateAuditSheet(Workbook workbook, CellStyle headerStyle, CellStyle oddStyle, CellStyle evenStyle) {
        Sheet sheet = workbook.createSheet("Auditoria");
        createHeader(sheet, new String[] { "ID", FECHA, "Acción", "Detalles", "Usuario ID" }, headerStyle);

        sheet.setColumnWidth(0, 15 * 256); // ID
        sheet.setColumnWidth(1, 30 * 256); // Fecha (Formatted YYYY-MM-DD -- HH:mm:ss)
        sheet.setColumnWidth(2, 25 * 256); // Accion
        sheet.setColumnWidth(3, 80 * 256); // Detalles (Wide for wrapping)
        sheet.setColumnWidth(4, 15 * 256); // Usuario ID

        int rowNum = 1;
        var audits = auditoriaRepository.findAll();
        for (var a : audits) {
            Row row = sheet.createRow(rowNum++);
            boolean isEven = rowNum % 2 != 0;
            CellStyle base = isEven ? evenStyle : oddStyle;

            createTextCell(row, 0, a.getId(), base);
            createTextCell(row, 1, formatIsoDateForExcel(a.getFechaHora()), base);
            createTextCell(row, 2, a.getAccion(), base);
            createTextCell(row, 3, a.getDetalles(), base);
            createTextCell(row, 4, a.getUsuarioId() != null ? a.getUsuarioId() : 0, base);
        }
    }
}
