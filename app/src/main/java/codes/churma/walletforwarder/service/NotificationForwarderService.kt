package codes.churma.walletforwarder.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import codes.churma.walletforwarder.data.PackageWhitelist
import codes.churma.walletforwarder.data.PreferencesManager
import codes.churma.walletforwarder.model.NotificationPayload
import codes.churma.walletforwarder.network.WebhookApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotificationForwarderService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefsManager: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        prefsManager = PreferencesManager(applicationContext)
        Log.i(TAG, "NotificationForwarderService created and listening")
        prefsManager.appendLog("Service started and listening for status bar notifications")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "NotificationForwarderService destroyed")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 1. Skip ongoing/persistent notifications (music players, call alerts, download progress)
        if (sbn.isOngoing) {
            return
        }

        val packageName = sbn.packageName ?: return

        // 2. Package whitelist check
        if (!PackageWhitelist.isWhitelisted(packageName)) {
            return
        }

        // 3. Extract text content
        val extras = sbn.notification.extras ?: return
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val rawText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""

        val title = rawTitle.trim()
        val text = rawText.trim()

        if (title.isBlank() && text.isBlank()) {
            return
        }

        val payload = NotificationPayload(
            packageName = packageName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        val targetUrl = prefsManager.webhookUrl
        val token = prefsManager.bearerToken

        if (targetUrl.isBlank()) {
            Log.w(TAG, "Captured notification from $packageName, but Webhook URL is not configured.")
            prefsManager.appendLog("⚠️ Skipped $packageName: Webhook URL not configured.")
            return
        }

        // 4. Transmit asynchronously via Coroutines
        serviceScope.launch {
            Log.d(TAG, "Forwarding notification from $packageName to $targetUrl")
            val result = WebhookApiClient.sendNotification(payload, targetUrl, token)

            result.onSuccess {
                Log.i(TAG, "Successfully forwarded notification from $packageName: $title")
                val snippet = if (text.length > 35) text.take(35) + "..." else text
                prefsManager.appendLog("✓ Sent [$packageName] $snippet")
            }.onFailure { error ->
                Log.e(TAG, "Failed to forward notification from $packageName: ${error.message}", error)
                prefsManager.appendLog("✗ Failed [$packageName]: ${error.message}")
            }
        }
    }

    companion object {
        private const val TAG = "WalletForwarderService"
    }
}
