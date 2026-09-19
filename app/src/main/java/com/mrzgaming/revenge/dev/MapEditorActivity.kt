package com.mrzgaming.revenge.dev

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MapEditorActivity : AppCompatActivity() {
    private lateinit var editor: MapEditorView
    private lateinit var status: TextView
    private var levelSlot = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D12"))
            setPadding(12, 20, 12, 12)
        }

        status = TextView(this).apply {
            setTextColor(Color.parseColor("#E091B8"))
            textSize = 16f
            setPadding(8, 0, 8, 8)
        }
        root.addView(status)

        val tools = HorizontalScrollView(this)
        val toolRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tools.addView(toolRow)
        root.addView(tools)

        fun toolBtn(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A2230"))
            setOnClickListener { action(); refreshStatus() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = 8
            layoutParams = lp
            toolRow.addView(this)
        }

        toolBtn("Lv1") { loadSlot(1) }
        toolBtn("Lv2") { loadSlot(2) }
        toolBtn("Bata #") { editor.tool = MapEditorView.Tool.PAINT; editor.brush = '#' }
        toolBtn("Metal =") { editor.tool = MapEditorView.Tool.PAINT; editor.brush = '=' }
        toolBtn("Beton ~") { editor.tool = MapEditorView.Tool.PAINT; editor.brush = '~' }
        toolBtn("Lantai .") { editor.tool = MapEditorView.Tool.PAINT; editor.brush = '.' }
        toolBtn("Player") { editor.tool = MapEditorView.Tool.PLAYER }
        toolBtn("Enemy+") { editor.tool = MapEditorView.Tool.ENEMY }
        toolBtn("Hapus") { editor.tool = MapEditorView.Tool.ERASE }
        toolBtn("Putar map 90°") { editor.rotateMapCw() }
        toolBtn("Putar player +15°") { editor.rotatePlayer(15f) }
        toolBtn("Putar player -15°") { editor.rotatePlayer(-15f) }

        editor = MapEditorView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            onMapChanged = { refreshStatus() }
        }
        root.addView(editor)

        val useCustom = CheckBox(this).apply {
            text = "Pakai map custom ini di game"
            setTextColor(Color.WHITE)
            isChecked = DevConfig.useCustomMap(this@MapEditorActivity, levelSlot)
            setOnCheckedChangeListener { _, checked ->
                DevConfig.setUseCustomMap(this@MapEditorActivity, levelSlot, checked)
            }
        }
        root.addView(useCustom)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(Button(this).apply {
            text = "Reset default"
            setOnClickListener {
                editor.map = if (levelSlot == 1) EditableMap.fromDefaultLevel1()
                else EditableMap.fromDefaultLevel2()
                refreshStatus()
            }
        })
        actions.addView(Button(this).apply {
            text = "Blank"
            setOnClickListener {
                editor.map = EditableMap.blank()
                refreshStatus()
            }
        })
        actions.addView(Button(this).apply {
            text = "Simpan"
            setBackgroundColor(Color.parseColor("#2A4A2A"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                MapStore.save(this@MapEditorActivity, levelSlot, editor.map)
                DevConfig.setUseCustomMap(this@MapEditorActivity, levelSlot, true)
                useCustom.isChecked = true
                Toast.makeText(this@MapEditorActivity, "Map level $levelSlot tersimpan", Toast.LENGTH_SHORT).show()
            }
        })
        actions.addView(Button(this).apply {
            text = "←"
            setOnClickListener { finish() }
        })
        root.addView(actions)

        setContentView(root)
        loadSlot(1)
    }

    private fun loadSlot(level: Int) {
        levelSlot = level
        editor.map = MapStore.load(this, level)
            ?: if (level == 1) EditableMap.fromDefaultLevel1() else EditableMap.fromDefaultLevel2()
        refreshStatus()
    }

    private fun refreshStatus() {
        val m = editor.map
        status.text = "Map Editor · Level $levelSlot · ${m.width}x${m.height} · " +
            "tool=${editor.tool} brush=${editor.brush} · enemies=${m.enemies.size} · " +
            "angle=${Math.toDegrees(m.playerAngle).toInt()}°"
    }
}
