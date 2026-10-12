package com.ngoloanmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class InputValidationTest {
    @Test fun takaConvertsToPoishaWithHalfUpRounding() {
        assertEquals(150_050L, InputValidation.takaToPoisha("1500.50", "x"))
        assertEquals(1L, InputValidation.takaToPoisha("0.005", "x"))
        assertEquals(0L + 100_000L, InputValidation.takaToPoisha(" 1000 ", "x"))
    }
    @Test fun badMoneyInputGivesUserFacingErrors() {
        for (bad in listOf("", "  ", "abc", "-5", "0", "NaN", "1e30", "99999999999999")) {
            assertThrows(UserFacingException::class.java) { InputValidation.takaToPoisha(bad, "পরিমাণ") }
        }
    }
    @Test fun rateAndTermBounds() {
        assertEquals(BigDecimal("12.5"), InputValidation.rate("12.5"))
        assertThrows(UserFacingException::class.java) { InputValidation.rate("-1") }
        assertThrows(UserFacingException::class.java) { InputValidation.rate("x") }
        assertEquals(10, InputValidation.termMonths("10"))
        for (bad in listOf("0", "-3", "601", "abc", "", "2147483648")) {
            assertThrows(UserFacingException::class.java) { InputValidation.termMonths(bad) }
        }
    }
    @Test fun datesAreValidatedSafely() {
        assertEquals(LocalDate.of(2026, 2, 28), InputValidation.date("2026-02-28", "d"))
        for (bad in listOf("", "2026-02-30", "2026-13-01", "28/02/2026", "abc")) {
            assertThrows(UserFacingException::class.java) { InputValidation.date(bad, "d") }
        }
    }
    @Test fun onlyUserFacingMessagesAreExposed() {
        assertEquals("safe", UserFacingException("safe").userMessage("fallback"))
        assertEquals("fallback", IllegalStateException("SQLite constraint secret detail").userMessage("fallback"))
    }
}
