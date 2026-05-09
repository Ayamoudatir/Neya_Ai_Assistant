package com.example.aistudyassistant_moudatir.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Fond décoratif : deux cercles pointillés concentriques + croix "+" aux points cardinaux.
 * S'adapte à toute taille. Placer DERRIÈRE le VideoView dans un FrameLayout.
 */
class DecorativeRingsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 1.2f
        color       = Color.parseColor("#80D4A899")   // rose poudré semi-transparent
        pathEffect  = DashPathEffect(floatArrayOf(4f, 10f), 0f)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#90E5A5A0")
    }

    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style       = Paint.Style.STROKE
        strokeWidth = 1.4f
        color       = Color.parseColor("#90D4A899")
        strokeCap   = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width  / 2f
        val cy = height / 2f
        val base = min(width, height) / 2f

        // Rayon de l'avatar vidéo ≈ 42 % de la demi-taille
        val avatarR  = base * 0.48f
        val innerR   = avatarR * 1.20f   // premier anneau juste autour
        val outerR   = avatarR * 1.55f   // deuxième anneau extérieur

        // Anneaux pointillés
        canvas.drawCircle(cx, cy, innerR, ringPaint)
        canvas.drawCircle(cx, cy, outerR, ringPaint)

        // "+" aux 4 points cardinaux sur l'anneau extérieur
        val angles = listOf(0.0, 90.0, 180.0, 270.0)
        val armLen = base * 0.045f
        for (deg in angles) {
            val rad = Math.toRadians(deg)
            val px  = cx + outerR * cos(rad).toFloat()
            val py  = cy + outerR * sin(rad).toFloat()
            // horizontal bar
            canvas.drawLine(px - armLen, py, px + armLen, py, crossPaint)
            // vertical bar
            canvas.drawLine(px, py - armLen, px, py + armLen, crossPaint)
        }

        // Petits points décoratifs (45°, 135°, 225°, 315°)
        val dotAngles = listOf(45.0, 135.0, 225.0, 315.0)
        for (deg in dotAngles) {
            val rad = Math.toRadians(deg)
            val px  = cx + (innerR + (outerR - innerR) * 0.5f) * cos(rad).toFloat()
            val py  = cy + (innerR + (outerR - innerR) * 0.5f) * sin(rad).toFloat()
            canvas.drawCircle(px, py, base * 0.022f, dotPaint)
        }
    }
}
