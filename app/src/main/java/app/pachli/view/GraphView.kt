/* Copyright 2023 Tusky Contributors
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import androidx.core.content.withStyledAttributes
import androidx.core.util.TypedValueCompat.dpToPx
import app.pachli.R
import app.pachli.core.common.util.formatNumber
import com.google.android.material.color.MaterialColors
import kotlin.math.max

class GraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.graphViewStyle,
) : View(context, attrs, defStyleAttr, R.style.Pachli_Widget_GraphView) {
    @get:ColorInt
    @ColorInt
    var primaryLineColor = 0

    @get:ColorInt
    @ColorInt
    var secondaryLineColor = 0

    @get:Dimension
    var lineWidth = 0f

    @get:ColorInt
    @ColorInt
    var graphColor = 0

    @get:ColorInt
    @ColorInt
    var metaColor = 0

    private var proportionalTrending = false

    private val primaryLinePaint: Paint
    private val secondaryLinePaint: Paint
    private val primaryCirclePaint: Paint
    private val secondaryCirclePaint: Paint
    private val primaryTextPaint: Paint
    private val secondaryTextPaint: Paint
    private var labelTextSize: Float = dpToPx(11f, context.resources.displayMetrics)
    private val graphPaint: Paint
    private val metaPaint: Paint

    private var paddingEnd: Float = 0f

    private lateinit var sizeRect: Rect
    private var primaryLinePath: Path = Path()
    private var secondaryLinePath: Path = Path()

    var maxTrendingValue: Long = 300
    var primaryLineData: List<Long> = if (isInEditMode) {
        listOf(30, 60, 70, 80, 130, 190, 80)
    } else {
        listOf(1, 1, 1, 1, 1, 1, 1)
    }
        set(value) {
            field = value.map { max(1, it) }
            primaryLinePath.reset()
            invalidate()
        }

    var secondaryLineData: List<Long> = if (isInEditMode) {
        listOf(10, 20, 40, 60, 100, 132, 20)
    } else {
        listOf(1, 1, 1, 1, 1, 1, 1)
    }
        set(value) {
            field = value.map { max(1, it) }
            secondaryLinePath.reset()
            invalidate()
        }

    init {
        context.withStyledAttributes(attrs, R.styleable.GraphView, defStyleAttr, R.style.Pachli_Widget_GraphView) {
            primaryLineColor = getColor(
                R.styleable.GraphView_primaryLineColor,
                MaterialColors.getColor(this@GraphView, com.google.android.material.R.attr.colorPrimary),
            )

            secondaryLineColor = getColor(
                R.styleable.GraphView_secondaryLineColor,
                MaterialColors.getColor(this@GraphView, com.google.android.material.R.attr.colorSecondary),
            )

            metaColor = getColor(
                R.styleable.GraphView_metaColor,
                MaterialColors.getColor(this@GraphView, com.google.android.material.R.attr.colorOutline),
            )

            lineWidth = getDimensionPixelSize(
                R.styleable.GraphView_lineWidth,
                R.dimen.graph_line_thickness,
            ).toFloat()

            graphColor = getColor(
                R.styleable.GraphView_graphColor,
                MaterialColors.getColor(this@GraphView, android.R.attr.colorBackground),
            )

            proportionalTrending = getBoolean(
                R.styleable.GraphView_proportionalTrending,
                proportionalTrending,
            )

            labelTextSize = getDimensionPixelSize(
                R.styleable.GraphView_labelTextSize,
                labelTextSize.toInt(),
            ).toFloat()
        }

        primaryLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryLineColor
            strokeWidth = lineWidth
            style = Paint.Style.STROKE
        }

        primaryCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryLineColor
            style = Paint.Style.FILL
        }

        secondaryLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryLineColor
            strokeWidth = lineWidth
            style = Paint.Style.STROKE
        }

        secondaryCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryLineColor
            style = Paint.Style.FILL
        }

        primaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryLineColor
            style = Paint.Style.FILL
            textSize = labelTextSize
            textAlign = Paint.Align.RIGHT
        }

        secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryLineColor
            style = Paint.Style.FILL
            textSize = labelTextSize
            textAlign = Paint.Align.RIGHT
        }

        graphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = graphColor
        }

        metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = metaColor
            strokeWidth = 0f
            style = Paint.Style.STROKE
        }

        // Determine how much padding to leave on the right/end of the chart so there's
        // space for the labels. The widest possible label string is "1000.0M", so
        // compute that width, with some additional space on the left to separate the
        // label from the line.
        val labelBounds = Rect()
        primaryTextPaint.getTextBounds("1000.0M", 0, 7, labelBounds)
        paddingEnd = (4 * lineWidth) + labelBounds.width() + labelBounds.left
    }

    private fun initializeVertices() {
        sizeRect = Rect(0, 0, width, height)

        initLine(primaryLineData, primaryLinePath)
        initLine(secondaryLineData, secondaryLinePath)
    }

    private fun initLine(lineData: List<Long>, path: Path) {
        val max = if (proportionalTrending) {
            maxTrendingValue
        } else {
            max(primaryLineData.max(), 1)
        }
        val mainRatio = height.toFloat() / max.toFloat()

        val ratioedData = lineData.map { it.toFloat() * mainRatio }

        val pointDistance = dataSpacing(ratioedData)

        /** X coord of the start of this path segment */
        var startX = 0F

        /** Y coord of the start of this path segment */
        var startY = 0F

        /** X coord of the end of this path segment */
        var endX: Float

        /** Y coord of the end of this path segment */
        var endY: Float

        /** X coord of bezier control point #1 */
        var controlX1: Float

        /** X coord of bezier control point #2 */
        var controlX2: Float

        // Draw cubic bezier curves between each pair of points.
        ratioedData.forEachIndexed { index, magnitude ->
            val x = pointDistance * index.toFloat()
            val y = height.toFloat() - magnitude

            if (index == 0) {
                path.reset()
                path.moveTo(x, y)
                startX = x
                startY = y
            } else {
                endX = x
                endY = y

                // X-coord for a control point is placed one third of the distance between the
                // two points.
                val offsetX = (endX - startX) / 3
                controlX1 = startX + offsetX
                controlX2 = endX - offsetX
                path.cubicTo(controlX1, startY, controlX2, endY, x, y)

                startX = x
                startY = y
            }
        }
    }

    private fun dataSpacing(data: List<Any>) = (width.toFloat() - paddingEnd) / max(data.size - 1, 1).toFloat()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (primaryLinePath.isEmpty && width > 0) {
            initializeVertices()
        }

        canvas.apply {
            drawRect(sizeRect, graphPaint)

            val pointDistance = dataSpacing(primaryLineData)

            // Vertical tick marks
            for (i in primaryLineData.indices) {
                drawLine(
                    i * pointDistance,
                    height.toFloat(),
                    i * pointDistance,
                    height - (height.toFloat() / 20),
                    metaPaint,
                )
            }

            // X-axis
            drawLine(0f, height.toFloat(), width.toFloat() - paddingEnd, height.toFloat(), metaPaint)

            // Data lines
            drawLine(
                canvas = canvas,
                linePath = secondaryLinePath,
                linePaint = secondaryLinePaint,
                circlePaint = secondaryCirclePaint,
            )
            drawLine(
                canvas = canvas,
                linePath = primaryLinePath,
                linePaint = primaryLinePaint,
                circlePaint = primaryCirclePaint,
            )

            // Data text
            drawEndText(
                canvas,
                formatNumber(primaryLineData.last(), 1000),
                formatNumber(secondaryLineData.last(), 1000),
                primaryLinePath,
                secondaryLinePath,
            )
        }
    }

    private fun drawLine(canvas: Canvas, linePath: Path, linePaint: Paint, circlePaint: Paint) {
        canvas.apply {
            drawPath(linePath, linePaint)
            val (x, y) = pathEnd(linePath)
            drawCircle(x, y, lineWidth * 2f, circlePaint)
        }
    }

    private fun drawEndText(
        canvas: Canvas,
        primaryValue: String,
        secondaryValue: String,
        primaryLinePath: Path,
        secondaryLinePath: Path,
    ) {
        var (primaryX, primaryY) = pathEnd(primaryLinePath)
        var (_, secondaryY) = pathEnd(secondaryLinePath)

        val primaryBounds = Rect()
        val secondaryBounds = Rect()
        primaryTextPaint.getTextBounds(primaryValue, 0, primaryValue.length, primaryBounds)
        secondaryTextPaint.getTextBounds(secondaryValue, 0, secondaryValue.length, secondaryBounds)

        // Adjust both texts to horizontally align with their respective circle endpoints
        primaryY += primaryBounds.height().toFloat() / 2
        secondaryY += secondaryBounds.height().toFloat() / 2

        // Force the two apart if they overlap
        val overlap = primaryY - (secondaryY - secondaryBounds.height())
        // First try and force them both apart
        if (overlap > 0) {
            secondaryY += (overlap / 2) + 5
            primaryY -= (overlap / 2) + 5
        }
        // Now, if secondary is off the canvas move both of them up to compensate
        val secondaryClip = secondaryY - canvas.height
        if (secondaryClip > 0) {
            secondaryY -= secondaryClip
            primaryY -= secondaryClip
        }

        // The number text is right aligned to ensure they line up. The primary text
        // (total usage) is always going to be larger than the secondary text, so use
        // that to determine the X position of the right-hand edge of the text. This is:
        // - primaryX
        // - + 4 * lineWidth (spacing between the line circle and the text)
        // - + primaryBounds.width() (width of the text)
        // - + primaryBounds.left (left margin of the text)
        val textX = primaryX + (4 * lineWidth) + primaryBounds.width() + primaryBounds.left
        canvas.apply {
            drawText(primaryValue, textX, primaryY, primaryTextPaint)
            drawText(secondaryValue, textX, secondaryY, secondaryTextPaint)
        }
    }

    private fun pathEnd(path: Path): Pair<Float, Float> {
        val pm = PathMeasure(path, false)
        val coord = floatArrayOf(0f, 0f)
        pm.getPosTan(pm.length * 1f, coord, null)
        return Pair(coord[0], coord[1])
    }
}
