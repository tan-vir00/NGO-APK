package com.ngoloanmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class FinancialSummaryTest {
    private fun loan(id: Long, principal: Long, interest: Long, status: String = "ACTIVE") =
        LoanFinancials(id, principal, interest, principal + interest, status)
    private fun paid(loanId: Long, amount: Long, cancelled: Boolean = false) = PaymentFinancials(loanId, amount, cancelled)

    @Test fun outstandingIncludesInterestNotJustPrincipal() {
        val s = FinancialSummary.summarize(listOf(loan(1, 1_000_000, 120_000)), listOf(paid(1, 500_000)))
        assertEquals(1_000_000L, s.principalPoisha)
        assertEquals(120_000L, s.interestPoisha)
        assertEquals(1_120_000L, s.totalPayablePoisha)
        assertEquals(500_000L, s.collectedPoisha)
        assertEquals(620_000L, s.outstandingPoisha) // principal - collections would wrongly give 500_000
        assertEquals(0L, s.advancePoisha)
    }

    @Test fun cancelledPaymentsAreExcluded() {
        val s = FinancialSummary.summarize(listOf(loan(1, 1_000_000, 120_000)), listOf(paid(1, 500_000), paid(1, 300_000, cancelled = true)))
        assertEquals(500_000L, s.collectedPoisha)
        assertEquals(620_000L, s.outstandingPoisha)
    }

    @Test fun cancelledLoansAreExcludedWithTheirPayments() {
        val loans = listOf(loan(1, 1_000_000, 120_000), loan(2, 2_000_000, 240_000, status = LOAN_STATUS_CANCELLED))
        val s = FinancialSummary.summarize(loans, listOf(paid(1, 100_000), paid(2, 900_000)))
        assertEquals(1_000_000L, s.principalPoisha)
        assertEquals(120_000L, s.interestPoisha)
        assertEquals(1_120_000L, s.totalPayablePoisha)
        assertEquals(100_000L, s.collectedPoisha)
        assertEquals(1_020_000L, s.outstandingPoisha)
    }

    @Test fun advanceOnOneLoanNeverOffsetsAnotherLoansBalance() {
        val loans = listOf(loan(1, 80_000, 20_000), loan(2, 160_000, 40_000))
        val s = FinancialSummary.summarize(loans, listOf(paid(1, 150_000)))
        assertEquals(50_000L, s.advancePoisha)
        assertEquals(200_000L, s.outstandingPoisha) // not 150_000
        assertEquals(150_000L, s.collectedPoisha)
    }

    @Test fun fullyPaidLoanHasZeroOutstandingAndZeroAdvance() {
        val s = FinancialSummary.summarize(listOf(loan(1, 1_000_000, 120_000)), listOf(paid(1, 600_000), paid(1, 520_000)))
        assertEquals(0L, s.outstandingPoisha)
        assertEquals(0L, s.advancePoisha)
        assertEquals(1_120_000L, s.collectedPoisha)
    }

    @Test fun cancellingTheOverpaymentRemovesTheAdvanceExactlyOnce() {
        val loans = listOf(loan(1, 100_000, 0))
        val withAdvance = FinancialSummary.summarize(loans, listOf(paid(1, 100_000), paid(1, 25_000)))
        assertEquals(25_000L, withAdvance.advancePoisha)
        val afterCancel = FinancialSummary.summarize(loans, listOf(paid(1, 100_000), paid(1, 25_000, cancelled = true)))
        assertEquals(0L, afterCancel.advancePoisha)
        assertEquals(100_000L, afterCancel.collectedPoisha)
    }

    @Test fun portfolioIdentityHolds() {
        val loans = listOf(loan(1, 500_000, 60_000), loan(2, 300_000, 0), loan(3, 700_000, 84_000), loan(4, 100_000, 10_000, LOAN_STATUS_CANCELLED))
        val pays = listOf(paid(1, 600_000), paid(2, 100_000), paid(3, 0), paid(3, 40_000, true), paid(4, 50_000))
        val s = FinancialSummary.summarize(loans, pays)
        assertEquals(s.totalPayablePoisha - s.collectedPoisha + s.advancePoisha, s.outstandingPoisha)
        assertEquals(s.principalPoisha + s.interestPoisha, s.totalPayablePoisha)
    }

    @Test fun loanLevelAndPortfolioOutstandingReconcile() {
        val loans = listOf(loan(1, 500_000, 60_000), loan(2, 300_000, 0), loan(3, 700_000, 84_000))
        val pays = listOf(paid(1, 600_000), paid(2, 100_000))
        val perLoan = loans.sumOf { l -> FinancialSummary.outstanding(l.totalPayablePoisha, pays.filter { it.loanId == l.id }.sumOf { it.amountPoisha }) }
        assertEquals(perLoan, FinancialSummary.summarize(loans, pays).outstandingPoisha)
    }

    @Test fun usesLoanMathTotalsForInterestBearingLoan() {
        val calc = LoanMath.calculate(2_000_000, BigDecimal("12"), 10, InterestPeriod.ANNUAL)
        val l = LoanFinancials(1, 2_000_000, calc.interestPoisha, calc.totalPayablePoisha, "ACTIVE")
        val threeInstallments = calc.installmentsPoisha.take(3).sum()
        val s = FinancialSummary.summarize(listOf(l), listOf(paid(1, threeInstallments)))
        assertEquals(2_200_000L - 660_000L, s.outstandingPoisha)
    }

    @Test fun emptyDataIsAllZero() {
        assertEquals(PortfolioSummary(), FinancialSummary.summarize(emptyList(), emptyList()))
    }
}
