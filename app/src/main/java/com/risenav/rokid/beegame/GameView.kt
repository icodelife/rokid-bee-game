package com.risenav.rokid.beegame

import android.content.Context
import android.graphics.*
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Window
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), Runnable, SurfaceHolder.Callback {

    private var thread: Thread? = null
    @Volatile private var running = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG) // 全局抗锯齿画笔

    private lateinit var player: Player // 玩家对象
    private val enemies = mutableListOf<Enemy>() // 敌人列表
    private val playerBullets = mutableListOf<Bullet>() // 玩家子弹列表
    private val enemyBullets = mutableListOf<Bullet>() // 敌人子弹列表
    private val explosions = mutableListOf<Explosion>() // 爆炸效果列表

    private var lastPlayerShotTime = 0L // 玩家上次射击时间
    private var playerCanShoot = true   // 玩家是否可以射击

    // 初始值常量，避免魔法数字重复
    private val INITIAL_SCORE = 0
    private val INITIAL_LIVES = 5
    private val INITIAL_LEVEL = 1

    private var score = INITIAL_SCORE       // 当前分数
    private var lives = INITIAL_LIVES       // 当前生命
    private var currentLevel = INITIAL_LEVEL // 当前关卡
    private var gameOver = false            // 游戏是否结束
    private var highScore = 0               // 最高分

    // 背景滚动参数
    private var backgroundScrollY = 0f
    private val backgroundScrollSpeed = 2f

    // 存储高分的 SharedPreferences
    private val PREFS_NAME = "BeeGamePrefs"
    private val HIGH_SCORE_KEY = "highScore"

    // 高分闪烁逻辑
    private var newHighScoreAchievedThisGame = false
    private var highScoreBlinkCount = 0
    private var highScoreBlinkStartTime = 0L
    private var highScoreBlinkStateVisible = true
    private val BLINK_DURATION_MS = 300L
    private val TOTAL_BLINK_CYCLES = 3
    private val TOTAL_BLINK_STATES = TOTAL_BLINK_CYCLES * 2

    // 玩家移动逻辑
    private var movingLeft = false
    private var movingRight = false
    private val playerSpeed = 20f

    // 敌人波次控制
    private var waitingForNextWave = false
    private var nextWaveSpawnTime = 0L
    private val ENEMY_SPAWN_DELAY = 3000L
    private val BASE_ENEMY_BULLET_SPEED = 8f

    // 游戏结束按钮
    private val restartButtonRect = RectF()
    private val restartButtonText = "重新开始"
    private lateinit var focusedButtonPaint: Paint
    private lateinit var focusedButtonTextPaint: Paint
    private lateinit var unfocusedButtonPaint: Paint
    private lateinit var unfocusedButtonTextPaint: Paint

    private val exitButtonRect = RectF()
    private val exitButtonText = "退出游戏"

    private var focusedButtonIndex = 0

    // 资源加载
    private val gamePrimaryColor: Int
    private val playerBitmap: Bitmap?
    private val enemySpriteSheet: Bitmap?
    private val playerBulletBitmap: Bitmap?
    private val enemyBulletBitmap: Bitmap?
    private val backgroundBitmap: Bitmap?
    private val explosionSprite: Bitmap?

    private var activityWindow: Window? = null // 存储 Activity 的 Window 对象

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true

        // 加载资源
        gamePrimaryColor = ContextCompat.getColor(context, R.color.game_primary_color)
        playerBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.airplane)
        enemySpriteSheet = BitmapFactory.decodeResource(context.resources, R.drawable.galaxing)
        playerBulletBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bullet)
        enemyBulletBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.enemy_bullet)
        backgroundBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.galaxing_bg)
        explosionSprite = BitmapFactory.decodeResource(context.resources, R.drawable.explode)

        // 按钮画笔样式
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

        // 加载最高分
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        highScore = prefs.getInt(HIGH_SCORE_KEY, 0)
        highScoreBlinkCount = TOTAL_BLINK_STATES
    }

    fun setWindow(window: Window) {
        this.activityWindow = window
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // 初始化玩家
        playerBitmap?.let {
            player = Player(width / 2f, height - (height * 0.15f).coerceAtLeast(60f), it)
        } ?: run {
            throw IllegalStateException("玩家图片 R.drawable.airplane 加载失败")
        }
        spawnNewEnemies()
        resume()
        requestFocus()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        pause()
    }

    // 生成敌人
    private fun spawnNewEnemies() {
        enemies.clear()
        enemySpriteSheet?.let { spriteSheet ->
            val screenWidthFloat = width.toFloat()
            val frameWidth = 60f
            val frameHeight = 44f
            val aspectRatio = frameWidth / frameHeight
            val enemyBaseWidth = screenWidthFloat / 10f
            val newEnemyWidth = enemyBaseWidth * 0.8f
            val newEnemyHeight = newEnemyWidth / aspectRatio
            val horizontalSpacing = newEnemyWidth * 0.25f
            val verticalSpacing = newEnemyHeight * 0.3f
            val newStartY = newEnemyHeight / 2f + (height * 0.05f)
            val formationRows = intArrayOf(5, 3, 1)

            var currentY = newStartY
            formationRows.forEachIndexed { rowIndex, enemiesInThisRow ->
                val totalRowWidth = (enemiesInThisRow * newEnemyWidth) + ((enemiesInThisRow - 1).coerceAtLeast(0) * horizontalSpacing)
                var currentX = (screenWidthFloat - totalRowWidth) / 2f
                if (currentX < newEnemyWidth / 2f) currentX = newEnemyWidth / 2f

                for (i in 0 until enemiesInThisRow) {
                    val actualEnemyCenterX = currentX + newEnemyWidth / 2f
                    if (actualEnemyCenterX + newEnemyWidth / 2f > screenWidthFloat) break
                    enemies.add(Enemy(actualEnemyCenterX, currentY, newEnemyWidth, newEnemyHeight, spriteSheet, rowIndex, width))
                    currentX += newEnemyWidth + horizontalSpacing
                }
                currentY += newEnemyHeight + verticalSpacing
            }
        } ?: run {
            throw IllegalStateException("敌人雪碧图 R.drawable.galaxing 加载失败")
        }
        waitingForNextWave = false
    }

    // 重新开始游戏
    private fun restartGame() {
        activityWindow?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        score = INITIAL_SCORE
        lives = INITIAL_LIVES
        currentLevel = INITIAL_LEVEL
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
                throw IllegalStateException("玩家图片 R.drawable.airplane 在重新开始时加载失败")
            }
        }
        playerCanShoot = true
        lastPlayerShotTime = 0L
        enemies.clear()
        playerBullets.clear()
        enemyBullets.clear()
        explosions.clear()
        spawnNewEnemies()
        waitingForNextWave = false
        nextWaveSpawnTime = 0L
    }

    // 游戏主循环
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
            } catch (_: InterruptedException) {}
        }
    }

    // 游戏逻辑更新
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

        // 背景滚动
        backgroundScrollY += backgroundScrollSpeed
        if (backgroundScrollY >= height) backgroundScrollY = 0f

        // 波次生成
        if (enemies.isEmpty() && !waitingForNextWave) {
            currentLevel++
            waitingForNextWave = true
            nextWaveSpawnTime = currentTime + ENEMY_SPAWN_DELAY
        }
        if (waitingForNextWave && currentTime >= nextWaveSpawnTime) {
            spawnNewEnemies()
        }

        // 玩家移动
        if (movingLeft) player.x -= playerSpeed
        if (movingRight) player.x += playerSpeed
        val playerHalfWidth = if (::player.isInitialized) player.rect.width() / 2f else 30f
        if (player.x - playerHalfWidth < 0) player.x = playerHalfWidth
        if (player.x + playerHalfWidth > width) player.x = width - playerHalfWidth

        // 玩家射击
        if (playerCanShoot && !waitingForNextWave && currentTime - lastPlayerShotTime > 500) {
            playerBulletBitmap?.let {
                playerBullets.add(Bullet(it, player.rect.centerX(), player.rect.top - 20, -20f, BulletType.PLAYER))
                lastPlayerShotTime = currentTime
                playerCanShoot = false
            }
        }

        // 敌人移动和射击
        enemies.forEach { it.update() }
        if (enemies.isNotEmpty() && !waitingForNextWave) {
            val potentialShooters = enemies.shuffled().take(minOf(enemies.size, currentLevel.coerceAtLeast(1)))
            val currentEnemyBulletSpeed = BASE_ENEMY_BULLET_SPEED * (1f + (currentLevel - 1) * 0.10f)
            for (enemy in potentialShooters) {
                if (enemyBullets.size < currentLevel + 2 && currentTime - enemy.lastShotTime > enemy.shotInterval) {
                    enemyBulletBitmap?.let {
                        enemyBullets.add(Bullet(it, enemy.rect.centerX(), enemy.rect.bottom + 10, currentEnemyBulletSpeed, BulletType.ENEMY))
                    }
                    enemy.lastShotTime = currentTime
                    enemy.shotInterval = (1500L + Random.nextLong(0, 2500_000_000L / (currentLevel * 100_000L))).coerceAtLeast(500L)
                }
            }
        }

        // 玩家子弹更新与碰撞检测
        val pIter = playerBullets.iterator()
        while (pIter.hasNext()) {
            val b = pIter.next()
            b.update()
            if (b.isOffScreen(height)) {
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
                    explosionSprite?.let {
                        explosions.add(Explosion(e.rect.centerX(), e.rect.centerY(), e.rect.width(), it))
                    }
                    break
                }
            }
        }

        // 敌人子弹更新与碰撞检测
        val eIter = enemyBullets.iterator()
        while (eIter.hasNext()) {
            val b = eIter.next()
            b.update()
            if (b.isOffScreen(height)) eIter.remove()
        }
        val enemyBulletIter = enemyBullets.iterator()
        while (enemyBulletIter.hasNext()) {
            val b = enemyBulletIter.next()
            if (::player.isInitialized && RectF.intersects(b.rect, player.rect)) {
                enemyBulletIter.remove()
                lives--
                explosionSprite?.let {
                    explosions.add(Explosion(player.rect.centerX(), player.rect.centerY(), player.rect.width(), it))
                }
                if (lives <= 0) {
                    gameOver = true
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

        // 爆炸效果更新
        val explosionIter = explosions.iterator()
        while (explosionIter.hasNext()) {
            val explosion = explosionIter.next()
            explosion.update()
            if (explosion.isFinished) {
                explosionIter.remove()
            }
        }
    }

    // 游戏绘制
    private fun drawGame(canvas: Canvas) {
        // 背景绘制（循环滚动）
        backgroundBitmap?.let {
            val destRect1 = RectF(0f, backgroundScrollY - height, width.toFloat(), backgroundScrollY)
            canvas.drawBitmap(it, null, destRect1, null)
            val destRect2 = RectF(0f, backgroundScrollY, width.toFloat(), backgroundScrollY + height)
            canvas.drawBitmap(it, null, destRect2, null)
        } ?: run {
            canvas.drawColor(Color.BLACK)
        }

        // 游戏元素绘制
        if (!gameOver) {
            if (::player.isInitialized) player.draw(canvas, paint)
            enemies.forEach { it.draw(canvas, paint) }
            playerBullets.forEach { it.draw(canvas, paint) }
            enemyBullets.forEach { it.draw(canvas, paint) }
            explosions.forEach { it.draw(canvas, paint) }
        }

        // 游戏信息绘制
        paint.color = gamePrimaryColor
        paint.textSize = 20f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("分数: $score", 20f, 30f, paint)
        canvas.drawText("生命: $lives", 20f, 60f, paint)
        canvas.drawText("关卡: $currentLevel", 20f, 90f, paint)

        // 游戏结束界面绘制
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

            // 绘制按钮
            val buttonWidth = 180f
            val buttonHeight = 60f
            val buttonSpacingHorizontal = 20f
            val totalButtonsWidth = buttonWidth * 2 + buttonSpacingHorizontal
            val buttonsY = highScoreTextY + buttonHeight + 20f
            val firstButtonLeft = (width - totalButtonsWidth) / 2f

            // 重新开始按钮
            restartButtonRect.set(firstButtonLeft, buttonsY, firstButtonLeft + buttonWidth, buttonsY + buttonHeight)
            val restartBgPaintToUse = if (focusedButtonIndex == 0) focusedButtonPaint else unfocusedButtonPaint
            val restartTxtPaintToUse = if (focusedButtonIndex == 0) focusedButtonTextPaint else unfocusedButtonTextPaint
            canvas.drawRect(restartButtonRect, restartBgPaintToUse)
            val textYRestart = restartButtonRect.centerY() - (restartTxtPaintToUse.descent() + restartTxtPaintToUse.ascent()) / 2
            canvas.drawText(restartButtonText, restartButtonRect.centerX(), textYRestart, restartTxtPaintToUse)

            // 退出按钮
            val exitButtonLeft = firstButtonLeft + buttonWidth + buttonSpacingHorizontal
            exitButtonRect.set(exitButtonLeft, buttonsY, exitButtonLeft + buttonWidth, buttonsY + buttonHeight)
            val exitBgPaintToUse = if (focusedButtonIndex == 1) focusedButtonPaint else unfocusedButtonPaint
            val exitTxtPaintToUse = if (focusedButtonIndex == 1) focusedButtonTextPaint else unfocusedButtonTextPaint
            canvas.drawRect(exitButtonRect, exitBgPaintToUse)
            val textYExit = exitButtonRect.centerY() - (exitTxtPaintToUse.descent() + exitTxtPaintToUse.ascent()) / 2
            canvas.drawText(exitButtonText, exitButtonRect.centerX(), textYExit, exitTxtPaintToUse)
        }
    }

    // 游戏控制
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameOver && event.action == MotionEvent.ACTION_DOWN) {
            if (restartButtonRect.contains(event.x, event.y)) {
                focusedButtonIndex = 0
                restartGame()
                return true
            } else if (exitButtonRect.contains(event.x, event.y)) {
                focusedButtonIndex = 1
                (context as? MainActivity)?.finish()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!gameOver) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { movingLeft = true; true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { movingRight = true; true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                    val currentTime = System.currentTimeMillis()
                    if (playerCanShoot && !waitingForNextWave && currentTime - lastPlayerShotTime > 500) {
                        playerBulletBitmap?.let {
                            playerBullets.add(Bullet(it, player.rect.centerX(), player.rect.top - 20, -20f, BulletType.PLAYER))
                            lastPlayerShotTime = currentTime
                            playerCanShoot = false
                        }
                    }
                    true
                }
                else -> super.onKeyDown(keyCode, event)
            }
        } else {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { focusedButtonIndex = 0; true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { focusedButtonIndex = 1; true }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE -> {
                    if (focusedButtonIndex == 0) {
                        restartGame()
                        true
                    } else {
                        (context as? MainActivity)?.finish()
                        true
                    }
                }
                else -> super.onKeyDown(keyCode, event)
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!gameOver) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { movingLeft = false; true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { movingRight = false; true }
                else -> super.onKeyUp(keyCode, event)
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    fun pause() { running = false; try { thread?.join() } catch (_: InterruptedException) {} }
    fun resume() { if (!running) { running = true; thread = Thread(this); thread?.start() } }
}
