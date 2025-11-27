package com.spamblocker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PERMISSION = 1
        private const val REQUEST_SCREENING_ROLE = 2
    }

    private lateinit var statusText: TextView
    private lateinit var enableSwitch: SwitchCompat
    private lateinit var setupButton: Button
    private lateinit var viewLogsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        updateStatus()
    }

    private fun requestPermissionsAndRole() {
        val permissions = mutableListOf(
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )

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
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            
            if (roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
                Toast.makeText(this, "App is already the default call screening app", Toast.LENGTH_SHORT).show()
                updateStatus()
                return
            }

            if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
                val roleIntent = roleManager.createRequestRoleIntent(
                    android.app.role.RoleManager.ROLE_CALL_SCREENING
                )
                startActivityForResult(roleIntent, REQUEST_SCREENING_ROLE)
            } else {
                Toast.makeText(this, "Call screening not available on this device", Toast.LENGTH_LONG).show()
            }
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
                requestScreeningRole()
            } else {
                Toast.makeText(this, "Permissions required for call screening", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SCREENING_ROLE) {
            updateStatus()
        }
    }

    private fun updateStatus() {
        val hasPermissions = checkPermissions()
        val isRoleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)
        } else {
            true
        }

        val isSetupComplete = hasPermissions && isRoleHeld

        if (isSetupComplete) {
            // Setup is done: hide the setup button
            setupButton.visibility = View.GONE
            
            // Enable the switch and set its state
            enableSwitch.isEnabled = true
            
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val isBlockingEnabled = prefs.getBoolean("blocking_enabled", true)
            
            // Set listener only after setting state to avoid double-trigger
            enableSwitch.setOnCheckedChangeListener(null)
            enableSwitch.isChecked = isBlockingEnabled
            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                val editor = getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                editor.putBoolean("blocking_enabled", isChecked)
                editor.apply()
                updateStatusText(isChecked)
            }
            
            updateStatusText(isBlockingEnabled)
        } else {
            // Setup required
            statusText.text = "⚠ Setup required"
            setupButton.visibility = View.VISIBLE
            setupButton.isEnabled = true
            setupButton.text = "Setup Call Screening"
            
            enableSwitch.isEnabled = false
            enableSwitch.isChecked = false
        }
    }

    private fun updateStatusText(isEnabled: Boolean) {
        if (isEnabled) {
            statusText.text = "✓ Spam blocking is active"
        } else {
            statusText.text = "Spam blocking is paused"
        }
    }

    private fun checkPermissions(): Boolean {
        val requiredPermissions = listOf(
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG
        )
        
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showBlockedCallsLog() {
        val prefs = getSharedPreferences("blocked_calls", MODE_PRIVATE)
        val logs = prefs.getStringSet("logs", setOf()) ?: setOf()

        if (logs.isEmpty()) {
            Toast.makeText(this, "No blocked calls yet", Toast.LENGTH_SHORT).show()
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
