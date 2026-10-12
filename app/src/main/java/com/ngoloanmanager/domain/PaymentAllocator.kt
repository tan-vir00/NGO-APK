package com.ngoloanmanager.domain

data class InstallmentDue(val id: Long, val number: Int, val dueAmountPoisha: Long)
data class PaymentInput(val id: Long, val paymentDate: String, val amountPoisha: Long)
data class AllocationLine(val paymentId: Long, val installmentId: Long, val amountPoisha: Long)
data class AllocationResult(
    val lines: List<AllocationLine>,
    /** installmentId -> total allocated poisha (every installment of the loan is present). */
    val allocatedByInstallment: Map<Long, Long>,
    /** Valid payment amount left after every scheduled installment is fully allocated. */
    val advancePoisha: Long
)

/**
 * Pure allocation rule from FINANCIAL_RULES.md: valid payments are applied in chronological order
 * (paymentDate, then id) and each payment fills the oldest unpaid installment first.
 * Pass ONLY non-cancelled payments. Dates are ISO yyyy-MM-dd (validated on input), so string order = date order.
 */
object PaymentAllocator {
    fun allocate(installments: List<InstallmentDue>, validPayments: List<PaymentInput>): AllocationResult {
        val ordered = installments.sortedBy { it.number }
        val allocated = LinkedHashMap<Long, Long>()
        ordered.forEach { allocated[it.id] = 0L }
        val lines = ArrayList<AllocationLine>()
        var advance = 0L
        for (payment in validPayments.sortedWith(compareBy<PaymentInput>({ it.paymentDate }, { it.id }))) {
            require(payment.amountPoisha > 0) { "Payment amount must be positive" }
            var remaining = payment.amountPoisha
            for (inst in ordered) {
                if (remaining == 0L) break
                val due = inst.dueAmountPoisha - allocated.getValue(inst.id)
                if (due <= 0L) continue
                val take = minOf(due, remaining)
                allocated[inst.id] = allocated.getValue(inst.id) + take
                lines.add(AllocationLine(payment.id, inst.id, take))
                remaining -= take
            }
            advance += remaining
        }
        return AllocationResult(lines, allocated, advance)
    }
}
