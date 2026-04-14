package com.hrishipvt.scantopdf.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.hrishipvt.scantopdf.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class ScanPulseCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val accentPrimary = ContextCompat.getColor(context, R.color.primary_warm)
    private val accentSecondary = ContextCompat.getColor(context, R.color.secondary_warm)
    private val accentHighlight = ContextCompat.getColor(context, R.color.accent_warm)
    private val surfaceColor = ContextCompat.getColor(context, R.color.surface)
    private val textSecondary = ContextCompat.getColor(context, R.color.hero_text_secondary)

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val mainCardRect = RectF()
    private val backCardRect = RectF()
    private var animationStartedAt = 0L
    private var shouldAnimate = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationStartedAt == 0L) {
            animationStartedAt = SystemClock.uptimeMillis()
        }
        shouldAnimate = true
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        shouldAnimate = false
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        shouldAnimate = visibility == VISIBLE
        if (shouldAnimate) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val widthF = width.toFloat()
        val heightF = height.toFloat()
        if (widthF <= 0f || heightF <= 0f) return

        val elapsedSeconds = (SystemClock.uptimeMillis() - animationStartedAt) / 1000f
        val centerX = widthF / 2f
        val centerY = heightF / 2f
        val baseSize = min(widthF, heightF)

        drawAmbientGlow(canvas, centerX, centerY, baseSize, elapsedSeconds)
        drawPulseRings(canvas, centerX, centerY, baseSize, elapsedSeconds)
        drawDocumentCards(canvas, centerX, centerY, baseSize, elapsedSeconds)
        drawOrbitalParticles(canvas, centerX, centerY, baseSize, elapsedSeconds)

        if (shouldAnimate) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawAmbientGlow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        baseSize: Float,
        timeSeconds: Float
    ) {
        val radius = baseSize * 0.52f
        val glowAlpha = (40 + (sin(timeSeconds * 1.3f) + 1f) * 25f).toInt()
        glowPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            withAlpha(accentSecondary, glowAlpha),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, glowPaint)
    }

    private fun drawPulseRings(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        baseSize: Float,
        timeSeconds: Float
    ) {
        for (ringIndex in 0 until 3) {
            val progress = ((timeSeconds * 0.28f) + ringIndex * 0.24f) % 1f
            val radius = baseSize * (0.22f + progress * 0.3f)
            val alpha = ((1f - progress) * 70f).toInt().coerceAtLeast(12)
            ringPaint.color = withAlpha(accentPrimary, alpha)
            canvas.drawCircle(centerX, centerY, radius, ringPaint)
        }

        gridPaint.color = withAlpha(textSecondary, 26)
        canvas.drawCircle(centerX, centerY, baseSize * 0.24f, gridPaint)
        canvas.drawCircle(centerX, centerY, baseSize * 0.34f, gridPaint)
    }

    private fun drawDocumentCards(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        baseSize: Float,
        timeSeconds: Float
    ) {
        val compactFactor = if (height < 220) 0.82f else 1f
        val cardWidth = baseSize * 0.34f * compactFactor
        val cardHeight = baseSize * 0.46f * compactFactor
        val floatOffset = sin(timeSeconds * 1.1f) * baseSize * 0.012f
        val corner = baseSize * 0.045f

        backCardRect.set(
            centerX - cardWidth / 2f - baseSize * 0.085f,
            centerY - cardHeight / 2f - baseSize * 0.025f + floatOffset,
            centerX + cardWidth / 2f - baseSize * 0.085f,
            centerY + cardHeight / 2f - baseSize * 0.025f + floatOffset
        )
        cardPaint.color = withAlpha(surfaceColor, 88)
        cardStrokePaint.color = withAlpha(accentSecondary, 96)
        canvas.save()
        canvas.rotate(-11f, backCardRect.centerX(), backCardRect.centerY())
        canvas.drawRoundRect(backCardRect, corner, corner, cardPaint)
        canvas.drawRoundRect(backCardRect, corner, corner, cardStrokePaint)
        drawCardRows(canvas, backCardRect, withAlpha(textSecondary, 42))
        canvas.restore()

        mainCardRect.set(
            centerX - cardWidth / 2f + baseSize * 0.055f,
            centerY - cardHeight / 2f + floatOffset,
            centerX + cardWidth / 2f + baseSize * 0.055f,
            centerY + cardHeight / 2f + floatOffset
        )
        cardPaint.color = withAlpha(surfaceColor, 214)
        cardStrokePaint.color = withAlpha(accentPrimary, 145)
        canvas.save()
        canvas.rotate(8f, mainCardRect.centerX(), mainCardRect.centerY())
        canvas.drawRoundRect(mainCardRect, corner, corner, cardPaint)
        canvas.drawRoundRect(mainCardRect, corner, corner, cardStrokePaint)
        drawCardRows(canvas, mainCardRect, withAlpha(textSecondary, 74))
        drawScanBeam(canvas, mainCardRect, timeSeconds)
        canvas.restore()
    }

    private fun drawCardRows(canvas: Canvas, rect: RectF, lineColor: Int) {
        particlePaint.color = lineColor
        particlePaint.strokeWidth = 6f
        val startX = rect.left + rect.width() * 0.15f
        val endX = rect.right - rect.width() * 0.15f
        val rowGap = rect.height() * 0.18f
        for (row in 0 until 4) {
            val y = rect.top + rect.height() * 0.24f + row * rowGap
            canvas.drawLine(startX, y, endX - row * rect.width() * 0.08f, y, particlePaint)
        }
    }

    private fun drawScanBeam(canvas: Canvas, rect: RectF, timeSeconds: Float) {
        val progress = ((timeSeconds * 0.45f) % 1f)
        val beamTop = rect.top + rect.height() * (0.16f + progress * 0.62f)
        val beamBottom = beamTop + rect.height() * 0.09f
        beamPaint.shader = LinearGradient(
            rect.left,
            beamTop,
            rect.right,
            beamBottom,
            intArrayOf(
                Color.TRANSPARENT,
                withAlpha(accentHighlight, 210),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            rect.left + rect.width() * 0.08f,
            beamTop,
            rect.right - rect.width() * 0.08f,
            beamBottom,
            rect.height() * 0.04f,
            rect.height() * 0.04f,
            beamPaint
        )
        beamPaint.shader = null
    }

    private fun drawOrbitalParticles(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        baseSize: Float,
        timeSeconds: Float
    ) {
        val orbitRadius = baseSize * 0.39f
        val particleRadius = if (height < 220) baseSize * 0.012f else baseSize * 0.014f

        for (particleIndex in 0 until 5) {
            val angle = timeSeconds * (0.55f + particleIndex * 0.08f) + particleIndex * 1.2f
            val x = centerX + cos(angle) * orbitRadius
            val y = centerY + sin(angle) * orbitRadius * 0.62f
            particlePaint.color = when (particleIndex % 3) {
                0 -> withAlpha(accentPrimary, 185)
                1 -> withAlpha(accentSecondary, 180)
                else -> withAlpha(accentHighlight, 180)
            }
            canvas.drawCircle(x, y, particleRadius, particlePaint)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}
