package com.spamblocker

import android.annotation.SuppressLint
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.content.edit

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
            "scam risk"
        )
    }

    override fun onScreenCall(callDetails: Call.Details) {
        Log.d(TAG, "Screening call from: ${callDetails.handle}")

        // Check if blocking is enabled by user settings
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val isBlockingEnabled = prefs.getBoolean("blocking_enabled", true)

        if (!isBlockingEnabled) {
            Log.d(TAG, "Blocking is disabled in settings. Allowing call.")
            allowCall(callDetails)
            return
        }

        val callerName = callDetails.callerDisplayName?.lowercase() ?: ""
        val callerNumber = callDetails.handle?.schemeSpecificPart ?: ""

        Log.d(TAG, "Caller name: $callerName")
        Log.d(TAG, "Caller number: $callerNumber")

        // Check if the caller name contains spam keywords
        val isSpam = SPAM_KEYWORDS.any { keyword ->
            callerName.contains(keyword)
        }

        if (isSpam) {
            Log.d(TAG, "Spam detected! Blocking call.")
            blockCall(callDetails)
        } else {
            Log.d(TAG, "Call appears legitimate. Allowing.")
            allowCall(callDetails)
        }
    }

    private fun blockCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)

        // Log the blocked call
        logBlockedCall(
            callDetails.handle?.schemeSpecificPart ?: "Unknown",
            callDetails.callerDisplayName ?: "Unknown"
        )
    }

    private fun allowCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .build()

        respondToCall(callDetails, response)
    }

    @SuppressLint("MutatingSharedPrefs")
    private fun logBlockedCall(number: String, name: String) {
        // In a real app, you'd save this to a database or shared preferences
        val prefs = getSharedPreferences("blocked_calls", MODE_PRIVATE)
        val timestamp = System.currentTimeMillis()
        val logEntry = "$timestamp|$number|$name"

        val existingLogs = prefs.getStringSet("logs", mutableSetOf()) ?: mutableSetOf()
        existingLogs.add(logEntry)

        prefs.edit { putStringSet("logs", existingLogs) }
        Log.d(TAG, "Logged blocked call: $logEntry")
    }
}