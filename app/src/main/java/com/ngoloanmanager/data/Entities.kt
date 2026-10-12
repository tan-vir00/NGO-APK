package com.ngoloanmanager.data

import androidx.room.*

@Entity(tableName = "admin")
data class AdminEntity(@PrimaryKey val id: Int = 1, val username: String, val salt: String, val passwordHash: String)

@Entity(tableName = "borrowers", indices = [Index(value = ["memberCode"], unique = true), Index(value = ["name"])])
data class BorrowerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberCode: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val nationalId: String = "",
    val registeredAt: String,
    val notes: String = "",
    val archived: Boolean = false
)

@Entity(tableName = "loans", foreignKeys = [ForeignKey(entity = BorrowerEntity::class, parentColumns = ["id"], childColumns = ["borrowerId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("borrowerId"), Index(value = ["loanCode"], unique = true)])
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanCode: String,
    val borrowerId: Long,
    val principalPoisha: Long,
    val ratePercent: String,
    val interestPeriod: String,
    val termMonths: Int,
    val loanDate: String,
    val firstDueDate: String,
    val interestPoisha: Long,
    val totalPayablePoisha: Long,
    val status: String = "ACTIVE",
    val notes: String = ""
)

@Entity(tableName = "installments", foreignKeys = [ForeignKey(entity = LoanEntity::class, parentColumns = ["id"], childColumns = ["loanId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("loanId"), Index(value = ["loanId", "number"], unique = true)])
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val number: Int,
    val dueDate: String,
    val dueAmountPoisha: Long,
    val allocatedPoisha: Long = 0
)

@Entity(tableName = "payments", foreignKeys = [ForeignKey(entity = LoanEntity::class, parentColumns = ["id"], childColumns = ["loanId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("loanId"), Index(value = ["receiptNo"], unique = true)])
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val receiptNo: String,
    val amountPoisha: Long,
    val paymentDate: String,
    val notes: String = "",
    val cancelled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "payment_allocations", primaryKeys = ["paymentId", "installmentId"], foreignKeys = [ForeignKey(entity = PaymentEntity::class, parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.RESTRICT), ForeignKey(entity = InstallmentEntity::class, parentColumns = ["id"], childColumns = ["installmentId"], onDelete = ForeignKey.RESTRICT)])
data class PaymentAllocationEntity(val paymentId: Long, val installmentId: Long, val amountPoisha: Long)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val action: String, val recordType: String, val recordId: String, val details: String, val timestamp: Long = System.currentTimeMillis())

@Entity(tableName = "app_settings")
data class AppSettingEntity(@PrimaryKey val key: String, val value: String)

data class BorrowerLoanRow(@Embedded val borrower: BorrowerEntity, @Relation(parentColumn = "id", entityColumn = "borrowerId") val loans: List<LoanEntity>)
