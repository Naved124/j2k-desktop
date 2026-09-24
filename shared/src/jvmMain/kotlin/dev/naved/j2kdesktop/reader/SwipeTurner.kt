package dev.naved.j2kdesktop.reader

import kotlin.math.abs
import kotlin.math.sign

/**
 * Turns touchpad/wheel scrolling into ONE page turn per gesture.
 * A two-finger swipe sends dozens of tiny scroll events; we add them up, turn once when they
 * pass [threshold], then ignore the rest until the gesture ends (no events for [gestureGapMs]).
 */
class SwipeTurner(
    private val threshold: Float = 1f,
    private val gestureGapMs: Long = 280,
) {
    private var accumulated = 0f
    private var lastEventAt = 0L
    private var turnedThisGesture = false

    /**
     * @param forward how far this event moves "forward" (positive) or "back" (negative)
     * @return +1 to go to the next page, -1 for the previous one, 0 for nothing
     */
    fun onScroll(forward: Float, now: Long = System.currentTimeMillis()): Int {
        if (now - lastEventAt > gestureGapMs) {
            accumulated = 0f
            turnedThisGesture = false
        }
        lastEventAt = now
        if (turnedThisGesture) return 0
        accumulated += forward
        if (abs(accumulated) >= threshold) {
            turnedThisGesture = true
            return accumulated.sign.toInt()
        }
        return 0
    }
}
