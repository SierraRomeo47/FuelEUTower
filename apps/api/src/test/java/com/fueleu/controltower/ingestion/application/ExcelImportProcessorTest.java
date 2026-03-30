package com.fueleu.controltower.ingestion.application;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelImportProcessorTest {

    @Test
    void parseLedgerOverrides_readsExpectedColumns() throws Exception {
        byte[] data = buildWorkbook(
                new String[]{"IMO", "EnergyInScope_MJ", "ActualIntensity_gCO2eqPerMJ", "TargetIntensity_gCO2eqPerMJ", "ICB_gCO2eq", "VCB_gCO2eq"},
                new Object[]{"9701001", 42000d, 88.90d, 89.3368d, 18480d, 17000d}
        );
        MultipartFile file = inMemoryXlsx("ledger.xlsx", data);
        ExcelImportProcessor p = new ExcelImportProcessor();
        List<ExcelImportProcessor.LedgerImportRow> rows = p.parseLedgerOverrides(file);
        assertEquals(1, rows.size());
        ExcelImportProcessor.LedgerImportRow row = rows.get(0);
        assertEquals("9701001", row.imoNumber());
        assertEquals(new BigDecimal("42000.0"), row.energyInScope());
        assertEquals(new BigDecimal("88.9"), row.actualIntensity());
        assertEquals(new BigDecimal("89.3368"), row.targetIntensity());
        assertEquals(new BigDecimal("18480.0"), row.icb());
        assertEquals(new BigDecimal("17000.0"), row.vcb());
    }

    @Test
    void parseVesselMaster_readsImosFromFirstColumn() throws Exception {
        byte[] data = buildWorkbook(
                new String[]{"IMO", "Name"},
                new Object[]{"9701001", "A"},
                new Object[]{"9701002", "B"}
        );
        MultipartFile file = inMemoryXlsx("vessels.xlsx", data);
        ExcelImportProcessor p = new ExcelImportProcessor();
        List<String> imos = p.parseVesselMaster(file);
        assertEquals(2, imos.size());
        assertEquals("9701001", imos.get(0));
        assertEquals("9701002", imos.get(1));
    }

    private static byte[] buildWorkbook(String[] headers, Object[]... dataRows) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("Sheet1");
            Row h = s.createRow(0);
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            for (int r = 0; r < dataRows.length; r++) {
                Row row = s.createRow(r + 1);
                for (int c = 0; c < dataRows[r].length; c++) {
                    Object v = dataRows[r][c];
                    if (v instanceof Number n) row.createCell(c).setCellValue(n.doubleValue());
                    else row.createCell(c).setCellValue(String.valueOf(v));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static MultipartFile inMemoryXlsx(String filename, byte[] bytes) {
        return new MultipartFile() {
            @Override public String getName() { return filename; }
            @Override public String getOriginalFilename() { return filename; }
            @Override public String getContentType() { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }
            @Override public boolean isEmpty() { return bytes.length == 0; }
            @Override public long getSize() { return bytes.length; }
            @Override public byte[] getBytes() { return bytes; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
            @Override public void transferTo(java.io.File dest) { throw new UnsupportedOperationException(); }
        };
    }
}

