package codes.churma.walletforwarder.ui

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
import androidx.lifecycle.lifecycleScope
import codes.churma.walletforwarder.R
import codes.churma.walletforwarder.data.PreferencesManager
import codes.churma.walletforwarder.databinding.ActivityMainBinding
import codes.churma.walletforwarder.model.NotificationPayload
import codes.churma.walletforwarder.network.WebhookApiClient
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

        binding.btnClearLogs.setOnClickListener {
            prefsManager.clearLogs()
            refreshLogs()
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
            packageName = "codes.churma.walletforwarder",
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
                    prefsManager.appendLog("✓ Test ping OK: $responseText")
                    refreshLogs()
                }.onFailure { error ->
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.toast_test_ping_failed, error.message),
                        Toast.LENGTH_LONG
                    ).show()
                    prefsManager.appendLog("✗ Test ping failed: ${error.message}")
                    refreshLogs()
                }
            }
        }
    }

    private fun refreshLogs() {
        val logs = prefsManager.getLogs()
        binding.tvLogsContent.text = logs.ifBlank { getString(R.string.logs_empty) }
    }
}
