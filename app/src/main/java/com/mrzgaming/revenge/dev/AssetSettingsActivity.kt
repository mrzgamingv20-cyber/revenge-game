package com.mrzgaming.revenge.dev

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mrzgaming.revenge.R
import com.mrzgaming.revenge.Textures

class AssetSettingsActivity : AppCompatActivity() {
    private val pickCode = 4401
    private var pendingSlot: String? = null
    private val slots = listOf(
        Triple(DevConfig.KEY_ENEMY, "Musuh (sprite)", R.drawable.sprite_enemy),
        Triple(DevConfig.KEY_WEAPON, "Senjata", R.drawable.weapon_hammer),
        Triple(DevConfig.KEY_STORY_CHAR, "Karakter cerita", R.drawable.char_doodle),
        Triple(DevConfig.KEY_BRICK, "Dinding bata #", R.drawable.tex_brick),
        Triple(DevConfig.KEY_WOOD, "Dinding metal =", R.drawable.tex_wood),
        Triple(DevConfig.KEY_CONCRETE, "Dinding beton ~", R.drawable.tex_concrete),
    )
    private val spinners = mutableMapOf<String, Spinner>()
    private val previews = mutableMapOf<String, ImageView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0D0D12")) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 40)
        }
        scroll.addView(col)

        col.addView(TextView(this).apply {
            text = "Asset Settings"
            setTextColor(Color.parseColor("#E091B8"))
            textSize = 24f
            setPadding(0, 0, 0, 8)
        })
        col.addView(TextView(this).apply {
            text = "Pilih aset built-in atau import PNG dari galeri/Download."
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, 0, 0, 24)
        })

        slots.forEach { (key, label, fallback) ->
            col.addView(buildSlot(key, label, fallback))
        }

        col.addView(Button(this).apply {
            text = "Import PNG ke slot yang dipilih…"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3A2030"))
            setOnClickListener {
                val slot = pendingSlot ?: DevConfig.KEY_ENEMY
                pendingSlot = slot
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, pickCode)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 12
            layoutParams = lp
        })

        col.addView(Button(this).apply {
            text = "Simpan & Terapkan ke Game"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2A4A2A"))
            setOnClickListener { saveAll() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 20
            layoutParams = lp
        })

        col.addView(Button(this).apply {
            text = "Reset ke default"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#442222"))
            setOnClickListener { resetDefaults() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 12
            layoutParams = lp
        })

        col.addView(Button(this).apply {
            text = "← Kembali"
            setOnClickListener { finish() }
        })

        setContentView(scroll)
        refreshSpinners()
    }

    private fun buildSlot(key: String, label: String, fallback: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 28)
        }
        box.addView(TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 16f
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val preview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(140, 140).apply { rightMargin = 16 }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.parseColor("#1A1A22"))
        }
        previews[key] = preview
        val spinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnTouchListener { _, _ ->
                pendingSlot = key
                false
            }
        }
        spinners[key] = spinner
        row.addView(preview)
        row.addView(spinner)
        box.addView(row)

        // seed current
        val current = DevConfig.getAssetKey(this, key, defaultFor(key))
        updatePreview(key, current, fallback)
        return box
    }

    private fun defaultFor(key: String) = when (key) {
        DevConfig.KEY_ENEMY -> "sprite_enemy"
        DevConfig.KEY_WEAPON -> "weapon_hammer"
        DevConfig.KEY_STORY_CHAR -> "char_doodle"
        DevConfig.KEY_BRICK -> "tex_brick"
        DevConfig.KEY_WOOD -> "tex_wood"
        DevConfig.KEY_CONCRETE -> "tex_concrete"
        else -> "sprite_enemy"
    }

    private fun refreshSpinners() {
        val choices = AssetCatalog.allChoices(this)
        val labels = choices.map { it.label }
        slots.forEach { (key, _, fallback) ->
            val spinner = spinners[key] ?: return@forEach
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
            val current = DevConfig.getAssetKey(this, key, defaultFor(key))
            val idx = choices.indexOfFirst { it.id == current }.coerceAtLeast(0)
            spinner.setSelection(idx)
            spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    pendingSlot = key
                    updatePreview(key, choices[position].id, fallback)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
            updatePreview(key, current, fallback)
        }
    }

    private fun updatePreview(key: String, assetId: String, fallback: Int) {
        val bmp = AssetCatalog.resolveBitmap(this, assetId, fallback)
        previews[key]?.setImageBitmap(bmp)
    }

    private fun saveAll() {
        val choices = AssetCatalog.allChoices(this)
        slots.forEach { (key, _, _) ->
            val pos = spinners[key]?.selectedItemPosition ?: 0
            val id = choices.getOrNull(pos)?.id ?: defaultFor(key)
            DevConfig.setAssetKey(this, key, id)
        }
        Textures.reload(this)
        Toast.makeText(this, "Aset diterapkan. Restart level / cerita untuk lihat penuh.", Toast.LENGTH_LONG).show()
    }

    private fun resetDefaults() {
        slots.forEach { (key, _, _) ->
            DevConfig.setAssetKey(this, key, defaultFor(key))
        }
        Textures.reload(this)
        refreshSpinners()
        Toast.makeText(this, "Default dipulihkan.", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickCode || resultCode != Activity.RESULT_OK) return
        val uri: Uri = data?.data ?: return
        val slot = pendingSlot ?: DevConfig.KEY_ENEMY
        val name = "import_${slot}_${System.currentTimeMillis()}.png"
        val key = AssetCatalog.importFromUri(this, uri, name)
        if (key == null) {
            Toast.makeText(this, "Gagal import.", Toast.LENGTH_SHORT).show()
            return
        }
        DevConfig.setAssetKey(this, slot, key)
        Textures.reload(this)
        refreshSpinners()
        Toast.makeText(this, "Import OK → $slot", Toast.LENGTH_SHORT).show()
    }
}
