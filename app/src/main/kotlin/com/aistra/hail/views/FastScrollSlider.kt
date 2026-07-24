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
 * Detaches cleanly to avoid listener leaks.
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
    private var scrollListener: RecyclerView.OnScrollListener? = null
    private var dataObserver: RecyclerView.AdapterDataObserver? = null

    init {
        runCatching {
            trackPaint.color = MaterialColors.getColor(
                this, com.google.android.material.R.attr.colorOutlineVariant
            )
            thumbPaint.color = MaterialColors.getColor(
                this, androidx.appcompat.R.attr.colorPrimary
            )
        }
        trackPaint.alpha = 70
        thumbPaint.alpha = 210
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun attachTo(recyclerView: RecyclerView) {
        detach()
        recycler = recyclerView
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!dragging) invalidate()
            }
        }.also { recyclerView.addOnScrollListener(it) }

        dataObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = invalidate()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = invalidate()
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = invalidate()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = invalidate()
        }.also { observer ->
            recyclerView.adapter?.registerAdapterDataObserver(observer)
        }
    }

    fun detach() {
        val rv = recycler ?: return
        scrollListener?.let { rv.removeOnScrollListener(it) }
        dataObserver?.let { observer ->
            runCatching { rv.adapter?.unregisterAdapterDataObserver(observer) }
        }
        scrollListener = null
        dataObserver = null
        recycler = null
    }

    override fun onDetachedFromWindow() {
        detach()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rv = recycler ?: return
        val range = rv.computeVerticalScrollRange()
        val extent = rv.computeVerticalScrollExtent()
        if (range <= extent) return

        val w = width.toFloat()
        val h = height.toFloat()
        val trackW = w * 0.4f
        val left = (w - trackW) / 2f
        canvas.drawRoundRect(left, 0f, left + trackW, h, trackW, trackW, trackPaint)

        thumbH = max(w * 2.2f, h * extent / range)
        val maxOffset = range - extent
        val offset = rv.computeVerticalScrollOffset().toFloat()
        val travel = h - thumbH
        val top = if (maxOffset <= 0) 0f else travel * (offset / maxOffset)
        thumbRect.set(0f, top, w, top + thumbH)
        canvas.drawRoundRect(thumbRect, trackW, trackW, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rv = recycler ?: return super.onTouchEvent(event)
        val range = rv.computeVerticalScrollRange()
        val extent = rv.computeVerticalScrollExtent()
        if (range <= extent) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Only grab if touch is near the thumb — avoid stealing checkbox taps
                if (event.y < thumbRect.top - thumbH || event.y > thumbRect.bottom + thumbH) {
                    return false
                }
                dragging = true
                parent.requestDisallowInterceptTouchEvent(true)
                scrollToY(event.y, rv, range, extent)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                scrollToY(event.y, rv, range, extent)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scrollToY(y: Float, rv: RecyclerView, range: Int, extent: Int) {
        val travel = height - thumbH
        val fraction = if (travel <= 0f) 0f else min(1f, max(0f, (y - thumbH / 2f) / travel))
        val target = ((range - extent) * fraction).toInt()
        rv.scrollBy(0, target - rv.computeVerticalScrollOffset())
        invalidate()
    }
}
