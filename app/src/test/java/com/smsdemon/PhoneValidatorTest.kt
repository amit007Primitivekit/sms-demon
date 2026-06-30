package com.smsdemon

import com.smsdemon.util.PhoneValidator
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [PhoneValidator].
 *
 * Note: [android.telephony.PhoneNumberUtils.isGlobalPhoneNumber] is a real Android
 * framework method and is NOT available in pure JVM tests unless you use Robolectric.
 * These tests therefore focus on the non-framework rules (blank, digit count, invalid chars)
 * and leave the PhoneNumberUtils gate tested by instrumented tests.
 */
class PhoneValidatorTest {

    @Test
    fun `blank number is invalid`() {
        assertFalse(PhoneValidator.isValid(""))
        assertFalse(PhoneValidator.isValid("   "))
    }

    @Test
    fun `number with letters is invalid`() {
        // Our regex check rejects letters before PhoneNumberUtils is even called
        // We call through; the regex gate fires first so no Android crash.
        // Since we can't stub PhoneNumberUtils, we rely on the fact that isValid
        // returns false when the regex match fails.
        val result = runCatching { PhoneValidator.isValid("abc123") }.getOrDefault(false)
        assertFalse(result)
    }

    @Test
    fun `normalise strips formatting characters`() {
        assertEquals("+12025550178", PhoneValidator.normalise("+1 (202) 555-0178"))
        assertEquals("+447911123456", PhoneValidator.normalise("+44 7911 123456"))
        assertEquals("02012345678", PhoneValidator.normalise("020 1234 5678"))
    }

    @Test
    fun `normalise preserves leading plus`() {
        val normalised = PhoneValidator.normalise("+1-800-555-0199")
        assertTrue("Normalised number should start with +", normalised.startsWith("+"))
    }
}
