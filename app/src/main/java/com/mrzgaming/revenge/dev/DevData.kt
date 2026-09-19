package com.mrzgaming.revenge.dev

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.mrzgaming.revenge.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Preferensi developer: override aset + map custom mana yang aktif. */
object DevConfig {
    private const val PREFS = "revenge_dev"

    const val KEY_ENEMY = "enemy"
    const val KEY_WEAPON = "weapon"
    const val KEY_BRICK = "brick"
    const val KEY_WOOD = "wood"
    const val KEY_CONCRETE = "concrete"
    const val KEY_STORY_CHAR = "story_char"
    const val KEY_MAP1 = "use_map1"
    const val KEY_MAP2 = "use_map2"

    fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAssetKey(ctx: Context, slot: String, default: String): String =
        prefs(ctx).getString(slot, default) ?: default

    fun setAssetKey(ctx: Context, slot: String, value: String) {
        prefs(ctx).edit().putString(slot, value).apply()
    }

    fun useCustomMap(ctx: Context, level: Int): Boolean =
        prefs(ctx).getBoolean(if (level == 1) KEY_MAP1 else KEY_MAP2, false)

    fun setUseCustomMap(ctx: Context, level: Int, use: Boolean) {
        prefs(ctx).edit().putBoolean(if (level == 1) KEY_MAP1 else KEY_MAP2, use).apply()
    }

    fun customAssetsDir(ctx: Context): File =
        File(ctx.filesDir, "custom_assets").also { it.mkdirs() }

    fun mapsDir(ctx: Context): File =
        File(ctx.filesDir, "maps").also { it.mkdirs() }

    fun cutscenesDir(ctx: Context): File =
        File(ctx.filesDir, "cutscenes").also { it.mkdirs() }
}

/** Katalog aset built-in + file custom yang bisa dipilih di Asset Settings. */
object AssetCatalog {
    data class Entry(val id: String, val label: String, val drawableRes: Int? = null)

    val builtins = listOf(
        Entry("sprite_enemy", "Enemy doodle", R.drawable.sprite_enemy),
        Entry("char_doodle", "Story doodle", R.drawable.char_doodle),
        Entry("weapon_hammer", "Hammer", R.drawable.weapon_hammer),
        Entry("tex_brick", "Wall brick", R.drawable.tex_brick),
        Entry("tex_wood", "Wall metal", R.drawable.tex_wood),
        Entry("tex_concrete", "Wall concrete", R.drawable.tex_concrete),
    )

    fun resolveBitmap(ctx: Context, key: String, fallbackRes: Int): Bitmap {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        // custom file: "file:nama.png"
        if (key.startsWith("file:")) {
            val f = File(DevConfig.customAssetsDir(ctx), key.removePrefix("file:"))
            if (f.exists()) {
                BitmapFactory.decodeFile(f.absolutePath, opts)?.let { return it }
            }
        }
        val entry = builtins.find { it.id == key }
        val resId = entry?.drawableRes ?: fallbackRes
        return BitmapFactory.decodeResource(ctx.resources, resId, opts)
            ?: Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    }

    fun importFromUri(ctx: Context, uri: Uri, suggestedName: String): String? {
        return try {
            val name = suggestedName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val out = File(DevConfig.customAssetsDir(ctx), name)
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
            "file:$name"
        } catch (_: Exception) {
            null
        }
    }

    fun listCustom(ctx: Context): List<Entry> =
        DevConfig.customAssetsDir(ctx).listFiles()?.filter { it.isFile }?.map {
            Entry("file:${it.name}", "Custom: ${it.name}")
        } ?: emptyList()

    fun allChoices(ctx: Context): List<Entry> = builtins + listCustom(ctx)
}

