package com.mrzgaming.revenge

import android.content.Context
import com.mrzgaming.revenge.dev.DevConfig
import com.mrzgaming.revenge.dev.MapStore

/**
 * Dua level aksi yang disisipkan di antara node cerita.
 * Kalau DevConfig pakai map custom, load dari MapStore.
 *
 * Simbol map: # bata, = metal/kayu, ~ beton, . lantai
 */
object Levels {

    fun resolveLevel1(ctx: Context): LevelMap {
        if (DevConfig.useCustomMap(ctx, 1)) {
            MapStore.load(ctx, 1)?.toLevelMap()?.let { return it }
        }
        return level1()
    }

    fun resolveLevel2(ctx: Context): LevelMap {
        if (DevConfig.useCustomMap(ctx, 2)) {
            MapStore.load(ctx, 2)?.toLevelMap()?.let { return it }
        }
        return level2()
    }

    fun level1(): LevelMap {
        val rows = listOf(
            "##############",
            "#............#",
            "#.==...~~..#.#",
            "#.==...~~..#.#",
            "#..........#.#",
            "#.####..####.#",
            "#............#",
            "#.==......==.#",
            "#.~~......~~.#",
            "#............#",
            "#.####..####.#",
            "#............#",
            "#............#",
            "##############"
        )
        return LevelMap(
            name = "Gudang Pabrik Handoko",
            rows = rows,
            playerStartX = 2.5,
            playerStartY = 12.5,
            playerStartAngle = -Math.PI / 2,
            enemySpawns = listOf(
                6.5 to 6.5,
                10.5 to 2.5,
                12.5 to 6.5
            )
        )
    }

    fun level2(): LevelMap {
        val rows = listOf(
            "##############",
            "#............#",
            "#.==.====.=..#",
            "#.==......=..#",
            "#....~~......#",
            "#..#....#....#",
            "#..#....#....#",
            "#............#",
            "#.====..===..#",
            "#............#",
            "#..==....~~..#",
            "#..==....~~..#",
            "#............#",
            "##############"
        )
        return LevelMap(
            name = "Rumah Handoko",
            rows = rows,
            playerStartX = 2.5,
            playerStartY = 12.5,
            playerStartAngle = -Math.PI / 2,
            enemySpawns = listOf(
                7.5 to 7.5,
                11.5 to 3.5,
                1.5 to 7.5,
                7.5 to 10.5
            )
        )
    }
}
