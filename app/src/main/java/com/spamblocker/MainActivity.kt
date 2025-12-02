package com.spamblocker

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PERMISSION = 1
        private const val TAG = "MainActivity"
        private const val PREF_ROLE_REQUESTED = "role_requested"
    }

    private lateinit var statusText: TextView
    private lateinit var enableSwitch: SwitchCompat
    private lateinit var setupButton: Button
    private lateinit var viewLogsButton: Button
    private lateinit var roleManager: RoleManager

    // Replace deprecated startActivityForResult with new API
    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.e(TAG, "User granted call screening role!")
            markRoleRequested()

            // IMPORTANT: Set blocking enabled to TRUE by default when role is granted
            getSharedPreferences(SpamCallScreeningService.PREFS_NAME, MODE_PRIVATE).edit {
                putBoolean(SpamCallScreeningService.KEY_BLOCKING_ENABLED, true)
            }

            updateUIForRoleGranted()
            Toast.makeText(this, "Call screening activated! Spam blocking is now active.", Toast.LENGTH_LONG).show()
        } else {
            Log.e(TAG, "User denied call screening role")
            Toast.makeText(this,
                "Call screening not enabled. You can enable it later in:\n" +
                        "Phone app → Settings → Call blocking & identification",
                Toast.LENGTH_LONG
            ).show()
        }
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize RoleManager if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager = getSystemService(RoleManager::class.java)
        }

        statusText = findViewById(R.id.statusText)
        enableSwitch = findViewById(R.id.enableSwitch)
        setupButton = findViewById(R.id.setupButton)
        viewLogsButton = findViewById(R.id.viewLogsButton)

        setupButton.setOnClickListener {
            requestPermissionsAndRole()
        }

        viewLogsButton.setOnClickListener {
            showBlockedCallsLog()
        }

        // Only check status once - onResume will be called automatically after onCreate
        autoRequestRoleIfNeeded()
    }

    // NEW METHOD: Automatically request role if we don't have it and haven't asked recently
    private fun autoRequestRoleIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasRole = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            val hasRequestedBefore = getSharedPreferences(SpamCallScreeningService.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_ROLE_REQUESTED, false)

            if (!hasRole && !hasRequestedBefore && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                Log.e(TAG, "Auto-requesting call screening role on first launch")
                // Small delay to let UI settle
                statusText.postDelayed({
                    requestScreeningRole()
                }, 1000)
            }
        }
    }

    // NEW METHOD: Mark that we've requested the role (to avoid spamming user)
    private fun markRoleRequested() {
        getSharedPreferences(SpamCallScreeningService.PREFS_NAME, MODE_PRIVATE).edit {
            putBoolean(PREF_ROLE_REQUESTED, true)
        }
    }

    // UPDATED METHOD: Check detailed call screening status with UI update
    private fun checkCurrentStatus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val isRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                Log.e(TAG, "Call screening role held: $isRoleHeld")

                if (isRoleHeld) {
                    Log.e(TAG, "SUCCESS: We have call screening role!")
                    updateUIForRoleGranted()
                } else {
                    Log.e(TAG, "Call screening role not held - need user permission")
                    updateUIForRoleMissing()
                }
            } else {
                Log.e(TAG, "Call screening requires Android 10+")
                updateUIForUnsupportedAndroid()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking status: ${e.message}")
        }
    }

    // NEW METHOD: Update UI when role is granted
    private fun updateUIForRoleGranted() {
        runOnUiThread {
            statusText.text = getString(R.string.call_screening_enabled)
            statusText.setTextColor(Color.GREEN)

            // Enable the blocking switch
            enableSwitch.isEnabled = true

            // Use the same SharedPreferences as the service
            val prefs = getSharedPreferences(SpamCallScreeningService.PREFS_NAME, MODE_PRIVATE)
            val isBlockingEnabled = prefs.getBoolean(SpamCallScreeningService.KEY_BLOCKING_ENABLED, true) // Default TRUE

            Log.e(TAG, "Current blocking state from prefs: $isBlockingEnabled")

            // Set up the switch without triggering the listener
            enableSwitch.setOnCheckedChangeListener(null)
            enableSwitch.isChecked = isBlockingEnabled

            // Now add the listener
            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                Log.e(TAG, "User toggled blocking to: $isChecked")
                getSharedPreferences(SpamCallScreeningService.PREFS_NAME, MODE_PRIVATE).edit {
                    putBoolean(SpamCallScreeningService.KEY_BLOCKING_ENABLED, isChecked)
                }
                updateStatusText(isChecked)
            }

            updateStatusText(isBlockingEnabled)
            setupButton.visibility = View.GONE
        }
    }

    private fun updateUIForRoleMissing() {
        runOnUiThread {
            statusText.text = getString(R.string.setup_required_text)
            statusText.setTextColor(Color.RED)

            // CRITICAL: Clear listener before changing switch state
            enableSwitch.setOnCheckedChangeListener(null)
            enableSwitch.isEnabled = false
            enableSwitch.isChecked = false

            setupButton.visibility = View.VISIBLE
        }
    }

    private fun updateUIForUnsupportedAndroid() {
        runOnUiThread {
            statusText.text = getString(R.string.screening_requires_android10)
            statusText.setTextColor(Color.GRAY)

            // CRITICAL: Clear listener before changing switch state
            enableSwitch.setOnCheckedChangeListener(null)
            enableSwitch.isEnabled = false
            enableSwitch.isChecked = false

            setupButton.visibility = View.VISIBLE
            setupButton.isEnabled = false
        }
    }

    private fun requestPermissionsAndRole() {
        val permissions = mutableListOf<String>()

        // Add basic permissions that are always available
        permissions.add(Manifest.permission.READ_PHONE_STATE)
        permissions.add(Manifest.permission.READ_CALL_LOG)

        // Only add ANSWER_PHONE_CALLS on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSION
            )
        } else {
            requestScreeningRole()
        }
    }

    private fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                Toast.makeText(this, getString(R.string.already_default_screening), Toast.LENGTH_SHORT).show()
                updateUIForRoleGranted()
                return
            }

            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                val roleIntent = roleManager.createRequestRoleIntent(
                    RoleManager.ROLE_CALL_SCREENING
                )

                roleRequestLauncher.launch(roleIntent)
                Log.e(TAG, "Starting role request intent")
            } else {
                Toast.makeText(this, getString(R.string.screening_not_available), Toast.LENGTH_LONG).show()
                Log.e(TAG, "Call screening role not available")
            }
        } else {
            Toast.makeText(this, getString(R.string.screening_requires_android10), Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, getString(R.string.permissions_granted), Toast.LENGTH_SHORT).show()
                requestScreeningRole()
            } else {
                Toast.makeText(this, getString(R.string.permissions_required), Toast.LENGTH_LONG).show()
                Log.e(TAG, "User denied required permissions")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check role status when app comes to foreground and sync UI with actual state
        checkCurrentStatus()
    }

    private fun updateStatus() {
        val hasPermissions = checkPermissions()
        val isRoleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else {
            false
        }

        val isSetupComplete = hasPermissions && isRoleHeld

        if (isSetupComplete) {
            updateUIForRoleGranted()
        } else {
            updateUIForRoleMissing()

            // Show what's missing
            if (!hasPermissions) {
                Log.e(TAG, "Status: Missing permissions")
            }
            if (!isRoleHeld) {
                Log.e(TAG, "Status: Call screening role not held")
            }
        }
    }

    private fun updateStatusText(isEnabled: Boolean) {
        if (isEnabled) {
            statusText.text = getString(R.string.blocking_active)
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        } else {
            statusText.text = getString(R.string.blocking_paused)
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
    }

    private fun checkPermissions(): Boolean {
        val requiredPermissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )

        // Only require ANSWER_PHONE_CALLS on API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requiredPermissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }

        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showBlockedCallsLog() {
        val prefs = getSharedPreferences("blocked_calls", MODE_PRIVATE)
        val logs = prefs.getStringSet("logs", setOf()) ?: setOf()

        if (logs.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_blocked_calls), Toast.LENGTH_SHORT).show()
        } else {
            val logText = logs.joinToString("\n") { log ->
                val parts = log.split("|")
                if (parts.size == 3) {
                    val timestamp = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.US)
                        .format(java.util.Date(parts[0].toLong()))
                    "$timestamp - ${parts[2]} (${parts[1]})"
                } else {
                    log
                }
            }
            Toast.makeText(this, logText, Toast.LENGTH_LONG).show()
        }
    }
}