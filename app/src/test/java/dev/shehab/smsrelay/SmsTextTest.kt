package dev.shehab.smsrelay

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM tests for multipart SMS joining. */
class SmsTextTest {

    @Test
    fun singlePartIsUnchanged() {
        assertEquals("hello", SmsText.joinParts(listOf("hello")))
    }

    @Test
    fun multipartIsConcatenatedWithoutSeparator() {
        assertEquals("Hello world", SmsText.joinParts(listOf("Hello ", "world")))
    }

    @Test
    fun orderIsPreserved() {
        assertEquals("12345", SmsText.joinParts(listOf("1", "2", "3", "4", "5")))
    }

    @Test
    fun emptyListBecomesEmptyString() {
        assertEquals("", SmsText.joinParts(emptyList()))
    }

    @Test
    fun whitespaceAndNewlinesArePreserved() {
        assertEquals("a\nb  c", SmsText.joinParts(listOf("a\nb", "  c")))
    }
}
