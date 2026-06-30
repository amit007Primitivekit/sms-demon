package com.smsdemon.util

import android.telephony.PhoneNumberUtils
import java.util.Locale

/**
 * Lightweight validation helper for destination phone numbers.
 *
 * Android's [PhoneNumberUtils] is used as the primary authority.
 * The check is intentionally permissive – it rejects obvious garbage
 * (empty, too short, contains letters) but does not enforce country-
 * specific formats so international numbers work without extra config.
 */
object PhoneValidator {

    private val VALID_CHARS = Regex("^[+\\d\\s\\-().]+$")

    /**
     * Returns `true` if [number] looks like a plausible phone number.
     *
     * Rules applied:
     *  1. Not blank.
     *  2. Contains only digits, `+`, spaces, hyphens, dots, parentheses.
     *  3. At least 7 digits present.
     *  4. Passes [PhoneNumberUtils.isGlobalPhoneNumber].
     */
    fun isValid(number: String): Boolean {
        if (number.isBlank()) return false
        if (!VALID_CHARS.matches(number)) return false

        val digitsOnly = number.filter { it.isDigit() }
        if (digitsOnly.length < 7) return false

        return PhoneNumberUtils.isGlobalPhoneNumber(number)
    }

    /**
     * Normalises a number to E.164-ish format for SmsManager.
     * Simply strips whitespace/dashes/dots/parentheses while keeping leading `+`.
     */
    fun normalise(number: String): String =
        number.replace(Regex("[\\s\\-().]+"), "")
}
