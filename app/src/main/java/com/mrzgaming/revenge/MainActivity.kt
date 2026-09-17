package com.mrzgaming.revenge

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class MainActivity : AppCompatActivity(), GameView.Listener {

    private lateinit var tvStory: TextView
    private lateinit var tvHumanity: TextView
    private lateinit var llChoices: LinearLayout
    private lateinit var btnRestart: Button
    private lateinit var storyContainer: View
    private lateinit var gameContainer: FrameLayout
    private var gameView: GameView? = null

    // Level aksi dipicu sekali saja per playthrough, tepat sebelum node cerita terkait tampil
    private var level1Played = false
    private var level2Played = false

    // --- Jembatan ke C++ (lihat native_bridge.cpp) ---
    private external fun nativeGetCurrentNode(): String
    private external fun nativeChooseOption(index: Int)
    private external fun nativeReset()

    companion object {
        init {
            System.loadLibrary("revenge")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStory = findViewById(R.id.tvStory)
        tvHumanity = findViewById(R.id.tvHumanity)
        llChoices = findViewById(R.id.llChoices)
        btnRestart = findViewById(R.id.btnRestart)
        storyContainer = findViewById(R.id.storyContainer)
        gameContainer = findViewById(R.id.gameContainer)

        btnRestart.setOnClickListener {
            nativeReset()
            level1Played = false
            level2Played = false
            proceedToCurrentNode()
        }

        proceedToCurrentNode()
    }

    override fun onPause() {
        super.onPause()
        gameView?.stop()
    }

    override fun onResume() {
        super.onResume()
        gameView?.start()
    }

    /** Cek node saat ini: kalau butuh level aksi sebelum ditampilkan, mainkan dulu levelnya. */
    private fun proceedToCurrentNode() {
        val json = JSONObject(nativeGetCurrentNode())
        val id = json.getInt("id")

        when {
            id == 1 && !level1Played -> {
                level1Played = true
                startLevel(Levels.level1())
            }
            id == 2 && !level2Played -> {
                level2Played = true
                startLevel(Levels.level2())
            }
            else -> renderNode(json)
        }
    }

    private fun startLevel(level: LevelMap) {
        showStory(false)

        val view = GameView(this)
        view.listener = this
        view.loadLevel(level)
        gameContainer.removeAllViews()
        gameContainer.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        gameView = view
        view.start()
    }

    // Dipanggil dari GameView begitu semua musuh di level tumbang
    override fun onLevelCleared() {
        runOnUiThread {
            llChoices.postDelayed({
                gameView?.stop()
                gameContainer.removeAllViews()
                gameView = null
                showStory(true)
                val json = JSONObject(nativeGetCurrentNode())
                renderNode(json)
            }, 900)
        }
    }

    override fun onPlayerDied() {
        // Sekadar hook feedback; GameView sudah auto-respawn levelnya sendiri.
    }

    private fun showStory(show: Boolean) {
        storyContainer.visibility = if (show) View.VISIBLE else View.GONE
        gameContainer.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun renderNode(json: JSONObject) {
        val humanity = json.getInt("humanity")
        val text = json.getString("text")
        val isEnding = json.getBoolean("isEnding")
        val choices = json.getJSONArray("choices")

        tvHumanity.text = "Humanity: $humanity"
        tvStory.text = text

        llChoices.removeAllViews()

        if (isEnding) {
            btnRestart.visibility = View.VISIBLE
            return
        }
        btnRestart.visibility = View.GONE

        for (i in 0 until choices.length()) {
            val choiceObj = choices.getJSONObject(i)
            val button = Button(this).apply {
                text = choiceObj.getString("text")
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#221822"))
                setPadding(24, 24, 24, 24)
                setOnClickListener {
                    nativeChooseOption(i)
                    proceedToCurrentNode()
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 20
            llChoices.addView(button, params)
        }
    }
}
