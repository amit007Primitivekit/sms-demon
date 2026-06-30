package com.smsdemon.util

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Resolves SMS template placeholders before each send.
 *
 * Supported tokens:
 *  - {random}    → cryptographically random 6-digit number (SecureRandom)
 *  - {timestamp} → current date-time formatted as "yyyy-MM-dd HH:mm:ss"
 *  - {counter}   → caller-supplied monotonic send counter
 */
object TemplateResolver {

    private val secureRandom = SecureRandom()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Substitutes all known placeholders in [template] and returns
     * both the resolved message and the individual resolved values so
     * they can be persisted in the log.
     *
     * @param template Raw template string containing zero or more placeholders.
     * @param counter  Current send counter value.
     * @return [ResolveResult] containing the fully resolved message plus the
     *         individual values that were substituted.
     */
    fun resolve(template: String, counter: Int): ResolveResult {
        // Generate a new 6-digit random number on every call
        val randomValue = String.format("%06d", secureRandom.nextInt(1_000_000))
        val timestamp   = dateFormat.format(Date())

        val message = template
            .replace(Constants.PLACEHOLDER_RANDOM, randomValue)
            .replace(Constants.PLACEHOLDER_TIMESTAMP, timestamp)
            .replace(Constants.PLACEHOLDER_COUNTER, counter.toString())

        return ResolveResult(
            message      = message,
            randomValue  = randomValue,
            timestamp    = timestamp,
            counter      = counter
        )
    }

    /** Structured return value from [resolve]. */
    data class ResolveResult(
        val message:     String,
        val randomValue: String,
        val timestamp:   String,
        val counter:     Int
    )
}
