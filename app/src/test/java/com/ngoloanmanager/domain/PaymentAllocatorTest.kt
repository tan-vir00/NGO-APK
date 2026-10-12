package com.ngoloanmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PaymentAllocatorTest {
    private val three = listOf(InstallmentDue(1, 1, 100), InstallmentDue(2, 2, 100), InstallmentDue(3, 3, 100))
    private fun pay(id: Long, date: String, amount: Long) = PaymentInput(id, date, amount)

    @Test fun partialPaymentFillsOldestInstallmentFirst() {
        val r = PaymentAllocator.allocate(three, listOf(pay(1, "2026-01-01", 150)))
        assertEquals(listOf(AllocationLine(1, 1, 100), AllocationLine(1, 2, 50)), r.lines)
        assertEquals(mapOf(1L to 100L, 2L to 50L, 3L to 0L), r.allocatedByInstallment)
        assertEquals(0L, r.advancePoisha)
    }

    @Test fun cancellingFirstOfMultiplePaymentsReallocatesRemainingOnes() {
        val p1 = pay(1, "2026-01-01", 100)
        val p2 = pay(2, "2026-02-01", 100)
        val before = PaymentAllocator.allocate(three, listOf(p1, p2))
        assertEquals(mapOf(1L to 100L, 2L to 100L, 3L to 0L), before.allocatedByInstallment)
        // cancel p1 -> only p2 is valid and must now settle the OLDEST installment
        val after = PaymentAllocator.allocate(three, listOf(p2))
        assertEquals(mapOf(1L to 100L, 2L to 0L, 3L to 0L), after.allocatedByInstallment)
        assertEquals(listOf(AllocationLine(2, 1, 100)), after.lines)
    }

    @Test fun partialPaymentsAcrossInstallmentsAfterCancellingOne() {
        val inst = listOf(InstallmentDue(1, 1, 100), InstallmentDue(2, 2, 100))
        val p1 = pay(1, "2026-01-01", 50)
        val p2 = pay(2, "2026-01-02", 70)
        val p3 = pay(3, "2026-01-03", 30)
        val all = PaymentAllocator.allocate(inst, listOf(p1, p2, p3))
        assertEquals(mapOf(1L to 100L, 2L to 50L), all.allocatedByInstallment)
        val afterCancelP1 = PaymentAllocator.allocate(inst, listOf(p2, p3))
        assertEquals(listOf(AllocationLine(2, 1, 70), AllocationLine(3, 1, 30)), afterCancelP1.lines)
        assertEquals(mapOf(1L to 100L, 2L to 0L), afterCancelP1.allocatedByInstallment)
    }

    @Test fun advanceBalanceIsDerivedAndDisappearsWhenOverpaymentIsCancelled() {
        val inst = listOf(InstallmentDue(1, 1, 100), InstallmentDue(2, 2, 100))
        val base = pay(1, "2026-01-01", 100)
        val big = pay(2, "2026-01-05", 150)
        val with = PaymentAllocator.allocate(inst, listOf(base, big))
        assertEquals(50L, with.advancePoisha)
        assertEquals(200L, with.lines.sumOf { it.amountPoisha })
        val without = PaymentAllocator.allocate(inst, listOf(base))
        assertEquals(0L, without.advancePoisha)
        assertEquals(mapOf(1L to 100L, 2L to 0L), without.allocatedByInstallment)
    }

    @Test fun backdatedPaymentIsAllocatedChronologicallyNotByInsertionOrder() {
        val inst = listOf(InstallmentDue(1, 1, 100), InstallmentDue(2, 2, 100))
        val inserted1 = pay(1, "2026-02-01", 60)
        val inserted2Backdated = pay(2, "2026-01-01", 60)
        val r = PaymentAllocator.allocate(inst, listOf(inserted1, inserted2Backdated))
        assertEquals(listOf(AllocationLine(2, 1, 60), AllocationLine(1, 1, 40), AllocationLine(1, 2, 20)), r.lines)
    }

    @Test fun sameDatePaymentsUseIdAsTieBreaker() {
        val inst = listOf(InstallmentDue(1, 1, 100))
        val r = PaymentAllocator.allocate(inst, listOf(pay(5, "2026-01-01", 80), pay(4, "2026-01-01", 80)))
        assertEquals(listOf(AllocationLine(4, 1, 80), AllocationLine(5, 1, 20)), r.lines)
        assertEquals(60L, r.advancePoisha)
    }

    @Test fun allocationIsDeterministicSoRepeatedRebuildsNeverDuplicate() {
        val payments = listOf(pay(1, "2026-01-01", 130), pay(2, "2026-01-02", 90))
        val a = PaymentAllocator.allocate(three, payments)
        val b = PaymentAllocator.allocate(three, payments.reversed())
        assertEquals(a, b)
        assertEquals(payments.sumOf { it.amountPoisha }, a.lines.sumOf { it.amountPoisha } + a.advancePoisha)
        assertEquals(a.lines.map { it.paymentId to it.installmentId }.distinct().size, a.lines.size)
    }

    @Test fun noValidPaymentsMeansNothingAllocated() {
        val r = PaymentAllocator.allocate(three, emptyList())
        assertEquals(emptyList<AllocationLine>(), r.lines)
        assertEquals(mapOf(1L to 0L, 2L to 0L, 3L to 0L), r.allocatedByInstallment)
        assertEquals(0L, r.advancePoisha)
    }

    @Test fun installmentsAreAllocatedByNumberEvenIfListedOutOfOrder() {
        val shuffled = listOf(InstallmentDue(3, 3, 100), InstallmentDue(1, 1, 100), InstallmentDue(2, 2, 100))
        val r = PaymentAllocator.allocate(shuffled, listOf(pay(1, "2026-01-01", 100)))
        assertEquals(listOf(AllocationLine(1, 1, 100)), r.lines)
    }

    @Test fun nonPositivePaymentIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { PaymentAllocator.allocate(three, listOf(pay(1, "2026-01-01", 0))) }
    }

    @Test fun worksWithRealLoanMathSchedule() {
        val calc = LoanMath.calculate(1_000_001, java.math.BigDecimal("0"), 3, InterestPeriod.FULL_TERM)
        val inst = calc.installmentsPoisha.mapIndexed { i, amt -> InstallmentDue(i + 1L, i + 1, amt) }
        val r = PaymentAllocator.allocate(inst, listOf(pay(1, "2026-01-01", calc.totalPayablePoisha)))
        assertEquals(inst.associate { it.id to it.dueAmountPoisha }, r.allocatedByInstallment)
        assertEquals(0L, r.advancePoisha)
    }
}
