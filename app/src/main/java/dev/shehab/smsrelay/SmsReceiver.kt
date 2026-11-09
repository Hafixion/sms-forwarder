package dev.shehab.smsrelay

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.provider.Telephony
import java.security.MessageDigest

/**
 * Receives the system SMS broadcast. It does no network work here: it filters,
 * then hands a small payload to JobScheduler. That keeps the receiver under
 * Android's broadcast time limit and avoids a foreground service.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val settings = SettingsStore.load(context)
        if (!settings.enabled) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        val first = parts.first()
        val sender = first.displayOriginatingAddress ?: first.originatingAddress ?: return
        val body = SmsText.joinParts(parts.mapNotNull { it.displayMessageBody })
        if (!settings.matches(sender, body)) return

        val whenMillis = first.timestampMillis
        val jobId = stableId(sender, whenMillis, body)
        val extras = PersistableBundle().apply {
            putString(KEY_SENDER, sender)
            putString(KEY_BODY, body)
            putLong(KEY_WHEN, whenMillis)
            putInt(KEY_JOB_ID, jobId)
        }
        val job = JobInfo.Builder(jobId, ComponentName(context, SmsForwardJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setExtras(extras)
            .build()

        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        runCatching { scheduler.schedule(job) }
    }

    private fun stableId(sender: String, whenMillis: Long, body: String): Int {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sender\n$whenMillis\n$body".toByteArray(Charsets.UTF_8))
        return ((digest[0].toInt() and 0xff) shl 24) or
            ((digest[1].toInt() and 0xff) shl 16) or
            ((digest[2].toInt() and 0xff) shl 8) or
            (digest[3].toInt() and 0xff)
    }

    companion object {
        const val KEY_SENDER = "sender"
        const val KEY_BODY = "body"
        const val KEY_WHEN = "when"
        const val KEY_JOB_ID = "job_id"
    }
}
