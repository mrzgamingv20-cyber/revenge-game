package com.mrzgaming.revenge.dev

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DevHubActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0D12"))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 48)
        }
        root.addView(col)

        fun title(t: String) = TextView(this).apply {
            text = t
            setTextColor(Color.parseColor("#E091B8"))
            textSize = 26f
            setPadding(0, 0, 0, 12)
        }

        fun body(t: String) = TextView(this).apply {
            text = t
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 14f
            setPadding(0, 0, 0, 28)
        }

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#221822"))
            setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 18
            layoutParams = lp
        }

        col.addView(title("Developer Tools"))
        col.addView(body("Cutscene maker (export MP4), asset overrides, dan map editor. Perubahan asset/map dipakai game setelah kembali ke layar utama."))

        col.addView(btn("1. Cutscene Maker → MP4") {
            startActivity(Intent(this, CutsceneEditorActivity::class.java))
        })
        col.addView(btn("2. Asset Settings") {
            startActivity(Intent(this, AssetSettingsActivity::class.java))
        })
        col.addView(btn("3. Map Editor (drag + rotate)") {
            startActivity(Intent(this, MapEditorActivity::class.java))
        })
        col.addView(btn("← Kembali ke Game") { finish() })

        val tip = TextView(this).apply {
            text = "Tip: di game, tombol DEV membuka halaman ini."
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        col.addView(tip)
        setContentView(root)
    }
}
