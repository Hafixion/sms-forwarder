package dev.shehab.smsrelay

import java.util.Locale

/**
 * The whole configuration, and the one place the forwarding rule lives.
 *
 * A message is forwarded only when the app is enabled, the sender contains one
 * of the sender terms, and (if any body terms are set) the body contains one of
 * them. Terms are separated by `|`; matching is case-insensitive substring.
 * An empty sender filter never matches — forwarding every SMS is not a feature.
 */
data class RelaySettings(
    val enabled: Boolean = false,
    val host: String = "smtp.gmail.com",
    val port: Int = 465,
    val username: String = "",
    val password: String = "",
    val from: String = "",
    val to: String = "",
    val senderFilter: String = "Vodafone",
    val bodyFilter: String = "Vodafone Cash|فودافون كاش|رصيد",
) {
    fun matches(sender: String, body: String): Boolean {
        if (!enabled) return false
        val senderTerms = terms(senderFilter)
        if (senderTerms.isEmpty()) return false
        val normalizedSender = sender.lowercase(Locale.ROOT)
        if (senderTerms.none { normalizedSender.contains(it) }) return false
        val bodyTerms = terms(bodyFilter)
        if (bodyTerms.isEmpty()) return true
        val normalizedBody = body.lowercase(Locale.ROOT)
        return bodyTerms.any { normalizedBody.contains(it) }
    }

    companion object {
        fun terms(value: String): List<String> =
            value.split('|')
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }
    }
}
