package com.mink.app.ui.theme

import android.content.Context
import android.graphics.*
import android.view.View
import com.mink.app.ui.dsl.dp

/**
 * Canvas-drawn icon system. No emoji, no letters, no XML drawables needed.
 * All icons are drawn at the specified size with configurable tint.
 */
enum class Icon {
    DISCOVER, LIBRARY, NEWS, SETTINGS,
    PLAY, PAUSE, SKIP_FORWARD, SKIP_BACK, FORWARD_30, BACK_15,
    CHEVRON_LEFT, CHEVRON_DOWN, CHEVRON_RIGHT,
    SEARCH, ADD, CLOSE, DELETE, SHARE, REFRESH,
    STAR, STAR_FILLED, RSS, AI_SPARK, SNIP, CHECK,
    SORT, FILTER, SPEED, SLEEP, MORE_VERT,
    PODCAST, EPISODE, HEADPHONE, HEART, HEART_FILLED,
    WAVEFORM, PLAYLIST, DOWNLOAD, ERROR, EMPTY_INBOX
}

class IconView(
    context: Context,
    var icon: Icon,
    private val sizeDp: Int = 24,
    var tint: Int = AppTheme.textSecondary
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()

    init {
        val s = dp(sizeDp)
        minimumWidth = s
        minimumHeight = s
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = dp(sizeDp)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val sw = w / 12f   // stroke weight relative to size

        paint.color = tint
        paint.strokeWidth = sw
        fillPaint.color = tint

        canvas.save()
        // Normalise to a 24×24 grid
        canvas.scale(w / 24f, h / 24f)
        drawIcon(canvas, icon)
        canvas.restore()
    }

    private fun drawIcon(c: Canvas, icon: Icon) {
        val p = paint
        val fp = fillPaint

        when (icon) {
            Icon.DISCOVER -> {
                // Premium Compass / Explore star
                p.strokeWidth = 1.8f
                c.drawCircle(12f, 12f, 8.5f, p)
                val starPath = Path().apply {
                    moveTo(12f, 4.5f); lineTo(13.5f, 10.5f); lineTo(19.5f, 12f); lineTo(13.5f, 13.5f)
                    lineTo(12f, 19.5f); lineTo(10.5f, 13.5f); lineTo(4.5f, 12f); lineTo(10.5f, 10.5f); close()
                }
                c.drawPath(starPath, p)
                c.drawCircle(12f, 12f, 1.8f, fp)
            }

            Icon.LIBRARY -> {
                // Premium Bookshelf / Stack
                p.strokeWidth = 1.8f
                val r1 = RectF(4f, 4f, 20f, 9f)
                val r2 = RectF(4f, 10f, 20f, 15f)
                val r3 = RectF(4f, 16f, 20f, 21f)
                c.drawRoundRect(r1, 2f, 2f, p)
                c.drawRoundRect(r2, 2f, 2f, p)
                c.drawRoundRect(r3, 2f, 2f, p)
                c.drawLine(7f, 4f, 7f, 9f, p)
                c.drawLine(7f, 10f, 7f, 15f, p)
                c.drawLine(7f, 16f, 7f, 21f, p)
            }

            Icon.NEWS -> {
                // Premium Newspaper / Feed
                p.strokeWidth = 1.8f
                val box = RectF(3f, 3f, 21f, 21f)
                c.drawRoundRect(box, 3f, 3f, p)
                c.drawLine(7f, 7f, 17f, 7f, p)
                val thumb = RectF(7f, 10f, 11f, 16f)
                c.drawRoundRect(thumb, 1f, 1f, p)
                c.drawLine(13f, 10.5f, 17f, 10.5f, p)
                c.drawLine(13f, 13f, 17f, 13f, p)
                c.drawLine(7f, 18.5f, 17f, 18.5f, p)
            }

            Icon.SETTINGS -> {
                // Premium Gear
                p.strokeWidth = 1.8f
                c.drawCircle(12f, 12f, 3.5f, p)
                c.drawCircle(12f, 12f, 8f, p)
                val gearPath = Path()
                for (i in 0 until 8) {
                    val angle = Math.toRadians(i * 45.0)
                    val cos = Math.cos(angle).toFloat()
                    val sin = Math.sin(angle).toFloat()
                    gearPath.moveTo(12f + cos * 7.5f, 12f + sin * 7.5f)
                    gearPath.lineTo(12f + cos * 10f, 12f + sin * 10f)
                }
                c.drawPath(gearPath, p)
            }

            Icon.PLAY -> {
                val tri = Path().apply {
                    moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
                }
                c.drawPath(tri, fp)
            }

            Icon.PAUSE -> {
                p.strokeWidth = 2.5f
                p.strokeCap = Paint.Cap.ROUND
                c.drawLine(8f, 5f, 8f, 19f, p)
                c.drawLine(16f, 5f, 16f, 19f, p)
            }

            Icon.SKIP_FORWARD -> {
                val tri1 = Path().apply { moveTo(5f, 5f); lineTo(14f, 12f); lineTo(5f, 19f); close() }
                val tri2 = Path().apply { moveTo(13f, 5f); lineTo(22f, 12f); lineTo(13f, 19f); close() }
                c.drawPath(tri1, fp); c.drawPath(tri2, fp)
            }

            Icon.SKIP_BACK -> {
                val tri1 = Path().apply { moveTo(19f, 19f); lineTo(10f, 12f); lineTo(19f, 5f); close() }
                val tri2 = Path().apply { moveTo(11f, 19f); lineTo(2f, 12f); lineTo(11f, 5f); close() }
                c.drawPath(tri1, fp); c.drawPath(tri2, fp)
            }

            Icon.FORWARD_30 -> {
                // Circular arrow + "30"
                p.strokeWidth = 1.6f
                val oval = RectF(3f, 4f, 17f, 18f)
                c.drawArc(oval, -200f, 300f, false, p)
                // arrowhead
                val arrPath = Path().apply { moveTo(15f, 3f); lineTo(18f, 6f); lineTo(15f, 9f) }
                c.drawPath(arrPath, p)
                // "30" text via small manual strokes – draw "3" and "0"
                val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = paint.color; textSize = 5f; isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                c.drawText("30", 19.5f, 14f, textP)
            }

            Icon.BACK_15 -> {
                p.strokeWidth = 1.6f
                val oval = RectF(7f, 4f, 21f, 18f)
                c.drawArc(oval, -340f, 300f, false, p)
                val arrPath = Path().apply { moveTo(9f, 3f); lineTo(6f, 6f); lineTo(9f, 9f) }
                c.drawPath(arrPath, p)
                val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = paint.color; textSize = 5f; isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                c.drawText("15", 4.5f, 14f, textP)
            }

            Icon.CHEVRON_LEFT -> {
                p.strokeWidth = 2f
                val chevPath = Path().apply { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
                c.drawPath(chevPath, p)
            }

            Icon.CHEVRON_RIGHT -> {
                p.strokeWidth = 2f
                val chevPath = Path().apply { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
                c.drawPath(chevPath, p)
            }

            Icon.CHEVRON_DOWN -> {
                p.strokeWidth = 2f
                val chevPath = Path().apply { moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f) }
                c.drawPath(chevPath, p)
            }

            Icon.SEARCH -> {
                p.strokeWidth = 1.8f
                c.drawCircle(10f, 10f, 6f, p)
                c.drawLine(14.5f, 14.5f, 21f, 21f, p)
            }

            Icon.ADD -> {
                p.strokeWidth = 2f
                c.drawLine(12f, 4f, 12f, 20f, p)
                c.drawLine(4f, 12f, 20f, 12f, p)
            }

            Icon.CLOSE -> {
                p.strokeWidth = 2f
                c.drawLine(4f, 4f, 20f, 20f, p)
                c.drawLine(20f, 4f, 4f, 20f, p)
            }

            Icon.DELETE -> {
                p.strokeWidth = 1.6f
                // trash can
                c.drawLine(3f, 6f, 21f, 6f, p)
                c.drawLine(8f, 6f, 8f, 4f, p); c.drawLine(8f, 4f, 16f, 4f, p); c.drawLine(16f, 4f, 16f, 6f, p)
                val bodyPath = Path().apply { moveTo(5f, 6f); lineTo(6f, 21f); lineTo(18f, 21f); lineTo(19f, 6f) }
                c.drawPath(bodyPath, p)
                c.drawLine(10f, 10f, 10f, 17f, p)
                c.drawLine(14f, 10f, 14f, 17f, p)
            }

            Icon.SHARE -> {
                p.strokeWidth = 1.6f
                c.drawCircle(5f, 12f, 2f, p)
                c.drawCircle(19f, 5f, 2f, p)
                c.drawCircle(19f, 19f, 2f, p)
                c.drawLine(7f, 11f, 17f, 6f, p)
                c.drawLine(7f, 13f, 17f, 18f, p)
            }

            Icon.REFRESH -> {
                p.strokeWidth = 1.8f
                c.drawArc(RectF(3f, 3f, 21f, 21f), 0f, 270f, false, p)
                val arrPath = Path().apply { moveTo(18f, 3f); lineTo(21f, 3f); lineTo(21f, 6f) }
                c.drawPath(arrPath, p)
            }

            Icon.STAR -> {
                p.strokeWidth = 1.5f
                c.drawPath(starPath(12f, 12f, 9f, 4f, 5), p)
            }

            Icon.STAR_FILLED -> {
                c.drawPath(starPath(12f, 12f, 9f, 4f, 5), fp)
            }

            Icon.RSS -> {
                p.strokeWidth = 1.8f
                // dot, two arcs
                c.drawCircle(5f, 19f, 1.5f, fp)
                c.drawArc(RectF(3f, 9f, 15f, 21f), 180f, -90f, false, p)
                c.drawArc(RectF(3f, 3f, 21f, 21f), 180f, -90f, false, p)
            }

            Icon.AI_SPARK -> {
                // 4-point star / sparkle
                fp.color = tint
                val sparkPath = Path().apply {
                    moveTo(12f, 2f); lineTo(13.5f, 10.5f); lineTo(22f, 12f)
                    lineTo(13.5f, 13.5f); lineTo(12f, 22f); lineTo(10.5f, 13.5f)
                    lineTo(2f, 12f); lineTo(10.5f, 10.5f); close()
                }
                c.drawPath(sparkPath, fp)
            }

            Icon.SNIP -> {
                p.strokeWidth = 1.6f
                // Scissors
                c.drawCircle(6f, 7f, 3f, p); c.drawCircle(6f, 17f, 3f, p)
                c.drawLine(9f, 8.5f, 21f, 3f, p); c.drawLine(9f, 15.5f, 21f, 21f, p)
                c.drawLine(9f, 8.5f, 15f, 12f, p); c.drawLine(9f, 15.5f, 15f, 12f, p)
            }

            Icon.CHECK -> {
                p.strokeWidth = 2.2f
                val checkPath = Path().apply { moveTo(4f, 12f); lineTo(9f, 17f); lineTo(20f, 5f) }
                c.drawPath(checkPath, p)
            }

            Icon.SORT -> {
                p.strokeWidth = 1.8f
                c.drawLine(3f, 6f, 21f, 6f, p)
                c.drawLine(6f, 12f, 18f, 12f, p)
                c.drawLine(9f, 18f, 15f, 18f, p)
            }

            Icon.FILTER -> {
                p.strokeWidth = 1.8f
                val funnel = Path().apply {
                    moveTo(3f, 4f); lineTo(21f, 4f); lineTo(14f, 13f); lineTo(14f, 20f)
                    lineTo(10f, 20f); lineTo(10f, 13f); close()
                }
                c.drawPath(funnel, p)
            }

            Icon.SPEED -> {
                p.strokeWidth = 1.6f
                c.drawArc(RectF(3f, 5f, 21f, 19f), 180f, 180f, false, p)
                c.drawLine(12f, 12f, 16f, 8f, p)
                c.drawCircle(12f, 12f, 1.5f, fp)
            }

            Icon.SLEEP -> {
                p.strokeWidth = 1.6f
                val moonPath = Path().apply {
                    moveTo(21f, 12.79f)
                    cubicTo(21f, 17.21f, 17.21f, 21f, 12.79f, 21f)
                    cubicTo(8.37f, 21f, 4.58f, 17.21f, 4.58f, 12.79f)
                    cubicTo(4.58f, 8.37f, 8.37f, 4.58f, 12.79f, 4.58f)
                    cubicTo(11.14f, 6.5f, 10.22f, 8.91f, 10.22f, 11.5f)
                    cubicTo(10.22f, 16.36f, 14.25f, 20.22f, 19.22f, 20.22f)
                    cubicTo(20.09f, 20.22f, 20.63f, 20.09f, 21f, 19.86f)
                    cubicTo(21f, 17.62f, 21f, 14.89f, 21f, 12.79f)
                }
                c.drawPath(moonPath, p)
            }

            Icon.MORE_VERT -> {
                c.drawCircle(12f, 5f, 1.5f, fp)
                c.drawCircle(12f, 12f, 1.5f, fp)
                c.drawCircle(12f, 19f, 1.5f, fp)
            }

            Icon.PODCAST -> {
                p.strokeWidth = 1.6f
                c.drawCircle(12f, 11f, 4f, p)
                c.drawLine(12f, 15f, 12f, 21f, p)
                c.drawLine(8f, 21f, 16f, 21f, p)
                val waveOval = RectF(6f, 5f, 18f, 17f)
                c.drawArc(waveOval, 200f, 140f, false, p)
            }

            Icon.EPISODE -> {
                p.strokeWidth = 1.6f
                val waveform = Path().apply {
                    moveTo(4f, 12f); lineTo(7f, 12f); lineTo(7f, 8f); lineTo(10f, 8f)
                    lineTo(10f, 16f); lineTo(13f, 16f); lineTo(13f, 6f); lineTo(16f, 6f)
                    lineTo(16f, 18f); lineTo(19f, 18f); lineTo(19f, 12f); lineTo(22f, 12f)
                }
                c.drawPath(waveform, p)
            }

            Icon.HEADPHONE -> {
                p.strokeWidth = 1.6f
                c.drawArc(RectF(4f, 2f, 20f, 16f), 180f, 180f, false, p)
                c.drawRoundRect(RectF(2f, 13f, 7f, 20f), 2f, 2f, p)
                c.drawRoundRect(RectF(17f, 13f, 22f, 20f), 2f, 2f, p)
                c.drawLine(4f, 16f, 4f, 13f, p)
                c.drawLine(20f, 16f, 20f, 13f, p)
            }

            Icon.HEART -> {
                p.strokeWidth = 1.6f
                c.drawPath(heartPath(), p)
            }

            Icon.HEART_FILLED -> {
                c.drawPath(heartPath(), fp)
            }

            Icon.WAVEFORM -> {
                p.strokeWidth = 1.8f
                val bars = floatArrayOf(4f, 8f, 6f, 4f, 9f, 5f, 7f, 3f, 6f)
                bars.forEachIndexed { i, half ->
                    val x = 3f + i * 2.25f
                    c.drawLine(x, 12f - half, x, 12f + half, p)
                }
            }

            Icon.PLAYLIST -> {
                p.strokeWidth = 1.6f
                c.drawLine(4f, 6f, 16f, 6f, p)
                c.drawLine(4f, 10f, 16f, 10f, p)
                c.drawLine(4f, 14f, 11f, 14f, p)
                // little play triangle on right
                val tri = Path().apply { moveTo(15f, 12f); lineTo(21f, 15f); lineTo(15f, 18f); close() }
                c.drawPath(tri, fp)
            }

            Icon.DOWNLOAD -> {
                p.strokeWidth = 1.8f
                c.drawLine(12f, 3f, 12f, 16f, p)
                val arrPath = Path().apply { moveTo(6f, 10f); lineTo(12f, 16f); lineTo(18f, 10f) }
                c.drawPath(arrPath, p)
                c.drawLine(4f, 21f, 20f, 21f, p)
            }

            Icon.ERROR -> {
                p.strokeWidth = 1.8f
                c.drawCircle(12f, 12f, 9f, p)
                c.drawLine(12f, 7f, 12f, 13f, p)
                c.drawCircle(12f, 17f, 1f, fp)
            }

            Icon.EMPTY_INBOX -> {
                p.strokeWidth = 1.5f
                val box = Path().apply {
                    moveTo(22f, 12f); lineTo(18f, 3f); lineTo(6f, 3f); lineTo(2f, 12f)
                    lineTo(2f, 20f); lineTo(22f, 20f); lineTo(22f, 12f)
                }
                c.drawPath(box, p)
                c.drawLine(2f, 12f, 8f, 12f, p)
                c.drawLine(16f, 12f, 22f, 12f, p)
                c.drawArc(RectF(8f, 9f, 16f, 15f), 0f, -180f, false, p)
            }
        }
    }

    private fun starPath(cx: Float, cy: Float, outerR: Float, innerR: Float, points: Int): Path {
        val path = Path()
        val angleStep = Math.PI / points
        for (i in 0 until points * 2) {
            val angle = i * angleStep - Math.PI / 2
            val r = if (i % 2 == 0) outerR else innerR
            val x = cx + (r * Math.cos(angle)).toFloat()
            val y = cy + (r * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun heartPath(): Path {
        return Path().apply {
            moveTo(12f, 21f)
            cubicTo(12f, 21f, 3f, 14f, 3f, 8.5f)
            cubicTo(3f, 5.42f, 5.42f, 3f, 8.5f, 3f)
            cubicTo(10.24f, 3f, 11.91f, 3.81f, 13f, 5.08f - 1f)
            cubicTo(13f, 5.08f - 1f, 11f, 5f, 12f, 21f)
            moveTo(12f, 21f)
            cubicTo(12f, 21f, 21f, 14f, 21f, 8.5f)
            cubicTo(21f, 5.42f, 18.58f, 3f, 15.5f, 3f)
            cubicTo(13.76f, 3f, 12.09f, 3.81f, 12f, 5f)
        }
    }
}
