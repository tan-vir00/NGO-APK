package com.ngoloanmanager.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** An error whose message is safe to show to the user. Anything else must be shown as a generic message. */
class UserFacingException(message: String) : Exception(message)

fun Throwable.userMessage(fallback: String): String = if (this is UserFacingException) (message ?: fallback) else fallback

object InputValidation {
    const val MAX_TERM_MONTHS = 600
    private const val MAX_TAKA_DIGITS = 12

    /** Taka text (e.g. "1500.50") -> integer poisha, rounded half-up per FINANCIAL_RULES.md. */
    fun takaToPoisha(text: String, label: String): Long {
        val t = text.trim()
        if (t.isEmpty()) throw UserFacingException("Enter $label")
        val value = try { BigDecimal(t) } catch (e: NumberFormatException) { throw UserFacingException("$label must be a valid number") }
        if (value.signum() <= 0) throw UserFacingException("$label must be greater than zero")
        if (value.precision() - value.scale() > MAX_TAKA_DIGITS) throw UserFacingException("$label is too large")
        return value.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    fun rate(text: String): BigDecimal {
        val t = text.trim()
        if (t.isEmpty()) throw UserFacingException("Enter an interest rate")
        val value = try { BigDecimal(t) } catch (e: NumberFormatException) { throw UserFacingException("Interest rate must be a valid number") }
        if (value.signum() < 0) throw UserFacingException("Interest rate cannot be negative")
        if (value.precision() - value.scale() > 4) throw UserFacingException("Interest rate is too large")
        return value
    }

    fun termMonths(text: String): Int {
        val n = text.trim().toIntOrNull() ?: throw UserFacingException("Enter the term as a whole number of months")
        if (n < 1 || n > MAX_TERM_MONTHS) throw UserFacingException("Term must be between 1 and $MAX_TERM_MONTHS months")
        return n
    }

    fun date(text: String, label: String): LocalDate =
        try { LocalDate.parse(text.trim()) } catch (e: DateTimeParseException) { throw UserFacingException("$label is invalid (YYYY-MM-DD)") }
}
