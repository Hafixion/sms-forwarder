# SMS Relay

A small personal Android app that forwards matching incoming SMS to your email over
SMTP. It is **not** a Play Store app: it is built by GitHub Actions and sideloaded onto
one phone. There is no account, no analytics, no ads, and no server of ours — the app
talks only to the SMTP host you configure.

Package `dev.shehab.smsrelay` · minSdk 26 · compileSdk/targetSdk 35 · Kotlin + XML views
(no Compose).

## How it works

1. `SmsReceiver` gets `SMS_RECEIVED`, loads the settings, and applies the sender/body
   filter.
2. A matching message is handed to `JobScheduler` with a network constraint. No
   `WorkManager`, no foreground service, no boot receiver.
3. `SmsForwardJobService` sends one email through `SmtpSender` and stops. Failed jobs
   are retried with JobScheduler backoff, at most three attempts per message.
4. `MainActivity` is the single settings screen. The test send runs on a background
   thread, never on the main thread.

## Permissions: exactly three

| Permission | Why |
| --- | --- |
| `android.permission.RECEIVE_SMS` | Read the incoming SMS that matches your filter. Requested at runtime on the settings screen. |
| `android.permission.INTERNET` | Connect to the SMTP server you configure. |
| `android.permission.ACCESS_NETWORK_STATE` | Let JobScheduler wait for a network before running the send job. |

There is no contacts, storage, location, phone-state, notification, wake-lock,
foreground-service or boot permission. The two component permissions in the manifest
(`BROADCAST_SMS` on the receiver, `BIND_JOB_SERVICE` on the service) are enforced by
Android on those components, not granted to the app.

## Setup

### 1. Gmail App Password

Use an app password, **not** your normal Google password.

1. Turn on 2-Step Verification for the Google account.
2. Go to <https://myaccount.google.com/apppasswords>.
3. Create an app password (for example named `SMS Relay`) and copy the 16-character
   value. Google shows it in groups of four; enter it without spaces.
4. In the app set:

| Field | Value |
| --- | --- |
| SMTP host | `smtp.gmail.com` |
| Port | `465` |
| SMTP username | your full Gmail address |
| App password | the 16-character app password |
| From address | optional; leave empty to use the SMTP username |
| Send to | where the forwarded mail should arrive |

Port 465 uses implicit TLS; any other port uses STARTTLS. `From` must normally be the
Gmail account itself (or an alias configured in Gmail), otherwise Gmail may rewrite or
reject it.

### 2. Install

The APK is debug-signed, so no signing secrets are needed:

```bash
adb install -r app-debug.apk
```

If you install the APK from a file manager or browser instead, Android 13+ may block
sensitive permissions for "restricted" sideloaded apps. Open **Settings → Apps → SMS
Relay**, use the ⋮ menu, choose **Allow restricted settings**, then grant SMS. Installing
with `adb` usually skips that prompt.

### 3. Grant SMS permission

Open the app and tap **Grant SMS permission**, then allow it. Without `RECEIVE_SMS`
nothing is received and nothing is forwarded. The button reads "SMS permission granted"
once it is on; if you deny twice Android stops asking, so grant it from **Settings → Apps
→ SMS Relay → Permissions**.

### 4. Battery optimization (optional but recommended)

JobScheduler work is deferred in Doze and can be killed by aggressive OEM battery
managers (Xiaomi, Samsung, Oppo and others). To make delivery more reliable, set the app
to **Unrestricted** under **Settings → Apps → SMS Relay → Battery**. The app does not
request the battery-optimization exemption permission; this is a manual setting so the
permission list stays at three.

## Filters and OTP warnings

The sender filter is required; a blank sender filter matches nothing by design. Both
filters are pipe-separated, case-insensitive substring matches:

```
sender: Vodafone|Etisalat
body:   Vodafone Cash|رصيد
```

Sender and body must both match. An empty body filter forwards every message from a
matching sender.

**Do not use this app as an OTP or banking channel.** A broad body filter can forward
one-time passwords to email, where they live longer and are easier to leak; SMS sender
IDs can be spoofed; and forwarding bank messages may violate your bank's terms. Keep the
filters narrow, and prefer not to forward OTPs at all.

