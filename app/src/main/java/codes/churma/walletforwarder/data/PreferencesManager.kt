package codes.churma.walletforwarder.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBHOOK_URL, value.trim()).apply()

    var bearerToken: String
        get() = prefs.getString(KEY_BEARER_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BEARER_TOKEN, value.trim()).apply()

    fun getLogs(): String {
        return prefs.getString(KEY_LOGS, "") ?: ""
    }

    @Synchronized
    fun appendLog(entry: String) {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val time = dateFormat.format(Date())
        val currentLogs = getLogs()
        val lines = if (currentLogs.isBlank()) mutableListOf() else currentLogs.split("\n").toMutableList()

        lines.add(0, "[$time] $entry")
        // Keep at most 30 recent log lines
        val trimmed = lines.take(30).joinToString("\n")
        prefs.edit().putString(KEY_LOGS, trimmed).apply()
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply()
    }

    companion object {
        private const val PREFS_NAME = "wallet_forwarder_prefs"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_BEARER_TOKEN = "bearer_token"
        private const val KEY_LOGS = "forwarded_logs"
    }
}
