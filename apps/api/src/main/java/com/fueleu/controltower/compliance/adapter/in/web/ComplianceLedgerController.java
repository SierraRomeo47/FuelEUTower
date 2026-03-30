package com.fueleu.controltower.compliance.adapter.in.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/v1/compliance-ledger")
@CrossOrigin(origins = "http://localhost:3000")
public class ComplianceLedgerController {

    private static final BigDecimal TARGET_INTENSITY = new BigDecimal("89.3368");
    private static final BigDecimal VLSFO_ENERGY_EQUIVALENT_MJ_PER_TON = new BigDecimal("41000");
    private static final BigDecimal PENALTY_RATE_EUR_PER_TON_VLSFO_EQUIVALENT = new BigDecimal("2400");
    private static final Set<String> EU_FLAGS = Set.of(
            "CYPRUS", "MALTA", "GREECE", "ITALY", "PORTUGAL", "SPAIN", "FRANCE", "GERMANY",
            "NETHERLANDS", "BELGIUM", "DENMARK", "SWEDEN", "FINLAND", "ESTONIA", "LATVIA",
            "LITHUANIA", "POLAND", "IRELAND", "CROATIA", "SLOVENIA", "ROMANIA", "BULGARIA"
    );

    private final JdbcTemplate jdbcTemplate;

