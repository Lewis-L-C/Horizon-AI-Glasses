package com.blue.glassesapp.feature.home.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ✅ OCR 文字框画笔（白色文字）
    private val textPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(180, 0, 0, 0)
        isAntiAlias = true
    }

    // ✅ OCR 框线画笔
    private val ocrStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.GREEN
        isAntiAlias = true
    }

    // ✅ 红绿灯框线画笔
    private val trafficStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    // ✅ 红绿灯标签背景画笔
    private val labelBgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(200, 0, 0, 0)
        isAntiAlias = true
    }

    // ✅ 红绿灯标签文字画笔
    private val labelPaint = Paint().apply {
        style = Paint.Style.FILL
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    // ✅ 存储映射参数（fillCenter）
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // ✅ 存储 OCR 文字框：Rect 和文字
    private val textRects = mutableListOf<Pair<Rect, String>>()

    // ✅ 存储红绿灯检测框：Rect、颜色、标签
    private val detectionRects = mutableListOf<Triple<Rect, Int, String>>()

    /**
     * 更新变换参数（在每一帧调用）
     */
    fun updateTransformation(previewWidth: Int, previewHeight: Int, imageWidth: Int, imageHeight: Int) {
        if (previewWidth == 0 || previewHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            return
        }

        val scaleX = previewWidth.toFloat() / imageWidth
        val scaleY = previewHeight.toFloat() / imageHeight
        scale = maxOf(scaleX, scaleY)

        offsetX = (previewWidth - imageWidth * scale) / 2
        offsetY = (previewHeight - imageHeight * scale) / 2
    }

    /**
     * 清除所有绘制
     */
    fun clear() {
        textRects.clear()
        detectionRects.clear()
        invalidate()
    }

    /**
     * 添加 OCR 文字矩形（自动应用变换）
     */
    fun addTextRect(rect: Rect, text: String) {
        val mappedRect = Rect(
            (rect.left * scale + offsetX).toInt(),
            (rect.top * scale + offsetY).toInt(),
            (rect.right * scale + offsetX).toInt(),
            (rect.bottom * scale + offsetY).toInt()
        )
        textRects.add(mappedRect to text)
    }

    /**
     * 添加红绿灯检测框（自动应用变换）
     */
    fun addDetectionRect(rect: Rect, color: Int, label: String) {
        val mappedRect = Rect(
            (rect.left * scale + offsetX).toInt(),
            (rect.top * scale + offsetY).toInt(),
            (rect.right * scale + offsetX).toInt(),
            (rect.bottom * scale + offsetY).toInt()
        )
        detectionRects.add(Triple(mappedRect, color, label))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // ============================================================
        // ✅ 绘制 OCR 文字框（绿色）
        // ============================================================
        for ((rect, text) in textRects) {
            ocrStrokePaint.color = Color.GREEN
            canvas.drawRect(rect, ocrStrokePaint)

            // 文字背景
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.textSize
            val padding = 6f

            val bgLeft = rect.left.toFloat()
            val bgTop = rect.top.toFloat() - textHeight - padding * 2
            val bgRight = bgLeft + textWidth + padding * 2
            val bgBottom = rect.top.toFloat()

            canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, bgPaint)

            // 绘制文字
            canvas.drawText(
                text,
                rect.left.toFloat() + padding,
                rect.top.toFloat() - padding,
                textPaint
            )
        }

        // ============================================================
        // ✅ 绘制红绿灯检测框（彩色）
        // ============================================================
        for ((rect, color, label) in detectionRects) {
            // 绘制矩形框（颜色动态设置）
            trafficStrokePaint.color = color
            canvas.drawRect(rect, trafficStrokePaint)

            // 绘制标签背景
            val labelText = label
            val textWidth = textPaint.measureText(labelText)
            val textHeight = textPaint.textSize
            val detectionPadding = 8f

            val bgLeft = rect.left.toFloat()
            val bgTop = rect.top.toFloat() - textHeight - detectionPadding * 2
            val bgRight = bgLeft + textWidth + detectionPadding * 2
            val bgBottom = rect.top.toFloat()

            canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, labelBgPaint)

            // 绘制标签文字（使用检测框颜色）
            labelPaint.color = color
            canvas.drawText(
                labelText,
                rect.left.toFloat() + detectionPadding,
                rect.top.toFloat() - detectionPadding,
                labelPaint
            )

            // 底部也显示文字
            val bottomLabelY = rect.bottom.toFloat() + textHeight + detectionPadding
            val bottomBgTop = rect.bottom.toFloat()
            val bottomBgBottom = bottomLabelY + detectionPadding
            canvas.drawRect(
                rect.left.toFloat(),
                bottomBgTop,
                rect.left.toFloat() + textWidth + detectionPadding * 2,
                bottomBgBottom,
                labelBgPaint
            )
            canvas.drawText(
                labelText,
                rect.left.toFloat() + detectionPadding,
                bottomLabelY,
                labelPaint
            )
        }
    }
}