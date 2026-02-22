package com.hrishipvt.scantopdf.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val borderPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val shadePaint = Paint().apply {
        color = Color.parseColor("#88000000")
    }

    private val handlePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val handleRadius = 20f
    private val minCropSize = 150f

    private val cropRect = RectF(200f, 200f, 800f, 1000f)

    private var lastX = 0f
    private var lastY = 0f

    private enum class TouchMode {
        NONE, MOVE, TL, TR, BL, BR
    }

    private var touchMode = TouchMode.NONE

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Darkened outside area
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, shadePaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), shadePaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, shadePaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, shadePaint)

        // Crop border
        canvas.drawRect(cropRect, borderPaint)

        // Corner handles
        drawHandle(canvas, cropRect.left, cropRect.top)
        drawHandle(canvas, cropRect.right, cropRect.top)
        drawHandle(canvas, cropRect.left, cropRect.bottom)
        drawHandle(canvas, cropRect.right, cropRect.bottom)
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                touchMode = detectTouchMode(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY

                when (touchMode) {
                    TouchMode.MOVE -> cropRect.offset(dx, dy)
                    TouchMode.TL -> {
                        cropRect.left += dx
                        cropRect.top += dy
                    }
                    TouchMode.TR -> {
                        cropRect.right += dx
                        cropRect.top += dy
                    }
                    TouchMode.BL -> {
                        cropRect.left += dx
                        cropRect.bottom += dy
                    }
                    TouchMode.BR -> {
                        cropRect.right += dx
                        cropRect.bottom += dy
                    }
                    else -> {}
                }

                constrainRect()
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                touchMode = TouchMode.NONE
            }
        }
        return true
    }

    private fun detectTouchMode(x: Float, y: Float): TouchMode {
        return when {
            isNear(x, y, cropRect.left, cropRect.top) -> TouchMode.TL
            isNear(x, y, cropRect.right, cropRect.top) -> TouchMode.TR
            isNear(x, y, cropRect.left, cropRect.bottom) -> TouchMode.BL
            isNear(x, y, cropRect.right, cropRect.bottom) -> TouchMode.BR
            cropRect.contains(x, y) -> TouchMode.MOVE
            else -> TouchMode.NONE
        }
    }

    private fun isNear(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        return abs(x1 - x2) < handleRadius * 2 &&
                abs(y1 - y2) < handleRadius * 2
    }

    private fun constrainRect() {
        // Minimum size
        if (cropRect.width() < minCropSize) {
            cropRect.right = cropRect.left + minCropSize
        }
        if (cropRect.height() < minCropSize) {
            cropRect.bottom = cropRect.top + minCropSize
        }

        // Keep inside view bounds
        cropRect.left = max(0f, cropRect.left)
        cropRect.top = max(0f, cropRect.top)
        cropRect.right = min(width.toFloat(), cropRect.right)
        cropRect.bottom = min(height.toFloat(), cropRect.bottom)
    }

    fun getCropRect(): RectF = RectF(cropRect)

    fun reset() {
        cropRect.set(
            width * 0.1f,
            height * 0.1f,
            width * 0.9f,
            height * 0.9f
        )
        invalidate()
    }

}
