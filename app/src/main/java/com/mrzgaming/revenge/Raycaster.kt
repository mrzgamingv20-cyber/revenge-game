package com.mrzgaming.revenge

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Raycasting engine klasik (algoritma DDA ala Lode Vandevenne / Wolfenstein 3D).
 * Render ke framebuffer resolusi rendah (RENDER_W x RENDER_H) lalu di-scale ke layar
 * oleh GameView, supaya ringan dijalankan murni di Kotlin tanpa OpenGL.
 */
object Raycaster {
    const val RENDER_W = 160
    var RENDER_H = 100 // disesuaikan dengan aspect ratio layar saat GameView di-layout

    private val ceilingColor = 0xFF1A1622.toInt()
    private val floorColor = 0xFF241A14.toInt()

    fun render(
        fb: IntArray,
        w: Int,
        h: Int,
        player: Player,
        level: LevelMap,
        enemies: List<Enemy>,
        zBuffer: DoubleArray
    ) {
        // langit-langit & lantai flat shaded
        for (y in 0 until h) {
            val color = if (y < h / 2) ceilingColor else floorColor
            val rowStart = y * w
            for (x in 0 until w) fb[rowStart + x] = color
        }

        for (x in 0 until w) {
            val cameraX = 2.0 * x / w - 1.0
            val rayDirX = player.dirX + player.planeX * cameraX
            val rayDirY = player.dirY + player.planeY * cameraX

            var mapX = floor(player.x).toInt()
            var mapY = floor(player.y).toInt()

            val deltaDistX = if (rayDirX == 0.0) 1e30 else abs(1.0 / rayDirX)
            val deltaDistY = if (rayDirY == 0.0) 1e30 else abs(1.0 / rayDirY)

            var stepX: Int
            var stepY: Int
            var sideDistX: Double
            var sideDistY: Double

            if (rayDirX < 0) {
                stepX = -1
                sideDistX = (player.x - mapX) * deltaDistX
            } else {
                stepX = 1
                sideDistX = (mapX + 1.0 - player.x) * deltaDistX
            }
            if (rayDirY < 0) {
                stepY = -1
                sideDistY = (player.y - mapY) * deltaDistY
            } else {
                stepY = 1
                sideDistY = (mapY + 1.0 - player.y) * deltaDistY
            }

            var hit = false
            var side = 0
            var wallType = 1
            var guard = 0
            while (!hit && guard < 64) {
                guard++
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX
                    mapX += stepX
                    side = 0
                } else {
                    sideDistY += deltaDistY
                    mapY += stepY
                    side = 1
                }
                if (level.isWall(mapX, mapY)) {
                    hit = true
                    wallType = level.grid[mapY.coerceIn(0, level.height - 1)][mapX.coerceIn(0, level.width - 1)]
                }
            }

            val perpWallDist = if (side == 0)
                (mapX - player.x + (1 - stepX) / 2.0) / rayDirX
            else
                (mapY - player.y + (1 - stepY) / 2.0) / rayDirY

            val safeDist = max(perpWallDist, 0.05)
            zBuffer[x] = safeDist

            val lineHeight = (h / safeDist).toInt()
            val drawStart = max(0, -lineHeight / 2 + h / 2)
            val drawEnd = min(h - 1, lineHeight / 2 + h / 2)

            var wallX = if (side == 0) player.y + safeDist * rayDirY else player.x + safeDist * rayDirX
            wallX -= floor(wallX)
            var texX = (wallX * Textures.TEX_SIZE).toInt()
            if (side == 0 && rayDirX > 0) texX = Textures.TEX_SIZE - texX - 1
            if (side == 1 && rayDirY < 0) texX = Textures.TEX_SIZE - texX - 1
            texX = Textures.clampIdx(texX)

            val tex = Textures.textureFor(wallType)
            val shadeFactor = (1.0 - min(1.0, safeDist / 12.0)).toFloat() * (if (side == 1) 0.7f else 1f)
            val step = Textures.TEX_SIZE.toDouble() / lineHeight.coerceAtLeast(1)
            var texPos = (drawStart - h / 2.0 + lineHeight / 2.0) * step

            for (y in drawStart..drawEnd) {
                val texY = Textures.clampIdx(texPos.toInt())
                texPos += step
                val raw = tex[texY * Textures.TEX_SIZE + texX]
                fb[y * w + x] = Textures.shade(raw, shadeFactor.coerceIn(0.15f, 1f))
            }
        }

        renderSprites(fb, w, h, player, enemies, zBuffer)
    }

    private fun renderSprites(
        fb: IntArray,
        w: Int,
        h: Int,
        player: Player,
        enemies: List<Enemy>,
        zBuffer: DoubleArray
    ) {
        val alive = enemies.filter { it.alive }
        val sorted = alive.sortedByDescending { dist(player.x, player.y, it.x, it.y) }
        val invDet = 1.0 / (player.planeX * player.dirY - player.dirX * player.planeY)

        for (enemy in sorted) {
            val spriteX = enemy.x - player.x
            val spriteY = enemy.y - player.y

            val transformX = invDet * (player.dirY * spriteX - player.dirX * spriteY)
            val transformY = invDet * (-player.planeY * spriteX + player.planeX * spriteY)
            if (transformY <= 0.15) continue // di belakang kamera

            val spriteScreenX = ((w / 2.0) * (1.0 + transformX / transformY)).toInt()

            val spriteHeight = abs((h / transformY).toInt())
            val spriteWidth = spriteHeight
            if (spriteWidth <= 0 || spriteHeight <= 0) continue

            val drawStartY = max(0, -spriteHeight / 2 + h / 2)
            val drawEndY = min(h - 1, spriteHeight / 2 + h / 2)
            val drawStartX = max(0, -spriteWidth / 2 + spriteScreenX)
            val drawEndX = min(w - 1, spriteWidth / 2 + spriteScreenX)

            val brightness = (1.0 - min(1.0, transformY / 12.0)).toFloat()

            for (stripe in drawStartX until drawEndX) {
                if (stripe < 0 || stripe >= w) continue
                if (transformY >= zBuffer[stripe]) continue // ketutup dinding
                val texX = (((stripe - (-spriteWidth / 2 + spriteScreenX)) * Textures.TEX_SIZE) / spriteWidth)
                    .coerceIn(0, Textures.TEX_SIZE - 1)
                for (y in drawStartY..drawEndY) {
                    val d = y - (-spriteHeight / 2 + h / 2)
                    val texY = ((d * Textures.TEX_SIZE) / spriteHeight).coerceIn(0, Textures.TEX_SIZE - 1)
                    var color = Textures.ENEMY[texY * Textures.TEX_SIZE + texX]
                    val alpha = (color ushr 24) and 0xFF
                    if (alpha == 0) continue
                    if (enemy.hitFlashTimer > 0f) color = Textures.tintWhite(color, 0.8f)
                    color = Textures.shade(color, brightness.coerceIn(0.2f, 1f))
                    fb[y * w + stripe] = color
                }
            }
        }
    }
}
