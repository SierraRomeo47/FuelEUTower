package com.fueleu.controltower.compliance.application;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ComplianceCalculationService {

    /**
     * Calculates the true borrowing cap based on FuelEU limits.
     * Cap = 2% * Applicable GHG limit * Energy in scope
     */
    public BigDecimal calculateBorrowingCap(BigDecimal ghgLimit, BigDecimal energyInScope) {
        return ghgLimit.multiply(energyInScope)
                .multiply(new BigDecimal("0.02"))
                .setScale(4, RoundingMode.HALF_UP);
    }
    
    /**
     * Determines the Adjusted Compliance Balance (ACB).
     */
    public BigDecimal calculateAdjustedComplianceBalance(BigDecimal initialComplianceBalance, BigDecimal priorYearBanked, BigDecimal priorYearBorrowed, BigDecimal penaltyMultiplier) {
        // Prior year borrowed amount incurs a repayment penalty to the deficit
        // Usually 1.10x but dynamically pulled from the active reporting period's RegulatoryParameter
        BigDecimal borrowedPenalty = priorYearBorrowed.multiply(penaltyMultiplier);
        
        return initialComplianceBalance
                .add(priorYearBanked)
                .subtract(borrowedPenalty)
                .setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Determines the Initial Compliance Balance (ICB).
     * Formula: (GHG Target - GHG Actual) * Total Energy Consumption
     */
    public BigDecimal calculateInitialComplianceBalance(BigDecimal ghgLimit, BigDecimal actualGhgIntensity, BigDecimal energyInScope) {
        return ghgLimit.subtract(actualGhgIntensity)
                .multiply(energyInScope)
                .setScale(4, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculates the FuelEU Penalty for a given deficit.
     * Penalty = (|Compliance Balance| / (GHG Actual * VLSFO LCV limit)) * PenaltyRate
     */
    public BigDecimal calculatePenaltyAmount(BigDecimal residualDeficit, BigDecimal actualGhgIntensity, BigDecimal penaltyRate, BigDecimal vlsfoLcv, int consecutiveYears) {
        if (residualDeficit.compareTo(BigDecimal.ZERO) >= 0) {
            return BigDecimal.ZERO; // No penalty for surplus/zero
        }
        
        // Prevent Divide by Zero
        if (actualGhgIntensity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO; // A ship with 0 intensity consumed no fuel in scope
        }

        BigDecimal converter = actualGhgIntensity.multiply(vlsfoLcv);
        BigDecimal basePenalty = residualDeficit.abs()
                .divide(converter, 4, RoundingMode.HALF_UP)
                .multiply(penaltyRate);
                
        // Progressive escalation rule: +10% for each consecutive year
        int consecutivePeriods = Math.max(1, consecutiveYears);
        BigDecimal escalation = BigDecimal.valueOf(1 + (consecutivePeriods - 1) * 0.10);
        
        return basePenalty.multiply(escalation).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Computes final pool net balances to assert Pool Validity.
     * A pool is valid only if the total pooled compliance is positive.
     */
    public boolean isPoolValid(BigDecimal totalPoolIcb) {
        return totalPoolIcb.compareTo(BigDecimal.ZERO) >= 0;
    }
}
