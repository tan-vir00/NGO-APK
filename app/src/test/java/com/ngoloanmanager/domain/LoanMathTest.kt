package com.ngoloanmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class LoanMathTest {
    @Test fun annualFlatInterest() {
        val r = LoanMath.calculate(2_000_000, BigDecimal("12"), 10, InterestPeriod.ANNUAL)
        assertEquals(200_000L, r.interestPoisha)
        assertEquals(2_200_000L, r.totalPayablePoisha)
        assertEquals(List(10) { 220_000L }, r.installmentsPoisha)
    }
    @Test fun termFlatInterest() {
        val r = LoanMath.calculate(1_000_000, BigDecimal("12"), 10, InterestPeriod.FULL_TERM)
        assertEquals(120_000L, r.interestPoisha)
        assertEquals(1_120_000L, r.totalPayablePoisha)
    }
    @Test fun zeroInterestAndRemainder() {
        val r = LoanMath.calculate(1001, BigDecimal("0"), 3, InterestPeriod.FULL_TERM)
        assertEquals(listOf(333L, 333L, 335L), r.installmentsPoisha)
    }
    @Test fun monthEndDatesAreClamped() {
        assertEquals(listOf(LocalDate.parse("2026-01-31"), LocalDate.parse("2026-02-28"), LocalDate.parse("2026-03-31")), LoanMath.monthlyDueDates(LocalDate.parse("2026-01-31"), 3))
    }
}
