package dev.shehab.smsrelay

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * One SMTP send. Implicit TLS on 465, STARTTLS on every other port. Timeouts
 * are short because this runs inside a JobService and a stuck socket should
 * become a retry, not a hang.
 */
object SmtpSender {
    fun send(settings: RelaySettings, sender: String, body: String) {
        val host = settings.host.trim()
        val username = settings.username.trim()
        val password = settings.password
        val to = settings.to.trim()
        val from = settings.from.trim().ifBlank { username }

        require(host.isNotEmpty()) { "SMTP host is empty" }
        require(settings.port in 1..65535) { "SMTP port is invalid" }
        require(username.isNotEmpty()) { "SMTP username is empty" }
        require(password.isNotEmpty()) { "SMTP password is empty" }
        require(to.contains("@")) { "Destination email is invalid" }
        require(from.contains("@")) { "From email is invalid" }

        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", settings.port.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "15000")
            put("mail.smtp.writetimeout", "15000")
            put("mail.smtp.ssl.protocols", "TLSv1.2")
            if (settings.port == 465) {
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.ssl.checkserveridentity", "true")
            } else {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(username, password)
        })

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(from))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false))
            subject = "SMS from $sender"
            setText(body, "UTF-8")
            setHeader("X-SMS-Relay", "1")
        }
        Transport.send(message)
    }
}
