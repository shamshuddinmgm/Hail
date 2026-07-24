package com.aistra.hail.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import kotlin.math.max
import kotlin.math.min

/**
 * Vertical fast-scroll thumb for long app lists — drag to jump quickly.
 */
class FastScrollSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var recycler: RecyclerView? = null
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbRect = RectF()
    private var thumbH = 0f
    private var dragging = false

    init {
        trackPaint.color = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorOutlineVariant
        )
        trackPaint.alpha = 80
        thumbPaint.color = MaterialColors.getColor(
            this, androidx.appcompat.R.attr.colorPrimary
        )
        thumbPaint.alpha = 200
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun attachTo(recyclerView: RecyclerView) {
        recycler = recyclerView
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!dragging) invalidate()
            }
        })
        recyclerView.adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = invalidate()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = invalidate()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = invalidate()
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rv = recycler ?: return
        val range = rv.computeVerticalScrollRange()
        val extent = rv.computeVerticalScrollExtent()
        if (range <= extent) return

        val w = width.toFloat()
        val h = height.toFloat()
        val trackW = w * 0.35f
        val left = (w - trackW) / 2f
        canvas.drawRoundRect(left, 0f, left + trackW, h, trackW, trackW, trackPaint)

        thumbH = max(w * 1.8f, h * extent / range)
        val maxOffset = range - extent
        val offset = rv.computeVerticalScrollOffset().toFloat()
        val travel = h - thumbH
        val top = if (maxOffset <= 0) 0f else travel * (offset / maxOffset)
        thumbRect.set(left - 2f, top, left + trackW + 2f, top + thumbH)
        canvas.drawRoundRect(thumbRect, trackW, trackW, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rv = recycler ?: return super.onTouchEvent(event)
        val range = rv.computeVerticalScrollRange()
        val extent = rv.computeVerticalScrollExtent()
        if (range <= extent) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                dragging = true
                parent.requestDisallowInterceptTouchEvent(true)
                val travel = height - thumbH
                val fraction = if (travel <= 0f) 0f else min(1f, max(0f, (event.y - thumbH / 2f) / travel))
                val target = ((range - extent) * fraction).toInt()
                rv.scrollBy(0, target - rv.computeVerticalScrollOffset())
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
