package com.ngoloanmanager.data

import androidx.room.withTransaction
import com.ngoloanmanager.domain.InputValidation
import com.ngoloanmanager.domain.InstallmentDue
import com.ngoloanmanager.domain.LOAN_STATUS_CANCELLED
import com.ngoloanmanager.domain.PaymentAllocator
import com.ngoloanmanager.domain.PaymentInput
import com.ngoloanmanager.domain.UserFacingException
import java.time.LocalDate
import java.util.UUID

/**
 * All payment writes go through here so that payment row + allocations + installment totals + audit log
 * change in ONE Room transaction. Any exception rolls everything back.
 * Cancelled payments are never deleted; their allocation rows are kept as history.
 */
class PaymentRepository(private val db: LoanDatabase) {
    private val dao get() = db.dao()

    suspend fun recordPayment(loanId: Long, amountPoisha: Long, paymentDate: String, notes: String): Long {
        if (amountPoisha <= 0) throw UserFacingException("পরিমাণ শূন্যের চেয়ে বেশি হতে হবে")
        val date = InputValidation.date(paymentDate, "পেমেন্টের তারিখ")
        return db.withTransaction {
            val loan = dao.loan(loanId) ?: throw UserFacingException("ঋণটি খুঁজে পাওয়া যায়নি")
            if (loan.status == LOAN_STATUS_CANCELLED) throw UserFacingException("বাতিল ঋণে পেমেন্ট নেওয়া যাবে না")
            if (date.isBefore(LocalDate.parse(loan.loanDate))) throw UserFacingException("পেমেন্টের তারিখ ঋণের তারিখের আগে হতে পারে না")
            val receipt = "RC-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4).uppercase()}"
            val paymentId = dao.insertPayment(
                PaymentEntity(loanId = loanId, receiptNo = receipt, amountPoisha = amountPoisha, paymentDate = date.toString(), notes = notes.trim())
            )
            reallocate(loanId)
            dao.audit(
                AuditLogEntity(
                    action = "PAYMENT_RECORDED", recordType = "payment", recordId = paymentId.toString(),
                    details = "Receipt $receipt, loan ${loan.loanCode}, amount $amountPoisha poisha, date $date"
                )
            )
            paymentId
        }
    }

    suspend fun cancelPayment(paymentId: Long, reason: String) {
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) throw UserFacingException("বাতিলের কারণ লিখুন")
        if (cleanReason.length > 200) throw UserFacingException("বাতিলের কারণ ২০০ অক্ষরের মধ্যে লিখুন")
        db.withTransaction {
            val payment = dao.payment(paymentId) ?: throw UserFacingException("পেমেন্টটি খুঁজে পাওয়া যায়নি")
            if (payment.cancelled) throw UserFacingException("এই পেমেন্ট আগেই বাতিল করা হয়েছে")
            if (dao.markPaymentCancelled(paymentId) != 1) throw UserFacingException("এই পেমেন্ট আগেই বাতিল করা হয়েছে")
            val loan = dao.loan(payment.loanId)
            reallocate(payment.loanId)
            dao.audit(
                AuditLogEntity(
                    action = "PAYMENT_CANCELLED", recordType = "payment", recordId = paymentId.toString(),
                    details = "Cancelled receipt ${payment.receiptNo}, loan ${loan?.loanCode ?: payment.loanId}, amount ${payment.amountPoisha} poisha, reason: $cleanReason"
                )
            )
        }
    }

    /** Must be called inside a transaction. Rebuilds allocations of VALID payments: chronological, oldest installment first. */
    private suspend fun reallocate(loanId: Long) {
        val installments = dao.installmentsNow(loanId)
        val valid = dao.validPayments(loanId)
        val result = PaymentAllocator.allocate(
            installments.map { InstallmentDue(it.id, it.number, it.dueAmountPoisha) },
            valid.map { PaymentInput(it.id, it.paymentDate, it.amountPoisha) }
        )
        if (valid.isNotEmpty()) dao.deleteAllocationsForPayments(valid.map { it.id })
        result.lines.forEach { dao.insertAllocation(PaymentAllocationEntity(it.paymentId, it.installmentId, it.amountPoisha)) }
        installments.forEach { inst ->
            val allocated = result.allocatedByInstallment.getValue(inst.id)
            if (allocated != inst.allocatedPoisha) dao.updateInstallment(inst.copy(allocatedPoisha = allocated))
        }
    }
}
