package dev.shehab.smsrelay

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Sends one SMS. JobScheduler owns the wake lock and the network constraint,
 * so this app does not need WAKE_LOCK or a foreground service.
 */
class SmsForwardJobService : JobService() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartJob(params: JobParameters): Boolean {
        val extras = params.extras
        val sender = extras.getString(SmsReceiver.KEY_SENDER)
        val body = extras.getString(SmsReceiver.KEY_BODY)
        val jobId = extras.getInt(SmsReceiver.KEY_JOB_ID, params.jobId)
        if (sender.isNullOrBlank() || body.isNullOrBlank()) {
            finish(params, false)
            return true
        }

        executor.execute {
            val settings = SettingsStore.load(applicationContext)
            if (!settings.enabled) {
                finish(params, false)
                return@execute
            }
            val result = runCatching { SmtpSender.send(settings, sender, body) }
            if (result.isSuccess) {
                SettingsStore.recordStatus(applicationContext, "Sent SMS from $sender")
                SettingsStore.clearAttempts(applicationContext, jobId)
                finish(params, false)
            } else {
                val attempts = SettingsStore.bumpAttempts(applicationContext, jobId)
                val reason = cleanReason(result.exceptionOrNull())
                SettingsStore.recordStatus(applicationContext, "Failed: $reason")
                finish(params, attempts < 3)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    private fun finish(params: JobParameters, reschedule: Boolean) {
        Handler(Looper.getMainLooper()).post { jobFinished(params, reschedule) }
    }

    private fun cleanReason(error: Throwable?): String {
        val raw = error?.message ?: error?.javaClass?.simpleName ?: "unknown error"
        return raw.replace('\n', ' ').take(180)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
