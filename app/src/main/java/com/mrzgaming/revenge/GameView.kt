package com.mrzgaming.revenge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.Choreographer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class GameView(context: Context, attrs: AttributeSet? = null) : View(context, attrs), Choreographer.FrameCallback {

    interface Listener {
        fun onLevelCleared()
        fun onPlayerDied()
    }

    var listener: Listener? = null

    private lateinit var level: LevelMap
    private lateinit var player: Player
    private val enemies = mutableListOf<Enemy>()

    private var frameBuffer = IntArray(Raycaster.RENDER_W * Raycaster.RENDER_H)
    private var zBuffer = DoubleArray(Raycaster.RENDER_W)
    private var fbBitmap = Bitmap.createBitmap(Raycaster.RENDER_W, Raycaster.RENDER_H, Bitmap.Config.ARGB_8888)

    private var lastFrameNanos = 0L
    private var running = false
    private var levelCleared = false
    private var animTime = 0f

    private var joystickPointerId = -1
    private var joystickBaseX = 0f
    private var joystickBaseY = 0f
    private var joystickDX = 0f
    private var joystickDY = 0f
    private val joystickRadius = 130f

    private var lookPointerId = -1
    private var lastLookX = 0f

    private var shootPointerId = -1
    private var shootBtnCx = 0f
    private var shootBtnCy = 0f
    private val shootBtnR = 90f

    private var message: String? = null
    private var messageTimer = 0f

    private var hammerBmp: Bitmap = Textures.loadWeaponBitmap(context)

    private val hudPaint = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        isAntiAlias = true
    }
    private val bgPaint = Paint()
    private val barBgPaint = Paint().apply { color = Color.argb(180, 30, 20, 20) }
    private val healthPaint = Paint().apply { color = Color.rgb(200, 60, 60) }
    private val joyPaint = Paint().apply { color = Color.argb(120, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 6f }
    private val joyStickPaint = Paint().apply { color = Color.argb(160, 255, 255, 255) }
    private val shootPaint = Paint().apply { color = Color.argb(150, 200, 60, 60) }
    private val crosshairPaint = Paint().apply { color = Color.argb(200, 255, 255, 255); strokeWidth = 4f }
    private val weaponPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }

    fun loadLevel(newLevel: LevelMap) {
        level = newLevel
        levelCleared = false
        message = null
        if (!hammerBmp.isRecycled) {
            // refresh senjata kalau asset settings berubah
            val fresh = Textures.loadWeaponBitmap(context)
            if (hammerBmp !== fresh) {
                hammerBmp.recycle()
                hammerBmp = fresh
            }
        }
        resetPlayerAndEnemies()
    }

    private fun resetPlayerAndEnemies() {
        player = Player(level.playerStartX, level.playerStartY, level.playerStartAngle)
        enemies.clear()
        for ((ex, ey) in level.enemySpawns) enemies.add(Enemy(ex, ey))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            Raycaster.RENDER_H = min(140, (Raycaster.RENDER_W * h / w))
            frameBuffer = IntArray(Raycaster.RENDER_W * Raycaster.RENDER_H)
            fbBitmap = Bitmap.createBitmap(Raycaster.RENDER_W, Raycaster.RENDER_H, Bitmap.Config.ARGB_8888)
        }
        shootBtnCx = w - 130f
        shootBtnCy = h - 160f
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos
        var dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = frameTimeNanos
        if (dt > 0.06f) dt = 0.06f

        update(dt)
        invalidate()

        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun update(dt: Float) {
        if (!::level.isInitialized) return

        animTime += dt
        if (player.muzzleFlashTimer > 0f) player.muzzleFlashTimer -= dt
        if (player.hurtFlashTimer > 0f) player.hurtFlashTimer -= dt
        if (player.swingTimer > 0f) player.swingTimer -= dt
        if (messageTimer > 0f) {
            messageTimer -= dt
            if (messageTimer <= 0f) message = null
        }

        if (levelCleared || player.health <= 0) return

        val moveSpeed = 2.4 * dt
        val forward = (-joystickDY / joystickRadius).coerceIn(-1f, 1f)
        val strafe = (joystickDX / joystickRadius).coerceIn(-1f, 1f)
        val moving = kotlin.math.abs(forward) > 0.08f || kotlin.math.abs(strafe) > 0.08f
        if (moving) player.walkBobPhase += dt * 10f

        val newX = player.x + player.dirX * forward * moveSpeed - player.dirY * strafe * moveSpeed
        val newY = player.y + player.dirY * forward * moveSpeed + player.dirX * strafe * moveSpeed
        if (level.canStand(newX, player.y)) player.x = newX
        if (level.canStand(player.x, newY)) player.y = newY

        updateEnemies(dt)

        if (enemies.all { !it.alive }) {
            levelCleared = true
            listener?.onLevelCleared()
        }
        if (player.health <= 0) {
            listener?.onPlayerDied()
            resetPlayerAndEnemies()
        }
    }

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            if (!e.alive) continue
            e.animTime += dt
            if (e.hitFlashTimer > 0f) e.hitFlashTimer -= dt
            if (e.attackCooldown > 0f) e.attackCooldown -= dt

            val d = dist(player.x, player.y, e.x, e.y)
            val sees = d < 7.0 && level.hasLineOfSight(e.x, e.y, player.x, player.y)
            e.moving = false

            if (sees) {
                if (d > 1.3) {
                    val dx = player.x - e.x
                    val dy = player.y - e.y
                    val len = hypot(dx, dy).coerceAtLeast(0.001)
                    val speed = 1.1 * dt
                    val nx = e.x + dx / len * speed
                    val ny = e.y + dy / len * speed
                    if (level.canStand(nx, e.y)) e.x = nx
                    if (level.canStand(e.x, ny)) e.y = ny
                    e.moving = true
                } else if (e.attackCooldown <= 0f) {
                    player.health = (player.health - 8).coerceAtLeast(0)
                    player.hurtFlashTimer = 0.25f
                    e.attackCooldown = 1.1f
                }
            } else {
                e.wanderTimer -= dt
                if (e.wanderTimer <= 0f) {
                    e.wanderAngle = Math.random() * Math.PI * 2
                    e.wanderTimer = (1f + Math.random() * 2f).toFloat()
                }
                val speed = 0.35 * dt
                val nx = e.x + cos(e.wanderAngle) * speed
                val ny = e.y + sin(e.wanderAngle) * speed
                var moved = false
                if (level.canStand(nx, e.y)) { e.x = nx; moved = true } else e.wanderTimer = 0f
                if (level.canStand(e.x, ny)) { e.y = ny; moved = true } else e.wanderTimer = 0f
                e.moving = moved
            }
        }
    }

    private fun shoot() {
        if (!::player.isInitialized || levelCleared || player.health <= 0) return
        if (player.ammo <= 0) {
            showMessage("Amunisi habis!")
            return
        }
        player.ammo--
        player.muzzleFlashTimer = 0.12f
        player.swingTimer = 0.28f

        var bestEnemy: Enemy? = null
        var bestDist = Double.MAX_VALUE
        for (e in enemies) {
            if (!e.alive) continue
            val dx = e.x - player.x
            val dy = e.y - player.y
            val d = hypot(dx, dy)
            if (d > 8.0) continue
            val angleToEnemy = atan2(dy, dx)
            val playerAngle = atan2(player.dirY, player.dirX)
            var diff = angleToEnemy - playerAngle
            while (diff > Math.PI) diff -= 2 * Math.PI
            while (diff < -Math.PI) diff += 2 * Math.PI
            if (Math.abs(diff) < 0.14 && level.hasLineOfSight(player.x, player.y, e.x, e.y)) {
                if (d < bestDist) {
                    bestDist = d
                    bestEnemy = e
                }
            }
        }
        bestEnemy?.let {
            it.health -= 20
            it.hitFlashTimer = 0.15f
            if (it.health <= 0) it.alive = false
        }
    }

    private fun showMessage(text: String) {
        message = text
        messageTimer = 1.2f
    }

    override fun onDraw(canvas: Canvas) {
        if (!::level.isInitialized || !::player.isInitialized) return

        Raycaster.render(frameBuffer, Raycaster.RENDER_W, Raycaster.RENDER_H, player, level, enemies, zBuffer)
        fbBitmap.setPixels(frameBuffer, 0, Raycaster.RENDER_W, 0, 0, Raycaster.RENDER_W, Raycaster.RENDER_H)
        canvas.drawBitmap(fbBitmap, Rect(0, 0, Raycaster.RENDER_W, Raycaster.RENDER_H), RectF(0f, 0f, width.toFloat(), height.toFloat()), bgPaint)

        if (player.muzzleFlashTimer > 0f) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.argb(40, 255, 240, 180) })
        }
        if (player.hurtFlashTimer > 0f) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.argb(70, 200, 20, 20) })
        }

        drawWeapon(canvas)
        drawHud(canvas)
        drawControls(canvas)

        if (message != null) {
            hudPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(message!!, width / 2f, height * 0.35f, hudPaint)
            hudPaint.textAlign = Paint.Align.LEFT
        }

        if (levelCleared) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.argb(160, 0, 0, 0) })
            hudPaint.textAlign = Paint.Align.CENTER
            hudPaint.textSize = 56f
            canvas.drawText("Area aman.", width / 2f, height / 2f, hudPaint)
            hudPaint.textSize = 42f
            canvas.drawText("Lanjut...", width / 2f, height / 2f + 60f, hudPaint)
            hudPaint.textAlign = Paint.Align.LEFT
            hudPaint.textSize = 42f
        }
    }

    private fun drawWeapon(canvas: Canvas) {
        val cx = width * 0.72f
        val baseY = height.toFloat()
        val idleBob = sin(animTime * 3.2f) * 10f
        val walkBob = if (player.walkBobPhase > 0f) sin(player.walkBobPhase) * 14f else 0f
        val swingT = (player.swingTimer / 0.28f).coerceIn(0f, 1f)
        // ease: angkat → pukul → kembali
        val swingAngle = when {
            swingT > 0.55f -> {
                val t = (1f - swingT) / 0.45f
                -25f + t * t * 70f
            }
            swingT > 0f -> {
                val t = swingT / 0.55f
                45f * (1f - t)
            }
            else -> 0f
        }
        val swingDrop = if (swingT > 0f) sin((1f - swingT) * Math.PI.toFloat()) * 40f else 0f

        val size = height * 0.42f
        canvas.save()
        canvas.translate(cx, baseY - size * 0.15f + idleBob + walkBob + swingDrop)
        canvas.rotate(swingAngle, 0f, 0f)
        val dst = RectF(-size * 0.35f, -size, size * 0.35f, size * 0.05f)
        canvas.drawBitmap(hammerBmp, null, dst, weaponPaint)
        canvas.restore()

        if (player.muzzleFlashTimer > 0f) {
            val flash = Paint().apply { color = Color.argb(200, 255, 220, 120) }
            canvas.drawCircle(cx - size * 0.05f, baseY - size * 0.85f + idleBob, 28f, flash)
        }
    }

    private fun drawHud(canvas: Canvas) {
        val barW = 260f
        canvas.drawRoundRect(RectF(24f, 24f, 24f + barW, 64f), 8f, 8f, barBgPaint)
        val hpFrac = (player.health / 100f).coerceIn(0f, 1f)
        canvas.drawRoundRect(RectF(24f, 24f, 24f + barW * hpFrac, 64f), 8f, 8f, healthPaint)
        hudPaint.textSize = 28f
        canvas.drawText("Nyawa: ${player.health}", 32f, 52f, hudPaint)
        hudPaint.textSize = 42f
        canvas.drawText("Peluru: ${player.ammo}", 24f, 100f, hudPaint)
        canvas.drawText(level.name, 24f, 145f, hudPaint)

        val cx = width / 2f
        val cy = height / 2f
        canvas.drawLine(cx - 16f, cy, cx + 16f, cy, crosshairPaint)
        canvas.drawLine(cx, cy - 16f, cx, cy + 16f, crosshairPaint)
    }

    private fun drawControls(canvas: Canvas) {
        canvas.drawCircle(joystickBaseX, joystickBaseY, joystickRadius, joyPaint)
        if (joystickPointerId != -1) {
            canvas.drawCircle(joystickBaseX + joystickDX, joystickBaseY + joystickDY, 45f, joyStickPaint)
        } else {
            canvas.drawCircle(if (joystickBaseX == 0f) 220f else joystickBaseX, if (joystickBaseY == 0f) height - 220f else joystickBaseY, 45f, joyStickPaint)
        }
        canvas.drawCircle(shootBtnCx, shootBtnCy, shootBtnR, shootPaint)
        hudPaint.textAlign = Paint.Align.CENTER
        hudPaint.textSize = 30f
        canvas.drawText("TEMBAK", shootBtnCx, shootBtnCy + 10f, hudPaint)
        hudPaint.textAlign = Paint.Align.LEFT
        hudPaint.textSize = 42f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val x = event.getX(idx)
                val y = event.getY(idx)

                val distToShoot = hypot((x - shootBtnCx).toDouble(), (y - shootBtnCy).toDouble())
                if (distToShoot < shootBtnR + 30 && shootPointerId == -1) {
                    shootPointerId = id
                    shoot()
                } else if (x < width / 2f && joystickPointerId == -1) {
                    joystickPointerId = id
                    joystickBaseX = x
                    joystickBaseY = y
                    joystickDX = 0f
                    joystickDY = 0f
                } else if (lookPointerId == -1) {
                    lookPointerId = id
                    lastLookX = x
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    if (id == joystickPointerId) {
                        var dx = x - joystickBaseX
                        var dy = y - joystickBaseY
                        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (len > joystickRadius) {
                            dx = dx / len * joystickRadius
                            dy = dy / len * joystickRadius
                        }
                        joystickDX = dx
                        joystickDY = dy
                    } else if (id == lookPointerId) {
                        val delta = x - lastLookX
                        lastLookX = x
                        if (::player.isInitialized) {
                            player.rotate(delta * 0.0032)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                when (id) {
                    joystickPointerId -> {
                        joystickPointerId = -1
                        joystickDX = 0f
                        joystickDY = 0f
                    }
                    lookPointerId -> lookPointerId = -1
                    shootPointerId -> shootPointerId = -1
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                joystickPointerId = -1
                lookPointerId = -1
                shootPointerId = -1
                joystickDX = 0f
                joystickDY = 0f
            }
        }
        return true
    }
}
