package com.mrzgaming.revenge

import android.content.Context
import android.graphics.Bitmap
import com.mrzgaming.revenge.dev.AssetCatalog
import com.mrzgaming.revenge.dev.DevConfig
import kotlin.math.min

/**
 * Tekstur & sprite dari drawable / override developer (DevConfig).
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
        reload(context)
    }

    /** Paksa muat ulang dari DevConfig (dipanggil Asset Settings). */
    fun reload(context: Context) {
        val ctx = context.applicationContext
        loadOpaque(
            BRICK,
            AssetCatalog.resolveBitmap(
                ctx,
                DevConfig.getAssetKey(ctx, DevConfig.KEY_BRICK, "tex_brick"),
                R.drawable.tex_brick
            )
        )
        loadOpaque(
            WOOD,
            AssetCatalog.resolveBitmap(
                ctx,
                DevConfig.getAssetKey(ctx, DevConfig.KEY_WOOD, "tex_wood"),
                R.drawable.tex_wood
            )
        )
        loadOpaque(
            CONCRETE,
            AssetCatalog.resolveBitmap(
                ctx,
                DevConfig.getAssetKey(ctx, DevConfig.KEY_CONCRETE, "tex_concrete"),
                R.drawable.tex_concrete
            )
        )
        loadSprite(
            ENEMY,
            AssetCatalog.resolveBitmap(
                ctx,
                DevConfig.getAssetKey(ctx, DevConfig.KEY_ENEMY, "sprite_enemy"),
                R.drawable.sprite_enemy
            )
        )
        ready = true
    }

    fun loadWeaponBitmap(context: Context): Bitmap {
        val ctx = context.applicationContext
        val raw = AssetCatalog.resolveBitmap(
            ctx,
            DevConfig.getAssetKey(ctx, DevConfig.KEY_WEAPON, "weapon_hammer"),
            R.drawable.weapon_hammer
        )
        return if (raw.width == 256 && raw.height == 256) raw
        else {
            val scaled = Bitmap.createScaledBitmap(raw, 256, 256, true)
            if (scaled !== raw) raw.recycle()
            scaled
        }
    }

    fun loadStoryCharBitmap(context: Context): Bitmap {
        val ctx = context.applicationContext
        return AssetCatalog.resolveBitmap(
            ctx,
            DevConfig.getAssetKey(ctx, DevConfig.KEY_STORY_CHAR, "char_doodle"),
            R.drawable.char_doodle
        )
    }

    private fun scaleToTex(src: Bitmap): Bitmap {
        return if (src.width == TEX_SIZE && src.height == TEX_SIZE) src
        else {
            val scaled = Bitmap.createScaledBitmap(src, TEX_SIZE, TEX_SIZE, true)
            if (scaled !== src) src.recycle()
            scaled
        }
    }

    private fun loadOpaque(dest: IntArray, src: Bitmap) {
        val bmp = scaleToTex(src)
        bmp.getPixels(dest, 0, TEX_SIZE, 0, 0, TEX_SIZE, TEX_SIZE)
        for (i in dest.indices) dest[i] = dest[i] or 0xFF000000.toInt()
        if (!bmp.isRecycled) bmp.recycle()
    }

    private fun loadSprite(dest: IntArray, src: Bitmap) {
        val bmp = scaleToTex(src)
        bmp.getPixels(dest, 0, TEX_SIZE, 0, 0, TEX_SIZE, TEX_SIZE)
        if (!bmp.isRecycled) bmp.recycle()
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

    fun textureFor(wallType: Int): IntArray = when (wallType) {
        2 -> WOOD
        3 -> CONCRETE
        else -> BRICK
    }

    fun clampIdx(v: Int) = min(TEX_SIZE - 1, v.coerceAtLeast(0))
}
