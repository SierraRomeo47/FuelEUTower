package com.fueleu.controltower.compliance.application;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ComplianceCalculationServiceTest {

    private final ComplianceCalculationService service = new ComplianceCalculationService();

    @Test
    void dnVExample1_matchesComplianceBalanceAndPenalty() {
        // DNV methodology PDF example (Fossil HFO + MDO):
        // GHGI_target = 89.33680 gCO2eq/MJ
        // GHGI_actual = 91.63722 gCO2eq/MJ
        // Energy_in_scope = 545,780,000 MJ
        // Expected compliance balance = -1,255,523,227.6 gCO2eq
        // Expected penalty ≈ 802,011 EUR (rounded to whole EUR in the PDF)
        BigDecimal ghgLimit = new BigDecimal("89.33680");
        BigDecimal actualGhgIntensity = new BigDecimal("91.63722");
        BigDecimal energyInScope = new BigDecimal("545780000");

        BigDecimal cb = service.calculateInitialComplianceBalance(ghgLimit, actualGhgIntensity, energyInScope);
        assertEquals(new BigDecimal("-1255523227.6000"), cb);

        BigDecimal penalty = service.calculatePenaltyAmount(
                cb,
                actualGhgIntensity,
                new BigDecimal("2400"),
                new BigDecimal("41000"),
                1
        );

        // Service returns 2 decimals; the DNV example reports whole EUR (802,011).
        assertEquals(new BigDecimal("802011"), penalty.setScale(0, RoundingMode.HALF_UP));
    }

    @Test
    void testCalculateInitialComplianceBalance() {
        BigDecimal ghgLimit = new BigDecimal("89.3368");
        BigDecimal actualGhgIntensity = new BigDecimal("85.0000");
        BigDecimal energyInScope = new BigDecimal("1000000");

        BigDecimal icb = service.calculateInitialComplianceBalance(ghgLimit, actualGhgIntensity, energyInScope);
        
        // (89.3368 - 85.0) * 1000000 = 4336800
        assertEquals(new BigDecimal("4336800.0000"), icb);
    }
    
    @Test
    void testCalculatePenaltyAmount_NoEscalation() {
        BigDecimal residualDeficit = new BigDecimal("-10000000");
        BigDecimal actualGhgIntensity = new BigDecimal("85.0");
        BigDecimal penaltyRate = new BigDecimal("2400");
        BigDecimal vlsfoLcv = new BigDecimal("41000");

        BigDecimal penalty = service.calculatePenaltyAmount(residualDeficit, actualGhgIntensity, penaltyRate, vlsfoLcv, 1);
        
        // Converter = 85 * 41000 = 3485000
        // BasePenalty = 10000000 / 3485000 = 2.8694 * 2400 = 6886.56
        assertEquals(new BigDecimal("6886.56"), penalty);
    }

    @Test
    void testCalculatePenaltyAmount_WithEscalation() {
        // Consecutive year 2 means +10% penalty
        BigDecimal residualDeficit = new BigDecimal("-10000000");
        BigDecimal actualGhgIntensity = new BigDecimal("85.0");
        BigDecimal penaltyRate = new BigDecimal("2400");
        BigDecimal vlsfoLcv = new BigDecimal("41000");

        BigDecimal penalty = service.calculatePenaltyAmount(residualDeficit, actualGhgIntensity, penaltyRate, vlsfoLcv, 2);
        
        // 6886.56 * 1.10 = 7575.22 (2.8694 * 2400 * 1.1)
        assertEquals(new BigDecimal("7575.22"), penalty);
    }

    @Test
    void testCalculateBorrowingCap() {
        BigDecimal cap = service.calculateBorrowingCap(new BigDecimal("89.3368"), new BigDecimal("1000000"));
        // 89.3368 * 1000000 * 0.02 = 1786736
        assertEquals(new BigDecimal("1786736.0000"), cap);
    }

    @Test
    void testCalculateAdjustedComplianceBalance() {
        BigDecimal icb = new BigDecimal("5000");
        BigDecimal banked = new BigDecimal("1000");
        BigDecimal borrowed = new BigDecimal("2000");
        BigDecimal penaltyMultiplier = new BigDecimal("1.10");

        BigDecimal acb = service.calculateAdjustedComplianceBalance(icb, banked, borrowed, penaltyMultiplier);
        // 5000 + 1000 - (2000 * 1.1) = 6000 - 2200 = 3800
        assertEquals(new BigDecimal("3800.0000"), acb);
    }
}
