package com.fake.birdgame

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.util.Timer
import kotlin.random.Random

class BirdieGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
     ) : View(context, attrs) {
    private enum class Screen { READY, PLAYING, PAUSED, FINISHED }
    private enum class ObjectType { PUGICORN, CUPCAKE }

    private data class GameObject(
        val type: ObjectType,
        var x: Float,
        val y: Float,
        val size: Float
    ) {
        fun bounds() = RectF(x, y, x + size, y + size)
    }

    companion object {
        private const val WORLD_WIDTH = 400f
        private const val BIRD_X = 48f
        private const val BIRD_SIZE = 76f
        private const val ROUND_SECONDS = 60
        private const val MAX_LIVES = 3
        private const val POINTS_PER_CUPCAKE = 10
        private const val PREFS = "birdie_dash_prefs"
        private const val BEST_SCORE_KEY = "best_score"
    }

    private fun bitmap(id: Int): Bitmap = BitmapFactory.decodeResource(resources, id)
    private val birdUp = bitmap(R.drawable.bird_up)
    private val birdDown = bitmap(R.drawable.bird_down)
    private val cupcake = bitmap(R.drawable.rainbowcuppicake)
    private val pugicorn = bitmap(R.drawable.pugicorn)
    private val fullLife = bitmap(R.drawable.life_full)
    private val emptyLife = bitmap(R.drawable.life_empty)

    private val spritesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(23, 51, 78)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-round", Typeface.BOLD)
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Mutable values are the game state. onDraw() reads them to render the next frame.
    private var screen = Screen.READY
    private val objects = mutableListOf<GameObject>()
    private val random = Random(SystemClock.uptimeMillis())
    private var logicalHeight = 800f
    private var birdY = 320f
    private var elapsedSeconds = 0f
    private var lastFrameMillis = 0L
    private var pugicornSpawnIn = 1.3f
    private var cupcakeSpawnIn = 0.8f
    private var shieldSeconds = 0f
    private var feedbackSeconds = 0f
    private var feedbackResource = R.string.feedback_plus_ten
    private var feedbackY = 0f
    private var secondsLeft = ROUND_SECONDS
    private var score = 0
    private var lives = MAX_LIVES
    private var timedOut = false
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var bestScore = preferences.getInt(BEST_SCORE_KEY, 0)

    private var countdownTimer: Timer? = null
    private var countdownToken = 0

    init {
        isClickable = true
        isFocusable = true
        contentDescription = resources.getString(R.string.game_accessibility_description)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            logicalHeight = h.toFloat() * WORLD_WIDTH / w.toFloat()
            if (screen == Screen.READY) birdY = logicalHeight * 0.48f
            birdY = birdY.coerceIn(135f, (logicalHeight - BIRD_SIZE - 65f).coerceAtLeast(135f))
            backgroundPaint.shader = LinearGradient(
                0f, 0f, 0f, logicalHeight,
                Color.rgb(
                    117, 217, 234
                ), Color.rgb(230, 249, 247),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrameMillis == 0L) 0f else
            ((now - lastFrameMillis) / 1000f).coerceIn(0f, 0.05f)
        lastFrameMillis = now

        if (screen == Screen.PLAYING) updateGame(dt)
        canvas.save()
        canvas.scale(width.toFloat() / WORLD_WIDTH, width.toFloat() / WORLD_WIDTH)
        drawBackground(canvas)
        drawObjects(canvas)
        drawBird(canvas)
        drawHud(canvas)
        drawOverlay(canvas)
        canvas.restore()
        // Ask Android to draw another frame only while gameplay is active.
        if (screen == Screen.PLAYING) postInvalidateOnAnimation()
    }
    private fun drawBackground(canvas: Canvas) {
        canvas.drawRect(0f, 0f, WORLD_WIDTH, logicalHeight, backgroundPaint)

        // Clouds and hills move slightly to suggest forward motion.
       for (index in 0..3) {
           val cycle = WORLD_WIDTH + 160f
           val movement = (elapsedSeconds * (8f + index * 3f)) % cycle
           val cloudX = (index * 157f + 25f - movement + cycle) % cycle - 55f
           val cloudY = 155f + (index % 3) * 104f
           fillPaint.color = Color.argb(170, 255, 255, 255)
            canvas.drawOval(RectF(cloudX, cloudY, cloudX + 110f, cloudY + 38f), fillPaint)
            canvas.drawCircle(cloudX + 34f, cloudY + 4f, 27f, fillPaint)
            canvas.drawCircle(cloudX + 68f, cloudY + 3f, 32f, fillPaint)
       }

        fillPaint.color = Color.rgb(153, 228, 192)
        canvas.drawOval(RectF(-80f, logicalHeight - 120f, 250f, logicalHeight + 86f), fillPaint)
        fillPaint.color = Color.rgb(101, 206, 177)
        canvas.drawOval(RectF(160f, logicalHeight - 104f, 500f, logicalHeight + 100f), fillPaint)
    }
    private fun drawObjects(canvas: Canvas) {
        objects.forEach { item ->
            val image = if (item.type == ObjectType.PUGICORN) pugicorn else cupcake
            canvas.drawBitmap(image, null, item.bounds(), spritesPaint)
        }
    }

    private fun drawBird(canvas: Canvas) {
        // Blink briefly after a collision to show the invincibility interval.
        if (shieldSeconds > 0f && (elapsedSeconds * 10).toInt() % 2 == 0) return
            val image = if ((elapsedSeconds * 5).toInt() % 2 == 0) birdUp else birdDown
            canvas.drawBitmap(
                image, null,
        RectF(BIRD_X, birdY, BIRD_X + BIRD_SIZE, birdY + BIRD_SIZE),
        spritesPaint)

        if (feedbackSeconds > 0f) {
            textPaint.textSize = 16f
            textPaint.color = Color.rgb(23, 51, 78)
            canvas.drawText(resources.getString(feedbackResource), BIRD_X + 44f, feedbackY, textPaint)
        }
    }



}

