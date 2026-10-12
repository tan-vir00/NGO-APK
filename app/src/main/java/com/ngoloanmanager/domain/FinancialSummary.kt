package com.ngoloanmanager.domain

const val LOAN_STATUS_CANCELLED = "CANCELLED"

data class LoanFinancials(val id: Long, val principalPoisha: Long, val interestPoisha: Long, val totalPayablePoisha: Long, val status: String)
data class PaymentFinancials(val loanId: Long, val amountPoisha: Long, val cancelled: Boolean)

data class PortfolioSummary(
    val principalPoisha: Long = 0,
    val interestPoisha: Long = 0,
    val totalPayablePoisha: Long = 0,
    /** Valid (non-cancelled) payments on non-cancelled loans, advances included. */
    val collectedPoisha: Long = 0,
    /** Sum of per-loan max(0, totalPayable - paid). One loan's advance never offsets another loan's balance. */
    val outstandingPoisha: Long = 0,
    /** Sum of per-loan max(0, paid - totalPayable). Always: outstanding = totalPayable - collected + advance. */
    val advancePoisha: Long = 0
)

/** All values are integer poisha. Totals come from canonical loan.totalPayable (principal + interest), never principal alone. */
object FinancialSummary {
    fun outstanding(totalPayablePoisha: Long, paidPoisha: Long): Long = maxOf(0L, totalPayablePoisha - paidPoisha)
    fun advance(totalPayablePoisha: Long, paidPoisha: Long): Long = maxOf(0L, paidPoisha - totalPayablePoisha)

    fun summarize(loans: List<LoanFinancials>, payments: List<PaymentFinancials>): PortfolioSummary {
        val paidByLoan = payments.filter { !it.cancelled }.groupBy { it.loanId }.mapValues { (_, v) -> v.sumOf { it.amountPoisha } }
        var principal = 0L; var interest = 0L; var total = 0L; var collected = 0L; var out = 0L; var adv = 0L
        for (loan in loans) {
            if (loan.status == LOAN_STATUS_CANCELLED) continue
            val paid = paidByLoan[loan.id] ?: 0L
            principal += loan.principalPoisha
            interest += loan.interestPoisha
            total += loan.totalPayablePoisha
            collected += paid
            out += outstanding(loan.totalPayablePoisha, paid)
            adv += advance(loan.totalPayablePoisha, paid)
        }
        return PortfolioSummary(principal, interest, total, collected, out, adv)
    }
}
