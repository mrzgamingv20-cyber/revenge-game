package com.mrzgaming.revenge

/**
 * Dua level aksi yang disisipkan di antara node cerita:
 *  - Level 1: menyusup ke gudang pabrik Handoko cari bukti (dipicu sebelum node id 1)
 *  - Level 2: masuk ke rumah Handoko, lewati pengawalnya (dipicu sebelum node id 2)
 */
object Levels {

    fun level1(): LevelMap {
        val rows = listOf(
            "##############",
            "#............#",
            "#.==...==..#.#",
            "#.==...==..#.#",
            "#..........#.#",
            "#.####..####.#",
            "#............#",
            "#.==......==.#",
            "#.==......==.#",
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
            "#....==......#",
            "#..#....#....#",
            "#..#....#....#",
            "#............#",
            "#.====..===..#",
            "#............#",
            "#..==....==..#",
            "#..==....==..#",
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
