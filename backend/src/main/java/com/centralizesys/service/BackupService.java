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
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter FMT_FILE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    // Constants
    private static final String DIR_DAILY = "backups/daily";
    private static final String DIR_MANUAL = "backups/manual";
    private static final String DIR_CHECKPOINTS = "backups/checkpoints";
    private static final String EXT_DB = ".db";
    private static final String EXT_XLSX = ".xlsx";
    private static final String PREFIX_DB = "centralizesys_";
    private static final String RESTORE_TRIGGER_PATH = "data/centralizesys.restore";
    private static final Long RETENTION_DAYS_DAILY = 60L;
    private static final Long CHECKPOINT_TTL_MS = 12 * 60 * 60 * 1000L;
    private static final String FECHA = "Fecha";

    private final JdbcTemplate jdbcTemplate;
    private final AuditoriaService auditoriaService;
    private final ProductRepository productRepository;
    private final VentaRepository ventaRepository;
    private final CompraRepository compraRepository;
    private final DeudoresRepository deudoresRepository;
    private final AuditoriaRepository auditoriaRepository;

    public BackupService(JdbcTemplate jdbcTemplate,
                         AuditoriaService auditoriaService,
                         ProductRepository productRepository,
                         VentaRepository ventaRepository,
                         CompraRepository compraRepository,
                         DeudoresRepository deudoresRepository,
                         AuditoriaRepository auditoriaRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditoriaService = auditoriaService;
        this.productRepository = productRepository;
        this.ventaRepository = ventaRepository;
        this.compraRepository = compraRepository;
        this.deudoresRepository = deudoresRepository;
        this.auditoriaRepository = auditoriaRepository;
    }

    public enum BackupType {
        DAILY(DIR_DAILY),
        MANUAL(DIR_MANUAL);

        final String path;

        BackupType(String path) {
            this.path = path;
        }
    }

    public void performBackup(BackupType type) {
        performBackup(type, com.centralizesys.security.SecurityUtils.getAuthenticatedUserId());
    }

    public void performBackup(BackupType type, Long userId) {
        // Fallback or System User if null passed (though unlikely if called correctly)
        if (userId == null)
            userId = 0L;

        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(FMT_FILE);
        String prefix = type == BackupType.DAILY ? "daily_" : "manual_";

        String dbFileName = PREFIX_DB + prefix + timestamp + EXT_DB;
        String excelFileName = PREFIX_DB + prefix + timestamp + EXT_XLSX;

        Path dirPath = Paths.get(type.path);

        try {
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            Path fullDbPath = dirPath.resolve(dbFileName).toAbsolutePath();
            Path fullExcelPath = dirPath.resolve(excelFileName).toAbsolutePath();

            // 1. Binary Backup (SQLite specific)
            executeVacuumInto(fullDbPath);

            // 2. Excel Export
            exportToExcel(fullExcelPath.toString());

            // 3. Audit Success
            String message = String.format("Respaldo %s completo. DB: %s", type.name(), dbFileName);
            auditoriaService.registrarAccion(userId, "BACKUP_EXITOSO", message);

        } catch (IOException | DataAccessException | IllegalArgumentException e) {
            auditoriaService.registrarAccion(userId, "BACKUP_FALLIDO", "Error: " + e.getMessage());
            throw new InfrastructureException("Backup failed: " + e.getMessage(), e);
        }
    }

    // Extract dynamic SQL execution to isolated method and validate path
    private void executeVacuumInto(Path fullDbPath) {
        String pathStr = fullDbPath.toAbsolutePath().toString();

        // STRICT SECURITY: Whitelist allowed characters to prevent SQL Injection via filename
        // Allow: Alphanumeric, underscore, hyphen, dot, forward/back slash, colon, space, brackets, parens, tilde, plus
        if (!pathStr.matches("^[a-zA-Z0-9_\\-./\\\\: ()+\\[\\]~]+$")) {
            throw new IllegalArgumentException(
                    "Invalid path for backup: Path contains prohibited characters. Path: " + pathStr);
        }

        // Additional sanity check for single quotes which are dangerous in SQL string
        // literals
        if (pathStr.contains("'")) {
            throw new IllegalArgumentException("Invalid path for backup: Single quotes not allowed");
        }

        String sql = "VACUUM INTO '" + pathStr + "'";
        jdbcTemplate.execute(sql);
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

            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));

            // --- SHEET 1: PRODUCTOS ---
            Sheet sheetProd = workbook.createSheet("Productos");
            String[] headersProd = { "ID", "Código", "Descripción", "Costo", "Precio Mayorista", "Precio Minorista",
                    "Stock" };
            createHeader(sheetProd, headersProd, headerStyle);

            List<Product> products = productRepository.findAll();
            int rowNum = 1;
            for (Product p : products) {
                Row row = sheetProd.createRow(rowNum++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(p.getCodigo());
                row.createCell(2).setCellValue(p.getDescripcion());
                createNumericCell(row, 3, p.getPrecioCosto(), currencyStyle);
                createNumericCell(row, 4, p.getPrecioMayorista(), currencyStyle);
                createNumericCell(row, 5, p.getPrecioMinorista(), currencyStyle);
                row.createCell(6).setCellValue(p.getCantidadStock());
            }
            for (int i = 0; i < 3; i++)
                sheetProd.autoSizeColumn(i);

            // --- SHEET 2: VENTAS ---
            Sheet sheetVentas = workbook.createSheet("Ventas");
            String[] headersVentas = { "ID", FECHA, "Cliente", "Total" };
            createHeader(sheetVentas, headersVentas, headerStyle);

            List<Venta> sales = ventaRepository.findAll();
            rowNum = 1;
            for (Venta v : sales) {
                Row row = sheetVentas.createRow(rowNum++);
                row.createCell(0).setCellValue(v.getId());
                row.createCell(1).setCellValue(v.getFecha());
                row.createCell(2).setCellValue(v.getClienteNombre());
                createNumericCell(row, 3, v.getTotalVenta(), currencyStyle);
            }
            sheetVentas.autoSizeColumn(1);
            sheetVentas.autoSizeColumn(2);

            // --- SHEET 3: COMPRAS ---
            Sheet sheetCompras = workbook.createSheet("Compras");
            createHeader(sheetCompras, new String[] { "ID", FECHA, "Proveedor", "Comprobante", "Total" },
                    headerStyle);
            int rowC = 1;
            var compras = compraRepository.findAll();
            for (var c : compras) {
                Row row = sheetCompras.createRow(rowC++);
                row.createCell(0).setCellValue(c.getId());
                row.createCell(1).setCellValue(c.getFecha());
                row.createCell(2).setCellValue(c.getProveedor());
                row.createCell(3).setCellValue(c.getNroComprobante());
                createNumericCell(row, 4, c.getTotalCompra(), currencyStyle);
            }
            sheetCompras.autoSizeColumn(1);
            sheetCompras.autoSizeColumn(2);

            // --- SHEET 4: DEUDORES ---
            Sheet sheetDeuda = workbook.createSheet("Deudores");
            createHeader(sheetDeuda, new String[] { "ID", "Cliente", "Monto Deuda", FECHA, "Estado" }, headerStyle);
            int rowD = 1;
            var deudores = deudoresRepository.findAll();
            for (var d : deudores) {
                Row row = sheetDeuda.createRow(rowD++);
                row.createCell(0).setCellValue(d.getId());
                row.createCell(1).setCellValue(d.getClienteNombre());
                createNumericCell(row, 2, d.getMontoDeuda(), currencyStyle);
                row.createCell(3).setCellValue(d.getFechaDeuda());
                row.createCell(4).setCellValue(d.getEstado());
            }
            sheetDeuda.autoSizeColumn(1);

            // --- SHEET 5: STOCK POR UBICACION ---
            Sheet sheetStock = workbook.createSheet("Stock_Ubicacion");
            createHeader(sheetStock, new String[] { "Producto ID", "Código", "Descripción", "Cantidad", "Ubicación" },
                    headerStyle);
            // Uses JDBC directly because StockRepository might separate logic awkwardly for
            // bulk dump
            // Or use a join query here for simplicity
            String sqlStock = """
                        SELECT p.id, p.codigo, p.descripcion, s.cantidad, u.nombre
                        FROM stock_por_ubicacion s
                        JOIN productos p ON s.producto_id = p.id
                        JOIN ubicaciones u ON s.ubicacion_id = u.id
                    """;
            int rowS = 1;
            var stockRows = jdbcTemplate.queryForList(sqlStock);
            for (var map : stockRows) {
                Row row = sheetStock.createRow(rowS++);
                row.createCell(0).setCellValue(((Number) map.get("id")).longValue());
                row.createCell(1).setCellValue((String) map.get("codigo"));
                row.createCell(2).setCellValue((String) map.get("descripcion"));
                row.createCell(3).setCellValue(((Number) map.get("cantidad")).intValue());
                row.createCell(4).setCellValue((String) map.get("nombre"));
            }
            sheetStock.autoSizeColumn(1);
            sheetStock.autoSizeColumn(2);
            sheetStock.autoSizeColumn(4);

            // --- SHEET 6: AUDITORIA ---
            Sheet sheetAudit = workbook.createSheet("Auditoria");
            createHeader(sheetAudit, new String[] { "ID", FECHA, "Acción", "Detalles", "Usuario ID" }, headerStyle);
            int rowA = 1;
            // Limit to last 1000 or relevant period to avoid massive explosion?
            // Request said "comprehensive". Let's dump all (SQLite is small usually).
            var audits = auditoriaRepository.findAll();
            for (var a : audits) {
                Row row = sheetAudit.createRow(rowA++);
                row.createCell(0).setCellValue(a.getId());
                row.createCell(1).setCellValue(a.getFechaHora());
                row.createCell(2).setCellValue(a.getAccion());
                row.createCell(3).setCellValue(a.getDetalles());
                row.createCell(4).setCellValue(a.getUsuarioId() != null ? a.getUsuarioId() : 0);
            }
            sheetAudit.autoSizeColumn(1);
            sheetAudit.autoSizeColumn(2);

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
        Path dailyPath = Paths.get(BackupType.DAILY.path, filename);
        if (Files.exists(dailyPath))
            return dailyPath.toFile();

        // Search in Manual
        Path manualPath = Paths.get(BackupType.MANUAL.path, filename);
        if (Files.exists(manualPath))
            return manualPath.toFile();

        throw new FileNotFoundException("File not found: " + filename);
    }

    private List<BackupFileDTO> scanDirectory(BackupType type) {
        Path dirPath = Paths.get(type.path);
        if (!Files.exists(dirPath))
            return Collections.emptyList();

        try (Stream<Path> stream = Files.list(dirPath)) {
            return stream
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        // Returns TRUE or FALSE based on filename extension. If true, path object
                        // is included in the resulting stream.
                        return name.endsWith(EXT_DB) || name.endsWith(EXT_XLSX);
                    })
                    .map(path -> mapPathToDto(path, type))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            log.error("Failed to scan directory: {}", type.path, e);
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
                date = LocalDateTime.now(); // Fallback
            }
        }
        try {
            return new BackupFileDTO(
                    name,
                    date,
                    Files.size(path),
                    type.name() + (name.endsWith(EXT_DB) ? "_DB" : "_EXCEL"));
                    // the operation after + is an if else that checks the file extension
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
        Path dirPath = Paths.get(DIR_DAILY);
        if (!Files.exists(dirPath))
            return;

        List<File> files;
        try (Stream<Path> stream = Files.list(dirPath)) {
            files = stream
                    .filter(p -> p.toString().endsWith(EXT_DB) || p.toString().endsWith(EXT_XLSX))
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
        LocalDateTime now = LocalDateTime.now();
        Set<Integer> yearsKept = new HashSet<>();

        // Process files older than retention period
        List<File> filesToDelete = files.stream()
                .filter(f -> {
                    LocalDateTime fileDate = parseDateFromFilename(f.getName());
                    return fileDate != null && ChronoUnit.DAYS.between(fileDate, now) > RETENTION_DAYS_DAILY;
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

    // --- System Restore ---
    public void scheduleRestore(String filename, Long userId) throws IOException {
        File backupFile = getBackupFile(filename);

        if (!backupFile.getName().endsWith(EXT_DB)) {
            throw new IllegalArgumentException("Only .db files can be restored.");
        }

        Path restoreTrigger = Paths.get(RESTORE_TRIGGER_PATH);
        Path parent = restoreTrigger.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Files.copy(backupFile.toPath(), restoreTrigger, StandardCopyOption.REPLACE_EXISTING);
        auditoriaService.registrarAccion(userId, "RESTORE_SCHEDULED", "Restore pending for: " + filename);
    }

    // --- Checkpoints ---
    public String createCheckpoint(String reason, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(FMT_FILE);
        String filename = "checkpoint_" + reason + "_" + timestamp + EXT_DB;

        Path dir = Paths.get(DIR_CHECKPOINTS);

        try {
            if (!Files.exists(dir))
                Files.createDirectories(dir);
            Path fullPath = dir.resolve(filename).toAbsolutePath();

            executeVacuumInto(fullPath);
            auditoriaService.registrarAccion(userId, "CHECKPOINT_CREATED", "Reason: " + reason);
            return filename;
        } catch (IOException | DataAccessException e) {
            throw new InfrastructureException("Failed to create checkpoint: " + e.getMessage(), e);
        }
    }

    public void cleanupCheckpoints() {
        Path dir = Paths.get(DIR_CHECKPOINTS);
        if (!Files.exists(dir))
            return;

        long now = System.currentTimeMillis();

        // Use atomic counter for lambda finality
        AtomicInteger deleted = new AtomicInteger(0);

        // Checkpoint TTL (time to live - lifespan of temporary checkpoints) Cleanup
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().startsWith("checkpoint_")
                            && p.getFileName().toString().endsWith(EXT_DB))
                    .forEach(p -> {
                        if (now - p.toFile().lastModified() > CHECKPOINT_TTL_MS) {
                            try {
                                Files.delete(p);
                                deleted.incrementAndGet();
                            } catch (IOException e) {
                                log.warn("Failed to delete checkpoint: {}", p);
                            }
                        }
                    });
        } catch (IOException e) {
            log.warn("Checkpoint cleanup failed", e);
        }
        // System Cleanup Task - UserID 0
        if (deleted.get() > 0) {
            auditoriaService.registrarAccion(0L, "CHECKPOINT_CLEANUP",
                    "Deleted " + deleted.get() + " expired checkpoints.");
        }
    }

    public void removeMidDayBackup() {
        LocalDateTime now = LocalDateTime.now();
        String datePart = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String midDaySuffix = "_" + datePart + "_1300";

        Path dir = Paths.get(DIR_DAILY);
        if (!Files.exists(dir))
            return;

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().contains(midDaySuffix))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                            auditoriaService.registrarAccion(0L, "BACKUP_CLEANUP",
                                    "Removed Mid-Day Backup: " + p.getFileName());
                        } catch (IOException e) {
                            log.warn("Failed to delete mid-day backup: {}", p);
                        }
                    });
        } catch (IOException e) {
            log.warn("Mid-day cleanup failed", e);
        }
    }
}
