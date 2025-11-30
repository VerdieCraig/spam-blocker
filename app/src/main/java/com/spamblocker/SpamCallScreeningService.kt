package com.spamblocker

import android.annotation.SuppressLint
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.spamblocker.data.AppDatabase
import com.spamblocker.data.BlockedCall
import kotlin.getValue

class SpamCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "SpamCallScreening"

        // Common carrier spam labels to detect
        private val SPAM_KEYWORDS = listOf(
            "spam risk",
            "scam likely",
            "potential spam",
            "fraud risk",
            "spam caller",
            "scam risk",
            "suspected spam",
            "telemarketer"
        )
    }

    // Room database instance
    private val database by lazy { AppDatabase.getInstance(this) }
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        // THIS LOG IS CRITICAL - if you don't see it, service isn't running
        Log.e(TAG, "=== CALL SCREENING SERVICE TRIGGERED ===")
        Log.e(TAG, "Screening call from: ${callDetails.handle}")

        // Show toast for debugging (you'll see this on screen)
        try {
            Toast.makeText(
                applicationContext,
                "Call screening activated!",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Toast error: ${e.message}")
        }

        // Check if blocking is enabled by user settings
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isBlockingEnabled = prefs.getBoolean("blocking_enabled", true)

        if (!isBlockingEnabled) {
            Log.e(TAG, "Blocking is disabled in settings. Allowing call.")
            allowCall(callDetails)
            return
        }

        val callerName = callDetails.callerDisplayName?.lowercase() ?: ""
        val callerNumber = callDetails.handle?.schemeSpecificPart ?: "Unknown"

        Log.e(TAG, "Raw Caller name: '${callDetails.callerDisplayName}'")
        Log.e(TAG, "Processed name: '$callerName'")
        Log.e(TAG, "Caller number: $callerNumber")

        // Check if the caller name contains spam keywords
        val isSpam = SPAM_KEYWORDS.any { keyword ->
            val matches = callerName.contains(keyword)
            if (matches) {
                Log.e(TAG, "MATCHED SPAM KEYWORD: $keyword")
            }
            matches
        }

        if (isSpam) {
            Log.e(TAG, "!!! SPAM DETECTED !!! Blocking call.")
            blockCall(callDetails)
        } else {
            Log.e(TAG, "Call appears legitimate. Allowing.")
            allowCall(callDetails)
        }

        Log.e(TAG, "=== SCREENING COMPLETE ===")
    }

    private fun blockCall(callDetails: Call.Details) {
        Log.e(TAG, "Building block response...")

        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)

        // Show toast for blocked call
        try {
            Toast.makeText(
                applicationContext,
                "Blocked spam call!",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Toast error: ${e.message}")
        }

        // Log the blocked call to Room database
        logBlockedCallToDatabase(callDetails)

        Log.e(TAG, "Block response sent!")
    }

    private fun allowCall(callDetails: Call.Details) {
        Log.e(TAG, "Building allow response...")

        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .build()

        respondToCall(callDetails, response)

        Log.e(TAG, "Allow response sent!")
    }

    private fun logBlockedCallToDatabase(callDetails: Call.Details) {
        coroutineScope.launch {
            try {
                val handle = callDetails.handle
                val number = handle?.schemeSpecificPart ?: "Unknown"
                val callerName = callDetails.callerDisplayName ?: "Unknown"

                val blockedCall = BlockedCall(
                    phoneNumber = number,
                    callerName = callerName,
                    timestamp = System.currentTimeMillis(),
                    reason = "Suspected spam"
                )

                database.blockedCallDao().insertCall(blockedCall)
                Log.e(TAG, "Successfully logged blocked call to Room database: $number")

                // Optional: Clean up old records to prevent DB from growing too large
                cleanupOldRecords()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to log blocked call to database: ${e.message}")
                // Fallback to SharedPreferences if Room fails
                logBlockedCallToSharedPrefs(
                    callDetails.handle?.schemeSpecificPart ?: "Unknown",
                    callDetails.callerDisplayName ?: "Unknown"
                )
            }
        }
    }

    @SuppressLint("MutatingSharedPrefs")
    private fun logBlockedCallToSharedPrefs(number: String, name: String) {
        try {
            val prefs = getSharedPreferences("blocked_calls", MODE_PRIVATE)
            val timestamp = System.currentTimeMillis()
            val logEntry = "$timestamp|$number|$name"

            // Get existing logs as mutable set
            val existingLogs = prefs.getStringSet("logs", setOf())?.toMutableSet() ?: mutableSetOf()
            existingLogs.add(logEntry)

            // Clear and re-add (workaround for SharedPreferences Set behavior)
            prefs.edit {
                remove("logs")
            }

            prefs.edit {
                putStringSet("logs", existingLogs)
            }

            Log.e(TAG, "Logged blocked call to SharedPreferences: $logEntry")
            Log.e(TAG, "Total blocked calls: ${existingLogs.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging call to SharedPreferences: ${e.message}")
        }
    }

    private suspend fun cleanupOldRecords() {
        try {
            val callCount = database.blockedCallDao().getCallCount()
            if (callCount > 1000) {
                val cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30 days
                val deletedCount = database.blockedCallDao().deleteOlderThan(cutoffTime)
                Log.e(TAG, "Cleaned up $deletedCount old records from database")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old records: ${e.message}")
        }
    }
}