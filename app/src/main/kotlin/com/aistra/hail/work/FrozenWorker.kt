package com.aistra.hail.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aistra.hail.app.AppManager
import com.aistra.hail.app.HailData

class FrozenWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val pkg = inputData.getString(HailData.KEY_PACKAGE) ?: return Result.failure()
        val frozen = inputData.getBoolean(HailData.KEY_FROZEN, true)
        val info = HailData.checkedList.find { it.packageName == pkg }
        val mode = if (frozen) {
            info?.let { HailData.workingModeForApp(it) } ?: HailData.workingMode
        } else {
            info?.frozenMode?.takeIf { it.isNotEmpty() }
                ?: info?.let { HailData.workingModeForApp(it) }
                ?: HailData.workingMode
        }
        if (AppManager.setAppFrozen(pkg, frozen, mode)) {
            info?.frozenMode = if (frozen) mode else null
            HailData.saveApps()
        }
        return Result.success()
    }
}