## Reliability: no delivery guarantee

This is a convenience relay, not a guaranteed pipe:

- JobScheduler may delay a send until the phone has network, is out of Doze, or is
  charging. Jobs are not persisted across a reboot, so a message queued just before a
  restart can be lost.
- A failed send is retried up to three times, then dropped. Errors are shown in the
  app's status line, not notified.
- If the app is force-stopped, Android will not deliver the SMS broadcast until you open
  the app again.
- Messages that arrive while forwarding is off, or that do not match the filter, are
  ignored and are not queued.
- SMTP itself can silently drop mail; there is no end-to-end receipt.

## Security and privacy

- The SMTP password is stored in the app's private `SharedPreferences` file. It is
  **not encrypted**: the file is readable only by this app on a non-rooted phone
  (`allowBackup="false"` keeps it out of cloud/ADB backups), but root access or a
  compromised device exposes it. Use a dedicated app password and revoke it in your
  Google account if the phone is lost.
- `allowBackup="false"`: no settings, password or message data is backed up.
- The password and full SMS bodies are never logged or shown outside the form/status
  line. Status text is stored app-private and may include the sender address.
- The only network destination is the SMTP host you configure. Cleartext traffic is
  disabled in the manifest; TLS is required.

## Project layout

```
app/src/main/java/dev/shehab/smsrelay/
  MainActivity.kt            settings screen, runtime permission, test send
  RelaySettings.kt           data class + the matching rule
  SettingsStore.kt           app-private SharedPreferences
  SmsReceiver.kt             SMS broadcast -> JobScheduler
  SmsForwardJobService.kt    one send per job, 3 attempts
  SmtpSender.kt              javax.mail send, implicit TLS / STARTTLS
  SmsText.kt                 multipart SMS joining
app/src/main/res/            layout, strings, colors, theme, launcher icon
app/src/test/java/...        JVM unit tests (no Android framework)
.github/workflows/android.yml
scripts/build.sh
```

## Tests

The unit tests cover `RelaySettings.matches` (sender+body, `|` alternatives, blank
sender, blank body, disabled) and `SmsText.joinParts`. They are plain JUnit 4 tests with
no Android dependencies:

```bash
gradle :app:testDebugUnitTest
```

There are no instrumentation or emulator tests.

## CI build (GitHub Actions)

`.github/workflows/android.yml` runs on every push, pull request and manual dispatch:

- checkout, Temurin JDK 17
- the runner's preinstalled Android SDK, then installs `platforms;android-35` and
  `build-tools;35.0.0`
- `gradle/actions/setup-gradle@v4` with Gradle 8.9
- `gradle :app:testDebugUnitTest --no-daemon` then
  `gradle :app:assembleDebug --no-daemon`
- uploads `app/build/outputs/apk/debug/app-debug.apk` as the `sms-relay-debug-apk`
  artifact (30 days)

No signing secrets are used; the artifact is debug-signed.

### Push and download the artifact

```bash
cd sms-forwarder
git remote add origin https://github.com/<you>/sms-forwarder.git
git branch -M main
git push -u origin main
```

Then open the repository on GitHub → **Actions** → the latest **Android** run →
**Artifacts** → download `sms-relay-debug-apk.zip`, unzip it, and install the contained
`app-debug.apk` with `adb install -r app-debug.apk`.

### Build locally (optional)

`scripts/build.sh` only calls a Gradle that is already installed; it never installs or
downloads a toolchain. You need JDK 17, an Android SDK with platform 35 and build-tools
35.0.0, and Gradle 8.9 on `PATH`:

```bash
ANDROID_HOME=/path/to/android-sdk scripts/build.sh
```

The repository intentionally has no Gradle wrapper; CI provides Gradle 8.9.

## Uninstall

```bash
adb uninstall dev.shehab.smsrelay
```

This removes the app, its settings and the stored app password. It does not delete mail
already forwarded, and it does not revoke the Google app password — revoke that at
<https://myaccount.google.com/apppasswords>.

## License

MIT. See [LICENSE](LICENSE).
