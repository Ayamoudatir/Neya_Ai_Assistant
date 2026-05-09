package com.example.aistudyassistant_moudatir.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom View that draws the Neya avatar using Canvas.
 *
 * Layers (bottom → top):
 *  1. Speaking rings (iridescent, animated outward pulses)
 *  2. Face — warm radial-gradient skin
 *  3. Hair — dark, glossy, with bezier side-strands + gloss highlight
 *  4. Eyes — iris gradient, pupil, catchlights, lashes
 *  5. Eyebrows
 *  6. Nose dots + bridge shadow
 *  7. Mouth — dusty-rose lips, gloss highlight, smile dimples
 *  8. Cheek blush (radial gradient, soft)
 *  9. Border ring (neya_rose stroke)
 *
 * Usage: set [isSpeaking] = true to start the ring animation.
 */
class NeyaAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Reusable objects (allocated once) ────────────────────────────────────
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path  = Path()

    // ── Speaking animation ───────────────────────────────────────────────────
    private var speakingProgress = 0f

    private val speakingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration     = 1600L
        repeatCount  = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            speakingProgress = it.animatedValue as Float
            invalidate()
        }
    }

    var isSpeaking: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                if (!speakingAnimator.isRunning) speakingAnimator.start()
            } else {
                speakingAnimator.cancel()
                speakingProgress = 0f
                invalidate()
            }
        }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        speakingAnimator.cancel()
    }

    // ── Master draw ──────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val cx   = width  / 2f
        val cy   = height / 2f
        val r    = size   / 2f * 0.84f   // face-circle radius

        if (isSpeaking || speakingProgress > 0f) {
            drawSpeakingRings(canvas, cx, cy, r)
        }

        // Clip all face drawing to a circle
        canvas.save()
        path.reset()
        path.addCircle(cx, cy, r, Path.Direction.CW)
        canvas.clipPath(path)

        drawFace(canvas, cx, cy, r)
        drawHair(canvas, cx, cy, r)
        drawEyes(canvas, cx, cy, r)
        drawNose(canvas, cx, cy, r)
        drawMouth(canvas, cx, cy, r)
        drawBlush(canvas, cx, cy, r)

        canvas.restore()

        // Thin rose border
        paint.reset()
        paint.isAntiAlias = true
        paint.style       = Paint.Style.STROKE
        paint.strokeWidth = size * 0.022f
        paint.color       = Color.parseColor("#E5A5A0")
        canvas.drawCircle(cx, cy, r, paint)
    }

    // ── 1. Speaking rings ────────────────────────────────────────────────────
    private fun drawSpeakingRings(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val ringCount = 3
        for (i in 0 until ringCount) {
            val phase     = (speakingProgress + i.toFloat() / ringCount.toFloat()) % 1f
            val ringR     = r + r * 0.08f + r * 0.28f * phase
            val alpha     = (255f * (1f - phase) * 0.55f).toInt()
            val thickness = r * 0.07f * (1f - phase * 0.6f)
            // Iridescent: rose → warm peach → soft lavender
            val hue = (338f + phase * 55f) % 360f
            paint.reset()
            paint.isAntiAlias = true
            paint.style       = Paint.Style.STROKE
            paint.strokeWidth = thickness
            paint.color       = Color.HSVToColor(alpha, floatArrayOf(hue, 0.38f, 0.96f))
            canvas.drawCircle(cx, cy, ringR, paint)
        }
    }

    // ── 2. Face / skin ───────────────────────────────────────────────────────
    private fun drawFace(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = RadialGradient(
            cx, cy - r * 0.12f, r * 0.95f,
            intArrayOf(
                Color.parseColor("#F7D8BF"),
                Color.parseColor("#F0C4A8"),
                Color.parseColor("#E4A882")
            ),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }

    // ── 3. Hair ──────────────────────────────────────────────────────────────
    private fun drawHair(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val darkBrown = Color.parseColor("#1A0F0A")
        val deepBlack = Color.parseColor("#100806")
        val warmBrown = Color.parseColor("#3D1F10")

        // Volume mass behind head
        paint.reset()
        paint.isAntiAlias = true
        paint.color       = deepBlack
        canvas.drawOval(
            cx - r * 0.76f, cy - r * 0.90f,
            cx + r * 0.76f, cy + r * 0.52f, paint
        )

        // Crown + hairline shape
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            cx - r * 0.25f, cy - r,
            cx + r * 0.25f, cy - r * 0.40f,
            intArrayOf(warmBrown, darkBrown, warmBrown),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        path.reset()
        path.moveTo(cx - r * 0.76f, cy + r * 0.08f)
        path.cubicTo(cx - r * 0.86f, cy - r * 0.50f,
                     cx - r * 0.50f, cy - r * 1.05f,
                     cx,             cy - r * 1.00f)
        path.cubicTo(cx + r * 0.50f, cy - r * 1.05f,
                     cx + r * 0.86f, cy - r * 0.50f,
                     cx + r * 0.76f, cy + r * 0.08f)
        // Hairline dip right→left
        path.cubicTo(cx + r * 0.50f, cy - r * 0.56f,
                     cx + r * 0.14f, cy - r * 0.63f,
                     cx,             cy - r * 0.60f)
        path.cubicTo(cx - r * 0.14f, cy - r * 0.63f,
                     cx - r * 0.50f, cy - r * 0.56f,
                     cx - r * 0.76f, cy + r * 0.08f)
        path.close()
        canvas.drawPath(path, paint)
        paint.shader = null

        // Left side-strand
        paint.reset()
        paint.isAntiAlias = true
        paint.color       = darkBrown
        path.reset()
        path.moveTo(cx - r * 0.73f, cy - r * 0.32f)
        path.cubicTo(cx - r * 0.92f, cy + r * 0.05f,
                     cx - r * 0.90f, cy + r * 0.45f,
                     cx - r * 0.70f, cy + r * 0.72f)
        path.cubicTo(cx - r * 0.59f, cy + r * 0.48f,
                     cx - r * 0.61f, cy + r * 0.08f,
                     cx - r * 0.60f, cy - r * 0.22f)
        path.close()
        canvas.drawPath(path, paint)

        // Right side-strand
        path.reset()
        path.moveTo(cx + r * 0.73f, cy - r * 0.32f)
        path.cubicTo(cx + r * 0.92f, cy + r * 0.05f,
                     cx + r * 0.90f, cy + r * 0.45f,
                     cx + r * 0.70f, cy + r * 0.72f)
        path.cubicTo(cx + r * 0.59f, cy + r * 0.48f,
                     cx + r * 0.61f, cy + r * 0.08f,
                     cx + r * 0.60f, cy - r * 0.22f)
        path.close()
        canvas.drawPath(path, paint)

        // Gloss highlight on crown
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            cx - r * 0.18f, cy - r * 0.96f,
            cx + r * 0.14f, cy - r * 0.66f,
            intArrayOf(Color.parseColor("#55FFFFFF"), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        path.reset()
        path.moveTo(cx - r * 0.22f, cy - r * 0.88f)
        path.cubicTo(cx - r * 0.12f, cy - r * 1.01f,
                     cx + r * 0.18f, cy - r * 0.90f,
                     cx + r * 0.14f, cy - r * 0.66f)
        path.cubicTo(cx + r * 0.04f, cy - r * 0.73f,
                     cx - r * 0.10f, cy - r * 0.76f,
                     cx - r * 0.22f, cy - r * 0.88f)
        path.close()
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    // ── 4 & 5. Eyes + Eyebrows ───────────────────────────────────────────────
    private fun drawEyes(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val ey    = cy - r * 0.06f
        val ofsX  = r * 0.27f
        val eyeRx = r * 0.18f
        val eyeRy = r * 0.115f
        drawSingleEye(canvas, cx - ofsX, ey, eyeRx, eyeRy, r)
        drawSingleEye(canvas, cx + ofsX, ey, eyeRx, eyeRy, r)
        drawEyebrows (canvas, cx, ey, ofsX, eyeRx, r)
    }

    private fun drawSingleEye(
        canvas: Canvas, ex: Float, ey: Float, rx: Float, ry: Float, r: Float
    ) {
        // White
        paint.reset()
        paint.isAntiAlias = true
        paint.color       = Color.parseColor("#FFF8F6")
        canvas.drawOval(ex - rx, ey - ry, ex + rx, ey + ry, paint)

        // Iris
        val ir = ry * 0.76f
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = RadialGradient(
            ex - ir * 0.22f, ey - ir * 0.22f, ir,
            intArrayOf(
                Color.parseColor("#7B4230"),
                Color.parseColor("#3E2010"),
                Color.parseColor("#1C0A04")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(ex, ey, ir, paint)
        paint.shader = null

        // Pupil
        paint.color = Color.parseColor("#0D0604")
        canvas.drawCircle(ex, ey, ir * 0.50f, paint)

        // Primary catchlight (top-left)
        paint.color = Color.parseColor("#DDFFFFFF")
        canvas.drawCircle(ex - ir * 0.30f, ey - ir * 0.34f, ir * 0.22f, paint)

        // Secondary micro-catchlight (bottom-right)
        paint.color = Color.parseColor("#88FFFFFF")
        canvas.drawCircle(ex + ir * 0.26f, ey + ir * 0.22f, ir * 0.11f, paint)

        // Upper-lid shadow
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            ex, ey - ry, ex, ey + ry * 0.30f,
            intArrayOf(Color.parseColor("#70B09080"), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(ex - rx, ey - ry, ex + rx, ey + ry * 0.30f, paint)
        paint.shader = null

        // Upper lashes
        paint.reset()
        paint.isAntiAlias = true
        paint.color       = Color.parseColor("#1A0F0A")
        paint.style       = Paint.Style.STROKE
        paint.strokeWidth = r * 0.024f
        paint.strokeCap   = Paint.Cap.ROUND

        val lashN = 6
        for (i in 0 until lashN) {
            val t       = i.toFloat() / (lashN - 1).toFloat()
            val spread  = 1f - abs(t - 0.5f) * 2f         // 0 at tips, 1 at centre
            val lashX   = ex - rx + rx * 2f * t
            val lashY0  = ey - ry * (0.80f + 0.12f * spread)
            val angle   = (-18.0 + 36.0 * t.toDouble())   // degrees
            val rad     = Math.toRadians(angle)
            val lashLen = ry * (0.38f + 0.18f * spread)
            canvas.drawLine(
                lashX, lashY0,
                lashX + lashLen * sin(rad).toFloat(),
                lashY0 - lashLen * cos(rad).toFloat(),
                paint
            )
        }
    }

    private fun drawEyebrows(
        canvas: Canvas, cx: Float, eyeY: Float, ofsX: Float, eyeRx: Float, r: Float
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color       = Color.parseColor("#2A1008")
        paint.style       = Paint.Style.STROKE
        paint.strokeWidth = r * 0.042f
        paint.strokeCap   = Paint.Cap.ROUND

        val browY = eyeY - r * 0.185f

        // Left brow
        path.reset()
        path.moveTo(cx - ofsX - eyeRx * 0.85f, browY + r * 0.022f)
        path.cubicTo(
            cx - ofsX - eyeRx * 0.20f, browY - r * 0.032f,
            cx - ofsX + eyeRx * 0.28f, browY - r * 0.020f,
            cx - ofsX + eyeRx * 0.75f, browY + r * 0.012f
        )
        canvas.drawPath(path, paint)

        // Right brow
        path.reset()
        path.moveTo(cx + ofsX - eyeRx * 0.75f, browY + r * 0.012f)
        path.cubicTo(
            cx + ofsX - eyeRx * 0.28f, browY - r * 0.020f,
            cx + ofsX + eyeRx * 0.20f, browY - r * 0.032f,
            cx + ofsX + eyeRx * 0.85f, browY + r * 0.022f
        )
        canvas.drawPath(path, paint)
    }

    // ── 6. Nose ──────────────────────────────────────────────────────────────
    private fun drawNose(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val noseY  = cy + r * 0.13f
        val nostRd = r * 0.038f

        paint.reset()
        paint.isAntiAlias = true
        paint.color       = Color.parseColor("#C09070")
        canvas.drawCircle(cx - r * 0.072f, noseY, nostRd, paint)
        canvas.drawCircle(cx + r * 0.072f, noseY, nostRd, paint)

        // Bridge shadow (very subtle)
        paint.color       = Color.parseColor("#28C09070")
        paint.style       = Paint.Style.STROKE
        paint.strokeWidth = r * 0.028f
        paint.strokeCap   = Paint.Cap.ROUND
        path.reset()
        path.moveTo(cx - r * 0.038f, cy - r * 0.06f)
        path.cubicTo(
            cx - r * 0.055f, cy + r * 0.06f,
            cx - r * 0.068f, cy + r * 0.10f,
            cx - r * 0.075f, noseY - nostRd * 1.2f
        )
        canvas.drawPath(path, paint)
    }

    // ── 7. Mouth ─────────────────────────────────────────────────────────────
    private fun drawMouth(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val mY = cy + r * 0.31f

        // Lip fill
        paint.reset()
        paint.isAntiAlias = true
        paint.color       = Color.parseColor("#D4887A")
        path.reset()
        path.moveTo(cx - r * 0.195f, mY)
        path.cubicTo(cx - r * 0.10f,  mY - r * 0.065f,
                     cx - r * 0.02f,  mY - r * 0.082f,
                     cx,              mY - r * 0.062f)
        path.cubicTo(cx + r * 0.02f,  mY - r * 0.082f,
                     cx + r * 0.10f,  mY - r * 0.065f,
                     cx + r * 0.195f, mY)
        path.cubicTo(cx + r * 0.10f,  mY + r * 0.072f,
                     cx - r * 0.10f,  mY + r * 0.072f,
                     cx - r * 0.195f, mY)
        path.close()
        canvas.drawPath(path, paint)

        // Philtrum dip
        paint.reset()
        paint.isAntiAlias = true
        paint.color       = Color.parseColor("#50B07060")
        paint.style       = Paint.Style.STROKE
        paint.strokeWidth = r * 0.018f
        paint.strokeCap   = Paint.Cap.ROUND
        path.reset()
        path.moveTo(cx - r * 0.05f, mY - r * 0.002f)
        path.cubicTo(cx - r * 0.01f, mY - r * 0.040f,
                     cx + r * 0.01f, mY - r * 0.040f,
                     cx + r * 0.05f, mY - r * 0.002f)
        canvas.drawPath(path, paint)

        // Gloss highlight (lower lip centre)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            cx, mY - r * 0.01f, cx, mY + r * 0.048f,
            intArrayOf(Color.parseColor("#55FFFFFF"), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        path.reset()
        path.moveTo(cx - r * 0.10f, mY + r * 0.005f)
        path.cubicTo(cx - r * 0.05f, mY - r * 0.010f,
                     cx + r * 0.05f, mY - r * 0.010f,
                     cx + r * 0.10f, mY + r * 0.005f)
        path.cubicTo(cx + r * 0.05f, mY + r * 0.048f,
                     cx - r * 0.05f, mY + r * 0.048f,
                     cx - r * 0.10f, mY + r * 0.005f)
        path.close()
        canvas.drawPath(path, paint)
        paint.shader = null

        // Smile corner dimples
        paint.reset()
        paint.isAntiAlias = true
        paint.color       = Color.parseColor("#90B07060")
        paint.style       = Paint.Style.STROKE
        paint.strokeWidth = r * 0.022f
        paint.strokeCap   = Paint.Cap.ROUND
        for (side in listOf(-1f, 1f)) {
            path.reset()
            path.moveTo(cx + side * r * 0.195f, mY)
            path.cubicTo(
                cx + side * r * 0.215f, mY + r * 0.040f,
                cx + side * r * 0.200f, mY + r * 0.052f,
                cx + side * r * 0.185f, mY + r * 0.042f
            )
            canvas.drawPath(path, paint)
        }
    }

    // ── 8. Cheek blush ───────────────────────────────────────────────────────
    private fun drawBlush(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val blushY  = cy + r * 0.12f
        val blushOx = r * 0.43f
        val blushR  = r * 0.22f
        for (side in listOf(-1f, 1f)) {
            val bx = cx + side * blushOx
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = RadialGradient(
                bx, blushY, blushR,
                intArrayOf(Color.parseColor("#48E5A5A0"), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(bx, blushY, blushR, paint)
            paint.shader = null
        }
    }
}
