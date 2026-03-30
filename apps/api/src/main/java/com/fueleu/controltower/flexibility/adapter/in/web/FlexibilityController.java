package com.fueleu.controltower.flexibility.adapter.in.web;

import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/v1/flexibility")
@CrossOrigin(origins = "http://localhost:3000")
public class FlexibilityController {
    
    private static final Logger log = LoggerFactory.getLogger(FlexibilityController.class);
    private static final Set<String> EU_FLAGS = Set.of(
            "CYPRUS", "MALTA", "GREECE", "ITALY", "PORTUGAL", "SPAIN", "FRANCE", "GERMANY",
            "NETHERLANDS", "BELGIUM", "DENMARK", "SWEDEN", "FINLAND", "ESTONIA", "LATVIA",
            "LITHUANIA", "POLAND", "IRELAND", "CROATIA", "SLOVENIA", "ROMANIA", "BULGARIA"
    );
    private final JdbcTemplate jdbcTemplate;

    public FlexibilityController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/pools")
    public List<Map<String, Object>> listPools(@RequestParam(defaultValue = "2025") int year) {
        List<Map<String, Object>> pools = jdbcTemplate.query(
                "SELECT p.id AS pool_id, p.name, p.status, p.net_balance " +
                        "FROM pool p JOIN reporting_period rp ON p.reporting_period_id = rp.id " +
                        "WHERE rp.year = ? ORDER BY p.name",
                (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("poolId", rs.getObject("pool_id", UUID.class));
                    row.put("name", rs.getString("name"));
                    row.put("status", rs.getString("status"));
                    row.put("netBalance", rs.getBigDecimal("net_balance"));
                    return row;
                },
                year
        );

        for (Map<String, Object> pool : pools) {
            UUID poolId = (UUID) pool.get("poolId");
            List<Map<String, Object>> participants = jdbcTemplate.query(
                    "SELECT v.id AS vessel_id, v.name, pp.id AS participant_id, pa.allocated_compliance_transfer AS transfer " +
                            "FROM pool_participant pp " +
                            "JOIN vessel_year vy ON pp.vessel_year_id = vy.id " +
                            "JOIN vessel v ON vy.vessel_id = v.id " +
                            "LEFT JOIN pool_allocation pa ON pa.pool_participant_id = pp.id " +
                            "WHERE pp.pool_id = ? ORDER BY v.name",
                    (rs, rowNum) -> {
                        Map<String, Object> p = new HashMap<>();
                        p.put("vesselId", rs.getObject("vessel_id", UUID.class));
                        p.put("name", rs.getString("name"));
                        p.put("participantId", rs.getObject("participant_id", UUID.class));
                        p.put("transfer", rs.getBigDecimal("transfer"));
                        return p;
                    },
                    poolId
            );
            pool.put("participants", participants);
        }
        return pools;
    }

