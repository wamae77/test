package com.example.fingerprintmodule

import ai.tech5.sdk.abis.T5AirSnap.SgmRectImage
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View

class GraphicOverlay(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {
    private var m_borderPaint: Paint? = null
    private var m_borderRect: Rect? = null
    private var m_boundBoxPaint: Paint? = null
    private val m_rectangles = ArrayList<SgmRectImage>()

    init {
        m_borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        m_borderPaint!!.style = Paint.Style.STROKE
        m_boundBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        m_boundBoxPaint!!.style = Paint.Style.STROKE
        m_boundBoxPaint!!.color = resources.getColor(R.color.colorPrimaryTrans)
    }

    fun init(borderRect: Rect) {
        val squareWidth = (borderRect.width() + borderRect.height()) / 2
        m_borderPaint!!.strokeWidth = 0.02f * squareWidth
        m_boundBoxPaint!!.strokeWidth = 0.01f * squareWidth
        m_borderRect = borderRect
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (m_borderRect == null) {
            return
        }
        for (rectangle in m_rectangles) {
            val x0 = rectangle.coords[0][0].toFloat()
            val y0 = rectangle.coords[0][1].toFloat()
            val dx = (rectangle.coords[1][0] - x0).toDouble()
            val dy = (rectangle.coords[1][1] - y0).toDouble()
            val angle = (Math.atan2(dy, dx) * 180.0 / Math.PI).toFloat()
            val distance = Math.sqrt(Math.pow(dx, 2.0) + Math.pow(dy, 2.0)).toFloat()
            canvas.rotate(angle, x0, y0)
            canvas.drawOval(
                x0 - distance, y0 - 0.6f * distance,
                x0 + distance, y0 + 0.6f * distance,
                m_boundBoxPaint!!
            )
            canvas.rotate(-angle, x0, y0)
        }
        canvas.drawRect(m_borderRect!!, m_borderPaint!!)
        Log.d("TAG", "onDraw: $m_boundBoxPaint.,$m_boundBoxPaint")
    }

    fun drawBorderAndBoundBoxes(color: Int, rectangles: ArrayList<SgmRectImage>?) {
        m_borderPaint!!.color = color
        m_rectangles.clear()
        m_rectangles.addAll(rectangles!!)
        invalidate()
    }
}
