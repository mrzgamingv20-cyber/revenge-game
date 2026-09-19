package com.mrzgaming.revenge.dev

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor
import kotlin.math.min

/**
 * Editor grid: paint tile, drag player/enemy markers, long-press hapus enemy.
 */
class MapEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Tool { PAINT, PLAYER, ENEMY, ERASE }

    var map: EditableMap = EditableMap.blank()
        set(value) {
            field = value
            invalidate()
        }

    var tool: Tool = Tool.PAINT
    var brush: Char = '#'

    var onMapChanged: (() -> Unit)? = null

    private var cellSize = 40f
    private var originX = 0f
    private var originY = 0f
    private var dragEnemyIndex = -1
    private var draggingPlayer = false

    private val gridPaint = Paint().apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 28f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutGrid()
    }

    private fun layoutGrid() {
        if (map.width == 0 || map.height == 0) return
        cellSize = min(width / (map.width + 0.5f), height / (map.height + 0.5f))
        originX = (width - cellSize * map.width) / 2f
        originY = (height - cellSize * map.height) / 2f
        textPaint.textSize = cellSize * 0.45f
    }

    override fun onDraw(canvas: Canvas) {
        layoutGrid()
        canvas.drawColor(Color.rgb(18, 16, 22))
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val left = originX + x * cellSize
                val top = originY + y * cellSize
                val rect = RectF(left, top, left + cellSize, top + cellSize)
                val fill = Paint().apply { color = colorFor(map.cell(x, y)) }
                canvas.drawRect(rect, fill)
                canvas.drawRect(rect, gridPaint)
            }
        }
        // enemies
        val enemyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(220, 80, 80) }
        map.enemies.forEachIndexed { i, (ex, ey) ->
            val cx = originX + ex.toFloat() * cellSize
            val cy = originY + ey.toFloat() * cellSize
            canvas.drawCircle(cx, cy, cellSize * 0.32f, enemyPaint)
            textPaint.textSize = cellSize * 0.28f
            canvas.drawText("E${i + 1}", cx, cy + cellSize * 0.1f, textPaint)
        }
        // player
        val px = originX + map.playerX.toFloat() * cellSize
        val py = originY + map.playerY.toFloat() * cellSize
        val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 200, 120) }
        canvas.drawCircle(px, py, cellSize * 0.36f, playerPaint)
        // direction wedge
        val dirLen = cellSize * 0.55f
        val dx = (kotlin.math.cos(map.playerAngle) * dirLen).toFloat()
        val dy = (kotlin.math.sin(map.playerAngle) * dirLen).toFloat()
        val dirPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(px, py, px + dx, py + dy, dirPaint)
        textPaint.textSize = cellSize * 0.28f
        canvas.drawText("P", px, py + cellSize * 0.1f, textPaint)
    }

    private fun colorFor(c: Char): Int = when (c) {
        '#' -> Color.rgb(90, 90, 95)
        '=' -> Color.rgb(140, 90, 50)
        '~' -> Color.rgb(120, 120, 130)
        else -> Color.rgb(35, 32, 40)
    }

    private fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val cx = floor((x - originX) / cellSize).toInt()
        val cy = floor((y - originY) / cellSize).toInt()
        if (cx !in 0 until map.width || cy !in 0 until map.height) return null
        return cx to cy
    }

    private fun worldAt(x: Float, y: Float): Pair<Double, Double> {
        val wx = ((x - originX) / cellSize).toDouble().coerceIn(0.5, map.width - 0.5)
        val wy = ((y - originY) / cellSize).toDouble().coerceIn(0.5, map.height - 0.5)
        return wx to wy
    }

    private fun hitEnemy(x: Float, y: Float): Int {
        map.enemies.forEachIndexed { i, (ex, ey) ->
            val cx = originX + ex.toFloat() * cellSize
            val cy = originY + ey.toFloat() * cellSize
            val dx = x - cx
            val dy = y - cy
            if (dx * dx + dy * dy < (cellSize * 0.4f) * (cellSize * 0.4f)) return i
        }
        return -1
    }

    private fun hitPlayer(x: Float, y: Float): Boolean {
        val cx = originX + map.playerX.toFloat() * cellSize
        val cy = originY + map.playerY.toFloat() * cellSize
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy < (cellSize * 0.45f) * (cellSize * 0.45f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (tool) {
                    Tool.PLAYER -> {
                        if (hitPlayer(x, y)) {
                            draggingPlayer = true
                        } else {
                            val (wx, wy) = worldAt(x, y)
                            map.playerX = wx
                            map.playerY = wy
                            onMapChanged?.invoke()
                            invalidate()
                        }
                    }
                    Tool.ENEMY -> {
                        val hit = hitEnemy(x, y)
                        if (hit >= 0) {
                            dragEnemyIndex = hit
                        } else {
                            val (wx, wy) = worldAt(x, y)
                            map.enemies.add(wx to wy)
                            dragEnemyIndex = map.enemies.lastIndex
                            onMapChanged?.invoke()
                            invalidate()
                        }
                    }
                    Tool.ERASE -> {
                        val hit = hitEnemy(x, y)
                        if (hit >= 0) {
                            map.enemies.removeAt(hit)
                            onMapChanged?.invoke()
                            invalidate()
                        } else {
                            cellAt(x, y)?.let { (cx, cy) ->
                                map.setCell(cx, cy, '.')
                                onMapChanged?.invoke()
                                invalidate()
                            }
                        }
                    }
                    Tool.PAINT -> {
                        // allow dragging player/enemy even in paint if hit
                        if (hitPlayer(x, y)) {
                            draggingPlayer = true
                        } else {
                            val hit = hitEnemy(x, y)
                            if (hit >= 0) {
                                dragEnemyIndex = hit
                            } else {
                                paintAt(x, y)
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when {
                    draggingPlayer -> {
                        val (wx, wy) = worldAt(x, y)
                        map.playerX = wx
                        map.playerY = wy
                        invalidate()
                    }
                    dragEnemyIndex >= 0 -> {
                        val (wx, wy) = worldAt(x, y)
                        map.enemies[dragEnemyIndex] = wx to wy
                        invalidate()
                    }
                    tool == Tool.PAINT || tool == Tool.ERASE -> paintAt(x, y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingPlayer || dragEnemyIndex >= 0) onMapChanged?.invoke()
                draggingPlayer = false
                dragEnemyIndex = -1
            }
        }
        return true
    }

    private fun paintAt(x: Float, y: Float) {
        val cell = cellAt(x, y) ?: return
        val c = if (tool == Tool.ERASE) '.' else brush
        if (map.cell(cell.first, cell.second) != c) {
            map.setCell(cell.first, cell.second, c)
            onMapChanged?.invoke()
            invalidate()
        }
    }

    fun rotatePlayer(deltaDeg: Float) {
        map.playerAngle += Math.toRadians(deltaDeg.toDouble())
        onMapChanged?.invoke()
        invalidate()
    }

    fun rotateMapCw() {
        map.rotateCw()
        onMapChanged?.invoke()
        invalidate()
    }
}
