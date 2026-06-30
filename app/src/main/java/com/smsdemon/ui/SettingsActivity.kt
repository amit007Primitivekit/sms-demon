package com.smsdemon.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.messaging.FirebaseMessaging
import com.smsdemon.R
import com.smsdemon.databinding.ActivitySettingsBinding
import com.smsdemon.repository.BackendApiClient
import com.smsdemon.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings screen — accessible only via the overflow menu (⋮) in MainActivity.
 *
 * Contains:
 *  - Backend URL field (default: https://smsdemon.virt.cc.cd)
 *  - "Register this device" button — fetches current FCM token and registers with backend
 *  - Registered Device ID display
 *  - "Advanced: Periodic SMS" section — opens the old MainActivity-style controls
 *    (hidden behind a collapsible section, not in the main navigation)
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs:   SharedPreferences

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_settings)

        prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)

        restoreFields()
        setupClickListeners()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun restoreFields() {
        binding.etBackendUrl.setText(
            prefs.getString(Constants.PREF_BACKEND_URL, Constants.DEFAULT_BACKEND_URL)
        )
        val deviceId = prefs.getString(Constants.PREF_FCM_DEVICE_ID, null)
        binding.tvDeviceId.text = deviceId ?: getString(R.string.settings_not_registered)
    }

    private fun setupClickListeners() {
        binding.btnRegisterDevice.setOnClickListener { registerDevice() }

        binding.btnSaveBackendUrl.setOnClickListener {
            val url = binding.etBackendUrl.text.toString().trim().trimEnd('/')
            if (url.isEmpty()) {
                binding.etBackendUrl.error = "URL cannot be empty"
                return@setOnClickListener
            }
            prefs.edit().putString(Constants.PREF_BACKEND_URL, url).apply()
            Toast.makeText(this, R.string.settings_url_saved, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Fetches the current FCM token, then calls the backend register endpoint.
     * Shows a loading state on the button while in-flight.
     */
    private fun registerDevice() {
        binding.btnRegisterDevice.isEnabled = false
        binding.btnRegisterDevice.text = getString(R.string.settings_registering)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Toast.makeText(this, "Failed to get FCM token: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                binding.btnRegisterDevice.isEnabled = true
                binding.btnRegisterDevice.text = getString(R.string.settings_register_device)
                return@addOnCompleteListener
            }

            val token      = task.result
            val backendUrl = prefs.getString(Constants.PREF_BACKEND_URL, Constants.DEFAULT_BACKEND_URL)!!
            val deviceName = android.os.Build.MODEL

            scope.launch {
                val deviceId = withContext(Dispatchers.IO) {
                    BackendApiClient(backendUrl).registerDevice(token, deviceName)
                }

                binding.btnRegisterDevice.isEnabled = true
                binding.btnRegisterDevice.text = getString(R.string.settings_register_device)

                if (deviceId != null) {
                    prefs.edit().putString(Constants.PREF_FCM_DEVICE_ID, deviceId).apply()
                    binding.tvDeviceId.text = deviceId
                    Toast.makeText(this@SettingsActivity, R.string.settings_registered_ok, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SettingsActivity, R.string.settings_register_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