    @PostMapping("/pools")
    @Transactional
    public ResponseEntity<Map<String, Object>> createPoolTransfer(@RequestBody Map<String, Object> payload) {
        String sourceVesselIdRaw = payload.get("sourceVesselId") == null ? null : payload.get("sourceVesselId").toString();
        String targetVesselIdRaw = payload.get("targetVesselId") == null ? null : payload.get("targetVesselId").toString();
        String amountRaw = payload.get("amount") == null ? null : payload.get("amount").toString();
        int year = payload.get("year") == null ? 2025 : Integer.parseInt(payload.get("year").toString());
        if (sourceVesselIdRaw == null || targetVesselIdRaw == null || amountRaw == null) {
            return badRequest("Missing required fields: sourceVesselId, targetVesselId, amount.");
        }

        UUID sourceVesselId;
        UUID targetVesselId;
        BigDecimal amount;
        try {
            sourceVesselId = UUID.fromString(sourceVesselIdRaw);
            targetVesselId = UUID.fromString(targetVesselIdRaw);
            amount = new BigDecimal(amountRaw);
        } catch (Exception ex) {
            return badRequest("Invalid vessel IDs or amount.");
        }
        if (sourceVesselId.equals(targetVesselId)) {
            return badRequest("Source and target vessels must be different.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return badRequest("Pool transfer amount must be positive.");
        }

        UUID periodId = ensureReportingPeriod(year);
        UUID sourceVesselYearId = ensureVesselYear(sourceVesselId, periodId);
        UUID targetVesselYearId = ensureVesselYear(targetVesselId, periodId);

        Integer sourceInPool = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM pool_participant WHERE vessel_year_id = ?", Integer.class, sourceVesselYearId);
        Integer targetInPool = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM pool_participant WHERE vessel_year_id = ?", Integer.class, targetVesselYearId);
        Integer sourceBorrowed = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM borrowing_record WHERE vessel_year_id = ?", Integer.class, sourceVesselYearId);
        Integer targetBorrowed = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM borrowing_record WHERE vessel_year_id = ?", Integer.class, targetVesselYearId);
        if ((sourceInPool != null && sourceInPool > 0) || (targetInPool != null && targetInPool > 0)) {
            return conflict("One of the selected vessels is already in a pool for this reporting period.");
        }
        if ((sourceBorrowed != null && sourceBorrowed > 0) || (targetBorrowed != null && targetBorrowed > 0)) {
            return conflict("A selected vessel already borrowed this year; cannot join pool.");
        }

        List<Map<String, Object>> eligibility = getEligibility(year);
        Map<String, Object> sourceEligibility = eligibility.stream()
                .filter(e -> sourceVesselId.toString().equals(String.valueOf(e.get("vesselId"))))
                .findFirst()
                .orElse(null);
        Map<String, Object> targetEligibility = eligibility.stream()
                .filter(e -> targetVesselId.toString().equals(String.valueOf(e.get("vesselId"))))
                .findFirst()
                .orElse(null);
        if (sourceEligibility == null || !Boolean.TRUE.equals(sourceEligibility.get("canPool"))) {
            return conflict("Source vessel is not eligible to contribute to pool.");
        }
        if (targetEligibility == null || !Boolean.TRUE.equals(targetEligibility.get("canBorrow"))) {
            return conflict("Target vessel is not eligible deficit receiver for pool transfer.");
        }
        BigDecimal sourceBalance = toBigDecimal(sourceEligibility.get("effectiveBalance"));
        BigDecimal targetBalance = toBigDecimal(targetEligibility.get("effectiveBalance"));
        if (sourceBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return conflict("Source vessel has no positive surplus for pooling.");
        }
        if (targetBalance.compareTo(BigDecimal.ZERO) >= 0) {
            return conflict("Target vessel is not in deficit.");
        }
        BigDecimal maxTransfer = sourceBalance.min(targetBalance.abs());
        if (amount.compareTo(maxTransfer) > 0) {
            return conflict("Transfer exceeds allowed bound. Max transfer is " + maxTransfer.setScale(2, RoundingMode.HALF_UP) + " gCO2eq.");
        }

        UUID poolId = UUID.randomUUID();
        String poolName = "Pool-" + year + "-" + poolId.toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO pool (id, reporting_period_id, name, status, net_balance) VALUES (?, ?, ?, 'DRAFT', 0)", poolId, periodId, poolName);

        UUID sourceParticipant = UUID.randomUUID();
        UUID targetParticipant = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pool_participant (id, pool_id, vessel_year_id) VALUES (?, ?, ?)", sourceParticipant, poolId, sourceVesselYearId);
        jdbcTemplate.update("INSERT INTO pool_participant (id, pool_id, vessel_year_id) VALUES (?, ?, ?)", targetParticipant, poolId, targetVesselYearId);
        jdbcTemplate.update("INSERT INTO pool_allocation (id, pool_participant_id, allocated_compliance_transfer) VALUES (?, ?, ?)", UUID.randomUUID(), sourceParticipant, amount);
        jdbcTemplate.update("INSERT INTO pool_allocation (id, pool_participant_id, allocated_compliance_transfer) VALUES (?, ?, ?)", UUID.randomUUID(), targetParticipant, amount.negate());
        jdbcTemplate.update("UPDATE pool SET net_balance = 0 WHERE id = ?", poolId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("poolId", poolId);
        response.put("message", "Pool transfer created.");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/pools/{poolId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updatePoolTransfer(@PathVariable UUID poolId, @RequestBody Map<String, Object> payload) {
        String amountRaw = payload.get("amount") == null ? null : payload.get("amount").toString();
        if (amountRaw == null) {
            return badRequest("Missing required field: amount.");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw);
        } catch (Exception ex) {
            return badRequest("Invalid amount format.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return badRequest("Amount must be positive.");
        }

        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM pool WHERE id = ?", Integer.class, poolId);
        if (exists == null || exists == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "NOT_FOUND", "message", "Pool not found."));
        }
        String poolStatus = jdbcTemplate.queryForObject("SELECT status FROM pool WHERE id = ?", String.class, poolId);
        if (!"DRAFT".equalsIgnoreCase(poolStatus)) {
            return conflict("Only DRAFT pools can be edited.");
        }

        List<Map<String, Object>> participants = jdbcTemplate.query(
                "SELECT pp.id as participant_id, pa.allocated_compliance_transfer as transfer " +
                        "FROM pool_participant pp JOIN pool_allocation pa ON pa.pool_participant_id = pp.id " +
                        "WHERE pp.pool_id = ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("participantId", rs.getObject("participant_id", UUID.class));
                    row.put("transfer", rs.getBigDecimal("transfer"));
                    return row;
                },
                poolId
        );
        if (participants.size() < 2) {
            return badRequest("Pool does not have enough participants to edit transfer.");
        }

        participants.sort(Comparator.comparing(p -> ((BigDecimal) p.get("transfer")).signum()));
        UUID negativeParticipant = (UUID) participants.get(0).get("participantId");
        UUID positiveParticipant = (UUID) participants.get(participants.size() - 1).get("participantId");
        BigDecimal currentNegative = ((BigDecimal) participants.get(0).get("transfer")).abs();
        BigDecimal currentPositive = (BigDecimal) participants.get(participants.size() - 1).get("transfer");
        BigDecimal maxAllowed = currentPositive.min(currentNegative);
        if (amount.compareTo(maxAllowed) > 0) {
            return badRequest("Updated amount exceeds pool balance bound. Max allowed is " + maxAllowed.setScale(2, RoundingMode.HALF_UP) + " gCO2eq.");
        }
        jdbcTemplate.update("UPDATE pool_allocation SET allocated_compliance_transfer = ? WHERE pool_participant_id = ?", amount.negate(), negativeParticipant);
        jdbcTemplate.update("UPDATE pool_allocation SET allocated_compliance_transfer = ? WHERE pool_participant_id = ?", amount, positiveParticipant);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Pool transfer updated.");
        response.put("poolId", poolId);
        response.put("amount", amount.setScale(2, RoundingMode.HALF_UP));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pools/{poolId}/allocations")
    @Transactional
    public ResponseEntity<Map<String, Object>> addPoolAllocation(@PathVariable UUID poolId, @RequestBody Map<String, Object> payload) {
        String vesselIdRaw = payload.get("vesselId") == null ? null : payload.get("vesselId").toString();
        String transferRaw = payload.get("transfer") == null ? null : payload.get("transfer").toString();
        if (vesselIdRaw == null || transferRaw == null) return badRequest("Missing required fields: vesselId, transfer.");
        UUID vesselId;
        BigDecimal transfer;
        try {
            vesselId = UUID.fromString(vesselIdRaw);
            transfer = new BigDecimal(transferRaw);
        } catch (Exception ex) {
            return badRequest("Invalid vesselId or transfer.");
        }
        if (transfer.signum() == 0) return badRequest("Transfer cannot be zero.");

        Map<String, Object> pool;
        try {
            pool = jdbcTemplate.queryForMap("SELECT reporting_period_id, status FROM pool WHERE id = ?", poolId);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "NOT_FOUND", "message", "Pool not found."));
        }
        if (!"DRAFT".equalsIgnoreCase(String.valueOf(pool.get("status")))) return conflict("Only DRAFT pools can be edited.");
        UUID periodId = (UUID) pool.get("reporting_period_id");
        UUID vesselYearId = ensureVesselYear(vesselId, periodId);

        Integer existsInAnyPool = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM pool_participant WHERE vessel_year_id = ?", Integer.class, vesselYearId);
        if (existsInAnyPool != null && existsInAnyPool > 0) return conflict("Vessel already participates in a pool for this reporting period.");
        Integer borrowed = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM borrowing_record WHERE vessel_year_id = ?", Integer.class, vesselYearId);
        if (borrowed != null && borrowed > 0) return conflict("Borrowed vessel cannot be added to a pool in same reporting period.");

        int year = jdbcTemplate.queryForObject("SELECT year FROM reporting_period WHERE id = ?", Integer.class, periodId);
        List<Map<String, Object>> eligibility = getEligibility(year);
        Map<String, Object> vesselEligibility = eligibility.stream()
                .filter(e -> vesselId.toString().equals(String.valueOf(e.get("vesselId"))))
                .findFirst()
                .orElse(null);
        if (vesselEligibility == null) return badRequest("Vessel eligibility data missing.");
        BigDecimal balance = toBigDecimal(vesselEligibility.get("effectiveBalance"));
        if (transfer.signum() > 0 && (balance.compareTo(BigDecimal.ZERO) <= 0 || transfer.compareTo(balance) > 0)) {
            return conflict("Positive transfer exceeds available surplus.");
        }
        if (transfer.signum() < 0 && (balance.compareTo(BigDecimal.ZERO) >= 0 || transfer.abs().compareTo(balance.abs()) > 0)) {
            return conflict("Negative transfer exceeds vessel deficit.");
        }

        UUID participantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO pool_participant (id, pool_id, vessel_year_id) VALUES (?, ?, ?)", participantId, poolId, vesselYearId);
        jdbcTemplate.update("INSERT INTO pool_allocation (id, pool_participant_id, allocated_compliance_transfer) VALUES (?, ?, ?)", UUID.randomUUID(), participantId, transfer);
        jdbcTemplate.update("UPDATE pool SET net_balance = COALESCE((SELECT SUM(pa.allocated_compliance_transfer) FROM pool_participant pp JOIN pool_allocation pa ON pa.pool_participant_id = pp.id WHERE pp.pool_id = ?), 0) WHERE id = ?", poolId, poolId);

        BigDecimal netBalance = jdbcTemplate.queryForObject("SELECT net_balance FROM pool WHERE id = ?", BigDecimal.class, poolId);
        if (netBalance != null && netBalance.signum() < 0) {
            throw new IllegalStateException("Pool net balance cannot become negative.");
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "poolId", poolId, "message", "Participant allocation added."));
    }

    @DeleteMapping("/pools/{poolId}/participants/{vesselId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> removePoolParticipant(@PathVariable UUID poolId, @PathVariable UUID vesselId) {
        Map<String, Object> pool;
        try {
            pool = jdbcTemplate.queryForMap("SELECT reporting_period_id, status FROM pool WHERE id = ?", poolId);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "NOT_FOUND", "message", "Pool not found."));
        }
        if (!"DRAFT".equalsIgnoreCase(String.valueOf(pool.get("status")))) return conflict("Only DRAFT pools can remove participants.");

        UUID periodId = (UUID) pool.get("reporting_period_id");
        UUID vesselYearId;
        try {
            vesselYearId = jdbcTemplate.queryForObject("SELECT id FROM vessel_year WHERE vessel_id = ? AND reporting_period_id = ? LIMIT 1", UUID.class, vesselId, periodId);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "NOT_FOUND", "message", "Vessel-year not found."));
        }
        List<UUID> participantIds = jdbcTemplate.query(
                "SELECT id FROM pool_participant WHERE pool_id = ? AND vessel_year_id = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                poolId, vesselYearId
        );
        if (participantIds.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "NOT_FOUND", "message", "Participant not found in pool."));

        for (UUID pid : participantIds) {
            jdbcTemplate.update("DELETE FROM pool_allocation WHERE pool_participant_id = ?", pid);
            jdbcTemplate.update("DELETE FROM pool_participant WHERE id = ?", pid);
        }
        jdbcTemplate.update("UPDATE pool SET net_balance = COALESCE((SELECT SUM(pa.allocated_compliance_transfer) FROM pool_participant pp JOIN pool_allocation pa ON pa.pool_participant_id = pp.id WHERE pp.pool_id = ?), 0) WHERE id = ?", poolId, poolId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "poolId", poolId, "message", "Participant removed."));
    }

    @PostMapping("/pools/{poolId}/cancel")
    @Transactional
    public ResponseEntity<Map<String, Object>> cancelPool(@PathVariable UUID poolId) {
        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM pool WHERE id = ?", Integer.class, poolId);
        if (exists == null || exists == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "NOT_FOUND", "message", "Pool not found."));
        }
        jdbcTemplate.update("UPDATE pool SET status = 'CANCELLED', net_balance = 0 WHERE id = ?", poolId);
        List<UUID> participantIds = jdbcTemplate.query("SELECT id FROM pool_participant WHERE pool_id = ?", (rs, rowNum) -> rs.getObject("id", UUID.class), poolId);
        for (UUID pid : participantIds) jdbcTemplate.update("DELETE FROM pool_allocation WHERE pool_participant_id = ?", pid);
        jdbcTemplate.update("DELETE FROM pool_participant WHERE pool_id = ?", poolId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "poolId", poolId, "message", "Pool cancelled."));
    }

    @DeleteMapping("/pools/{poolId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deletePool(@PathVariable UUID poolId) {
        Map<String, Object> pool;
        try {
            pool = jdbcTemplate.queryForMap("SELECT status FROM pool WHERE id = ?", poolId);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "NOT_FOUND", "message", "Pool not found."));
        }
        String status = String.valueOf(pool.get("status"));
        if (!"DRAFT".equalsIgnoreCase(status) && !"CANCELLED".equalsIgnoreCase(status)) {
            return conflict("Only DRAFT or CANCELLED pools can be deleted.");
        }
        List<UUID> participantIds = jdbcTemplate.query("SELECT id FROM pool_participant WHERE pool_id = ?", (rs, rowNum) -> rs.getObject("id", UUID.class), poolId);
        for (UUID pid : participantIds) jdbcTemplate.update("DELETE FROM pool_allocation WHERE pool_participant_id = ?", pid);
        jdbcTemplate.update("DELETE FROM pool_participant WHERE pool_id = ?", poolId);
        jdbcTemplate.update("DELETE FROM pool WHERE id = ?", poolId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "poolId", poolId, "message", "Pool deleted."));
    }

    @GetMapping("/eligibility")
    public List<Map<String, Object>> getEligibility(@RequestParam(defaultValue = "2025") int year) {
        UUID periodId;
        try {
            periodId = jdbcTemplate.queryForObject("SELECT id FROM reporting_period WHERE year = ? LIMIT 1", UUID.class, year);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            periodId = null;
        }

        List<Map<String, Object>> vessels = jdbcTemplate.query(
                "SELECT id, name, imo_number, vessel_type, build_year, flag_state FROM vessel ORDER BY name",
                (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("vesselId", rs.getObject("id", UUID.class));
                    row.put("name", rs.getString("name"));
                    row.put("imoNumber", rs.getString("imo_number"));
                    row.put("vesselType", rs.getString("vessel_type"));
                    row.put("buildYear", rs.getObject("build_year", Integer.class));
                    row.put("flagState", rs.getString("flag_state"));
                    return row;
                }
        );

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> vessel : vessels) {
            UUID vesselId = (UUID) vessel.get("vesselId");
            UUID vesselYearId = null;
            if (periodId != null) {
                try {
                    vesselYearId = jdbcTemplate.queryForObject(
                            "SELECT id FROM vessel_year WHERE vessel_id = ? AND reporting_period_id = ? LIMIT 1",
                            UUID.class,
                            vesselId,
                            periodId
                    );
                } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {
                    vesselYearId = null;
                }
            }

            BigDecimal ghgLimit = periodId == null
                    ? new BigDecimal("89.3368")
                    : jdbcTemplate.queryForObject("SELECT ghg_limit FROM reporting_period WHERE id = ?", BigDecimal.class, periodId);

            BigDecimal energyInScope = BigDecimal.ZERO;
            BigDecimal icbValue = null;
            BigDecimal acbValue = null;
            BigDecimal vcbValue = null;
            Integer hasPoolMembership = 0;
            Integer hasBorrowThisYear = 0;
            Integer hasBankThisYear = 0;

            if (vesselYearId != null) {
                energyInScope = jdbcTemplate.queryForObject(
                        "SELECT COALESCE((SELECT energy_in_scope FROM compliance_calculation WHERE vessel_year_id = ?), 0)",
                        BigDecimal.class,
                        vesselYearId
                );
                icbValue = jdbcTemplate.queryForObject(
                        "SELECT (SELECT icb_value FROM compliance_calculation WHERE vessel_year_id = ?)",
                        BigDecimal.class,
                        vesselYearId
                );
                acbValue = jdbcTemplate.queryForObject(
                        "SELECT (SELECT acb_value FROM compliance_calculation WHERE vessel_year_id = ?)",
                        BigDecimal.class,
                        vesselYearId
                );
                vcbValue = jdbcTemplate.queryForObject(
                        "SELECT (SELECT vcb_value FROM compliance_calculation WHERE vessel_year_id = ?)",
                        BigDecimal.class,
                        vesselYearId
                );
                hasPoolMembership = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM pool_participant WHERE vessel_year_id = ?",
                        Integer.class,
                        vesselYearId
                );
                hasBorrowThisYear = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM borrowing_record WHERE vessel_year_id = ?",
                        Integer.class,
                        vesselYearId
                );
                hasBankThisYear = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM banking_record WHERE vessel_year_id = ?",
                        Integer.class,
                        vesselYearId
                );
            }

            BigDecimal effectiveBalance = firstNonNull(vcbValue, acbValue, icbValue, BigDecimal.ZERO);
            if (vcbValue == null && acbValue == null && icbValue == null) {
                int buildYear = vessel.get("buildYear") == null ? 2018 : (Integer) vessel.get("buildYear");
                String vesselType = vessel.get("vesselType") == null ? "container" : vessel.get("vesselType").toString();
                String flagState = vessel.get("flagState") == null ? "" : vessel.get("flagState").toString();
                DeterministicMetrics fallback = computeDeterministicMetrics(vesselType, buildYear, flagState, year, ghgLimit);
                effectiveBalance = fallback.icb;
                energyInScope = fallback.energyInScope;
            }
            BigDecimal purchasedCbGco2eq = vesselYearId == null ? BigDecimal.ZERO : jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(cb_amount_gco2eq), 0) FROM cb_market_trade WHERE vessel_year_id = ?",
                    BigDecimal.class,
                    vesselYearId
            );
            effectiveBalance = effectiveBalance.add(firstNonNull(purchasedCbGco2eq, BigDecimal.ZERO));

            BigDecimal borrowingCap = ghgLimit.multiply(energyInScope).multiply(new BigDecimal("0.02"));
            boolean canBank = effectiveBalance.compareTo(BigDecimal.ZERO) > 0 && (hasBankThisYear == null || hasBankThisYear == 0);
            boolean canBorrow = effectiveBalance.compareTo(BigDecimal.ZERO) < 0 && (hasPoolMembership == null || hasPoolMembership == 0) && (hasBorrowThisYear == null || hasBorrowThisYear == 0);
            boolean canPool = effectiveBalance.compareTo(BigDecimal.ZERO) > 0 && (hasBorrowThisYear == null || hasBorrowThisYear == 0) && (hasPoolMembership == null || hasPoolMembership == 0);

            Map<String, Object> row = new HashMap<>();
            row.put("vesselId", vesselId);
            row.put("name", vessel.get("name"));
            row.put("imoNumber", vessel.get("imoNumber"));
            row.put("effectiveBalance", effectiveBalance.setScale(2, RoundingMode.HALF_UP));
            row.put("purchasedCbGco2eq", firstNonNull(purchasedCbGco2eq, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            row.put("borrowingCap", borrowingCap.setScale(2, RoundingMode.HALF_UP));
            row.put("hasPoolMembership", hasPoolMembership != null && hasPoolMembership > 0);
            row.put("hasBorrowThisYear", hasBorrowThisYear != null && hasBorrowThisYear > 0);
            row.put("hasBankThisYear", hasBankThisYear != null && hasBankThisYear > 0);
            row.put("canBank", canBank);
            row.put("canBorrow", canBorrow);
            row.put("canPool", canPool);
            out.add(row);
        }

        return out;
    }

    @GetMapping("/strategy/recommendations")
    public List<Map<String, Object>> getStrategyRecommendations(@RequestParam(defaultValue = "2025") int year) {
        BigDecimal cbToGco2eq = new BigDecimal("1000000");
        BigDecimal vlsfoMjPerTon = new BigDecimal("41000");
        BigDecimal penaltyRateEurPerTon = new BigDecimal("2400");
        BigDecimal ghgLimit = new BigDecimal("89.3368");
        BigDecimal marketPriceEurPerCb;
        try {
            marketPriceEurPerCb = jdbcTemplate.queryForObject(
                    "SELECT quoted_price_eur_per_gco2eq * ? FROM cb_market_rate_snapshot ORDER BY captured_at DESC LIMIT 1",
                    BigDecimal.class,
                    cbToGco2eq
            );
        } catch (Exception ex) {
            marketPriceEurPerCb = new BigDecimal("200.00");
        }

        List<Map<String, Object>> eligibility = getEligibility(year);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : eligibility) {
            UUID vesselId = (UUID) row.get("vesselId");
            BigDecimal balance = toBigDecimal(row.get("effectiveBalance"));
            BigDecimal cap = toBigDecimal(row.get("borrowingCap"));
            boolean canPool = Boolean.TRUE.equals(row.get("canPool"));
            boolean canBorrow = Boolean.TRUE.equals(row.get("canBorrow"));
            boolean canBank = Boolean.TRUE.equals(row.get("canBank"));
            BigDecimal deficit = balance.signum() < 0 ? balance.abs() : BigDecimal.ZERO;
            BigDecimal deficitCb = deficit.divide(cbToGco2eq, 8, RoundingMode.HALF_UP);

            BigDecimal buyCost = deficitCb.multiply(marketPriceEurPerCb);
            BigDecimal penaltyCost = deficit.divide(ghgLimit.multiply(vlsfoMjPerTon), 12, RoundingMode.HALF_UP)
                    .multiply(penaltyRateEurPerTon);
            BigDecimal borrowCost = deficitCb.multiply(marketPriceEurPerCb).multiply(new BigDecimal("0.10"));

            String recommendation;
            if (balance.signum() > 0) recommendation = canBank ? "BANK_SURPLUS" : (canPool ? "POOL_CONTRIBUTE" : "HOLD");
            else if (balance.signum() == 0) recommendation = "NO_ACTION";
            else if (canPool) recommendation = "POOL_RECEIVE";
            else if (canBorrow && deficit.compareTo(cap) <= 0 && borrowCost.compareTo(buyCost.min(penaltyCost)) < 0) recommendation = "BORROW";
            else if (buyCost.compareTo(penaltyCost) < 0) recommendation = "BUY_CB";
            else recommendation = "PAY_PENALTY";

            Map<String, Object> rec = new HashMap<>();
            rec.put("vesselId", vesselId);
            rec.put("name", row.get("name"));
            rec.put("effectiveBalance", balance.setScale(2, RoundingMode.HALF_UP));
            rec.put("recommendation", recommendation);
            rec.put("estimatedPenaltyCostEur", penaltyCost.setScale(2, RoundingMode.HALF_UP));
            rec.put("estimatedBuyCostEur", buyCost.setScale(2, RoundingMode.HALF_UP));
            rec.put("estimatedBorrowCostEur", borrowCost.setScale(2, RoundingMode.HALF_UP));
            rec.put("benchmarkPriceEurPerCb", marketPriceEurPerCb.setScale(2, RoundingMode.HALF_UP));
            out.add(rec);
        }
        return out;
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
        // Fallback to deterministic vessel-based estimation if calculation rows are missing.
        if (vcbValue == null && acbValue == null && icbValue == null) {
            Map<String, Object> vesselMeta = jdbcTemplate.queryForMap(
                    "SELECT vessel_type, build_year, flag_state FROM vessel WHERE id = ?",
                    vesselId
            );
            int buildYear = vesselMeta.get("build_year") == null ? 2018 : ((Number) vesselMeta.get("build_year")).intValue();
            String vesselType = vesselMeta.get("vessel_type") == null ? "container" : vesselMeta.get("vessel_type").toString();
            String flagState = vesselMeta.get("flag_state") == null ? "" : vesselMeta.get("flag_state").toString();
            DeterministicMetrics fallback = computeDeterministicMetrics(vesselType, buildYear, flagState, reportingYear, ghgLimit);
            effectiveBalance = fallback.icb;
            energyInScope = fallback.energyInScope;
        }
        BigDecimal purchasedCbGco2eq = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(cb_amount_gco2eq), 0) FROM cb_market_trade WHERE vessel_year_id = ?",
                BigDecimal.class,
                vesselYearId
        );
        effectiveBalance = effectiveBalance.add(firstNonNull(purchasedCbGco2eq, BigDecimal.ZERO));
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

    private UUID ensureReportingPeriod(int year) {
        try {
            return jdbcTemplate.queryForObject("SELECT id FROM reporting_period WHERE year = ? LIMIT 1", UUID.class, year);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            UUID periodId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO reporting_period (id, year, status, ghg_limit) VALUES (?, ?, 'OPEN', 89.3368)", periodId, year);
            return periodId;
        }
    }

    private UUID ensureVesselYear(UUID vesselId, UUID periodId) {
        try {
            return jdbcTemplate.queryForObject("SELECT id FROM vessel_year WHERE vessel_id = ? AND reporting_period_id = ? LIMIT 1", UUID.class, vesselId, periodId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            UUID vesselYearId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO vessel_year (id, vessel_id, reporting_period_id) VALUES (?, ?, ?)", vesselYearId, vesselId, periodId);
            return vesselYearId;
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private DeterministicMetrics computeDeterministicMetrics(String vesselTypeRaw, int buildYear, String flagStateRaw, int year, BigDecimal ghgLimit) {
        String vesselType = vesselTypeRaw == null ? "container" : vesselTypeRaw.toLowerCase();
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

        int ageYears = Math.max(0, year - buildYear);
        BigDecimal exposureFactor = euFlag ? new BigDecimal("0.78") : new BigDecimal("0.52");
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
        BigDecimal actualIntensity = ghgLimit.add(typeDelta).add(ageDelta).subtract(exposureDelta);
        if (buildYear >= 2019 && ("lng".equals(vesselType) || "ro-ro".equals(vesselType) || "roro".equals(vesselType))) {
            actualIntensity = actualIntensity.subtract(new BigDecimal("0.80"));
        }
        BigDecimal icb = ghgLimit.subtract(actualIntensity).multiply(energy).setScale(2, RoundingMode.HALF_UP);

        return new DeterministicMetrics(energy.setScale(2, RoundingMode.HALF_UP), icb);
    }

    private record DeterministicMetrics(BigDecimal energyInScope, BigDecimal icb) {}
}