    public ComplianceLedgerController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/rows")
    public List<Map<String, Object>> getRows(@RequestParam(defaultValue = "2025") int year) {
        UUID periodId = ensureReportingPeriod(year);
        return jdbcTemplate.query(
                "SELECT id, imo_number, name, vessel_type, build_year, flag_state FROM vessel ORDER BY name",
                (rs, rowNum) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    String imo = rs.getString("imo_number");
                    String name = rs.getString("name");
                    String vesselType = rs.getString("vessel_type");
                    Integer buildYear = rs.getObject("build_year", Integer.class);
                    String flagState = rs.getString("flag_state");
                    UUID vesselYearId = ensureVesselYear(id, periodId);

                    Metrics m = computeMetrics(vesselType, buildYear, flagState, year);
                    Map<String, Object> override = getLedgerOverride(vesselYearId);
                    Map<String, Object> cc = getComplianceCalculation(vesselYearId);

                    BigDecimal energy = firstNonNull(
                            toBigDecimal(override.get("energy_in_scope")),
                            toBigDecimal(cc.get("energy_in_scope")),
                            m.energyInScope
                    );
                    BigDecimal targetIntensity = firstNonNull(
                            toBigDecimal(override.get("target_intensity")),
                            TARGET_INTENSITY
                    );
                    BigDecimal icb = toBigDecimal(override.get("icb_value"));
                    BigDecimal actualIntensity;
                    if (toBigDecimal(override.get("actual_intensity")) != null) {
                        actualIntensity = toBigDecimal(override.get("actual_intensity"));
                    } else if (icb != null && energy != null && energy.compareTo(BigDecimal.ZERO) > 0) {
                        actualIntensity = targetIntensity.subtract(icb.divide(energy, 8, RoundingMode.HALF_UP));
                    } else if (toBigDecimal(cc.get("icb_value")) != null && energy.compareTo(BigDecimal.ZERO) > 0) {
                        icb = toBigDecimal(cc.get("icb_value"));
                        actualIntensity = targetIntensity.subtract(icb.divide(energy, 8, RoundingMode.HALF_UP));
                    } else {
                        actualIntensity = m.actualIntensity;
                        icb = m.icb;
                    }
                    if (icb == null) {
                        icb = targetIntensity.subtract(actualIntensity).multiply(energy);
                    }
                    BigDecimal vcb = firstNonNull(
                            toBigDecimal(override.get("vcb_value")),
                            toBigDecimal(cc.get("vcb_value")),
                            m.vcb
                    );
                    BigDecimal borrowed = firstNonNull(
                            toBigDecimal(override.get("borrowed_amount")),
                            sumBorrowed(vesselYearId),
                            m.borrowedAmount
                    );
                    BigDecimal banked = firstNonNull(
                            toBigDecimal(override.get("banked_amount")),
                            sumBanked(vesselYearId),
                            BigDecimal.ZERO
                    );

                    Map<String, Object> row = new HashMap<>();
                    row.put("vesselId", id);
                    row.put("imo", imo);
                    row.put("name", name);
                    row.put("vesselType", vesselType);
                    row.put("buildYear", buildYear);
                    row.put("flagState", flagState);
                    row.put("energyInScope", energy.setScale(2, RoundingMode.HALF_UP));
                    row.put("actualIntensity", actualIntensity.setScale(2, RoundingMode.HALF_UP));
                    row.put("targetIntensity", targetIntensity.setScale(2, RoundingMode.HALF_UP));
                    row.put("icb", icb.setScale(2, RoundingMode.HALF_UP));
                    row.put("vcb", vcb == null ? null : vcb.setScale(2, RoundingMode.HALF_UP));
                    row.put("borrowedAmount", borrowed.setScale(2, RoundingMode.HALF_UP));
                    row.put("bankedAmount", banked.setScale(2, RoundingMode.HALF_UP));
                    row.put("reportingYear", year);
                    row.put("status", icb.signum() < 0 ? "Deficit" : "Compliant");
                    return row;
                }
        );
    }

    @PutMapping("/rows/{vesselId}")
    public ResponseEntity<Map<String, Object>> updateRow(
            @PathVariable UUID vesselId,
            @RequestParam(defaultValue = "2025") int year,
            @RequestBody Map<String, Object> payload
    ) {
        UUID periodId = ensureReportingPeriod(year);
        UUID vesselYearId = ensureVesselYear(vesselId, periodId);

        BigDecimal energy = toBigDecimal(payload.get("energyInScope"));
        BigDecimal actual = toBigDecimal(payload.get("actualIntensity"));
        BigDecimal target = firstNonNull(toBigDecimal(payload.get("targetIntensity")), TARGET_INTENSITY);
        BigDecimal icb = toBigDecimal(payload.get("icb"));
        BigDecimal vcb = toBigDecimal(payload.get("vcb"));
        BigDecimal banked = toBigDecimal(payload.get("bankedAmount"));
        BigDecimal borrowed = toBigDecimal(payload.get("borrowedAmount"));

        if (energy != null && energy.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("status", "BAD_REQUEST", "message", "energyInScope must be positive."));
        }
        if (actual != null && icb == null && energy != null) {
            icb = target.subtract(actual).multiply(energy);
        }
        if (icb != null && actual == null && energy != null && energy.compareTo(BigDecimal.ZERO) > 0) {
            actual = target.subtract(icb.divide(energy, 8, RoundingMode.HALF_UP));
        }

        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM compliance_ledger_override WHERE vessel_year_id = ?",
                Integer.class,
                vesselYearId
        );
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                    "UPDATE compliance_ledger_override SET energy_in_scope = ?, actual_intensity = ?, target_intensity = ?, icb_value = ?, vcb_value = ?, banked_amount = ?, borrowed_amount = ?, updated_at = CURRENT_TIMESTAMP WHERE vessel_year_id = ?",
                    energy, actual, target, icb, vcb, banked, borrowed, vesselYearId
            );
        } else {
            jdbcTemplate.update(
                    "INSERT INTO compliance_ledger_override (id, vessel_year_id, energy_in_scope, actual_intensity, target_intensity, icb_value, vcb_value, banked_amount, borrowed_amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), vesselYearId, energy, actual, target, icb, vcb, banked, borrowed
            );
        }

        upsertComplianceCalculation(vesselYearId, energy, icb, vcb, target);
        upsertBankBorrowRecords(vesselYearId, banked, borrowed);

        Map<String, Object> updated = getRows(year).stream()
                .filter(r -> vesselId.equals(r.get("vesselId")))
                .findFirst()
                .orElse(Map.of("vesselId", vesselId));
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary(@RequestParam(defaultValue = "2025") int year) {
        List<Map<String, Object>> rows = getRows(year);

        BigDecimal totalIcb = BigDecimal.ZERO;
        BigDecimal totalAcb = BigDecimal.ZERO;
        BigDecimal totalBorrowingCap = BigDecimal.ZERO;
        BigDecimal penaltyExposure = BigDecimal.ZERO;

        for (Map<String, Object> row : rows) {
            BigDecimal icb = (BigDecimal) row.get("icb");
            BigDecimal vcb = (BigDecimal) row.get("vcb");
            BigDecimal energy = (BigDecimal) row.get("energyInScope");
            BigDecimal actualIntensity = (BigDecimal) row.get("actualIntensity");

            totalIcb = totalIcb.add(icb);
            if (vcb != null) totalAcb = totalAcb.add(vcb);
            totalBorrowingCap = totalBorrowingCap.add(TARGET_INTENSITY.multiply(energy).multiply(new BigDecimal("0.02")));
            if (icb.signum() < 0) {
                // Annex IV Part B:
                // Penalty [EUR] = |Compliance balance [gCO2eq]| / (GHGIE_actual [gCO2eq/MJ] * 41,000 [MJ/tVLSFOeq]) * 2,400 [EUR/tVLSFOeq]
                if (actualIntensity != null && actualIntensity.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal denominator = actualIntensity.multiply(VLSFO_ENERGY_EQUIVALENT_MJ_PER_TON);
                    BigDecimal vesselPenalty = icb.abs()
                            .divide(denominator, 12, RoundingMode.HALF_UP)
                            .multiply(PENALTY_RATE_EUR_PER_TON_VLSFO_EQUIVALENT);
                    penaltyExposure = penaltyExposure.add(vesselPenalty);
                }
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalIcb", totalIcb.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalAcb", totalAcb.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalBorrowingCap", totalBorrowingCap.setScale(2, RoundingMode.HALF_UP));
        summary.put("penaltyExposure", penaltyExposure.setScale(2, RoundingMode.HALF_UP));
        summary.put("rowCount", rows.size());
        summary.put("year", year);
        return summary;
    }

    private Metrics computeMetrics(String vesselTypeRaw, Integer buildYearRaw, String flagStateRaw, int year) {
        String vesselType = vesselTypeRaw == null ? "container" : vesselTypeRaw.toLowerCase();
        int buildYear = buildYearRaw == null ? 2018 : buildYearRaw;
        String flag = flagStateRaw == null ? "" : flagStateRaw.toUpperCase();
        boolean euFlag = EU_FLAGS.contains(flag);

        BigDecimal vesselBase = switch (vesselType) {
            case "container" -> new BigDecimal("42000");
            case "tanker" -> new BigDecimal("52000");
            case "bulker" -> new BigDecimal("36000");
            case "ro-ro", "roro" -> new BigDecimal("28000");
            case "lng" -> new BigDecimal("30000");
            case "passenger" -> new BigDecimal("24000");
            default -> new BigDecimal("34000");
        };

        BigDecimal exposureFactor = euFlag ? new BigDecimal("0.78") : new BigDecimal("0.52");
        int ageYears = Math.max(0, year - buildYear);
        BigDecimal ageFactor = BigDecimal.ONE.add(new BigDecimal(Math.min(ageYears, 25)).multiply(new BigDecimal("0.01")));
        BigDecimal energy = vesselBase.multiply(exposureFactor).multiply(ageFactor);

        BigDecimal typeDelta = switch (vesselType) {
            case "container" -> new BigDecimal("0.55");
            case "tanker" -> new BigDecimal("0.70");
            case "bulker" -> new BigDecimal("0.35");
            case "ro-ro", "roro" -> new BigDecimal("0.10");
            case "lng" -> new BigDecimal("-0.45");
            case "passenger" -> new BigDecimal("0.25");
            default -> new BigDecimal("0.30");
        };
        BigDecimal ageDelta = new BigDecimal(Math.min(ageYears, 25)).multiply(new BigDecimal("0.03"));
        BigDecimal exposureDelta = euFlag ? new BigDecimal("0.18") : new BigDecimal("0.35");
        BigDecimal actualIntensity = TARGET_INTENSITY.add(typeDelta).add(ageDelta).subtract(exposureDelta);
        // Ensure a realistic testable mix: newer LNG/Ro-Ro units tend to overperform and can bank.
        if (buildYear >= 2019 && ("lng".equals(vesselType) || "ro-ro".equals(vesselType) || "roro".equals(vesselType))) {
            actualIntensity = actualIntensity.subtract(new BigDecimal("0.80"));
        }

        // Annex IV Part A unit-consistent form:
        // Compliance balance [gCO2eq] = (target - actual) [gCO2eq/MJ] * energy [MJ]
        BigDecimal icb = TARGET_INTENSITY.subtract(actualIntensity).multiply(energy);
        BigDecimal vcb = icb.signum() > 0 ? icb.multiply(new BigDecimal("0.92")) : null;
        BigDecimal borrowedAmount = icb.compareTo(new BigDecimal("-1200")) < 0
                ? icb.abs().multiply(new BigDecimal("0.18")).min(new BigDecimal("800"))
                : BigDecimal.ZERO;

        return new Metrics(
                energy.setScale(2, RoundingMode.HALF_UP),
                actualIntensity.setScale(2, RoundingMode.HALF_UP),
                icb.setScale(2, RoundingMode.HALF_UP),
                vcb == null ? null : vcb.setScale(2, RoundingMode.HALF_UP),
                borrowedAmount.setScale(2, RoundingMode.HALF_UP)
        );
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

    private Map<String, Object> getLedgerOverride(UUID vesselYearId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM compliance_ledger_override WHERE vessel_year_id = ? LIMIT 1", vesselYearId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> getComplianceCalculation(UUID vesselYearId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT energy_in_scope, icb_value, vcb_value FROM compliance_calculation WHERE vessel_year_id = ? LIMIT 1",
                vesselYearId
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private BigDecimal sumBorrowed(UUID vesselYearId) {
        return jdbcTemplate.queryForObject("SELECT COALESCE(SUM(borrowed_amount), 0) FROM borrowing_record WHERE vessel_year_id = ?", BigDecimal.class, vesselYearId);
    }

    private BigDecimal sumBanked(UUID vesselYearId) {
        return jdbcTemplate.queryForObject("SELECT COALESCE(SUM(banked_amount), 0) FROM banking_record WHERE vessel_year_id = ?", BigDecimal.class, vesselYearId);
    }

    private void upsertComplianceCalculation(UUID vesselYearId, BigDecimal energy, BigDecimal icb, BigDecimal vcb, BigDecimal targetIntensity) {
        if (energy == null && icb == null && vcb == null) return;
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
                    UUID.randomUUID(), vesselYearId, firstNonNull(energy, BigDecimal.ZERO), icb, vcb, cap
            );
        }
    }

    private void upsertBankBorrowRecords(UUID vesselYearId, BigDecimal banked, BigDecimal borrowed) {
        if (banked != null) {
            jdbcTemplate.update("DELETE FROM banking_record WHERE vessel_year_id = ?", vesselYearId);
            if (banked.compareTo(BigDecimal.ZERO) > 0) {
                jdbcTemplate.update("INSERT INTO banking_record (id, vessel_year_id, banked_amount, status) VALUES (?, ?, ?, 'PROPOSED')",
                        UUID.randomUUID(), vesselYearId, banked);
            }
        }
        if (borrowed != null) {
            jdbcTemplate.update("DELETE FROM borrowing_record WHERE vessel_year_id = ?", vesselYearId);
            if (borrowed.compareTo(BigDecimal.ZERO) > 0) {
                jdbcTemplate.update("INSERT INTO borrowing_record (id, vessel_year_id, borrowed_amount, penalty_multiplier) VALUES (?, ?, ?, 1.10)",
                        UUID.randomUUID(), vesselYearId, borrowed);
            }
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value.toString().isBlank()) return null;
        return new BigDecimal(value.toString());
    }

    private BigDecimal firstNonNull(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private record Metrics(
            BigDecimal energyInScope,
            BigDecimal actualIntensity,
            BigDecimal icb,
            BigDecimal vcb,
            BigDecimal borrowedAmount
    ) {}
}
