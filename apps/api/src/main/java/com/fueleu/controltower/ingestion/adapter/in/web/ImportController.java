package com.fueleu.controltower.ingestion.adapter.in.web;

import com.fueleu.controltower.ingestion.domain.ImportBatch;
import com.fueleu.controltower.ingestion.application.ExcelImportProcessor;
import com.fueleu.controltower.ingestion.application.XmlImportProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/imports")
@CrossOrigin(origins = "http://localhost:3000")
public class ImportController {

    private final ExcelImportProcessor excelProcessor;
    private final XmlImportProcessor xmlProcessor;
    private final JdbcTemplate jdbcTemplate;

    public ImportController(ExcelImportProcessor excelProcessor, XmlImportProcessor xmlProcessor, JdbcTemplate jdbcTemplate) {
        this.excelProcessor = excelProcessor;
        this.xmlProcessor = xmlProcessor;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            String uploadedBy = "system-admin"; 
            
            // This endpoint currently runs a lightweight ingestion pass without persisting the batch entity.
            // Generate a deterministic tracking ID to return to the UI.
            UUID batchId = UUID.randomUUID();
            new ImportBatch(filename, file.getContentType(), uploadedBy);
            
            if (filename != null && filename.endsWith(".xlsx")) {
                var imos = excelProcessor.parseVesselMaster(file);
                List<ExcelImportProcessor.LedgerImportRow> ledgerRows = excelProcessor.parseLedgerOverrides(file);
                int applied = applyLedgerOverrides(ledgerRows, 2025);
                return ResponseEntity.ok(Map.of(
                        "message", "Excel processed",
                        "imosFound", imos.size(),
                        "ledgerRowsFound", ledgerRows.size(),
                        "ledgerRowsApplied", applied,
                        "batchId", batchId
                ));
            } else if (filename != null && filename.endsWith(".xml")) {
                xmlProcessor.processPortEmissions(file);
                return ResponseEntity.ok(Map.of("message", "XML processed", "batchId", batchId));
            }
            
            return ResponseEntity.badRequest().body("Unsupported file type");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    private int applyLedgerOverrides(List<ExcelImportProcessor.LedgerImportRow> rows, int year) {
        if (rows == null || rows.isEmpty()) return 0;
        UUID periodId = ensureReportingPeriod(year);
        int applied = 0;
        for (ExcelImportProcessor.LedgerImportRow r : rows) {
            UUID vesselId = lookupVesselByImo(r.imoNumber());
            if (vesselId == null) continue;
            UUID vesselYearId = ensureVesselYear(vesselId, periodId);
            BigDecimal energy = r.energyInScope();
            BigDecimal target = r.targetIntensity() == null ? new BigDecimal("89.3368") : r.targetIntensity();
            BigDecimal icb = r.icb();
            BigDecimal actual = r.actualIntensity();
            if (icb == null && actual != null && energy != null && energy.compareTo(BigDecimal.ZERO) > 0) {
                icb = target.subtract(actual).multiply(energy);
            }
            if (actual == null && icb != null && energy != null && energy.compareTo(BigDecimal.ZERO) > 0) {
                actual = target.subtract(icb.divide(energy, 8, RoundingMode.HALF_UP));
            }
            Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM compliance_ledger_override WHERE vessel_year_id = ?", Integer.class, vesselYearId);
            if (exists != null && exists > 0) {
                jdbcTemplate.update(
                        "UPDATE compliance_ledger_override SET energy_in_scope = ?, actual_intensity = ?, target_intensity = ?, icb_value = ?, vcb_value = ?, updated_at = CURRENT_TIMESTAMP WHERE vessel_year_id = ?",
                        energy, actual, target, icb, r.vcb(), vesselYearId
                );
            } else {
                jdbcTemplate.update(
                        "INSERT INTO compliance_ledger_override (id, vessel_year_id, energy_in_scope, actual_intensity, target_intensity, icb_value, vcb_value) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(), vesselYearId, energy, actual, target, icb, r.vcb()
                );
            }
            upsertComplianceCalculation(vesselYearId, energy, icb, r.vcb(), target);
            applied++;
        }
        return applied;
    }

    private UUID lookupVesselByImo(String imoRaw) {
        if (imoRaw == null || imoRaw.isBlank()) return null;
        String plain = imoRaw.replace("IMO", "").trim();
        List<UUID> ids = jdbcTemplate.query(
                "SELECT id FROM vessel WHERE imo_number = ? OR imo_number = ? LIMIT 1",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                plain, "IMO" + plain
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private UUID ensureReportingPeriod(int year) {
        try {
            return jdbcTemplate.queryForObject("SELECT id FROM reporting_period WHERE year = ? LIMIT 1", UUID.class, year);
        } catch (Exception e) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO reporting_period (id, year, status, ghg_limit) VALUES (?, ?, 'OPEN', 89.3368)", id, year);
            return id;
        }
    }

    private UUID ensureVesselYear(UUID vesselId, UUID periodId) {
        try {
            return jdbcTemplate.queryForObject("SELECT id FROM vessel_year WHERE vessel_id = ? AND reporting_period_id = ? LIMIT 1", UUID.class, vesselId, periodId);
        } catch (Exception e) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO vessel_year (id, vessel_id, reporting_period_id) VALUES (?, ?, ?)", id, vesselId, periodId);
            return id;
        }
    }

    private void upsertComplianceCalculation(UUID vesselYearId, BigDecimal energy, BigDecimal icb, BigDecimal vcb, BigDecimal targetIntensity) {
        BigDecimal cap = (energy == null ? BigDecimal.ZERO : targetIntensity.multiply(energy).multiply(new BigDecimal("0.02")));
        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM compliance_calculation WHERE vessel_year_id = ?", Integer.class, vesselYearId);
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                    "UPDATE compliance_calculation SET energy_in_scope = COALESCE(?, energy_in_scope), icb_value = COALESCE(?, icb_value), vcb_value = COALESCE(?, vcb_value), borrowing_cap = ? WHERE vessel_year_id = ?",
                    energy, icb, vcb, cap, vesselYearId
            );
        } else {
            jdbcTemplate.update(
                    "INSERT INTO compliance_calculation (id, vessel_year_id, energy_in_scope, icb_value, vcb_value, borrowing_cap) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), vesselYearId, energy == null ? BigDecimal.ZERO : energy, icb, vcb, cap
            );
        }
    }
}
