package com.smsdemon

import com.smsdemon.util.TemplateResolver
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TemplateResolver].
 *
 * These run on the JVM — no Android framework needed.
 */
class TemplateResolverTest {

    @Test
    fun `random placeholder replaced with 6-digit number`() {
        val result = TemplateResolver.resolve("{random}", counter = 1)
        assertTrue(
            "Random value must be a 6-digit numeric string",
            result.randomValue.matches(Regex("\\d{6}"))
        )
        assertEquals(result.randomValue, result.message)
    }

    @Test
    fun `counter placeholder replaced with correct value`() {
        val result = TemplateResolver.resolve("{counter}", counter = 42)
        assertEquals("42", result.message)
        assertEquals(42, result.counter)
    }

    @Test
    fun `timestamp placeholder replaced with non-empty string`() {
        val result = TemplateResolver.resolve("{timestamp}", counter = 1)
        assertTrue("Timestamp must not be empty", result.message.isNotEmpty())
        assertNotEquals("{timestamp}", result.message)
    }

    @Test
    fun `all placeholders resolved simultaneously`() {
        val template = "ID:{random} TS:{timestamp} C:{counter}"
        val result   = TemplateResolver.resolve(template, counter = 7)

        assertFalse("Resolved message must not contain {random}",     result.message.contains("{random}"))
        assertFalse("Resolved message must not contain {timestamp}",  result.message.contains("{timestamp}"))
        assertFalse("Resolved message must not contain {counter}",    result.message.contains("{counter}"))
    }

    @Test
    fun `template with no placeholders is unchanged`() {
        val template = "Hello World"
        val result   = TemplateResolver.resolve(template, counter = 1)
        assertEquals(template, result.message)
    }

    @Test
    fun `each call generates a different random value`() {
        val results = (1..20).map { TemplateResolver.resolve("{random}", counter = it) }
        val distinct = results.map { it.randomValue }.toSet()
        // With a 6-digit space (1M values), 20 calls should almost certainly yield multiple distinct values
        assertTrue("Should see multiple different random values in 20 calls", distinct.size > 1)
    }

    @Test
    fun `empty template resolved to empty string`() {
        val result = TemplateResolver.resolve("", counter = 1)
        assertEquals("", result.message)
    }
}
