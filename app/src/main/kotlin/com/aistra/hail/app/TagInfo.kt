package com.aistra.hail.app

/**
 * A Home tag with an optional freeze working-mode preset.
 * [workingMode] null or empty means the global Settings working mode is used.
 */
data class TagInfo(
    var name: String,
    val id: Int,
    var workingMode: String? = null
) {
    val hasCustomMode: Boolean
        get() = !workingMode.isNullOrEmpty()

    fun resolvedWorkingMode(): String =
        workingMode?.takeIf { it.isNotEmpty() } ?: HailData.workingMode
}
