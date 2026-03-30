package com.fueleu.controltower.compliance.adapter.in.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/v1/compliance-ledger")
@CrossOrigin(origins = "http://localhost:3000")
public class ComplianceLedgerController {

    private static final BigDecimal TARGET_INTENSITY = new BigDecimal("89.34");
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
        return jdbcTemplate.query(
                "SELECT id, imo_number, name, vessel_type, build_year, flag_state FROM vessel ORDER BY name",
                (rs, rowNum) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    String imo = rs.getString("imo_number");
                    String name = rs.getString("name");
                    String vesselType = rs.getString("vessel_type");
                    Integer buildYear = rs.getObject("build_year", Integer.class);
                    String flagState = rs.getString("flag_state");

                    Metrics m = computeMetrics(vesselType, buildYear, flagState, year);
                    Map<String, Object> row = new HashMap<>();
                    row.put("vesselId", id);
                    row.put("imo", imo);
                    row.put("name", name);
                    row.put("vesselType", vesselType);
                    row.put("buildYear", buildYear);
                    row.put("flagState", flagState);
                    row.put("energyInScope", m.energyInScope);
                    row.put("actualIntensity", m.actualIntensity);
                    row.put("targetIntensity", TARGET_INTENSITY);
                    row.put("icb", m.icb);
                    row.put("vcb", m.vcb);
                    row.put("borrowedAmount", m.borrowedAmount);
                    row.put("bankedAmount", BigDecimal.ZERO);
                    row.put("reportingYear", year);
                    row.put("status", m.icb.signum() < 0 ? "Deficit" : "Compliant");
                    return row;
                }
        );
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

            totalIcb = totalIcb.add(icb);
            if (vcb != null) totalAcb = totalAcb.add(vcb);
            totalBorrowingCap = totalBorrowingCap.add(TARGET_INTENSITY.multiply(energy).multiply(new BigDecimal("0.02")));
            if (icb.signum() < 0) {
                penaltyExposure = penaltyExposure.add(icb.abs().multiply(new BigDecimal("0.12")));
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalIcb", totalIcb.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalAcb", totalAcb.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalBorrowingCap", totalBorrowingCap.setScale(2, RoundingMode.HALF_UP));
        summary.put("penaltyExposure", penaltyExposure.setScale(0, RoundingMode.HALF_UP));
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

        BigDecimal icb = TARGET_INTENSITY.subtract(actualIntensity).multiply(energy).multiply(new BigDecimal("0.11"));
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

    private record Metrics(
            BigDecimal energyInScope,
            BigDecimal actualIntensity,
            BigDecimal icb,
            BigDecimal vcb,
            BigDecimal borrowedAmount
    ) {}
}
