package com.aistra.hail.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.app.HailData

/**
 * After reboot, restore AutoFreeze foreground service only if the user enabled
 * "freeze after lock" — keeps battery use opt-in, not always-on.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        // Credential-encrypted prefs are unavailable until the user unlocks
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val um = context.getSystemService(android.os.UserManager::class.java)
            if (um != null && !um.isUserUnlocked) return
        }
        runCatching {
            if (HailData.autoFreezeAfterLock) {
                app.setAutoFreezeService(true, context.applicationContext)
                Log.i(TAG, "Restored AutoFreezeService after $action")
            }
        }.onFailure {
            Log.w(TAG, "Boot restore skipped", it)
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
