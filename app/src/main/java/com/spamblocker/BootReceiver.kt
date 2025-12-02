package com.spamblocker

    import android.content.BroadcastReceiver
    import android.content.Context
    import android.content.Intent
    import android.util.Log

    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                Log.d("SpamBlocker", "Device booted")

                val prefs = context.getSharedPreferences(
                    SpamCallScreeningService.PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                val isEnabled = prefs.getBoolean(
                    SpamCallScreeningService.KEY_BLOCKING_ENABLED,
                    true
                )

                Log.d("SpamBlocker", "Spam blocking enabled: $isEnabled")
                // CallScreeningService will automatically handle calls if set as default
            }
        }
    }