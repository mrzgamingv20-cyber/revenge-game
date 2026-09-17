package com.mrzgaming.revenge

import kotlin.math.sqrt
import kotlin.math.min

/**
 * Tekstur digambar procedural sebagai array pixel ARGB mentah (tanpa file gambar),
 * jadi tidak perlu aset apapun. Ukuran tiap tekstur TEX_SIZE x TEX_SIZE.
 */
object Textures {
    const val TEX_SIZE = 64

    // 0 = floor kosong (tidak dipakai buat wall), 1 = bata, 2 = kayu
    val BRICK = IntArray(TEX_SIZE * TEX_SIZE)
    val WOOD = IntArray(TEX_SIZE * TEX_SIZE)

    // Sprite musuh: manusia sederhana, latar transparan (alpha 0)
    val ENEMY = IntArray(TEX_SIZE * TEX_SIZE)

    init {
        buildBrick()
        buildWood()
        buildEnemy()
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int) =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun buildBrick() {
        val mortar = argb(255, 58, 46, 46)
        val brickBase = argb(255, 133, 58, 46)
        val brickDark = argb(255, 108, 44, 34)
        val brickH = 10
        val brickW = 20
        for (y in 0 until TEX_SIZE) {
            val row = y / brickH
            val rowOffset = if (row % 2 == 0) 0 else brickW / 2
            for (x in 0 until TEX_SIZE) {
                val xx = (x + rowOffset) % brickW
                val yy = y % brickH
                val onMortar = xx == 0 || yy == 0
                val c = if (onMortar) mortar else {
                    // sedikit noise supaya tidak flat
                    if ((x * 7 + y * 13) % 23 == 0) brickDark else brickBase
                }
                BRICK[y * TEX_SIZE + x] = c
            }
        }
    }

    private fun buildWood() {
        val plankGap = argb(255, 40, 26, 16)
        val base1 = argb(255, 122, 82, 48)
        val base2 = argb(255, 104, 68, 38)
        val grain = argb(255, 90, 58, 32)
        val plankW = 16
        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                val plankIndex = x / plankW
                val edge = x % plankW == 0
                val base = if (plankIndex % 2 == 0) base1 else base2
                val grainLine = ((x * 3 + y * 5 + plankIndex * 11) % 17) == 0
                val c = when {
                    edge -> plankGap
                    grainLine -> grain
                    else -> base
                }
                WOOD[y * TEX_SIZE + x] = c
            }
        }
    }

    private fun buildEnemy() {
        val outline = argb(255, 20, 10, 10)
        val skin = argb(255, 165, 120, 95)
        val shirt = argb(255, 90, 30, 30)
        val pants = argb(255, 45, 40, 55)
        val cx = TEX_SIZE / 2f
        for (y in 0 until TEX_SIZE) {
            for (x in 0 until TEX_SIZE) {
                var c = argb(0, 0, 0, 0)
                val fy = y.toFloat()
                val fx = x.toFloat()
                // kepala: lingkaran di bagian atas
                val headCy = TEX_SIZE * 0.20f
                val headR = TEX_SIZE * 0.12f
                val dHead = sqrt((fx - cx) * (fx - cx) + (fy - headCy) * (fy - headCy))
                // badan: trapesium kasar dari y 0.30..0.75
                val bodyTop = TEX_SIZE * 0.30f
                val bodyBottom = TEX_SIZE * 0.78f
                val legBottom = TEX_SIZE * 0.98f
                if (dHead < headR) {
                    c = if (dHead > headR - 2f) outline else skin
                } else if (fy in bodyTop..bodyBottom) {
                    val t = (fy - bodyTop) / (bodyBottom - bodyTop)
                    val halfW = TEX_SIZE * (0.16f + 0.10f * t)
                    if (fx in (cx - halfW)..(cx + halfW)) {
                        c = if (fx < cx - halfW + 1.5f || fx > cx + halfW - 1.5f) outline else shirt
                    }
                } else if (fy in bodyBottom..legBottom) {
                    val legHalfGap = TEX_SIZE * 0.03f
                    val legHalfW = TEX_SIZE * 0.09f
                    val leftLegC = cx - legHalfGap - legHalfW
                    val rightLegC = cx + legHalfGap + legHalfW
                    val inLeft = fx in (leftLegC - legHalfW)..(leftLegC + legHalfW)
                    val inRight = fx in (rightLegC - legHalfW)..(rightLegC + legHalfW)
                    if (inLeft || inRight) c = pants
                }
                ENEMY[y * TEX_SIZE + x] = c
            }
        }
    }

    fun shade(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        val a = (color ushr 24) and 0xFF
        val r = ((color shr 16) and 0xFF) * f
        val g = ((color shr 8) and 0xFF) * f
        val b = (color and 0xFF) * f
        return (a shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    fun tintWhite(color: Int, amount: Float): Int {
        val a = (color ushr 24) and 0xFF
        if (a == 0) return color
        val amt = amount.coerceIn(0f, 1f)
        val r = ((color shr 16) and 0xFF)
        val g = ((color shr 8) and 0xFF)
        val b = (color and 0xFF)
        val nr = (r + (255 - r) * amt).toInt().coerceIn(0, 255)
        val ng = (g + (255 - g) * amt).toInt().coerceIn(0, 255)
        val nb = (b + (255 - b) * amt).toInt().coerceIn(0, 255)
        return (a shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    fun textureFor(wallType: Int): IntArray = if (wallType == 2) WOOD else BRICK

    fun clampIdx(v: Int) = min(TEX_SIZE - 1, v.coerceAtLeast(0))
}
