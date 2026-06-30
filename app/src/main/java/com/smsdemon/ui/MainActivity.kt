package com.smsdemon.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.smsdemon.R
import com.smsdemon.databinding.ActivityMainBinding
import com.smsdemon.model.ServiceState
import com.smsdemon.service.SmsSenderService
import com.smsdemon.util.Constants
import com.smsdemon.util.PhoneValidator

private const val TAG = "MainActivity"

/**
 * Main screen of the application.
 *
 * ## UI responsibilities
 *  - Phone number, SMS template, and interval input fields.
 *  - Start / Stop buttons with validation.
 *  - Status label that reflects [MainViewModel.serviceState].
 *  - Navigation to [LogActivity].
 *
 * ## Permission handling
 *  - Requests [Manifest.permission.SEND_SMS] (mandatory).
 *  - Requests [Manifest.permission.POST_NOTIFICATIONS] on Android 13+.
 *  - Informs the user if either is denied and prevents starting the service.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // ── Permission launchers ──────────────────────────────────────────────────

    private val requestSmsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.i(TAG, "SEND_SMS permission granted")
                // Attempt start again if the user pressed Start before granting
                if (pendingStart) { pendingStart = false; doStartService() }
            } else {
                Log.w(TAG, "SEND_SMS permission denied")
                Toast.makeText(this, R.string.permission_sms_denied, Toast.LENGTH_LONG).show()
                pendingStart = false
            }
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.i(TAG, "POST_NOTIFICATIONS granted=$granted")
            // Notification permission is optional; proceed regardless
            if (pendingStart) { pendingStart = false; doStartService() }
        }

    /** Flag set when the user pressed Start before permissions were granted. */
    private var pendingStart = false

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        restoreFields()
        observeViewModel()
        setupClickListeners()
    }

    override fun onPause() {
        super.onPause()
        // Auto-save whenever the user leaves the screen
        saveCurrentFields()
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private fun restoreFields() {
        binding.etPhoneNumber.setText(viewModel.loadPhoneNumber())
        binding.etSmsTemplate.setText(viewModel.loadTemplate())
        binding.etInterval.setText(viewModel.loadInterval().toString())
    }

    private fun observeViewModel() {
        viewModel.serviceState.observe(this) { state ->
            Log.d(TAG, "serviceState → $state")
            when (state) {
                is ServiceState.Stopped -> {
                    binding.tvStatus.text       = getString(R.string.status_stopped)
                    binding.tvStatus.setTextColor(getColor(R.color.status_stopped))
                    binding.btnStart.isEnabled  = true
                    binding.btnStop.isEnabled   = false
                    setInputsEnabled(true)
                }
                is ServiceState.Running -> {
                    binding.tvStatus.text = getString(
                        R.string.status_running,
                        state.intervalMinutes
                    )
                    binding.tvStatus.setTextColor(getColor(R.color.status_running))
                    binding.btnStart.isEnabled  = false
                    binding.btnStop.isEnabled   = true
                    setInputsEnabled(false)
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnStart.setOnClickListener { onStartClicked() }
        binding.btnStop.setOnClickListener  { onStopClicked() }
        binding.btnViewLogs.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    private fun onStartClicked() {
        val phone    = binding.etPhoneNumber.text.toString().trim()
        val template = binding.etSmsTemplate.text.toString()
        val interval = binding.etInterval.text.toString().trim().toIntOrNull()
            ?: Constants.DEFAULT_INTERVAL_MINUTES

        // Validate phone
        if (!PhoneValidator.isValid(phone)) {
            binding.etPhoneNumber.error = getString(R.string.error_invalid_phone)
            return
        }

        // Validate interval
        if (interval < 1) {
            binding.etInterval.error = getString(R.string.error_invalid_interval)
            return
        }

        // Persist settings immediately
        viewModel.saveSettingsAndMarkRunning(phone, template, interval)

        // Check permissions before touching the service
        when {
            !hasSmsPermission() -> {
                pendingStart = true
                requestSmsPermission.launch(Manifest.permission.SEND_SMS)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission() -> {
                pendingStart = true
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> doStartService()
        }
    }

    private fun onStopClicked() {
        Log.i(TAG, "Stop pressed")
        viewModel.markStopped()
        val intent = Intent(this, SmsSenderService::class.java).apply {
            action = Constants.ACTION_STOP_SERVICE
        }
        startService(intent)
    }

    // ── Service start ─────────────────────────────────────────────────────────

    private fun doStartService() {
        Log.i(TAG, "Starting SmsSenderService")
        val intent = Intent(this, SmsSenderService::class.java).apply {
            action = Constants.ACTION_START_SERVICE
        }
        startForegroundService(intent)
        Toast.makeText(this, R.string.toast_service_started, Toast.LENGTH_SHORT).show()
    }

    // ── Permission helpers ────────────────────────────────────────────────────

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private fun saveCurrentFields() {
        val interval = binding.etInterval.text.toString().trim().toIntOrNull()
            ?: Constants.DEFAULT_INTERVAL_MINUTES
        viewModel.saveSettings(
            phone    = binding.etPhoneNumber.text.toString().trim(),
            template = binding.etSmsTemplate.text.toString(),
            intervalMinutes = interval
        )
    }

    private fun setInputsEnabled(enabled: Boolean) {
        binding.etPhoneNumber.isEnabled = enabled
        binding.etSmsTemplate.isEnabled = enabled
        binding.etInterval.isEnabled    = enabled
    }
}
