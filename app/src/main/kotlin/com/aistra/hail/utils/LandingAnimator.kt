package com.aistra.hail.utils

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.doOnPreDraw
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 * Soft landing when splash dismisses: content rises in, chrome follows.
 */
object LandingAnimator {
    private val ease = FastOutSlowInInterpolator()
    private val settle = DecelerateInterpolator(1.4f)
    private val pop = OvershootInterpolator(1.15f)

    fun prepare(root: View, bottomNav: View?, fab: View?) {
        root.alpha = 0f
        root.translationY = root.resources.displayMetrics.density * 28f
        bottomNav?.let {
            it.alpha = 0f
            it.translationY = it.resources.displayMetrics.density * 36f
        }
        fab?.let {
            it.alpha = 0f
            it.scaleX = 0.82f
            it.scaleY = 0.82f
        }
    }

    fun play(root: View, bottomNav: View?, fab: View?) {
        root.doOnPreDraw {
            root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(460)
                .setInterpolator(ease)
                .start()

            bottomNav?.animate()
                ?.alpha(1f)
                ?.translationY(0f)
                ?.setStartDelay(80)
                ?.setDuration(420)
                ?.setInterpolator(settle)
                ?.start()

            fab?.animate()
                ?.alpha(1f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setStartDelay(160)
                ?.setDuration(480)
                ?.setInterpolator(pop)
                ?.start()
        }
    }
}
