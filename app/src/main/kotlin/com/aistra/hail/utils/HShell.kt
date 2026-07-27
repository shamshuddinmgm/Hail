package com.aistra.hail.utils

import android.os.Build
import androidx.annotation.RequiresApi

object HShell {
    fun execute(command: String, root: Boolean): Pair<Int, String?> = runCatching {
        Runtime.getRuntime().exec(if (root) "su" else "sh").run {
            outputStream.use {
                it.write(command.toByteArray())
            }
            // Drain streams before waitFor to avoid pipe-buffer deadlock on large output
            val stdout = inputStream.bufferedReader()
            val stderr = errorStream.bufferedReader()
            val outText = StringBuilder()
            val errText = StringBuilder()
            val outThread = Thread { outText.append(stdout.readText()) }.also { it.start() }
            val errThread = Thread { errText.append(stderr.readText()) }.also { it.start() }
            val code = waitFor()
            outThread.join(5_000)
            errThread.join(5_000)
            destroy()
            code to outText.toString().ifBlank { errText.toString() }
        }
    }.getOrElse { 1 to it.stackTraceToString() }

    private fun execSU(command: String) = execute(command, true)

    val checkSU get() = execSU("whoami").first == 0

    val lockScreen get() = execSU("input keyevent KEYCODE_POWER").first == 0

    fun forceStopApp(packageName: String): Boolean =
        execSU("am force-stop --user current ${HPackages.shellQuote(packageName)}").first == 0

    fun setAppDisabled(packageName: String, disabled: Boolean): Boolean =
        execSU("pm ${if (disabled) "disable" else "enable"} --user current ${HPackages.shellQuote(packageName)}").first == 0

    fun setAppHidden(packageName: String, hidden: Boolean): Boolean =
        execSU("pm ${if (hidden) "hide" else "unhide"} --user current ${HPackages.shellQuote(packageName)}").first == 0

    fun setAppSuspended(packageName: String, suspended: Boolean): Boolean =
        execSU("pm ${if (suspended) "suspend" else "unsuspend"} --user current ${HPackages.shellQuote(packageName)}").first == 0

    fun uninstallApp(packageName: String) = execSU(
        "pm ${if (HPackages.canUninstallNormally(packageName)) "uninstall" else "uninstall --user current"} ${HPackages.shellQuote(packageName)}"
    ).first == 0

    fun reinstallApp(packageName: String) =
        execSU("pm install-existing --user current ${HPackages.shellQuote(packageName)}").first == 0

    @RequiresApi(Build.VERSION_CODES.P)
    fun setAppRestricted(packageName: String, restricted: Boolean) = execSU(
        "appops set --user current ${HPackages.shellQuote(packageName)} RUN_ANY_IN_BACKGROUND ${if (restricted) "ignore" else "allow"}"
    ).first == 0
}
