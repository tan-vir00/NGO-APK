package com.ngoloanmanager.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM borrowers WHERE archived = 0 ORDER BY name COLLATE NOCASE") fun borrowers(): Flow<List<BorrowerEntity>>
    @Query("SELECT * FROM borrowers WHERE archived = 0 AND (name LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%' OR memberCode LIKE '%' || :q || '%') ORDER BY name") fun searchBorrowers(q: String): Flow<List<BorrowerEntity>>
    @Query("SELECT * FROM borrowers WHERE id = :id") suspend fun borrower(id: Long): BorrowerEntity?
    @Insert suspend fun insertBorrower(item: BorrowerEntity): Long
    @Update suspend fun updateBorrower(item: BorrowerEntity)
    @Query("UPDATE borrowers SET archived = 1 WHERE id = :id") suspend fun archiveBorrower(id: Long)
    @Query("SELECT * FROM loans ORDER BY id DESC") fun loans(): Flow<List<LoanEntity>>
    @Query("SELECT * FROM loans WHERE borrowerId = :borrowerId ORDER BY id DESC") fun loansForBorrower(borrowerId: Long): Flow<List<LoanEntity>>
    @Query("SELECT * FROM loans WHERE id = :id") suspend fun loan(id: Long): LoanEntity?
    @Insert suspend fun insertLoan(item: LoanEntity): Long
    @Update suspend fun updateLoan(item: LoanEntity)
    @Query("SELECT * FROM installments WHERE loanId = :loanId ORDER BY number") fun installments(loanId: Long): Flow<List<InstallmentEntity>>
    @Query("SELECT * FROM installments WHERE loanId = :loanId ORDER BY number") suspend fun installmentsNow(loanId: Long): List<InstallmentEntity>
    @Insert suspend fun insertInstallments(items: List<InstallmentEntity>)
    @Update suspend fun updateInstallment(item: InstallmentEntity)
    @Insert suspend fun insertPayment(item: PaymentEntity): Long
    @Query("SELECT * FROM payments WHERE loanId = :loanId ORDER BY paymentDate DESC, id DESC") fun payments(loanId: Long): Flow<List<PaymentEntity>>
    @Query("SELECT * FROM payments WHERE id = :id") suspend fun payment(id: Long): PaymentEntity?
    @Query("SELECT * FROM payments WHERE loanId = :loanId AND cancelled = 0") suspend fun validPayments(loanId: Long): List<PaymentEntity>
    @Update suspend fun updatePayment(item: PaymentEntity)
    /** Atomic guard: returns 0 if the payment is already cancelled (or missing). The row itself is kept for audit. */
    @Query("UPDATE payments SET cancelled = 1 WHERE id = :id AND cancelled = 0") suspend fun markPaymentCancelled(id: Long): Int
    @Query("SELECT * FROM payments") fun allPayments(): Flow<List<PaymentEntity>>
    /** Only used for allocation rows of still-valid payments (derived data). Rows of cancelled payments are never deleted. */
    @Query("DELETE FROM payment_allocations WHERE paymentId IN (:paymentIds)") suspend fun deleteAllocationsForPayments(paymentIds: List<Long>)
    @Insert suspend fun insertAllocation(item: PaymentAllocationEntity)
    @Query("SELECT * FROM payment_allocations WHERE paymentId = :paymentId") suspend fun allocations(paymentId: Long): List<PaymentAllocationEntity>
    @Query("SELECT * FROM loans") suspend fun allLoans(): List<LoanEntity>
    @Query("SELECT * FROM borrowers WHERE archived = 0") suspend fun allBorrowers(): List<BorrowerEntity>
    @Query("SELECT COUNT(*) FROM borrowers WHERE archived = 0") fun borrowerCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM loans WHERE status = 'ACTIVE'") fun activeLoanCount(): Flow<Int>
    @Query("SELECT * FROM admin WHERE id = 1") suspend fun admin(): AdminEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAdmin(item: AdminEntity)
    @Insert suspend fun audit(item: AuditLogEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setSetting(item: AppSettingEntity)
    @Query("SELECT value FROM app_settings WHERE `key` = :key") suspend fun setting(key: String): String?
}

@Database(entities = [AdminEntity::class, BorrowerEntity::class, LoanEntity::class, InstallmentEntity::class, PaymentEntity::class, PaymentAllocationEntity::class, AuditLogEntity::class, AppSettingEntity::class], version = 1, exportSchema = true)
abstract class LoanDatabase : RoomDatabase() {
    abstract fun dao(): LoanDao
    companion object {
        @Volatile private var INSTANCE: LoanDatabase? = null
        fun get(context: Context): LoanDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, LoanDatabase::class.java, "ngo_loan_manager.db").build().also { INSTANCE = it }
        }
    }
}
