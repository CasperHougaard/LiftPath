package com.liftpath.helpers

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * The app's shared motion vocabulary.
 *
 * Extracted from MainActivity so every screen animates the same way rather than each one
 * re-deriving its own timings. Two primitives only — an entrance and a press — because a
 * consistent small set reads as deliberate where a large varied set reads as noise.
 */
object Motion {

    /** Gap between entrance waves. Below ~40ms the stagger stops reading as deliberate;
     *  above ~80ms the screen feels slow to settle. */
    const val ENTRANCE_STAGGER_MS = 55L

    /** How far each element rises into place, in dp. */
    const val ENTRANCE_RISE_DP = 18f

    const val ENTRANCE_FADE_MS = 240L

    /** Press feedback. 0.97 is felt but not seen — deeper reads as a toy. */
    const val PRESSED_SCALE = 0.97f
    const val PRESS_DOWN_MS = 90L
    const val PRESS_RELEASE_MS = 220L

    /**
     * Fades and spring-rises [view] into place after [delayMs].
     *
     * Replaces the old `R.anim.fade_in_up` / `pop_in` view animations. Two reasons beyond
     * taste: those used a decelerate interpolator, which arrives and stops dead, whereas a
     * spring settles — and that settle is most of what reads as expensive. View animations
     * also only transform how a view is *drawn*, so the cards were never actually where
     * they appeared to be mid-flight.
     */
    fun springIn(view: View, delayMs: Long = 0L) {
        val risePx = ENTRANCE_RISE_DP * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = risePx
        view.postDelayed({
            view.animate()
                .alpha(1f)
                .setDuration(ENTRANCE_FADE_MS)
                .start()

            SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                // LOW stiffness with a touch of bounce: the view overshoots ~1dp and
                // settles. DAMPING_RATIO_NO_BOUNCY here would just be a slow fade.
                spring.stiffness = SpringForce.STIFFNESS_LOW
                spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }.start()
        }, delayMs)
    }

    /**
     * Runs [springIn] across [waves], staggering each wave by [ENTRANCE_STAGGER_MS].
     *
     * Waves are passed as a list of lists so the order stays legible and adding a view to a
     * wave does not mean re-deriving a magic start offset.
     */
    fun springInWaves(waves: List<List<View>>) {
        waves.forEachIndexed { index, views ->
            views.forEach { springIn(it, index * ENTRANCE_STAGGER_MS) }
        }
    }

    /**
     * Scale-down on touch, overshoot back on release.
     *
     * Deliberately a touch listener that never consumes the event, so the view's own
     * OnClickListener and MaterialCardView's ripple both still work. Uses
     * ViewPropertyAnimator rather than a spring because presses repeat and
     * ViewPropertyAnimator cancels its predecessor for free; two competing
     * SpringAnimations on the same property fight each other.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun applyPressResponse(vararg views: View) {
        views.forEach { view ->
            view.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate()
                        .scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE)
                        .setDuration(PRESS_DOWN_MS)
                        .start()

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> v.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(PRESS_RELEASE_MS)
                        .setInterpolator(OvershootInterpolator(2f))
                        .start()
                }
                false
            }
        }
    }
}
