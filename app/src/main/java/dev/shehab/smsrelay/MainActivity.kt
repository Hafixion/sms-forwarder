package dev.shehab.smsrelay

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The only screen: settings, the enable switch, the SMS permission, and a test
 * send. Nothing is saved until Save is pressed; the test send uses the values
 * on screen and runs on a single background thread, never on the main thread.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var hostLayout: TextInputLayout
    private lateinit var hostInput: TextInputEditText
    private lateinit var portLayout: TextInputLayout
    private lateinit var portInput: TextInputEditText
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var fromLayout: TextInputLayout
    private lateinit var fromInput: TextInputEditText
    private lateinit var toLayout: TextInputLayout
    private lateinit var toInput: TextInputEditText
    private lateinit var senderLayout: TextInputLayout
    private lateinit var senderInput: TextInputEditText
    private lateinit var bodyLayout: TextInputLayout
    private lateinit var bodyInput: TextInputEditText
    private lateinit var enabledSwitch: SwitchMaterial
    private lateinit var saveButton: MaterialButton
    private lateinit var testButton: MaterialButton
    private lateinit var grantSmsButton: MaterialButton
    private lateinit var statusText: TextView

    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        applyEdgeToEdge()
        fillForm(SettingsStore.load(this))
        wireActions()
        refreshPermissionUi()
    }

    override fun onResume() {
        super.onResume()
        // The permission may have been granted from system settings while away.
        refreshPermissionUi()
    }

    override fun onDestroy() {
        sendExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        hostLayout = findViewById(R.id.hostLayout)
        hostInput = findViewById(R.id.hostInput)
        portLayout = findViewById(R.id.portLayout)
        portInput = findViewById(R.id.portInput)
        usernameLayout = findViewById(R.id.usernameLayout)
        usernameInput = findViewById(R.id.usernameInput)
        passwordLayout = findViewById(R.id.passwordLayout)
        passwordInput = findViewById(R.id.passwordInput)
        fromLayout = findViewById(R.id.fromLayout)
        fromInput = findViewById(R.id.fromInput)
        toLayout = findViewById(R.id.toLayout)
        toInput = findViewById(R.id.toInput)
        senderLayout = findViewById(R.id.senderLayout)
        senderInput = findViewById(R.id.senderInput)
        bodyLayout = findViewById(R.id.bodyLayout)
        bodyInput = findViewById(R.id.bodyInput)
        enabledSwitch = findViewById(R.id.enabledSwitch)
        saveButton = findViewById(R.id.saveButton)
        testButton = findViewById(R.id.testButton)
        grantSmsButton = findViewById(R.id.grantSmsButton)
        statusText = findViewById(R.id.statusText)
    }

    /**
     * targetSdk 35 enforces edge-to-edge, so the app draws its own background
     * behind the system bars and pads for the bars plus the keyboard.
     */
    private fun applyEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !night
        controller.isAppearanceLightNavigationBars = !night

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
            )
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            insets
        }
    }

    private fun fillForm(settings: RelaySettings) {
        hostInput.setText(settings.host)
        portInput.setText(settings.port.toString())
        usernameInput.setText(settings.username)
        passwordInput.setText(settings.password)
        fromInput.setText(settings.from)
        toInput.setText(settings.to)
        senderInput.setText(settings.senderFilter)
        bodyInput.setText(settings.bodyFilter)
        enabledSwitch.isChecked = settings.enabled
        statusText.text = SettingsStore.lastStatus(this)
    }

    private fun wireActions() {
        saveButton.setOnClickListener {
            val settings = readForm(forTest = false) ?: return@setOnClickListener
            SettingsStore.save(this, settings)
            if (settings.enabled && !hasSmsPermission()) {
                showStatus(getString(R.string.status_saved_needs_permission))
            } else {
                showStatus(getString(R.string.status_saved))
            }
        }

        testButton.setOnClickListener {
            val settings = readForm(forTest = true) ?: return@setOnClickListener
            startTestSend(settings)
        }

        grantSmsButton.setOnClickListener {
            if (hasSmsPermission()) {
                showStatus(getString(R.string.status_permission_granted))
                refreshPermissionUi()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECEIVE_SMS),
                    REQ_SMS,
                )
            }
        }
    }

    private fun startTestSend(settings: RelaySettings) {
        setBusy(true)
        showStatus(getString(R.string.status_testing))

        val body = getString(
            R.string.test_body,
            DateFormat.getDateTimeInstance().format(Date()),
        )
        sendExecutor.execute {
            // SmtpSender throws on bad configuration or network failure. The
            // message is shown, not logged, and never contains the password.
            val failure = runCatching {
                SmtpSender.send(settings, TEST_SENDER, body)
            }.exceptionOrNull()

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    val message = if (failure == null) {
                        getString(R.string.status_test_sent, settings.to)
                    } else {
                        getString(R.string.status_test_failed, cleanReason(failure))
                    }
                    SettingsStore.recordStatus(this, message)
                    showStatus(message)
                    setBusy(false)
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        testButton.isEnabled = !busy
        saveButton.isEnabled = !busy
        testButton.text = getString(if (busy) R.string.sending else R.string.send_test)
    }

    /**
     * Returns the form as settings, or null after marking the bad fields. A
     * fully disabled app is allowed to be saved without a working mailbox, so
     * the mail fields are required only while forwarding is on or when the
     * caller is about to send a test email.
     */
    private fun readForm(forTest: Boolean): RelaySettings? {
        clearErrors()

        val host = text(hostInput)
        val port = text(portInput).toIntOrNull() ?: 0
        val username = text(usernameInput)
        val password = text(passwordInput)
        val from = text(fromInput)
        val to = text(toInput)
        val senderFilter = text(senderInput)
        val bodyFilter = text(bodyInput)
        val enabled = enabledSwitch.isChecked
        val needsMailbox = enabled || forTest

        var firstBad: View? = null
        fun fail(layout: TextInputLayout, field: TextInputEditText, message: String) {
            layout.error = message
            if (firstBad == null) firstBad = field
        }

        if (host.isEmpty()) {
            fail(hostLayout, hostInput, getString(R.string.error_host_required))
        } else if (host.any { it.isWhitespace() }) {
            fail(hostLayout, hostInput, getString(R.string.error_host_space))
        }
        if (port !in 1..65535) {
            fail(portLayout, portInput, getString(R.string.error_port))
        }
        if (needsMailbox) {
            if (username.isEmpty()) {
                fail(usernameLayout, usernameInput, getString(R.string.error_username))
            }
            if (password.isEmpty()) {
                fail(passwordLayout, passwordInput, getString(R.string.error_password))
            }
            if (!looksLikeEmail(to)) {
                fail(toLayout, toInput, getString(R.string.error_to))
            }
            if (RelaySettings.terms(senderFilter).isEmpty()) {
                fail(senderLayout, senderInput, getString(R.string.error_sender_filter))
            }
        }
        if (from.isNotEmpty() && !looksLikeEmail(from)) {
            fail(fromLayout, fromInput, getString(R.string.error_from))
        }

        val bad = firstBad
        if (bad != null) {
            bad.requestFocus()
            return null
        }

        return RelaySettings(
            enabled = enabled,
            host = host,
            port = port,
            username = username,
            password = password,
            from = from,
            to = to,
            senderFilter = senderFilter,
            bodyFilter = bodyFilter,
        )
    }

    private fun clearErrors() {
        hostLayout.error = null
        portLayout.error = null
        usernameLayout.error = null
        passwordLayout.error = null
        fromLayout.error = null
        toLayout.error = null
        senderLayout.error = null
        bodyLayout.error = null
    }

    private fun text(input: TextInputEditText): String =
        input.text?.toString()?.trim().orEmpty()

    private fun looksLikeEmail(value: String): Boolean {
        val at = value.indexOf('@')
        return at > 0 && at < value.length - 1 && !value.contains(' ')
    }

    private fun cleanReason(error: Throwable): String {
        val raw = error.message ?: error.javaClass.simpleName
        return raw.replace('\n', ' ').replace('\r', ' ').trim().take(160)
    }

    private fun showStatus(message: String) {
        statusText.text = message
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun refreshPermissionUi() {
        val granted = hasSmsPermission()
        grantSmsButton.isEnabled = !granted
        grantSmsButton.text = getString(
            if (granted) R.string.permission_granted else R.string.grant_sms,
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_SMS) return
        refreshPermissionUi()
        showStatus(
            if (hasSmsPermission()) {
                getString(R.string.status_permission_granted)
            } else {
                getString(R.string.status_permission_denied)
            },
        )
    }

    companion object {
        private const val REQ_SMS = 1001
        private const val TEST_SENDER = "SMS Relay test"
    }
}
