package com.risenav.rokid.beegame

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable // 引入 Drawable
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Window // 新增导入
import android.view.WindowManager // 新增导入
import androidx.core.content.ContextCompat // 用于从 XML 加载颜色和 Drawable
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), Runnable, SurfaceHolder.Callback {

    private var thread: Thread? = null
    @Volatile private var running = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG) // 全局抗锯齿画笔

    private lateinit var player: Player // 玩家对象
    private val enemies = mutableListOf<Enemy>() // 敌人列表
    private val playerBullets = mutableListOf<Bullet>() // 玩家子弹列表
    private val enemyBullets = mutableListOf<Bullet>() // 敌人子弹列表

    private var lastPlayerShotTime = 0L // 玩家上次射击时间
    private var playerCanShoot = true   // 玩家是否可以射击

    private var score = 0               // 分数
    private var lives = 5              // 初始生命值
    private var gameOver = false            // 游戏是否结束
    private var currentLevel = 1        // 当前关卡
    private var highScore = 0           // 最高分

    private var backgroundScrollY = 0f
    private val backgroundScrollSpeed = 2f

    private val PREFS_NAME = "BeeGamePrefs"
    private val HIGH_SCORE_KEY = "highScore"

    private var newHighScoreAchievedThisGame = false
    private var highScoreBlinkCount = 0
    private var highScoreBlinkStartTime = 0L
    private var highScoreBlinkStateVisible = true
    private val BLINK_DURATION_MS = 300L
    private val TOTAL_BLINK_CYCLES = 3
    private val TOTAL_BLINK_STATES = TOTAL_BLINK_CYCLES * 2

    private var movingLeft = false
    private var movingRight = false
    private val playerSpeed = 20f

    private var waitingForNextWave = false
    private var nextWaveSpawnTime = 0L
    private val ENEMY_SPAWN_DELAY = 3000L
    private val BASE_ENEMY_BULLET_SPEED = 8f

    private val restartButtonRect = RectF()
    private val restartButtonText = "重新开始"
    private lateinit var focusedButtonPaint: Paint
    private lateinit var focusedButtonTextPaint: Paint
    private lateinit var unfocusedButtonPaint: Paint
    private lateinit var unfocusedButtonTextPaint: Paint

    private val exitButtonRect = RectF()
    private val exitButtonText = "退出游戏"

    private var focusedButtonIndex = 0
    private val gamePrimaryColor: Int
    private val playerBitmap: Bitmap?
    private val enemySpriteSheet: Bitmap?
    private val playerBulletBitmap: Bitmap?
    private val enemyBulletBitmap: Bitmap?
    private val backgroundBitmap: Bitmap?

    private var activityWindow: Window? = null // 新增：存储 Activity 的 Window 对象

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true

        gamePrimaryColor = ContextCompat.getColor(context, R.color.game_primary_color)
        playerBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.airplane)
        enemySpriteSheet = BitmapFactory.decodeResource(context.resources, R.drawable.galaxing)
        playerBulletBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bullet)
        enemyBulletBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.enemy_bullet)
        backgroundBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.galaxing_bg)

        focusedButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = gamePrimaryColor
        }
        focusedButtonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gamePrimaryColor
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }

        unfocusedButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            val alpha = (255 * 0.4).toInt()
            color = Color.argb(alpha, Color.red(gamePrimaryColor), Color.green(gamePrimaryColor), Color.blue(gamePrimaryColor))
        }
        unfocusedButtonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val alpha = (255 * 0.4).toInt()
            color = Color.argb(alpha, Color.red(gamePrimaryColor), Color.green(gamePrimaryColor), Color.blue(gamePrimaryColor))
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        highScore = prefs.getInt(HIGH_SCORE_KEY, 0)
        highScoreBlinkCount = TOTAL_BLINK_STATES
    }

    fun setWindow(window: Window) { // 新增：方法用于接收 Window 对象
        this.activityWindow = window
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        playerBitmap?.let {
            player = Player(width / 2f, height - (height * 0.15f).coerceAtLeast(60f), it)
        } ?: run {
            throw IllegalStateException("Player drawable R.drawable.play could not be loaded.")
        }
        spawnNewEnemies()
        resume()
        requestFocus()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pause()
    }

    private fun spawnNewEnemies() {
        enemies.clear()
        enemySpriteSheet?.let { spriteSheet ->
            val screenWidthFloat = width.toFloat()

            // Original sprite sheet is 360x44 for 6 frames, so one frame is 60x44
            val frameWidth = 60f
            val frameHeight = 44f
            val aspectRatio = frameWidth / frameHeight

            val enemyBaseWidth = screenWidthFloat / 10f
            val newEnemyWidth = enemyBaseWidth * 0.8f
            val newEnemyHeight = newEnemyWidth / aspectRatio // Maintain aspect ratio

            val horizontalSpacing = newEnemyWidth * 0.25f
            val verticalSpacing = newEnemyHeight * 0.3f
            val newStartY = newEnemyHeight / 2f + (height * 0.05f)

            val formationRows = intArrayOf(5, 3, 1)
            var currentY = newStartY
            formationRows.forEachIndexed { rowIndex, enemiesInThisRow ->
                val totalRowWidth = (enemiesInThisRow * newEnemyWidth) + ((enemiesInThisRow - 1).coerceAtLeast(0) * horizontalSpacing)
                var currentX = (screenWidthFloat - totalRowWidth) / 2f
                if (currentX < newEnemyWidth / 2f) {
                    currentX = newEnemyWidth / 2f
                }
                for (i in 0 until enemiesInThisRow) {
                    val actualEnemyCenterX = currentX + newEnemyWidth / 2f
                    if (actualEnemyCenterX + newEnemyWidth / 2f > screenWidthFloat) {
                        break
                    }
                    // rowIndex can be used as the enemy type (0, 1, or 2)
                    enemies.add(Enemy(actualEnemyCenterX, currentY, newEnemyWidth, newEnemyHeight, spriteSheet, rowIndex))
                    currentX += newEnemyWidth + horizontalSpacing
                }
                currentY += newEnemyHeight + verticalSpacing
            }
        } ?: run {
            throw IllegalStateException("Enemy spritesheet R.drawable.galaxing could not be loaded.")
        }
        waitingForNextWave = false
    }

    private fun restartGame() {
        activityWindow?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // 新增：重新开启屏幕常亮
        score = 0
        lives = 5
        currentLevel = 1
        gameOver = false
        newHighScoreAchievedThisGame = false
        highScoreBlinkCount = TOTAL_BLINK_STATES
        focusedButtonIndex = 0

        if (::player.isInitialized) {
            player.x = width / 2f
            player.y = height - (height * 0.15f).coerceAtLeast(60f)
        } else {
            playerBitmap?.let {
                player = Player(width / 2f, height - (height * 0.15f).coerceAtLeast(60f), it)
            } ?: run {
                 throw IllegalStateException("Player drawable R.drawable.play could not be loaded during restart.")
            }
        }
        playerCanShoot = true
        lastPlayerShotTime = 0L

        enemies.clear()
        playerBullets.clear()
        enemyBullets.clear()
        spawnNewEnemies()
        waitingForNextWave = false
        nextWaveSpawnTime = 0L
    }

    override fun run() {
        while (running) {
            if (!holder.surface.isValid) continue
            val canvas: Canvas? = holder.lockCanvas()
            if (canvas == null) continue
            synchronized(holder) {
                update()
                drawGame(canvas)
            }
            holder.unlockCanvasAndPost(canvas)
            try {
                Thread.sleep(16)
            } catch (e: InterruptedException) {
            }
        }
    }

    private fun update() {
        val currentTime = System.currentTimeMillis()
        if (gameOver) {
            if (newHighScoreAchievedThisGame && highScoreBlinkCount < TOTAL_BLINK_STATES) {
                if (currentTime - highScoreBlinkStartTime >= BLINK_DURATION_MS) {
                    highScoreBlinkStateVisible = !highScoreBlinkStateVisible
                    highScoreBlinkCount++
                    highScoreBlinkStartTime = currentTime
                }
            }
            return
        }

        backgroundScrollY += backgroundScrollSpeed
        if (backgroundScrollY >= height) {
            backgroundScrollY = 0f
        }

        if (enemies.isEmpty() && !waitingForNextWave) {
            currentLevel++
            waitingForNextWave = true
            nextWaveSpawnTime = currentTime + ENEMY_SPAWN_DELAY
        }
        if (waitingForNextWave && currentTime >= nextWaveSpawnTime) {
            spawnNewEnemies()
        }

        if (movingLeft) player.x -= playerSpeed
        if (movingRight) player.x += playerSpeed
        val playerHalfWidth = if (::player.isInitialized) player.rect.width() / 2f else 30f
        if (player.x - playerHalfWidth < 0) player.x = playerHalfWidth
        if (player.x + playerHalfWidth > width) player.x = width - playerHalfWidth

        if (playerCanShoot && !waitingForNextWave && currentTime - lastPlayerShotTime > 500) {
            playerBulletBitmap?.let {
                playerBullets.add(Bullet(it, player.rect.centerX(), player.rect.top - 20, -20f, isPlayerBullet = true))
                lastPlayerShotTime = currentTime
                playerCanShoot = false
            }
        }

        enemies.forEach {
            it.update()
            it.checkBounds(width)
        }

        if (enemies.isNotEmpty() && !waitingForNextWave) {
            val potentialShooters = enemies.shuffled().take(minOf(enemies.size, currentLevel.coerceAtLeast(1)))
            val currentEnemyBulletSpeed = BASE_ENEMY_BULLET_SPEED * (1f + (currentLevel - 1) * 0.10f)
            for (enemy in potentialShooters) {
                if (enemyBullets.size < currentLevel + 2 && currentTime - enemy.lastShotTime > enemy.shotInterval) {
                    enemyBulletBitmap?.let {
                        enemyBullets.add(Bullet(it, enemy.rect.centerX(), enemy.rect.bottom + 10, currentEnemyBulletSpeed, isPlayerBullet = false))
                    }
                    enemy.lastShotTime = currentTime
                    enemy.shotInterval = (1500L + Random.nextLong(0, 2500_000_000L / (currentLevel * 100_000L))).coerceAtLeast(500L)
                }
            }
        }

        val pIter = playerBullets.iterator()
        while (pIter.hasNext()) {
            val b = pIter.next()
            b.update()
            if (b.rect.top < 0) {
                pIter.remove()
                playerCanShoot = true
            }
        }

        val bulletIter = playerBullets.iterator()
        while (bulletIter.hasNext()) {
            val b = bulletIter.next()
            val enemyIter = enemies.iterator()
            while (enemyIter.hasNext()) {
                val e = enemyIter.next()
                if (RectF.intersects(b.rect, e.rect)) {
                    bulletIter.remove()
                    enemyIter.remove()
                    score += 10
                    playerCanShoot = true
                    break
                }
            }
        }

        val eIter = enemyBullets.iterator()
        while (eIter.hasNext()) {
            val b = eIter.next()
            b.update()
            if (b.rect.bottom > height) {
                eIter.remove()
            }
        }

        val enemyBulletIter = enemyBullets.iterator()
        while (enemyBulletIter.hasNext()) {
            val b = enemyBulletIter.next()
            if (::player.isInitialized && RectF.intersects(b.rect, player.rect)) {
                enemyBulletIter.remove()
                lives--
                if (lives <= 0) {
                    gameOver = true
                    // 使用 post 将 clearFlags 操作延迟执行
                    post { activityWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                    focusedButtonIndex = 0
                    newHighScoreAchievedThisGame = false
                    if (score > highScore) {
                        highScore = score
                        newHighScoreAchievedThisGame = true
                        highScoreBlinkCount = 0
                        highScoreBlinkStartTime = currentTime
                        highScoreBlinkStateVisible = true
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        with(prefs.edit()) {
                            putInt(HIGH_SCORE_KEY, highScore)
                            apply()
                        }
                    }
                }
            }
        }
    }

    private fun drawGame(canvas: Canvas) {
        backgroundBitmap?.let {
            val destRect1 = RectF(0f, backgroundScrollY - height, width.toFloat(), backgroundScrollY)
            canvas.drawBitmap(it, null, destRect1, null)
            val destRect2 = RectF(0f, backgroundScrollY, width.toFloat(), backgroundScrollY + height)
            canvas.drawBitmap(it, null, destRect2, null)
        } ?: run {
            canvas.drawColor(Color.BLACK)
        }

        if (!gameOver) {
            if (::player.isInitialized) player.draw(canvas, paint)
            enemies.forEach { it.draw(canvas, paint) }
            playerBullets.forEach { it.draw(canvas, paint) }
            enemyBullets.forEach { it.draw(canvas, paint) }
        }

        paint.color = gamePrimaryColor
        paint.textSize = 20f
        paint.textAlign = Paint.Align.LEFT
        val infoTextTopMargin = 30f
        val textVerticalSpacing = 30f
        canvas.drawText("分数: $score", 20f, infoTextTopMargin, paint)
        canvas.drawText("生命: $lives", 20f, infoTextTopMargin + textVerticalSpacing, paint)
        canvas.drawText("关卡: $currentLevel", 20f, infoTextTopMargin + textVerticalSpacing * 2, paint)

        if (gameOver) {
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 32f
            val gameOverTextY = height / 2f - 100f
            canvas.drawText("游戏结束", width / 2f, gameOverTextY, paint)

            var highScoreTextY = gameOverTextY + 50f
            if (newHighScoreAchievedThisGame) {
                val highScoreTextPaint = if (highScoreBlinkCount < TOTAL_BLINK_STATES && !highScoreBlinkStateVisible) {
                    Paint(focusedButtonTextPaint).apply { alpha = 0; textSize = 32f }
                } else {
                    Paint(focusedButtonTextPaint).apply { textSize = 32f }
                }
                if (highScoreTextPaint.alpha != 0) {
                     canvas.drawText("新纪录: $highScore", width / 2f, highScoreTextY, highScoreTextPaint)
                }
            } else {
                 highScoreTextY = gameOverTextY + 20f
            }

            val buttonWidth = 180f
            val buttonHeight = 60f
            val buttonSpacingHorizontal = 20f
            val totalButtonsWidth = buttonWidth * 2 + buttonSpacingHorizontal
            val buttonsY = highScoreTextY + buttonHeight + 20f

            val firstButtonLeft = (width - totalButtonsWidth) / 2f

            restartButtonRect.set(
                firstButtonLeft,
                buttonsY,
                firstButtonLeft + buttonWidth,
                buttonsY + buttonHeight
            )

            val restartBgPaintToUse = if (focusedButtonIndex == 0) focusedButtonPaint else unfocusedButtonPaint
            val restartTxtPaintToUse = if (focusedButtonIndex == 0) focusedButtonTextPaint else unfocusedButtonTextPaint
            canvas.drawRect(restartButtonRect, restartBgPaintToUse)
            val textXRestart = restartButtonRect.centerX()
            val textYRestart = restartButtonRect.centerY() - (restartTxtPaintToUse.descent() + restartTxtPaintToUse.ascent()) / 2
            canvas.drawText(restartButtonText, textXRestart, textYRestart, restartTxtPaintToUse)

            val exitButtonLeft = firstButtonLeft + buttonWidth + buttonSpacingHorizontal
            exitButtonRect.set(
                exitButtonLeft,
                buttonsY,
                exitButtonLeft + buttonWidth,
                buttonsY + buttonHeight
            )

            val exitBgPaintToUse = if (focusedButtonIndex == 1) focusedButtonPaint else unfocusedButtonPaint
            val exitTxtPaintToUse = if (focusedButtonIndex == 1) focusedButtonTextPaint else unfocusedButtonTextPaint
            canvas.drawRect(exitButtonRect, exitBgPaintToUse)
            val textXExit = exitButtonRect.centerX()
            val textYExit = exitButtonRect.centerY() - (exitTxtPaintToUse.descent() + exitTxtPaintToUse.ascent()) / 2
            canvas.drawText(exitButtonText, textXExit, textYExit, exitTxtPaintToUse)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (gameOver) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (focusedButtonIndex == 1) focusedButtonIndex = 0
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (focusedButtonIndex == 0) focusedButtonIndex = 1
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (focusedButtonIndex == 0) {
                        restartGame()
                    } else {
                        (context as? android.app.Activity)?.finish()
                    }
                    return true
                }
            }
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> {
                    movingLeft = true; return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> {
                    movingRight = true; return true
                }
                 KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                     if (playerCanShoot && !waitingForNextWave && System.currentTimeMillis() - lastPlayerShotTime > 500) {
                         playerBulletBitmap?.let {
                            playerBullets.add(Bullet(it, player.rect.centerX(), player.rect.top - 20, -20f, isPlayerBullet = true))
                            lastPlayerShotTime = System.currentTimeMillis()
                            playerCanShoot = false
                         }
                     }
                    return true
                 }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!gameOver) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> movingLeft = false
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> movingRight = false
            }
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameOver && event.action == MotionEvent.ACTION_DOWN) {
            if (restartButtonRect.contains(event.x, event.y)) {
                focusedButtonIndex = 0
                restartGame()
                return true
            } else if (exitButtonRect.contains(event.x, event.y)) {
                focusedButtonIndex = 1
                 (context as? android.app.Activity)?.finish()
                return true
            }
        } else if (!gameOver && event.action == MotionEvent.ACTION_DOWN) {
            if (playerCanShoot && !waitingForNextWave && System.currentTimeMillis() - lastPlayerShotTime > 500) {
                 playerBulletBitmap?.let {
                     playerBullets.add(Bullet(it, player.rect.centerX(), player.rect.top - 20, -20f, isPlayerBullet = true))
                     lastPlayerShotTime = System.currentTimeMillis()
                     playerCanShoot = false
                     return true
                 }
            }
        }
        return super.onTouchEvent(event)
    }

    fun pause() {
        running = false
        try {
            thread?.join(500)
        } catch (e: InterruptedException) {
        }
        thread = null
    }

    fun resume() {
        if (thread == null || !running) {
            running = true
            thread = Thread(this)
            thread?.start()
        }
    }
}
