package com.fueleu.controltower.flexibility.application;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class FlexibilityService {

    /**
     * Enforce strict banking regulatory guidelines.
     */
    public void validateBankingRequest(BigDecimal finalVerifiedSurplus, boolean isDocIssued) {
        if (isDocIssued) {
            throw new IllegalStateException("Regulatory Violation: Banking cannot be initiated after the Document of Compliance (DoC) has been issued.");
        }
        if (finalVerifiedSurplus.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Regulatory Violation: Banking logic requires a strict positive verified surplus.");
        }
    }

    /**
     * Enforce strict borrowing regulatory caps and timeline states.
     */
    public void validateBorrowingRequest(BigDecimal requestedAmount, BigDecimal borrowingCap, boolean borrowedLastYear, BigDecimal residualDeficit) {
        if (borrowedLastYear) {
            throw new IllegalStateException("Regulatory Violation: Borrowing mechanism is strictly prohibited in two consecutive reporting periods.");
        }
        if (residualDeficit.compareTo(BigDecimal.ZERO) >= 0) {
            throw new IllegalStateException("Regulatory Violation: Borrowing can only be requested if a registered deficit exists.");
        }
        if (requestedAmount.compareTo(borrowingCap) > 0) {
            throw new IllegalStateException("Regulatory Violation: Requested borrowing amount exceeds the 2% maximum statutory cap.");
        }
        
        BigDecimal absoluteDeficit = residualDeficit.abs();
        if (requestedAmount.compareTo(absoluteDeficit) > 0) {
            throw new IllegalArgumentException("Logical Violation: Cannot borrow more than the required residual deficit.");
        }
    }

    /**
     * Enforce cross-mechanism rules. Borrowing and Pooling cannot apply to the same ship in the same year.
     */
    public void validateMutuallyExclusiveMechanisms(boolean hasBorrowingRecord, boolean hasPoolAllocation) {
        if (hasBorrowingRecord && hasPoolAllocation) {
            throw new IllegalStateException("Regulatory Violation: A ship cannot participate in both Borrowing and Pooling simultaneously in a single verification period.");
        }
    }
}
