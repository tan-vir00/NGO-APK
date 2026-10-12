package com.ngoloanmanager.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/** Money is stored as integer poisha (100 poisha = ৳1). Rates are percentages, e.g. 12.00. */
enum class InterestPeriod { ANNUAL, FULL_TERM }

data class LoanCalculation(val interestPoisha: Long, val totalPayablePoisha: Long, val installmentsPoisha: List<Long>)

object LoanMath {
    fun calculate(principalPoisha: Long, ratePercent: BigDecimal, termMonths: Int, period: InterestPeriod): LoanCalculation {
        require(principalPoisha >= 0) { "Principal cannot be negative" }
        require(ratePercent >= BigDecimal.ZERO) { "Rate cannot be negative" }
        require(termMonths > 0) { "Term must be at least one month" }
        val principal = BigDecimal.valueOf(principalPoisha)
        val divisor = BigDecimal("100")
        val rawInterest = when (period) {
            InterestPeriod.ANNUAL -> principal.multiply(ratePercent).multiply(BigDecimal.valueOf(termMonths.toLong())).divide(divisor.multiply(BigDecimal("12")), 0, RoundingMode.HALF_UP)
            InterestPeriod.FULL_TERM -> principal.multiply(ratePercent).divide(divisor, 0, RoundingMode.HALF_UP)
        }
        val interest = rawInterest.longValueExact()
        val total = Math.addExact(principalPoisha, interest)
        val base = total / termMonths
        val remainder = total % termMonths
        val installments = List(termMonths) { index -> base + if (index == termMonths - 1) remainder else 0L }
        check(installments.sum() == total)
        return LoanCalculation(interest, total, installments)
    }

    /** Keeps the original day where possible and clamps to month-end otherwise. */
    fun monthlyDueDates(firstDueDate: LocalDate, count: Int): List<LocalDate> {
        require(count > 0)
        val day = firstDueDate.dayOfMonth
        val firstMonth = YearMonth.from(firstDueDate)
        return (0 until count).map { offset ->
            val month = firstMonth.plusMonths(offset.toLong())
            month.atDay(minOf(day, month.lengthOfMonth()))
        }
    }
}
