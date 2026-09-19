package com.mrzgaming.revenge

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mrzgaming.revenge.dev.DevHubActivity
import org.json.JSONObject

class MainActivity : AppCompatActivity(), GameView.Listener {

    private lateinit var tvStory: TextView
    private lateinit var tvHumanity: TextView
    private lateinit var llChoices: LinearLayout
    private lateinit var btnRestart: Button
    private lateinit var btnDev: Button
    private lateinit var storyContainer: View
    private lateinit var gameContainer: FrameLayout
    private lateinit var imgStoryChar: ImageView
    private var gameView: GameView? = null
    private var charBobAnimator: ObjectAnimator? = null

    private var level1Played = false
    private var level2Played = false

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
        Textures.init(this)
        setContentView(R.layout.activity_main)

        tvStory = findViewById(R.id.tvStory)
        tvHumanity = findViewById(R.id.tvHumanity)
        llChoices = findViewById(R.id.llChoices)
        btnRestart = findViewById(R.id.btnRestart)
        btnDev = findViewById(R.id.btnDev)
        storyContainer = findViewById(R.id.storyContainer)
        gameContainer = findViewById(R.id.gameContainer)
        imgStoryChar = findViewById(R.id.imgStoryChar)

        refreshStoryChar()
        startCharBob()

        btnDev.setOnClickListener {
            startActivity(Intent(this, DevHubActivity::class.java))
        }

        btnRestart.setOnClickListener {
            nativeReset()
            level1Played = false
            level2Played = false
            proceedToCurrentNode()
        }

        proceedToCurrentNode()
    }

    override fun onResume() {
        super.onResume()
        Textures.reload(this)
        refreshStoryChar()
        gameView?.start()
    }

    private fun refreshStoryChar() {
        imgStoryChar.setImageBitmap(Textures.loadStoryCharBitmap(this))
    }

    private fun startCharBob() {
        charBobAnimator?.cancel()
        charBobAnimator = ObjectAnimator.ofFloat(imgStoryChar, View.TRANSLATION_Y, 0f, -14f).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(imgStoryChar, View.ROTATION, -3f, 3f).apply {
            duration = 1400
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onPause() {
        super.onPause()
        gameView?.stop()
    }

    private fun proceedToCurrentNode() {
        val json = JSONObject(nativeGetCurrentNode())
        val id = json.getInt("id")

        when {
            id == 1 && !level1Played -> {
                level1Played = true
                startLevel(Levels.resolveLevel1(this))
            }
            id == 2 && !level2Played -> {
                level2Played = true
                startLevel(Levels.resolveLevel2(this))
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
        gameContainer.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        gameView = view
        view.start()
        gameContainer.alpha = 0f
        gameContainer.animate().alpha(1f).setDuration(350).start()
    }

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
        // GameView sudah auto-respawn.
    }

    private fun showStory(show: Boolean) {
        storyContainer.visibility = if (show) View.VISIBLE else View.GONE
        gameContainer.visibility = if (show) View.GONE else View.VISIBLE
        if (show) {
            storyContainer.alpha = 0f
            storyContainer.animate().alpha(1f).setDuration(400).start()
        }
    }

    private fun renderNode(json: JSONObject) {
        val humanity = json.getInt("humanity")
        val storyText = json.getString("text")
        val isEnding = json.getBoolean("isEnding")
        val choices = json.getJSONArray("choices")

        tvHumanity.text = "Humanity: $humanity"
        tvStory.text = storyText
        tvStory.alpha = 0f
        tvStory.translationY = 18f
        tvStory.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(420)
            .setInterpolator(DecelerateInterpolator())
            .start()

        imgStoryChar.scaleX = 0.85f
        imgStoryChar.scaleY = 0.85f
        imgStoryChar.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(380)
            .setInterpolator(DecelerateInterpolator())
            .start()

        llChoices.removeAllViews()

        if (isEnding) {
            btnRestart.visibility = View.VISIBLE
            btnRestart.alpha = 0f
            btnRestart.animate().alpha(1f).setDuration(400).start()
            return
        }
        btnRestart.visibility = View.GONE

        for (i in 0 until choices.length()) {
            val choiceIndex = i
            val choiceObj = choices.getJSONObject(i)
            val button = Button(this).apply {
                text = choiceObj.getString("text")
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#221822"))
                setPadding(24, 24, 24, 24)
                alpha = 0f
                translationY = 24f
                setOnClickListener {
                    animate().alpha(0.5f).setDuration(80).withEndAction {
                        nativeChooseOption(choiceIndex)
                        proceedToCurrentNode()
                    }.start()
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 20
            llChoices.addView(button, params)
            button.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((i * 90).toLong())
                .setDuration(320)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
}
