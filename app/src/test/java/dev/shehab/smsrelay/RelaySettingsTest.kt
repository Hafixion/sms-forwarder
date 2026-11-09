package dev.shehab.smsrelay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the forwarding rule. No Android framework is involved,
 * so these run with `gradle :app:testDebugUnitTest`.
 */
class RelaySettingsTest {

    private val settings = RelaySettings(
        enabled = true,
        senderFilter = "Vodafone",
        bodyFilter = "Vodafone Cash|رصيد",
    )

    @Test
    fun matchesWhenSenderAndBodyMatch() {
        assertTrue(settings.matches("Vodafone", "You received 100 EGP via Vodafone Cash"))
    }

    @Test
    fun matchingIsCaseInsensitiveSubstring() {
        assertTrue(settings.matches("VODAFONE EG", "vodafone cash: 50 EGP"))
    }

    @Test
    fun pipeSeparatedSenderTermsAreAlternatives() {
        val relay = settings.copy(senderFilter = "Vodafone|Etisalat|Orange")
        assertTrue(relay.matches("Etisalat", "Vodafone Cash received"))
        assertTrue(relay.matches("Orange", "رصيد جديد"))
        assertFalse(relay.matches("WE", "Vodafone Cash received"))
    }

    @Test
    fun pipeSeparatedBodyTermsAreAlternatives() {
        assertTrue(settings.matches("Vodafone", "تم تحويل رصيد"))
        assertTrue(settings.matches("Vodafone", "Vodafone Cash: 10"))
        assertFalse(settings.matches("Vodafone", "Your bill is ready"))
    }

    @Test
    fun blankSenderFilterNeverMatches() {
        assertFalse(settings.copy(senderFilter = "").matches("Vodafone", "Vodafone Cash"))
        assertFalse(settings.copy(senderFilter = "   ").matches("Vodafone", "Vodafone Cash"))
        assertFalse(settings.copy(senderFilter = "|").matches("Vodafone", "Vodafone Cash"))
    }

    @Test
    fun blankBodyFilterForwardsOnSenderAlone() {
        assertTrue(settings.copy(bodyFilter = "").matches("Vodafone", "anything at all"))
        assertTrue(settings.copy(bodyFilter = "").matches("Vodafone", ""))
        assertTrue(settings.copy(bodyFilter = " |  ").matches("Vodafone", ""))
    }

    @Test
    fun bodyFilterMustMatchWhenSet() {
        assertFalse(settings.matches("Vodafone", "Your bill is ready"))
        assertFalse(settings.matches("Vodafone", ""))
    }

    @Test
    fun disabledNeverMatches() {
        assertFalse(settings.copy(enabled = false).matches("Vodafone", "Vodafone Cash"))
    }

    @Test
    fun senderMustMatch() {
        assertFalse(settings.matches("Etisalat", "Vodafone Cash"))
    }

    @Test
    fun termsAreTrimmedAndLowercased() {
        val relay = settings.copy(senderFilter = "  Vodafone  |  Etisalat ")
        assertTrue(relay.matches("etisalat", "Vodafone Cash"))
    }

    @Test
    fun blankTermsAreDiscarded() {
        assertEquals(
            listOf("vodafone", "etisalat"),
            RelaySettings.terms("Vodafone|| |Etisalat"),
        )
    }
}
