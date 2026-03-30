package com.fueleu.controltower.ingestion.application;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelImportProcessor {

    public List<String> parseVesselMaster(MultipartFile file) throws Exception {
        List<String> imoNumbersFound = new ArrayList<>();
        
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            
            Sheet sheet = workbook.getSheetAt(0); // Assuming first sheet is Vessel Master
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header
                
                Cell imoCell = row.getCell(0); // Assuming IMO is column 0
                if (imoCell != null) {
                    imoNumbersFound.add(imoCell.getStringCellValue());
                }
            }
        }
        return imoNumbersFound;
    }

    public List<LedgerImportRow> parseLedgerOverrides(MultipartFile file) throws Exception {
        List<LedgerImportRow> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) return rows;
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return rows;

            Map<Integer, String> headers = new HashMap<>();
            for (Cell c : headerRow) {
                String h = normalize(c.getStringCellValue());
                headers.put(c.getColumnIndex(), h);
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String imo = null;
                BigDecimal energy = null;
                BigDecimal actual = null;
                BigDecimal target = null;
                BigDecimal icb = null;
                BigDecimal vcb = null;
                for (Cell c : row) {
                    String h = headers.get(c.getColumnIndex());
                    if (h == null) continue;
                    if ("imo".equals(h) || "imonumber".equals(h)) imo = readCellString(c);
                    else if ("energyinscopemj".equals(h) || "energy_scope_mj".equals(h)) energy = readCellNumber(c);
                    else if ("actualintensitygco2eqpermj".equals(h) || "actualintensity".equals(h)) actual = readCellNumber(c);
                    else if ("targetintensitygco2eqpermj".equals(h) || "targetintensity".equals(h)) target = readCellNumber(c);
                    else if ("icbgco2eq".equals(h) || "icb".equals(h)) icb = readCellNumber(c);
                    else if ("vcbgco2eq".equals(h) || "vcb".equals(h)) vcb = readCellNumber(c);
                }
                if (imo == null || imo.isBlank()) continue;
                if (energy == null && actual == null && target == null && icb == null && vcb == null) continue;
                rows.add(new LedgerImportRow(imo.replace("IMO", "").trim(), energy, actual, target, icb, vcb));
            }
        }
        return rows;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private String readCellString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private BigDecimal readCellNumber(Cell cell) {
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> {
                    String s = cell.getStringCellValue();
                    if (s == null || s.isBlank()) yield null;
                    yield new BigDecimal(s.trim().replace(",", ""));
                }
                default -> null;
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    public record LedgerImportRow(
            String imoNumber,
            BigDecimal energyInScope,
            BigDecimal actualIntensity,
            BigDecimal targetIntensity,
            BigDecimal icb,
            BigDecimal vcb
    ) {}
}
