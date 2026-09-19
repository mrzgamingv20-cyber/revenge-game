package com.mrzgaming.revenge.dev

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class CutsceneEditorActivity : AppCompatActivity() {
    private var project = CutsceneProject()
    private var shotIndex = 0
    private lateinit var tvShotMeta: TextView
    private lateinit var etTitle: EditText
    private lateinit var etText: EditText
    private lateinit var etDuration: EditText
    private lateinit var etShotTitle: EditText
    private lateinit var cbShowChar: CheckBox
    private lateinit var progress: ProgressBar
    private lateinit var tvProgress: TextView
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0D0D12")) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 36)
        }
        scroll.addView(col)

        col.addView(header("Cutscene Maker"))
        col.addView(hint("Susun shot → Preview → Export MP4 ke Download."))

        etTitle = field("Judul proyek", project.title)
        col.addView(label("Judul proyek"))
        col.addView(etTitle)

        tvShotMeta = TextView(this).apply {
            setTextColor(Color.parseColor("#E091B8"))
            textSize = 15f
            setPadding(0, 16, 0, 8)
        }
        col.addView(tvShotMeta)

        etShotTitle = field("Shot title", "")
        etText = field("Teks narrasi", "").apply {
            minLines = 4
            gravity = android.view.Gravity.TOP
        }
        etDuration = field("Durasi detik", "2.5")
        cbShowChar = CheckBox(this).apply {
            text = "Tampilkan karakter"
            setTextColor(Color.WHITE)
            isChecked = true
        }

        col.addView(label("Judul shot"))
        col.addView(etShotTitle)
        col.addView(label("Teks"))
        col.addView(etText)
        col.addView(label("Durasi (detik)"))
        col.addView(etDuration)
        col.addView(cbShowChar)

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(smallBtn("⟨ Prev") {
            commitShot(); if (shotIndex > 0) { shotIndex--; bindShot() }
        })
        nav.addView(smallBtn("Next ⟩") {
            commitShot()
            if (shotIndex < project.shots.lastIndex) shotIndex++ else {
                project.shots.add(CutsceneShot(title = "Shot ${project.shots.size + 1}"))
                shotIndex = project.shots.lastIndex
            }
            bindShot()
        })
        nav.addView(smallBtn("+ Shot") {
            commitShot()
            project.shots.add(CutsceneShot(title = "Shot ${project.shots.size + 1}"))
            shotIndex = project.shots.lastIndex
            bindShot()
        })
        nav.addView(smallBtn("Hapus") {
            if (project.shots.size <= 1) {
                Toast.makeText(this, "Minimal 1 shot", Toast.LENGTH_SHORT).show()
            } else {
                project.shots.removeAt(shotIndex)
                shotIndex = shotIndex.coerceAtMost(project.shots.lastIndex)
                bindShot()
            }
        })
        col.addView(nav)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        tvProgress = TextView(this).apply {
            setTextColor(Color.parseColor("#AAAAAA"))
            visibility = View.GONE
        }
        col.addView(progress)
        col.addView(tvProgress)

        col.addView(bigBtn("Preview singkat (Toast timeline)") {
            commitShot()
            val summary = project.shots.mapIndexed { i, s ->
                "${i + 1}. ${s.title} (${s.durationSec}s)"
            }.joinToString("\n")
            AlertDialog.Builder(this)
                .setTitle("Timeline: ${etTitle.text}")
                .setMessage(summary + "\n\nTotal ~${project.shots.sumOf { it.durationSec.toDouble() }}s")
                .setPositiveButton("OK", null)
                .show()
        })

        col.addView(bigBtn("Simpan proyek JSON") {
            commitShot()
            project.title = etTitle.text.toString().ifBlank { "Cutscene" }
            val f = CutsceneStore.save(this, project.title, project)
            Toast.makeText(this, "Tersimpan: ${f.name}", Toast.LENGTH_SHORT).show()
        })

        col.addView(bigBtn("Load proyek…") {
            val files = CutsceneStore.list(this)
            if (files.isEmpty()) {
                Toast.makeText(this, "Belum ada proyek tersimpan", Toast.LENGTH_SHORT).show()
                return@bigBtn
            }
            val names = files.map { it.nameWithoutExtension }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Pilih cutscene")
                .setItems(names) { _, which ->
                    project = CutsceneStore.load(files[which])
                    shotIndex = 0
                    etTitle.setText(project.title)
                    bindShot()
                }
                .show()
        })

        col.addView(bigBtn("⬇ Export MP4 ke Download") {
            commitShot()
            project.title = etTitle.text.toString().ifBlank { "Cutscene" }
            progress.visibility = View.VISIBLE
            tvProgress.visibility = View.VISIBLE
            progress.progress = 0
            tvProgress.text = "Menyiapkan encoder…"
            io.execute {
                try {
                    val out = Mp4Exporter.export(this, project) { p ->
                        main.post {
                            val pct = if (p.total == 0) 0 else (100 * p.frame / p.total)
                            progress.progress = pct
                            tvProgress.text = "${p.message} ($pct%)"
                        }
                    }
                    main.post {
                        progress.visibility = View.GONE
                        tvProgress.visibility = View.GONE
                        AlertDialog.Builder(this)
                            .setTitle("Export sukses")
                            .setMessage("MP4 tersimpan di Download:\n${out.name}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                } catch (e: Exception) {
                    main.post {
                        progress.visibility = View.GONE
                        tvProgress.visibility = View.GONE
                        AlertDialog.Builder(this)
                            .setTitle("Export gagal")
                            .setMessage(e.message ?: e.toString())
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }.apply { setBackgroundColor(Color.parseColor("#2A4A2A")) })

        col.addView(bigBtn("← Kembali") { finish() })

        setContentView(scroll)
        bindShot()
    }

    private fun commitShot() {
        if (shotIndex !in project.shots.indices) return
        val s = project.shots[shotIndex]
        s.title = etShotTitle.text.toString().ifBlank { "Shot ${shotIndex + 1}" }
        s.text = etText.text.toString()
        s.durationSec = etDuration.text.toString().toFloatOrNull()?.coerceIn(0.5f, 30f) ?: 2.5f
        s.showChar = cbShowChar.isChecked
        project.title = etTitle.text.toString().ifBlank { project.title }
    }

    private fun bindShot() {
        val s = project.shots[shotIndex]
        tvShotMeta.text = "Shot ${shotIndex + 1} / ${project.shots.size}"
        etShotTitle.setText(s.title)
        etText.setText(s.text)
        etDuration.setText(s.durationSec.toString())
        cbShowChar.isChecked = s.showChar
    }

    private fun header(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor("#E091B8"))
        textSize = 24f
    }

    private fun hint(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.parseColor("#AAAAAA"))
        setPadding(0, 0, 0, 16)
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        setTextColor(Color.WHITE)
        setPadding(0, 10, 0, 4)
    }

    private fun field(hintText: String, value: String) = EditText(this).apply {
        setText(value)
        setHint(hintText)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
        setBackgroundColor(Color.parseColor("#1A1620"))
        setPadding(24, 20, 24, 20)
    }

    private fun smallBtn(t: String, click: () -> Unit) = Button(this).apply {
        text = t
        textSize = 11f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#2A2230"))
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = 6
        }
    }

    private fun bigBtn(t: String, click: () -> Unit) = Button(this).apply {
        text = t
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#221822"))
        setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = 12
        layoutParams = lp
    }
}
