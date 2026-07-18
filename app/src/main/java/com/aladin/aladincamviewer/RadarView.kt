package com.aladin.aladincamviewer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6D00") // brand_orange
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FF6D00")
        style = Paint.Style.FILL
    }

    private var sweepAngle = 0f
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            sweepAngle = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(width, height) / 2f * 0.8f

        // Draw background circles
        circlePaint.alpha = 50
        canvas.drawCircle(centerX, centerY, radius, circlePaint)
        canvas.drawCircle(centerX, centerY, radius * 0.66f, circlePaint)
        canvas.drawCircle(centerX, centerY, radius * 0.33f, circlePaint)

        // Draw sweeping radar
        canvas.save()
        canvas.rotate(sweepAngle, centerX, centerY)
        canvas.drawArc(
            centerX - radius, centerY - radius,
            centerX + radius, centerY + radius,
            -90f, 60f, true, sweepPaint
        )
        canvas.restore()
        
        // Draw crosshair
        circlePaint.alpha = 100
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, circlePaint)
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, circlePaint)
    }
}
