package com.example.kotlinmod4

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#101C2A")
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#27425E")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6E879E")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E24A4A")
        style = Paint.Style.FILL
    }

    private val southPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A99A8")
        style = Paint.Style.FILL
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8EEF5")
        style = Paint.Style.FILL
    }

    private val northTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F3F7FB")
        textSize = 56f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var renderedRotation = 0f
    private var animator: ValueAnimator? = null

    fun animateToAzimuth(azimuth: Float) {
        val targetRotation = -azimuth
        val delta = shortestDelta(renderedRotation, targetRotation)
        val finalTarget = renderedRotation + delta

        animator?.cancel()
        animator = ValueAnimator.ofFloat(renderedRotation, finalTarget).apply {
            duration = 180L
            addUpdateListener { valueAnimator ->
                renderedRotation = valueAnimator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()
        val radius = size * 0.42f
        val cx = width / 2f
        val cy = height / 2f

        canvas.drawCircle(cx, cy, radius, discPaint)
        canvas.drawCircle(cx, cy, radius * 0.92f, ringPaint)
        canvas.drawCircle(cx, cy, radius * 0.70f, ringPaint)

        drawTicks(canvas, cx, cy, radius)
        canvas.drawText("N", cx, cy - radius * 0.68f, northTextPaint)

        canvas.save()
        canvas.rotate(renderedRotation, cx, cy)
        drawArrow(canvas, cx, cy, radius)
        canvas.restore()
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        for (degree in 0 until 360 step 30) {
            val angle = Math.toRadians((degree - 90).toDouble())
            val start = if (degree % 90 == 0) radius * 0.78f else radius * 0.84f
            val stop = radius * 0.92f
            val startX = cx + cos(angle).toFloat() * start
            val startY = cy + sin(angle).toFloat() * start
            val stopX = cx + cos(angle).toFloat() * stop
            val stopY = cy + sin(angle).toFloat() * stop
            canvas.drawLine(startX, startY, stopX, stopY, tickPaint)
        }
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val arrowLength = radius * 0.72f
        val arrowHalfWidth = radius * 0.10f

        val northPath = android.graphics.Path().apply {
            moveTo(cx, cy - arrowLength)
            lineTo(cx - arrowHalfWidth, cy)
            lineTo(cx + arrowHalfWidth, cy)
            close()
        }

        val southPath = android.graphics.Path().apply {
            moveTo(cx, cy + arrowLength * 0.62f)
            lineTo(cx - arrowHalfWidth, cy)
            lineTo(cx + arrowHalfWidth, cy)
            close()
        }

        canvas.drawPath(southPath, southPaint)
        canvas.drawPath(northPath, northPaint)
        canvas.drawOval(
            RectF(cx - radius * 0.05f, cy - radius * 0.05f, cx + radius * 0.05f, cy + radius * 0.05f),
            centerPaint
        )
    }

    private fun shortestDelta(current: Float, target: Float): Float {
        var delta = (target - current) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }
}
