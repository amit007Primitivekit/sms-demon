package com.smsdemon.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.smsdemon.R
import com.smsdemon.databinding.ActivityMainBinding
import com.smsdemon.model.ServiceState
import com.smsdemon.repository.BackendApiClient
import com.smsdemon.service.SmsSenderService
import com.smsdemon.util.Constants
import com.smsdemon.util.PhoneValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MainActivity"

/**
 * Main screen.
 *
 * Primary feature — FCM mode:
 *   The status card shows whether this device is registered with the backend.
 *   The user opens Settings (⋮ menu) to register the device.
 *   After that, the backend can push SMS commands remotely at any time.
 *
 * Secondary feature — Periodic SMS (Advanced, collapsed by default):
 *   The old interval-based local service, accessible only by expanding
 *   the "Advanced: Periodic SMS" card at the bottom of the screen.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var prefs: SharedPreferences

    private val scope = CoroutineScope(Dispatchers.Main)

    // ── Permission launchers ──────────────────────────────────────────────────

    // Used by the periodic SMS "Start" button flow
    private val requestSmsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                if (pendingStart) { pendingStart = false; doStartService() }
            } else {
                Toast.makeText(this, R.string.permission_sms_denied, Toast.LENGTH_LONG).show()
                pendingStart = false
            }
        }

    // Used on launch — ensures permission is granted for FCM-driven SMS sends
    private val requestSmsPermissionForFcm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.i(TAG, "SEND_SMS (FCM mode) granted=$granted")
            if (!granted) {
                Toast.makeText(this, R.string.permission_sms_denied, Toast.LENGTH_LONG).show()
            }
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            if (pendingStart) { pendingStart = false; doStartService() }
        }

    private var pendingStart = false
    private var advancedExpanded = false

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)

        restoreAdvancedFields()
        observeViewModel()
        setupClickListeners()
        refreshFcmStatus()
        ensureSmsPermission()          // request upfront — needed for FCM-driven sends
        silentlyRegisterTokenIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshFcmStatus()
    }

    override fun onPause() {
        super.onPause()
        saveCurrentAdvancedFields()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ── FCM status card ───────────────────────────────────────────────────────

    private fun refreshFcmStatus() {
        val deviceId = prefs.getString(Constants.PREF_FCM_DEVICE_ID, null)
        if (deviceId != null) {
            binding.tvFcmStatus.text = getString(R.string.fcm_status_registered)
            binding.tvFcmStatus.setTextColor(getColor(R.color.status_running))
            binding.tvDeviceIdDisplay.text = getString(R.string.fcm_device_id_fmt, deviceId)
        } else {
            binding.tvFcmStatus.text = getString(R.string.fcm_status_not_registered)
            binding.tvFcmStatus.setTextColor(getColor(R.color.status_stopped))
            binding.tvDeviceIdDisplay.text = getString(R.string.fcm_no_device_id)
        }
    }

    /**
     * On first launch (or after a token refresh), quietly register with the backend
     * if a device ID is not yet stored.  No UI blocking — runs in background.
     */
    private fun silentlyRegisterTokenIfNeeded() {
        if (prefs.getString(Constants.PREF_FCM_DEVICE_ID, null) != null) return

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val backendUrl = prefs.getString(Constants.PREF_BACKEND_URL, Constants.DEFAULT_BACKEND_URL)!!
            val deviceName = android.os.Build.MODEL
            scope.launch {
                val deviceId = withContext(Dispatchers.IO) {
                    BackendApiClient(backendUrl).registerDevice(token, deviceName)
                }
                if (deviceId != null) {
                    prefs.edit().putString(Constants.PREF_FCM_DEVICE_ID, deviceId).apply()
                    Log.i(TAG, "Auto-registered deviceId=$deviceId")
                    refreshFcmStatus()
                }
            }
        }
    }

    // ── Advanced section (periodic SMS) ──────────────────────────────────────

    private fun restoreAdvancedFields() {
        binding.etPhoneNumber.setText(viewModel.loadPhoneNumber())
        binding.etSmsTemplate.setText(viewModel.loadTemplate())
        binding.etInterval.setText(viewModel.loadInterval().toString())
    }

    private fun observeViewModel() {
        viewModel.serviceState.observe(this) { state ->
            when (state) {
                is ServiceState.Stopped -> {
                    binding.tvStatus.text = getString(R.string.status_stopped)
                    binding.tvStatus.setTextColor(getColor(R.color.status_stopped))
                    binding.btnStart.isEnabled = true
                    binding.btnStop.isEnabled  = false
                    setAdvancedInputsEnabled(true)
                }
                is ServiceState.Running -> {
                    binding.tvStatus.text = getString(R.string.status_running, state.intervalMinutes)
                    binding.tvStatus.setTextColor(getColor(R.color.status_running))
                    binding.btnStart.isEnabled = false
                    binding.btnStop.isEnabled  = true
                    setAdvancedInputsEnabled(false)
                }
            }
        }
    }

    private fun setupClickListeners() {
        // Advanced card toggle
        binding.layoutAdvancedHeader.setOnClickListener { toggleAdvanced() }

        // Periodic SMS buttons
        binding.btnStart.setOnClickListener { onStartClicked() }
        binding.btnStop.setOnClickListener  { onStopClicked() }

        // Logs
        binding.btnViewLogs.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        // Settings shortcut in FCM card
        binding.btnGoToSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun toggleAdvanced() {
        advancedExpanded = !advancedExpanded
        binding.layoutAdvancedBody.visibility = if (advancedExpanded) View.VISIBLE else View.GONE
        binding.tvAdvancedToggle.text = getString(
            if (advancedExpanded) R.string.action_hide else R.string.action_show
        )
    }

    // ── Periodic SMS start/stop ───────────────────────────────────────────────

    private fun onStartClicked() {
        val phone    = binding.etPhoneNumber.text.toString().trim()
        val template = binding.etSmsTemplate.text.toString()
        val interval = binding.etInterval.text.toString().trim().toIntOrNull()
            ?: Constants.DEFAULT_INTERVAL_MINUTES

        if (!PhoneValidator.isValid(phone)) {
            binding.etPhoneNumber.error = getString(R.string.error_invalid_phone)
            return
        }
        if (interval < 1) {
            binding.etInterval.error = getString(R.string.error_invalid_interval)
            return
        }

        viewModel.saveSettingsAndMarkRunning(phone, template, interval)

        when {
            !hasSmsPermission() -> {
                pendingStart = true
                requestSmsPermission.launch(Manifest.permission.SEND_SMS)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission() -> {
                pendingStart = true
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> doStartService()
        }
    }

    private fun onStopClicked() {
        viewModel.markStopped()
        startService(Intent(this, SmsSenderService::class.java).apply {
            action = Constants.ACTION_STOP_SERVICE
        })
    }

    private fun doStartService() {
        startForegroundService(Intent(this, SmsSenderService::class.java).apply {
            action = Constants.ACTION_START_SERVICE
        })
        Toast.makeText(this, R.string.toast_service_started, Toast.LENGTH_SHORT).show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hasSmsPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Called on every launch to ensure SEND_SMS is granted.
     * This is required for FCM-driven sends — the user may never open the
     * Advanced section but we still need the permission for remote commands.
     */
    private fun ensureSmsPermission() {
        if (!hasSmsPermission()) {
            requestSmsPermissionForFcm.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun saveCurrentAdvancedFields() {
        viewModel.saveSettings(
            phone           = binding.etPhoneNumber.text.toString().trim(),
            template        = binding.etSmsTemplate.text.toString(),
            intervalMinutes = binding.etInterval.text.toString().trim().toIntOrNull()
                ?: Constants.DEFAULT_INTERVAL_MINUTES
        )
    }

    private fun setAdvancedInputsEnabled(enabled: Boolean) {
        binding.etPhoneNumber.isEnabled = enabled
        binding.etSmsTemplate.isEnabled = enabled
        binding.etInterval.isEnabled    = enabled
    }
}
