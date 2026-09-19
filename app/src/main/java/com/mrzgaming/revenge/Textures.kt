package com.mrzgaming.revenge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.min

/**
 * Tekstur & sprite dari aset PNG di res/drawable-nodpi.
 * Dipanggil via [init] sekali di MainActivity sebelum level jalan.
 */
object Textures {
    const val TEX_SIZE = 128

    val BRICK = IntArray(TEX_SIZE * TEX_SIZE)
    val WOOD = IntArray(TEX_SIZE * TEX_SIZE)
    val CONCRETE = IntArray(TEX_SIZE * TEX_SIZE)
    val ENEMY = IntArray(TEX_SIZE * TEX_SIZE)

    @Volatile
    var ready = false
        private set

    fun init(context: Context) {
        if (ready) return
        val res = context.applicationContext.resources
        loadOpaque(BRICK, res, R.drawable.tex_brick)
        loadOpaque(WOOD, res, R.drawable.tex_wood)
        loadOpaque(CONCRETE, res, R.drawable.tex_concrete)
        loadSprite(ENEMY, res, R.drawable.sprite_enemy)
        ready = true
    }

    private fun decodeScaled(res: android.content.res.Resources, id: Int): Bitmap {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val raw = BitmapFactory.decodeResource(res, id, opts)
            ?: error("Gagal load drawable $id")
        return if (raw.width == TEX_SIZE && raw.height == TEX_SIZE) {
            raw
        } else {
            val scaled = Bitmap.createScaledBitmap(raw, TEX_SIZE, TEX_SIZE, true)
            if (scaled !== raw) raw.recycle()
            scaled
        }
    }

    private fun loadOpaque(dest: IntArray, res: android.content.res.Resources, id: Int) {
        val bmp = decodeScaled(res, id)
        bmp.getPixels(dest, 0, TEX_SIZE, 0, 0, TEX_SIZE, TEX_SIZE)
        // pastikan alpha penuh biar shading wall tidak transparan
        for (i in dest.indices) {
            dest[i] = dest[i] or 0xFF000000.toInt()
        }
        bmp.recycle()
    }

    private fun loadSprite(dest: IntArray, res: android.content.res.Resources, id: Int) {
        val bmp = decodeScaled(res, id)
        bmp.getPixels(dest, 0, TEX_SIZE, 0, 0, TEX_SIZE, TEX_SIZE)
        bmp.recycle()
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
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val nr = (r + (255 - r) * amt).toInt().coerceIn(0, 255)
        val ng = (g + (255 - g) * amt).toInt().coerceIn(0, 255)
        val nb = (b + (255 - b) * amt).toInt().coerceIn(0, 255)
        return (a shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    /** 1=bata, 2=kayu/metal, 3=beton */
    fun textureFor(wallType: Int): IntArray = when (wallType) {
        2 -> WOOD
        3 -> CONCRETE
        else -> BRICK
    }

    fun clampIdx(v: Int) = min(TEX_SIZE - 1, v.coerceAtLeast(0))
}
