package com.fueleu.controltower.flexibility.adapter.in.web;

import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1/flexibility")
@CrossOrigin(origins = "http://localhost:3000")
public class FlexibilityController {
    
    private static final Logger log = LoggerFactory.getLogger(FlexibilityController.class);
    private final JdbcTemplate jdbcTemplate;

    public FlexibilityController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/execute")
    @Transactional
    public ResponseEntity<Map<String, Object>> executeFlexibilityAction(@RequestBody Map<String, Object> payload) {
        String actionType = (String) payload.get("type");
        String vesselIdRaw = payload.get("vesselId") == null ? null : payload.get("vesselId").toString();
        String amountRaw = payload.get("amount") == null ? null : payload.get("amount").toString();
        if (actionType == null || vesselIdRaw == null || amountRaw == null) {
            return badRequest("Missing required fields: type, vesselId, amount.");
        }

        if (!"bank".equals(actionType) && !"borrow".equals(actionType) && !"pool".equals(actionType)) {
            return badRequest("Invalid action type. Allowed values: bank, borrow, pool.");
        }

        UUID vesselId;
        BigDecimal amount;
        try {
            vesselId = UUID.fromString(vesselIdRaw);
            amount = new BigDecimal(amountRaw);
        } catch (Exception ex) {
            return badRequest("Invalid vesselId or amount format.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return badRequest("Amount must be a positive number.");
        }
        
        log.info("Executing Flexibility Action: {} on Vessel: {} Amount: {}", actionType, vesselId, amount);

        Integer vesselExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM vessel WHERE id = ?",
                Integer.class,
                vesselId
        );
        if (vesselExists == null || vesselExists == 0) {
            return badRequest("Unknown vesselId. Register vessel before running flexibility actions.");
        }

        // 1. Ensure Reporting Period 2025 exists
        int reportingYear = 2025;
        UUID periodId;
        try {
            periodId = jdbcTemplate.queryForObject("SELECT id FROM reporting_period WHERE year = ? LIMIT 1", UUID.class, reportingYear);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            periodId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO reporting_period (id, year, status, ghg_limit) VALUES (?, ?, 'OPEN', 89.3368)", periodId, reportingYear);
        }

        // 2. Ensure VesselYear exists for this Vessel + Period
        UUID vesselYearId;
        try {
            vesselYearId = jdbcTemplate.queryForObject("SELECT id FROM vessel_year WHERE vessel_id = ? AND reporting_period_id = ? LIMIT 1", UUID.class, vesselId, periodId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            vesselYearId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO vessel_year (id, vessel_id, reporting_period_id) VALUES (?, ?, ?)", vesselYearId, vesselId, periodId);
        }

        String docStatus = jdbcTemplate.queryForObject(
                "SELECT COALESCE(doc_status, 'PENDING') FROM vessel_year WHERE id = ?",
                String.class,
                vesselYearId
        );

        BigDecimal ghgLimit = jdbcTemplate.queryForObject(
                "SELECT ghg_limit FROM reporting_period WHERE id = ?",
                BigDecimal.class,
                periodId
        );

        BigDecimal energyInScope = jdbcTemplate.queryForObject(
                "SELECT COALESCE((SELECT energy_in_scope FROM compliance_calculation WHERE vessel_year_id = ?), 0)",
                BigDecimal.class,
                vesselYearId
        );

        BigDecimal icbValue = jdbcTemplate.queryForObject(
                "SELECT (SELECT icb_value FROM compliance_calculation WHERE vessel_year_id = ?)",
                BigDecimal.class,
                vesselYearId
        );
        BigDecimal acbValue = jdbcTemplate.queryForObject(
                "SELECT (SELECT acb_value FROM compliance_calculation WHERE vessel_year_id = ?)",
                BigDecimal.class,
                vesselYearId
        );
        BigDecimal vcbValue = jdbcTemplate.queryForObject(
                "SELECT (SELECT vcb_value FROM compliance_calculation WHERE vessel_year_id = ?)",
                BigDecimal.class,
                vesselYearId
        );
        BigDecimal effectiveBalance = firstNonNull(vcbValue, acbValue, icbValue, BigDecimal.ZERO);
        BigDecimal borrowingCap = ghgLimit.multiply(energyInScope).multiply(new BigDecimal("0.02"));

        Integer hasPoolMembership = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM pool_participant WHERE vessel_year_id = ?",
                Integer.class,
                vesselYearId
        );
        Integer hasBorrowThisYear = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM borrowing_record WHERE vessel_year_id = ?",
                Integer.class,
                vesselYearId
        );
        Integer hasBankThisYear = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM banking_record WHERE vessel_year_id = ?",
                Integer.class,
                vesselYearId
        );

        Integer hasBorrowPriorYear = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) " +
                        "FROM borrowing_record br " +
                        "JOIN vessel_year vy ON br.vessel_year_id = vy.id " +
                        "JOIN reporting_period rp ON vy.reporting_period_id = rp.id " +
                        "WHERE vy.vessel_id = ? AND rp.year = ?",
                Integer.class,
                vesselId,
                reportingYear - 1
        );

        // 3. Policy checks + execution
        try {
            if ("bank".equals(actionType)) {
                if ("ISSUED".equalsIgnoreCase(docStatus) || "LOCKED".equalsIgnoreCase(docStatus)) {
                    return conflict("Banking is blocked after DoC lock/issuance.");
                }
                if (effectiveBalance.compareTo(BigDecimal.ZERO) <= 0) {
                    return conflict("Banking requires positive compliance balance (surplus).");
                }
                if (hasBankThisYear != null && hasBankThisYear > 0) {
                    return conflict("A banking record already exists for this vessel and reporting period.");
                }
                jdbcTemplate.update("INSERT INTO banking_record (id, vessel_year_id, banked_amount, status) VALUES (?, ?, ?, 'PROPOSED')",
                        UUID.randomUUID(), vesselYearId, amount);
                insertAudit("BANKING_RECORD", vesselYearId, "system-admin", "CREATE",
                        "{\"amount\":" + amount + ",\"year\":" + reportingYear + "}");
            } else if ("borrow".equals(actionType)) {
                if (effectiveBalance.compareTo(BigDecimal.ZERO) >= 0) {
                    return conflict("Borrowing requires a compliance deficit (negative balance).");
                }
                if (energyInScope.compareTo(BigDecimal.ZERO) <= 0) {
                    return conflict("Borrowing requires non-zero in-scope energy.");
                }
                if (amount.compareTo(borrowingCap) > 0) {
                    return conflict("Borrowing amount exceeds statutory cap (2% × GHG limit × in-scope energy).");
                }
                if (hasPoolMembership != null && hasPoolMembership > 0) {
                    return conflict("Borrowing and pooling are mutually exclusive in the same reporting period.");
                }
                if (hasBorrowThisYear != null && hasBorrowThisYear > 0) {
                    return conflict("A borrowing record already exists for this vessel and reporting period.");
                }
                if (hasBorrowPriorYear != null && hasBorrowPriorYear > 0) {
                    return conflict("Consecutive borrowing is blocked (previous reporting period already borrowed).");
                }
                jdbcTemplate.update("INSERT INTO borrowing_record (id, vessel_year_id, borrowed_amount, penalty_multiplier) VALUES (?, ?, ?, 1.10)",
                        UUID.randomUUID(), vesselYearId, amount);
                insertAudit("BORROWING_RECORD", vesselYearId, "system-admin", "CREATE",
                        "{\"amount\":" + amount + ",\"cap\":" + borrowingCap + ",\"year\":" + reportingYear + "}");
            } else if ("pool".equals(actionType)) {
                if (hasBorrowThisYear != null && hasBorrowThisYear > 0) {
                    return conflict("Borrowing and pooling are mutually exclusive in the same reporting period.");
                }
                if (hasPoolMembership != null && hasPoolMembership > 0) {
                    return conflict("A vessel can only participate in one pool per reporting period.");
                }
                if (effectiveBalance.compareTo(BigDecimal.ZERO) <= 0) {
                    return conflict("Pool creation requires positive pre-allocation balance for this simplified MVP flow.");
                }

                UUID poolId = UUID.randomUUID();
                jdbcTemplate.update("INSERT INTO pool (id, reporting_period_id, name, status, net_balance) VALUES (?, ?, ?, 'DRAFT', ?)",
                        poolId, periodId, "Strategic Pool 2025", amount);

                UUID participantId = UUID.randomUUID();
                jdbcTemplate.update("INSERT INTO pool_participant (id, pool_id, vessel_year_id) VALUES (?, ?, ?)",
                        participantId, poolId, vesselYearId);

                jdbcTemplate.update("INSERT INTO pool_allocation (id, pool_participant_id, allocated_compliance_transfer) VALUES (?, ?, ?)",
                        UUID.randomUUID(), participantId, amount);
                insertAudit("POOL_ALLOCATION", participantId, "system-admin", "CREATE",
                        "{\"amount\":" + amount + ",\"year\":" + reportingYear + "}");
            }
        } catch (Exception ex) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", actionType + " operation recorded.");
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "BAD_REQUEST");
        response.put("message", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    private ResponseEntity<Map<String, Object>> conflict(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "CONFLICT");
        response.put("message", message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    private BigDecimal firstNonNull(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    private void insertAudit(String entityType, UUID entityId, String actorId, String action, String afterPayloadJson) {
        jdbcTemplate.update(
                "INSERT INTO audit_event (id, entity_type, entity_id, actor_id, action, after_payload) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))",
                UUID.randomUUID(),
                entityType,
                entityId,
                actorId,
                action,
                afterPayloadJson
        );
    }
}
