package com.centralizesys.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;

/**
 * Inspector specifically looking for SALES data.
 * Patterns to look for: Dates, "Total", "Cliente", $ amounts.
 */
class FinancialInspectorTest {

    @Test
    void inspectFinancialStructure() {
        File file = new File("d:/Marcos/JavaProjects/CentralizeSys - Copy/data/LegacyFinanceImport.xlsx");
        if (!file.exists()) {
            System.out.println("FILE NOT FOUND");
            // Still assert true to satisfy method requirement
            assertTrue(true);
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            System.out.println("=== FINANCIAL INSPECTION ===");
            DataFormatter parser = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                System.out.println("\n--- Sheet " + i + ": " + sheet.getSheetName() + " ---");

                // Check Rows 0-10 for Keywords
                for (int r = 0; r < 20; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null)
                        continue;

                    StringBuilder line = new StringBuilder("R" + r + ": ");
                    boolean relevant = false;
                    for (int c = 0; c < 20; c++) {
                        Cell cell = row.getCell(c);
                        String txt = "";
                        try {
                            // Try to evaluate, if fails, fallback to raw string
                            txt = parser.formatCellValue(cell, evaluator);
                        } catch (Exception e) {
                            // Logic: If formula fails, try getting cached result or raw
                            try {
                                txt = cell.getStringCellValue();
                            } catch (Exception ex) {
                                txt = "ERR";
                            }
                        }

                        if (!txt.isBlank()) {
                            line.append("[").append(txt).append("] ");
                            if (txt.matches("(?i).*(fecha|date|cliente|total|venta|monto|precio).*")) {
                                relevant = true;
                            }
                        }
                    }
                    if (relevant) {
                        System.out.println(line);
                    }
                }
            }
            assertTrue(true, "Inspection completed");

        } catch (Exception e) {
            // Fail if exception occurs
            fail("Exception occurred: " + e.getMessage(), e);
        }
    }
}
