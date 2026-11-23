package com.onion.calcognito

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * The seek bar of Nosved Player, drawn the same way: a thin rounded track that thickens while it is being
 * dragged, the played part in the accent blue, the rest faint white, and a white pill as the thumb. Tap or drag
 * anywhere on it to seek. While dragging, [onSeek] is called with `finished = false` (throttled), and once more
 * with `finished = true` when the finger lifts.
 */
class NosvedSeekBar(context: Context) : View(context) {

    var durationMs: Long = 0L
        set(value) {
            field = value.coerceAtLeast(0L)
            invalidate()
        }

    var positionMs: Long = 0L
        set(value) {
            field = value.coerceAtLeast(0L)
            if (!scrubbing) invalidate()
        }

    var bufferedMs: Long = 0L
        set(value) {
            field = value.coerceAtLeast(0L)
            invalidate()
        }

    /** Colour of the played / buffered part. Nosved Player draws it in this blue whatever the theme is. */
    var accent: Int = Color.parseColor("#3B82F6")
        set(value) {
            field = value
            invalidate()
        }

    var onSeek: ((Long, Boolean) -> Unit)? = null
    var onScrubStart: (() -> Unit)? = null
    var onScrubEnd: (() -> Unit)? = null

    /** True from the moment a finger goes down until it lifts (and the final seek has been sent). */
    var scrubbing = false
        private set

    private var scrubMs = 0L
    private var dragging = false
    private var lastSentAt = 0L

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun shownMs(): Long = if (scrubbing) scrubMs else positionMs

    // Track and thumb sizes ease between "resting" and "dragging" with the same spring Nosved uses
    // (stiffness 500, damping ratio 0.8): 6 -> 10 dp track and 18 -> 24 dp thumb.
    private var grow = 0f
    private var growAnim: ValueAnimator? = null
    private fun animateGrow(to: Float) {
        growAnim?.cancel()
        growAnim = ValueAnimator.ofFloat(grow, to).apply {
            duration = 380
            interpolator = TimeInterpolator { t ->
                val omega = Math.sqrt(500.0)
                val zeta = 0.8
                val wd = omega * Math.sqrt(1 - zeta * zeta)
                val x = t * 0.38   // seconds
                (1 - Math.exp(-zeta * omega * x) * (Math.cos(wd * x) + (zeta * omega / wd) * Math.sin(wd * x))).toFloat()
            }
            addUpdateListener { grow = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        growAnim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val trackH = dp(6f) + (dp(10f) - dp(6f)) * grow
        val thumbW = dp(6f)
        val thumbH = dp(18f) + (dp(24f) - dp(18f)) * grow
        val cy = height / 2f
        val totalW = width.toFloat()
        val dur = durationMs.coerceAtLeast(1L).toFloat()
        val progress = (shownMs() / dur).coerceIn(0f, 1f)
        val playedPx = totalW * progress
        val radius = trackH / 2f

        // Nosved's "gapped" slider: the thumb sits in a 6 dp gap between the played and the unplayed track.
        val thumbLeft = (playedPx - thumbW / 2f).coerceIn(0f, (totalW - thumbW).coerceAtLeast(0f))
        val thumbRight = thumbLeft + thumbW
        val gap = dp(6f)
        val playedEnd = (thumbLeft - gap).coerceAtLeast(0f)
        val unplayedStart = (thumbRight + gap).coerceAtMost(totalW)
        val unplayedLen = (totalW - unplayedStart).coerceAtLeast(0f)

        // 1. Unplayed track (white 25 %).
        if (unplayedLen > 0f) {
            paint.color = Color.argb(64, 255, 255, 255)
            rect.set(unplayedStart, cy - radius, totalW, cy + radius)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        // 2. Buffered, after the gap (accent 45 %).
        val bufferPx = if (bufferedMs > 0L) (playedPx + (bufferedMs / dur) * totalW).coerceIn(playedPx, totalW) else playedPx
        if (bufferPx > unplayedStart) {
            paint.color = Color.argb(115, Color.red(accent), Color.green(accent), Color.blue(accent))
            rect.set(unplayedStart, cy - radius, bufferPx, cy + radius)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        // 3. Played, before the gap.
        if (playedEnd > 0f) {
            paint.color = accent
            rect.set(0f, cy - radius, playedEnd, cy + radius)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        // 4. White pill thumb.
        paint.color = Color.WHITE
        rect.set(thumbLeft, cy - thumbH / 2f, thumbRight, cy + thumbH / 2f)
        canvas.drawRoundRect(rect, thumbW / 2f, thumbW / 2f, paint)
    }

    private fun msAt(x: Float): Long {
        if (durationMs <= 0L || width <= 0) return 0L
        return ((x / width.toFloat()) * durationMs).toLong().coerceIn(0L, durationMs)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0L) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scrubbing = true
                dragging = true
                scrubMs = msAt(event.x)
                lastSentAt = System.currentTimeMillis()
                onScrubStart?.invoke()
                animateGrow(1f)
                onSeek?.invoke(scrubMs, false)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                scrubMs = msAt(event.x)
                // Seek while dragging so the picture follows, but not on every single touch event.
                val now = System.currentTimeMillis()
                if (now - lastSentAt >= 100L) {
                    lastSentAt = now
                    onSeek?.invoke(scrubMs, false)
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> if (dragging) {
                scrubMs = msAt(event.x)
                dragging = false
                scrubbing = false
                positionMs = scrubMs
                onSeek?.invoke(scrubMs, true)
                onScrubEnd?.invoke()
                animateGrow(0f)
                invalidate()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                scrubbing = false
                onScrubEnd?.invoke()
                animateGrow(0f)
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
