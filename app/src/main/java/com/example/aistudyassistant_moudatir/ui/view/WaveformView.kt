package com.example.aistudyassistant_moudatir.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * Affiche une barre de forme d'onde animée (style audio-playback).
 * Démarrer avec [isAnimating] = true, arrêter avec false.
 */
class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barCount  = 9
    private val barColor  = Color.parseColor("#E5A5A0")
    private val barPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color       = barColor
        strokeCap   = Paint.Cap.ROUND
        style       = Paint.Style.FILL
    }

    private var animPhase = 0f
    private val animator  = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
        duration     = 1000L
        repeatCount  = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            animPhase = it.animatedValue as Float
            invalidate()
        }
    }

    var isAnimating: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                if (!animator.isRunning) animator.start()
            } else {
                animator.cancel()
                animPhase = 0f
                invalidate()
            }
        }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w         = width.toFloat()
        val h         = height.toFloat()
        val barW      = w / (barCount * 2f - 1f)
        val maxBarH   = h * 0.85f
        val minBarH   = h * 0.15f
        val cornerR   = barW / 2f

        for (i in 0 until barCount) {
            val x      = i * barW * 2f
            val phase  = animPhase + i.toFloat() * 0.55f
            val scale  = if (isAnimating) (0.5f + 0.5f * sin(phase.toDouble()).toFloat()) else 0.15f
            val barH   = minBarH + (maxBarH - minBarH) * scale
            val top    = (h - barH) / 2f
            val bottom = top + barH

            canvas.drawRoundRect(x, top, x + barW, bottom, cornerR, cornerR, barPaint)
        }
    }
}
