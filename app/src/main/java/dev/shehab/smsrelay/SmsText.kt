package dev.shehab.smsrelay

object SmsText {
    /** A multipart SMS arrives as several bodies that concatenate with no separator. */
    fun joinParts(parts: List<String>): String = parts.joinToString(separator = "")
}
