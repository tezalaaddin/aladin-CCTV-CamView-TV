package com.aladin.aladincamviewer

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.CheckBox
import android.widget.EditText
import androidx.core.view.doOnDetach
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.WeakHashMap

/** Applies one premium, D-pad-first focus language to every interactive screen element. */
object TvFocusManager {
    private const val FOCUS_SCALE = 1.055f
    private const val FOCUS_ELEVATION_DP = 14f
    private val orange = Color.rgb(255, 109, 0)
    private val snapshots = WeakHashMap<View, Snapshot>()

    private data class Snapshot(
        val scaleX: Float,
        val scaleY: Float,
        val translationZ: Float,
        val cardStrokeWidth: Int? = null,
        val cardStrokeColor: Int? = null,
        val buttonStrokeWidth: Int? = null,
        val buttonStrokeColor: ColorStateList? = null
    )

    fun install(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val focusRing = FocusRingView(activity)
        root.addView(focusRing, ViewGroup.LayoutParams(0, 0))
        prepareInteractiveViews(root)
        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { oldFocus, newFocus ->
            oldFocus?.let(::clearFocusStyle)
            if (newFocus == null) {
                focusRing.visibility = View.INVISIBLE
            } else {
                applyFocusStyle(newFocus)
                newFocus.post { positionRing(root, newFocus, focusRing) }
            }
        }
        root.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
        root.doOnDetach {
            if (root.viewTreeObserver.isAlive) {
                root.viewTreeObserver.removeOnGlobalFocusChangeListener(listener)
            }
            root.removeView(focusRing)
        }
    }

    private fun positionRing(root: View, focused: View, ring: FocusRingView) {
        if (!focused.isShown || !focused.hasFocus()) return
        val rootLocation = IntArray(2)
        val focusLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        focused.getLocationInWindow(focusLocation)
        val inset = (6f * root.resources.displayMetrics.density).toInt()
        ring.layoutParams = ring.layoutParams.apply {
            width = focused.width + inset * 2
            height = focused.height + inset * 2
        }
        ring.x = (focusLocation[0] - rootLocation[0] - inset).toFloat()
        ring.y = (focusLocation[1] - rootLocation[1] - inset).toFloat()
        ring.visibility = View.VISIBLE
        ring.bringToFront()
        ring.invalidate()
    }

    private fun prepareInteractiveViews(view: View) {
        if (view.isClickable || view is MaterialButton || view is CheckBox || view is EditText) {
            view.isFocusable = true
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) prepareInteractiveViews(view.getChildAt(index))
        }
    }

    private fun applyFocusStyle(view: View) {
        snapshots[view] = Snapshot(
            scaleX = view.scaleX,
            scaleY = view.scaleY,
            translationZ = view.translationZ,
            cardStrokeWidth = (view as? MaterialCardView)?.strokeWidth,
            cardStrokeColor = (view as? MaterialCardView)?.strokeColor,
            buttonStrokeWidth = (view as? MaterialButton)?.strokeWidth,
            buttonStrokeColor = (view as? MaterialButton)?.strokeColor
        )
        val density = view.resources.displayMetrics.density
        view.animate()
            .scaleX(FOCUS_SCALE)
            .scaleY(FOCUS_SCALE)
            .translationZ(FOCUS_ELEVATION_DP * density)
            .setDuration(140L)
            .start()
        (view as? MaterialCardView)?.apply {
            strokeColor = orange
            strokeWidth = (3f * density).toInt()
        }
        (view as? MaterialButton)?.apply {
            strokeColor = ColorStateList.valueOf(orange)
            strokeWidth = (2f * density).toInt()
        }
    }

    private fun clearFocusStyle(view: View) {
        val snapshot = snapshots.remove(view) ?: return
        view.animate()
            .scaleX(snapshot.scaleX)
            .scaleY(snapshot.scaleY)
            .translationZ(snapshot.translationZ)
            .setDuration(120L)
            .start()
        (view as? MaterialCardView)?.apply {
            snapshot.cardStrokeColor?.let { strokeColor = it }
            snapshot.cardStrokeWidth?.let { strokeWidth = it }
        }
        (view as? MaterialButton)?.apply {
            strokeColor = snapshot.buttonStrokeColor
            snapshot.buttonStrokeWidth?.let { strokeWidth = it }
        }
    }

    private class FocusRingView(context: android.content.Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 109, 0)
            style = Paint.Style.STROKE
            strokeWidth = 8f * density
        }
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 157, 40)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        private val radius = 13f * density

        init {
            isClickable = false
            isFocusable = false
            visibility = INVISIBLE
            elevation = 40f * density
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRoundRect(
                4f * density, 4f * density, width - 4f * density, height - 4f * density,
                radius, radius, outerPaint
            )
            canvas.drawRoundRect(
                4f * density, 4f * density, width - 4f * density, height - 4f * density,
                radius, radius, innerPaint
            )
        }
    }
}
