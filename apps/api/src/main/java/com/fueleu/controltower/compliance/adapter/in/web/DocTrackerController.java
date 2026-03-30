package com.fueleu.controltower.compliance.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/doc-tracker")
@CrossOrigin(origins = "http://localhost:3000")
public class DocTrackerController {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "MISSING_FLEXIBILITY",
            "PENDING_AUDITOR",
            "IN_REVIEW",
            "VERIFICATION_COMPLETE",
            "DOC_ISSUED_FINAL"
    );

    private static final Map<String, String> NEXT_STATUS = Map.of(
            "MISSING_FLEXIBILITY", "PENDING_AUDITOR",
            "PENDING_AUDITOR", "IN_REVIEW",
            "IN_REVIEW", "VERIFICATION_COMPLETE",
            "VERIFICATION_COMPLETE", "DOC_ISSUED_FINAL"
    );

    private final JdbcTemplate jdbcTemplate;

    public DocTrackerController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/statuses")
    public List<Map<String, Object>> getStatuses(@RequestParam(defaultValue = "2025") int year) {
        UUID periodId = ensureReportingPeriod(year);

        String sql = "SELECT v.id as vessel_id, v.imo_number, v.name, " +
                "COALESCE(vy.doc_status, 'MISSING_FLEXIBILITY') as doc_status " +
                "FROM vessel v " +
                "LEFT JOIN vessel_year vy ON vy.vessel_id = v.id AND vy.reporting_period_id = ? " +
                "ORDER BY v.name";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String status = rs.getString("doc_status");
            int step = toStep(status);
            Map<String, Object> row = new HashMap<>();
            row.put("vesselId", rs.getObject("vessel_id", UUID.class));
            row.put("imo", rs.getString("imo_number"));
            row.put("name", rs.getString("name"));
            row.put("docStatus", status);
            row.put("step", step);
            row.put("statusLabel", toLabel(status));
            row.put("year", year);
            row.put("nextStatus", NEXT_STATUS.get(status));
            row.put("canAdvance", NEXT_STATUS.containsKey(status));
            return row;
        }, periodId);
    }

    @PostMapping("/statuses/update")
    public ResponseEntity<Map<String, Object>> updateStatus(@RequestBody Map<String, Object> payload) {
        String vesselIdRaw = payload.get("vesselId") == null ? null : payload.get("vesselId").toString();
        String statusRaw = payload.get("docStatus") == null ? null : payload.get("docStatus").toString().toUpperCase();
        int year = payload.get("year") == null ? 2025 : Integer.parseInt(payload.get("year").toString());

        if (vesselIdRaw == null || statusRaw == null) {
            return badRequest("Missing required fields: vesselId, docStatus.");
        }
        if (!ALLOWED_STATUSES.contains(statusRaw)) {
            return badRequest("Invalid docStatus value.");
        }

        UUID vesselId;
        try {
            vesselId = UUID.fromString(vesselIdRaw);
        } catch (Exception ex) {
            return badRequest("Invalid vesselId format.");
        }

        Integer vesselExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM vessel WHERE id = ?",
                Integer.class,
                vesselId
        );
        if (vesselExists == null || vesselExists == 0) {
            return badRequest("Unknown vesselId.");
        }

        UUID periodId = ensureReportingPeriod(year);
        UUID vesselYearId = ensureVesselYear(vesselId, periodId);
        String currentStatus = jdbcTemplate.queryForObject(
                "SELECT COALESCE(doc_status, 'MISSING_FLEXIBILITY') FROM vessel_year WHERE id = ?",
                String.class,
                vesselYearId
        );

        if (!isValidTransition(currentStatus, statusRaw)) {
            return conflict("Invalid DoC transition from " + currentStatus + " to " + statusRaw + ".");
        }

        jdbcTemplate.update("UPDATE vessel_year SET doc_status = ? WHERE id = ?", statusRaw, vesselYearId);
        jdbcTemplate.update(
                "INSERT INTO audit_event (id, entity_type, entity_id, actor_id, action, before_payload, after_payload) " +
                        "VALUES (?, 'DOC_STATUS', ?, 'system-admin', 'UPDATE', CAST(? AS jsonb), CAST(? AS jsonb))",
                UUID.randomUUID(),
                vesselYearId,
                "{\"docStatus\":\"" + currentStatus + "\",\"year\":" + year + "}",
                "{\"docStatus\":\"" + statusRaw + "\",\"year\":" + year + "}"
        );

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "DoC status updated.");
        response.put("docStatus", statusRaw);
        response.put("statusLabel", toLabel(statusRaw));
        response.put("step", toStep(statusRaw));
        response.put("canAdvance", NEXT_STATUS.containsKey(statusRaw));
        response.put("nextStatus", NEXT_STATUS.get(statusRaw));
        return ResponseEntity.ok(response);
    }

    private UUID ensureReportingPeriod(int year) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM reporting_period WHERE year = ? LIMIT 1",
                    UUID.class,
                    year
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            UUID periodId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO reporting_period (id, year, status, ghg_limit) VALUES (?, ?, 'OPEN', 89.3368)",
                    periodId,
                    year
            );
            return periodId;
        }
    }

    private UUID ensureVesselYear(UUID vesselId, UUID periodId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM vessel_year WHERE vessel_id = ? AND reporting_period_id = ? LIMIT 1",
                    UUID.class,
                    vesselId,
                    periodId
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            UUID vesselYearId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO vessel_year (id, vessel_id, reporting_period_id, doc_status) VALUES (?, ?, ?, 'MISSING_FLEXIBILITY')",
                    vesselYearId,
                    vesselId,
                    periodId
            );
            return vesselYearId;
        }
    }

    private boolean isValidTransition(String currentStatus, String targetStatus) {
        if (Objects.equals(currentStatus, targetStatus)) return true;
        String next = NEXT_STATUS.get(currentStatus);
        return Objects.equals(next, targetStatus);
    }

    private int toStep(String status) {
        return switch (status) {
            case "PENDING_AUDITOR" -> 1;
            case "IN_REVIEW" -> 2;
            case "VERIFICATION_COMPLETE" -> 3;
            case "DOC_ISSUED_FINAL" -> 4;
            default -> 0;
        };
    }

    private String toLabel(String status) {
        return switch (status) {
            case "PENDING_AUDITOR" -> "Pending Auditor";
            case "IN_REVIEW" -> "In Review";
            case "VERIFICATION_COMPLETE" -> "Verification Complete";
            case "DOC_ISSUED_FINAL" -> "DoC Issued (Final)";
            default -> "Missing Flexibility";
        };
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("status", "BAD_REQUEST");
        err.put("message", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
    }

    private ResponseEntity<Map<String, Object>> conflict(String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("status", "CONFLICT");
        err.put("message", message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
    }
}
