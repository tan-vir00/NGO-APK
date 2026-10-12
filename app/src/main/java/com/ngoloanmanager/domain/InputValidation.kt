package com.ngoloanmanager.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** An error whose (Bangla) message is safe to show to the user. Anything else must be shown as a generic message. */
class UserFacingException(message: String) : Exception(message)

fun Throwable.userMessage(fallback: String): String = if (this is UserFacingException) (message ?: fallback) else fallback

object InputValidation {
    const val MAX_TERM_MONTHS = 600
    private const val MAX_TAKA_DIGITS = 12

    /** Taka text (e.g. "1500.50") -> integer poisha, rounded half-up per FINANCIAL_RULES.md. */
    fun takaToPoisha(text: String, label: String): Long {
        val t = text.trim()
        if (t.isEmpty()) throw UserFacingException("$label লিখুন")
        val value = try { BigDecimal(t) } catch (e: NumberFormatException) { throw UserFacingException("$label সঠিক সংখ্যা হতে হবে") }
        if (value.signum() <= 0) throw UserFacingException("$label শূন্যের চেয়ে বেশি হতে হবে")
        if (value.precision() - value.scale() > MAX_TAKA_DIGITS) throw UserFacingException("$label অনেক বড়")
        return value.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    fun rate(text: String): BigDecimal {
        val t = text.trim()
        if (t.isEmpty()) throw UserFacingException("সুদের হার লিখুন")
        val value = try { BigDecimal(t) } catch (e: NumberFormatException) { throw UserFacingException("সুদের হার সঠিক সংখ্যা হতে হবে") }
        if (value.signum() < 0) throw UserFacingException("সুদের হার ঋণাত্মক হতে পারে না")
        if (value.precision() - value.scale() > 4) throw UserFacingException("সুদের হার অনেক বড়")
        return value
    }

    fun termMonths(text: String): Int {
        val n = text.trim().toIntOrNull() ?: throw UserFacingException("মেয়াদ পূর্ণ সংখ্যায় (মাস) লিখুন")
        if (n < 1 || n > MAX_TERM_MONTHS) throw UserFacingException("মেয়াদ ১ থেকে $MAX_TERM_MONTHS মাসের মধ্যে হতে হবে")
        return n
    }

    fun date(text: String, label: String): LocalDate =
        try { LocalDate.parse(text.trim()) } catch (e: DateTimeParseException) { throw UserFacingException("$label সঠিক নয় (YYYY-MM-DD)") }
}
