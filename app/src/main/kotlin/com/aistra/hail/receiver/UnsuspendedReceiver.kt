package com.aistra.hail.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.utils.HPackages
import com.aistra.hail.utils.HShizuku.setAppRestricted
import com.aistra.hail.utils.HTarget

class UnsuspendedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PACKAGE_UNSUSPENDED_MANUALLY) return
        val pkg = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
        if (HTarget.P && HPackages.isValidPackageName(pkg)) {
            runCatching { setAppRestricted(pkg!!, false) }
        }
        app.setAutoFreezeService()
    }

    companion object {
        private const val ACTION_PACKAGE_UNSUSPENDED_MANUALLY =
            "android.intent.action.PACKAGE_UNSUSPENDED_MANUALLY"
    }
}