/** Data map yang bisa diedit & disimpan. */
data class EditableMap(
    var name: String,
    var rows: MutableList<StringBuilder>,
    var playerX: Double,
    var playerY: Double,
    var playerAngle: Double,
    var enemies: MutableList<Pair<Double, Double>>
) {
    val width: Int get() = rows.firstOrNull()?.length ?: 0
    val height: Int get() = rows.size

    fun cell(x: Int, y: Int): Char {
        if (y !in rows.indices || x !in 0 until rows[y].length) return '#'
        return rows[y][x]
    }

    fun setCell(x: Int, y: Int, c: Char) {
        if (y in rows.indices && x in 0 until rows[y].length) rows[y][x] = c
    }

    /** Rotasi 90° searah jarum jam; spawn ikut berputar. */
    fun rotateCw() {
        val w = width
        val h = height
        val next = MutableList(w) { StringBuilder(".".repeat(h)) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val nx = h - 1 - y
                val ny = x
                next[ny][nx] = cell(x, y)
            }
        }
        rows = next
        // (x,y) -> (h-1-y, x) in cell space; world = cell+0.5
        fun rot(px: Double, py: Double): Pair<Double, Double> {
            val cx = px - 0.5
            val cy = py - 0.5
            val nx = (h - 1 - cy) + 0.5
            val ny = cx + 0.5
            return nx to ny
        }
        val (nx, ny) = rot(playerX, playerY)
        playerX = nx
        playerY = ny
        playerAngle += Math.PI / 2
        enemies = enemies.map { (ex, ey) -> rot(ex, ey) }.toMutableList()
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("name", name)
        o.put("playerX", playerX)
        o.put("playerY", playerY)
        o.put("playerAngle", playerAngle)
        val rArr = JSONArray()
        rows.forEach { rArr.put(it.toString()) }
        o.put("rows", rArr)
        val eArr = JSONArray()
        enemies.forEach { (x, y) ->
            eArr.put(JSONObject().put("x", x).put("y", y))
        }
        o.put("enemies", eArr)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): EditableMap {
            val rowsArr = o.getJSONArray("rows")
            val rows = MutableList(rowsArr.length()) { i ->
                StringBuilder(rowsArr.getString(i))
            }
            val enemies = mutableListOf<Pair<Double, Double>>()
            val eArr = o.optJSONArray("enemies") ?: JSONArray()
            for (i in 0 until eArr.length()) {
                val e = eArr.getJSONObject(i)
                enemies.add(e.getDouble("x") to e.getDouble("y"))
            }
            return EditableMap(
                name = o.optString("name", "Custom Map"),
                rows = rows,
                playerX = o.optDouble("playerX", 2.5),
                playerY = o.optDouble("playerY", 2.5),
                playerAngle = o.optDouble("playerAngle", -Math.PI / 2),
                enemies = enemies
            )
        }

        fun fromDefaultLevel1(): EditableMap {
            val built = com.mrzgaming.revenge.Levels.level1()
            return fromLevelMap(built)
        }

        fun fromDefaultLevel2(): EditableMap {
            return fromLevelMap(com.mrzgaming.revenge.Levels.level2())
        }

        fun fromLevelMap(lm: com.mrzgaming.revenge.LevelMap): EditableMap {
            val rows = MutableList(lm.height) { y ->
                StringBuilder(buildString {
                    for (x in 0 until lm.width) {
                        append(
                            when (lm.grid[y][x]) {
                                1 -> '#'
                                2 -> '='
                                3 -> '~'
                                else -> '.'
                            }
                        )
                    }
                })
            }
            return EditableMap(
                name = lm.name,
                rows = rows,
                playerX = lm.playerStartX,
                playerY = lm.playerStartY,
                playerAngle = lm.playerStartAngle,
                enemies = lm.enemySpawns.toMutableList()
            )
        }

        fun blank(w: Int = 14, h: Int = 14): EditableMap {
            val rows = MutableList(h) { y ->
                StringBuilder(buildString {
                    for (x in 0 until w) {
                        append(if (x == 0 || y == 0 || x == w - 1 || y == h - 1) '#' else '.')
                    }
                })
            }
            return EditableMap("Custom Map", rows, 2.5, h - 1.5, -Math.PI / 2, mutableListOf())
        }
    }

    fun toLevelMap(): com.mrzgaming.revenge.LevelMap {
        // bersihkan P/E dari grid sebelum ke LevelMap
        val clean = rows.map { row ->
            row.toString().map { c ->
                when (c) {
                    'P', 'E' -> '.'
                    else -> c
                }
            }.joinToString("")
        }
        return com.mrzgaming.revenge.LevelMap(
            name = name,
            rows = clean,
            playerStartX = playerX,
            playerStartY = playerY,
            playerStartAngle = playerAngle,
            enemySpawns = enemies.toList()
        )
    }
}

object MapStore {
    fun fileFor(ctx: Context, level: Int) = File(DevConfig.mapsDir(ctx), "level$level.json")

    fun load(ctx: Context, level: Int): EditableMap? {
        val f = fileFor(ctx, level)
        if (!f.exists()) return null
        return try {
            EditableMap.fromJson(JSONObject(f.readText()))
        } catch (_: Exception) {
            null
        }
    }

    fun save(ctx: Context, level: Int, map: EditableMap) {
        fileFor(ctx, level).writeText(map.toJson().toString(2))
    }
}

data class CutsceneShot(
    var text: String = "Teks cutscene…",
    var durationSec: Float = 2.5f,
    var bgColor: Int = 0xFF0D0D12.toInt(),
    var showChar: Boolean = true,
    var title: String = "Shot"
)

data class CutsceneProject(
    var title: String = "Cutscene",
    var shots: MutableList<CutsceneShot> = mutableListOf(CutsceneShot())
) {
    fun toJson(): JSONObject {
        val o = JSONObject().put("title", title)
        val arr = JSONArray()
        shots.forEach { s ->
            arr.put(
                JSONObject()
                    .put("text", s.text)
                    .put("durationSec", s.durationSec.toDouble())
                    .put("bgColor", s.bgColor)
                    .put("showChar", s.showChar)
                    .put("title", s.title)
            )
        }
        o.put("shots", arr)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): CutsceneProject {
            val shots = mutableListOf<CutsceneShot>()
            val arr = o.optJSONArray("shots") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                shots.add(
                    CutsceneShot(
                        text = s.optString("text", ""),
                        durationSec = s.optDouble("durationSec", 2.5).toFloat(),
                        bgColor = s.optInt("bgColor", 0xFF0D0D12.toInt()),
                        showChar = s.optBoolean("showChar", true),
                        title = s.optString("title", "Shot ${i + 1}")
                    )
                )
            }
            if (shots.isEmpty()) shots.add(CutsceneShot())
            return CutsceneProject(o.optString("title", "Cutscene"), shots)
        }
    }
}

object CutsceneStore {
    fun list(ctx: Context): List<File> =
        DevConfig.cutscenesDir(ctx).listFiles()?.filter { it.extension == "json" }?.sortedBy { it.name }
            ?: emptyList()

    fun save(ctx: Context, name: String, project: CutsceneProject): File {
        val safe = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val f = File(DevConfig.cutscenesDir(ctx), "$safe.json")
        f.writeText(project.toJson().toString(2))
        return f
    }

    fun load(file: File): CutsceneProject =
        CutsceneProject.fromJson(JSONObject(file.readText()))
}
