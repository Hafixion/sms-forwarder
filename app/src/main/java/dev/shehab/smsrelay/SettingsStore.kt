package dev.shehab.smsrelay

import android.content.Context
import android.content.SharedPreferences
import java.text.DateFormat
import java.util.Date

/**
 * App-private settings. The SMTP password is stored as plain text inside the
 * app sandbox (allowBackup=false). That is a deliberate v1 tradeoff: no
 * keystore code to audit, no cloud backup, and the file is unreadable to other
 * apps on a non-rooted phone.
 */
object SettingsStore {
    private const val FILE = "relay-settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"
    private const val KEY_FROM = "from"
    private const val KEY_TO = "to"
    private const val KEY_SENDER_FILTER = "sender_filter"
    private const val KEY_BODY_FILTER = "body_filter"
    private const val KEY_LAST_STATUS = "last_status"
    private const val KEY_LAST_AT = "last_at"
    private const val KEY_ATTEMPTS = "attempts_"

    fun load(context: Context): RelaySettings {
        val prefs = prefs(context)
        val defaults = RelaySettings()
        return RelaySettings(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            host = prefs.getString(KEY_HOST, defaults.host) ?: defaults.host,
            port = prefs.getInt(KEY_PORT, defaults.port),
            username = prefs.getString(KEY_USERNAME, defaults.username) ?: defaults.username,
            password = prefs.getString(KEY_PASSWORD, defaults.password) ?: defaults.password,
            from = prefs.getString(KEY_FROM, defaults.from) ?: defaults.from,
            to = prefs.getString(KEY_TO, defaults.to) ?: defaults.to,
            senderFilter = prefs.getString(KEY_SENDER_FILTER, defaults.senderFilter)
                ?: defaults.senderFilter,
            bodyFilter = prefs.getString(KEY_BODY_FILTER, defaults.bodyFilter)
                ?: defaults.bodyFilter,
        )
    }

    fun save(context: Context, settings: RelaySettings) {
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_HOST, settings.host)
            .putInt(KEY_PORT, settings.port)
            .putString(KEY_USERNAME, settings.username)
            .putString(KEY_PASSWORD, settings.password)
            .putString(KEY_FROM, settings.from)
            .putString(KEY_TO, settings.to)
            .putString(KEY_SENDER_FILTER, settings.senderFilter)
            .putString(KEY_BODY_FILTER, settings.bodyFilter)
            .apply()
    }

    fun recordStatus(context: Context, status: String) {
        prefs(context).edit()
            .putString(KEY_LAST_STATUS, status)
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .apply()
    }

    fun lastStatus(context: Context): String {
        val prefs = prefs(context)
        val status = prefs.getString(KEY_LAST_STATUS, null) ?: return "Nothing sent yet."
        val at = prefs.getLong(KEY_LAST_AT, 0L)
        if (at <= 0L) return status
        return "$status · ${DateFormat.getDateTimeInstance().format(Date(at))}"
    }

    /** Retry counter per JobScheduler id, so a broken SMTP config stops after 3. */
    fun bumpAttempts(context: Context, jobId: Int): Int {
        val prefs = prefs(context)
        val key = KEY_ATTEMPTS + jobId
        val next = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, next).apply()
        return next
    }

    fun clearAttempts(context: Context, jobId: Int) {
        prefs(context).edit().remove(KEY_ATTEMPTS + jobId).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
