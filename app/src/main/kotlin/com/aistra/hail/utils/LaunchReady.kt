package com.aistra.hail.utils

/**
 * Coordinates splash keep-on-screen until the first Home list is ready (or timeout).
 * Reset on each cold process start via [reset].
 */
object LaunchReady {
    @Volatile
    var homeContentReady: Boolean = false
        private set

    @Volatile
    var timedOut: Boolean = false
        private set

    @Volatile
    private var landingPlayed: Boolean = false

    fun reset() {
        homeContentReady = false
        timedOut = false
        landingPlayed = false
    }

    fun markHomeReady() {
        homeContentReady = true
    }

    fun markTimedOut() {
        timedOut = true
    }

    fun shouldKeepSplash(): Boolean = !homeContentReady && !timedOut

    /** Returns true once — so landing animation plays only on cold start path. */
    fun consumeLandingSlot(): Boolean {
        if (landingPlayed) return false
        landingPlayed = true
        return true
    }
}
