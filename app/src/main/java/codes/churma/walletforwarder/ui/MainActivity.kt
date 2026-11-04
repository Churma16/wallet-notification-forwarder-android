package codes.churma.walletforwarder.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import codes.churma.walletforwarder.BuildConfig
import codes.churma.walletforwarder.R
import codes.churma.walletforwarder.data.PreferencesManager
import codes.churma.walletforwarder.databinding.ActivityMainBinding
import codes.churma.walletforwarder.model.NotificationPayload
import codes.churma.walletforwarder.network.WebhookApiClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefsManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PreferencesManager(applicationContext)

        setupInputs()
        setupListeners()
        setupAppVersion()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        refreshLogs()
    }

    private fun setupInputs() {
        binding.etWebhookUrl.setText(prefsManager.webhookUrl)
        binding.etBearerToken.setText(prefsManager.bearerToken)
    }

    private fun setupListeners() {
        binding.btnGrantNotification.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        binding.btnFixBattery.setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        binding.btnSaveConfig.setOnClickListener {
            val url = binding.etWebhookUrl.text?.toString()?.trim() ?: ""
            val token = binding.etBearerToken.text?.toString()?.trim() ?: ""

            prefsManager.webhookUrl = url
            prefsManager.bearerToken = token

            Toast.makeText(this, getString(R.string.toast_config_saved), Toast.LENGTH_SHORT).show()
            prefsManager.appendLog("Configuration updated")
            refreshLogs()
        }

        binding.btnTestPing.setOnClickListener {
            executeTestPing()
        }

        binding.btnCopyLogs.setOnClickListener {
            val logs = prefsManager.getLogs()
            if (logs.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Wallet Forwarder Logs", logs)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, getString(R.string.toast_logs_copied), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.logs_empty), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearLogs.setOnClickListener {
            val logs = prefsManager.getLogs()
            if (logs.isBlank()) {
                Toast.makeText(this, getString(R.string.logs_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.dialog_clear_logs_title))
                .setMessage(getString(R.string.dialog_clear_logs_message))
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .setPositiveButton(getString(R.string.dialog_clear_confirm)) { _, _ ->
                    prefsManager.clearLogs()
                    refreshLogs()
                }
                .show()
        }
    }

    private fun updatePermissionStatus() {
        // 1. Notification Listener Permission check
        val isNotificationEnabled = isNotificationServiceEnabled()
        if (isNotificationEnabled) {
            binding.tvNotificationStatus.text = getString(R.string.status_granted)
            binding.tvNotificationStatus.setTextColor(getColor(R.color.status_green))
            binding.btnGrantNotification.isEnabled = false
            binding.btnGrantNotification.text = "Active"
        } else {
            binding.tvNotificationStatus.text = getString(R.string.status_not_granted)
            binding.tvNotificationStatus.setTextColor(getColor(R.color.status_red))
            binding.btnGrantNotification.isEnabled = true
            binding.btnGrantNotification.text = "Grant"
        }

        // 2. Battery Optimization check
        val isBatteryExempt = isBatteryOptimizationExempt()
        if (isBatteryExempt) {
            binding.tvBatteryStatus.text = getString(R.string.status_battery_unrestricted)
            binding.tvBatteryStatus.setTextColor(getColor(R.color.status_green))
            binding.btnFixBattery.isEnabled = false
            binding.btnFixBattery.text = "Allowed"
        } else {
            binding.tvBatteryStatus.text = getString(R.string.status_battery_optimized)
            binding.tvBatteryStatus.setTextColor(getColor(R.color.status_red))
            binding.btnFixBattery.isEnabled = true
            binding.btnFixBattery.text = "Exempt"
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val packageNames = NotificationManagerCompat.getEnabledListenerPackages(this)
        return packageNames.contains(packageName)
    }

    private fun isBatteryOptimizationExempt(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to standard battery settings
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(fallbackIntent)
        }
    }

    private fun executeTestPing() {
        val url = binding.etWebhookUrl.text?.toString()?.trim() ?: ""
        val token = binding.etBearerToken.text?.toString()?.trim() ?: ""

        if (url.isBlank()) {
            Toast.makeText(this, "Please enter Webhook URL first", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnTestPing.isEnabled = false
        binding.btnTestPing.text = "Pinging..."

        val testPayload = NotificationPayload(
            packageName = packageName,
            title = "Test Ping (Wallet Forwarder)",
            text = "Uji coba koneksi push notification webhook dari perangkat Android",
            timestamp = System.currentTimeMillis(),
            isTest = true
        )

        lifecycleScope.launch {
            val result = WebhookApiClient.sendNotification(testPayload, url, token)

            withContext(Dispatchers.Main) {
                binding.btnTestPing.isEnabled = true
                binding.btnTestPing.text = getString(R.string.btn_test_ping)

                result.onSuccess { responseText ->
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.toast_test_ping_success),
                        Toast.LENGTH_LONG
                    ).show()
                    prefsManager.appendLog("[OK] Test ping OK: $responseText")
                    refreshLogs()
                }.onFailure { error ->
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.toast_test_ping_failed, error.message),
                        Toast.LENGTH_LONG
                    ).show()
                    val errorMessage = error.message ?: "Unknown error"
                    val errorSnippet = if (errorMessage.length > 50) errorMessage.take(50) + "..." else errorMessage
                    prefsManager.appendLog("[FAIL] Test ping failed: $errorSnippet")
                    refreshLogs()
                }
            }
        }
    }

    private fun refreshLogs() {
        val logs = prefsManager.getLogs()
        binding.tvLogsContent.text = logs.ifBlank { getString(R.string.logs_empty) }
        binding.scrollLogs.post {
            binding.scrollLogs.scrollTo(0, 0)
        }
    }

    private fun setupAppVersion() {
        val versionText = getString(
            R.string.app_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
        binding.tvAppVersion.text = versionText

        binding.tvAppVersion.setOnClickListener {
            val clipboard = ContextCompat.getSystemService(this, ClipboardManager::class.java)
            val clip = ClipData.newPlainText("Wallet Forwarder Version", versionText)
            clipboard?.setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(this, getString(R.string.toast_version_copied), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
