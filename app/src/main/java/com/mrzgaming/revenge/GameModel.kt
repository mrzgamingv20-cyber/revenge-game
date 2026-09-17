package com.mrzgaming.revenge

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class Player(var x: Double, var y: Double, angle: Double) {
    var dirX = cos(angle)
    var dirY = sin(angle)
    var planeX = -sin(angle) * 0.66
    var planeY = cos(angle) * 0.66
    var health = 100
    var ammo = 24
    var muzzleFlashTimer = 0f
    var hurtFlashTimer = 0f

    fun rotate(rot: Double) {
        val oldDirX = dirX
        dirX = dirX * cos(rot) - dirY * sin(rot)
        dirY = oldDirX * sin(rot) + dirY * cos(rot)
        val oldPlaneX = planeX
        planeX = planeX * cos(rot) - planeY * sin(rot)
        planeY = oldPlaneX * sin(rot) + planeY * cos(rot)
    }
}

class Enemy(var x: Double, var y: Double, var health: Int = 30) {
    var alive = true
    var hitFlashTimer = 0f
    var attackCooldown = 0f
    var wanderAngle = Math.random() * Math.PI * 2
    var wanderTimer = 0f
}

class LevelMap(
    val name: String,
    rows: List<String>,
    val playerStartX: Double,
    val playerStartY: Double,
    val playerStartAngle: Double,
    val enemySpawns: List<Pair<Double, Double>>
) {
    val height = rows.size
    val width = rows[0].length
    // 0 = lantai kosong, 1 = bata, 2 = kayu
    val grid: Array<IntArray> = Array(height) { r ->
        IntArray(width) { c ->
            when (rows[r][c]) {
                '#' -> 1
                '=' -> 2
                else -> 0
            }
        }
    }

    fun isWall(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= width || y >= height) return true
        return grid[y][x] != 0
    }

    fun canStand(x: Double, y: Double): Boolean {
        val margin = 0.2
        return !isWall((x - margin).toInt(), (y - margin).toInt()) &&
            !isWall((x + margin).toInt(), (y - margin).toInt()) &&
            !isWall((x - margin).toInt(), (y + margin).toInt()) &&
            !isWall((x + margin).toInt(), (y + margin).toInt())
    }

    fun hasLineOfSight(x0: Double, y0: Double, x1: Double, y1: Double): Boolean {
        val steps = 40
        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val x = x0 + (x1 - x0) * t
            val y = y0 + (y1 - y0) * t
            if (isWall(x.toInt(), y.toInt())) return false
        }
        return true
    }
}

fun dist(x1: Double, y1: Double, x2: Double, y2: Double): Double {
    val dx = x2 - x1
    val dy = y2 - y1
    return sqrt(dx * dx + dy * dy)
}
