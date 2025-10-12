package com.risenav.rokid.beegame

import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Window
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.scale
import java.lang.ref.WeakReference
import kotlin.random.Random

class GameView(context: Context) : SurfaceView(context), Runnable, SurfaceHolder.Callback {

    private var thread: Thread? = null

    @Volatile
    private var running = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG) // 全局抗锯齿画笔

    private lateinit var player: Player // 玩家对象
    private val enemies = mutableListOf<Enemy>() // 敌人列表
    private val explosions = mutableListOf<Explosion>() // 爆炸效果列表

    // 初始值常量
    private val initialScore = 0
    private val initialLives = 5
    private val initialLevel = 1

    private var score = initialScore       // 当前分数
    private var lives = initialLives       // 当前生命
    private var currentLevel = initialLevel // 当前关卡
    private var gameOver = false            // 游戏是否结束
    private var highScore = 0               // 最高分
    private var isPaused = false

    // 背景滚动参数
    private var backgroundScrollY = 0f
    private val backgroundScrollSpeed = 2f

    // 存储高分的 SharedPreferences
    private val prefsName = "BeeGamePrefs"
    private val highScoreKey = "highScore"

    // 高分闪烁逻辑
    private var newHighScoreAchievedThisGame = false
    private var highScoreBlinkCount = 0
    private var highScoreBlinkStartTime = 0L
    private var highScoreBlinkStateVisible = true
    private val blinkDurationMs = 300L
    private val totalBlinkCycles = 3
    private val totalBlinkStates = totalBlinkCycles * 2

    // 玩家移动逻辑
    private var movingLeft = false
    private var movingRight = false
    private val playerSpeed = 20f

    // 敌人波次控制
    private var waitingForNextWave = false
    private var nextWaveSpawnTime = 0L
    private val enemySpawnDelay = 3000L
    private val baseEnemyBulletSpeed = 8f

    // 游戏结束按钮
    private val restartButtonRect = RectF()
    private val restartButtonText = "重新开始"
    private val focusedButtonPaint: Paint
    private val focusedButtonTextPaint: Paint
    private val unfocusedButtonPaint: Paint
    private val unfocusedButtonTextPaint: Paint

    private val exitButtonRect = RectF()
    private val exitButtonText = "退出游戏"

    private var focusedButtonIndex = 0

    // 资源加载
    private val gamePrimaryColor: Int
    private var playerBitmap: Bitmap? = null
    private var enemyBitmap: Bitmap? = null
    private var playerBulletBitmap: Bitmap? = null
    private var enemyBulletBitmap: Bitmap? = null
    private var backgroundBitmap: Bitmap? = null
    private var explosionSprite: Bitmap? = null

    // 使用弱引用保存 Window，避免内存泄漏
    private var activityWindowRef: WeakReference<Window?> = WeakReference(null)

    // 缓存高分绘制 paint，避免每帧 new 对象
    private val highScorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pauseOverlayPaint: Paint
    private val pauseTextPaint: Paint

    // 控制单子弹逻辑（仍在 GameView 中保留）：上次发射时间与标志
    private var lastPlayerShotTime = 0L
    private var playerCanShoot = true   // 只有当场上没有玩家子弹时才允许再次发射
    private val singleShotInterval = 500L // 子弹 500ms 冷却检查

    // Player 构造时使用的子弹池大小（可调整）
    private val playerBulletPoolSize = 5
    private val enemyBulletPool = mutableListOf<Bullet>()
    private val enemyBulletPoolSize = 10

    private val bgDestRect1 = RectF()
    private val bgDestRect2 = RectF()


    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true

        gamePrimaryColor = ContextCompat.getColor(context, R.color.game_primary_color)

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
            color = Color.argb(
                alpha,
                Color.red(gamePrimaryColor),
                Color.green(gamePrimaryColor),
                Color.blue(gamePrimaryColor)
            )
        }
        unfocusedButtonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val alpha = (255 * 0.4).toInt()
            color = Color.argb(
                alpha,
                Color.red(gamePrimaryColor),
                Color.green(gamePrimaryColor),
                Color.blue(gamePrimaryColor)
            )
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }

        // 高分画笔初始设置
        highScorePaint.apply {
            textSize = 32f
            textAlign = Paint.Align.CENTER
            color = gamePrimaryColor
            alpha = 255
        }

        pauseOverlayPaint = Paint().apply {
            color = Color.argb(180, 0, 0, 0) // 半透明黑色遮罩
        }
        pauseTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }

        // 加载最高分
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        highScore = prefs.getInt(highScoreKey, 0)
        highScoreBlinkCount = totalBlinkStates
    }

    fun setWindow(window: Window) {
        activityWindowRef = WeakReference(window)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val screenWidth = width
        val screenHeight = height
        val res = context.resources

        val playerWidth = (screenWidth * 0.08f).toInt().coerceAtLeast(40)
        val playerHeight = (playerWidth * 1.2f).toInt()

        playerBitmap = BitmapUtils.decodeVectorToBitmap(
            context,
            R.drawable.airplane,
            playerWidth,
            playerHeight
        )

        val enemyWidth = (screenWidth * 0.1f).toInt().coerceAtLeast(40)
        val enemyHeight = (enemyWidth * 0.8f).toInt()
        enemyBitmap =
            BitmapUtils.decodeVectorToBitmap(context, R.drawable.enemy, enemyWidth, enemyHeight)

        playerBulletBitmap =
            BitmapUtils.decodeSampledBitmapFromResource(res, R.drawable.bullet, 50, 100)
        enemyBulletBitmap =
            BitmapUtils.decodeSampledBitmapFromResource(res, R.drawable.enemy_bullet, 50, 100)
        backgroundBitmap = BitmapUtils.decodeSampledBitmapFromResource(
            res,
            R.drawable.galaxing_bg,
            screenWidth,
            screenHeight
        )
        explosionSprite =
            BitmapUtils.decodeSampledBitmapFromResource(res, R.drawable.explode, 200, 200)

        playerBitmap?.let { pBmp ->
            val bulletBmp =
                playerBulletBitmap ?: throw IllegalStateException("玩家子弹位图加载失败")
            // 传入子弹位图与池大小，Player 内部实现子弹池复用
            player = Player(
                screenWidth / 2f,
                screenHeight - (screenHeight * 0.15f).coerceAtLeast(60f),
                pBmp,
                bulletBmp,
                bulletPoolSize = playerBulletPoolSize
            )
        } ?: throw IllegalStateException("玩家图片 R.drawable.play 加载失败")

        enemyBulletBitmap?.let { bulletBmp ->
            for (i in 0 until enemyBulletPoolSize) {
                enemyBulletPool.add(
                    Bullet(
                        bulletBmp,
                        -100f, -100f, 0f, BulletType.ENEMY
                    )
                )
            }
        } ?: throw IllegalStateException("敌人子弹位图加载失败")

        backgroundBitmap?.let {
            try {
                val scaledBg =
                    it.scale(screenWidth.coerceAtLeast(1), screenHeight.coerceAtLeast(1), true)
                if (scaledBg != it) {
                    it.recycle()
                }
                backgroundBitmap = scaledBg
            } catch (e: Exception) {
                Log.e("GameView", "Background scaling failed", e)
            }
        }

        synchronized(holder) {
            spawnNewEnemies()
        }

        resume()
        requestFocus()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // 在 surface 销毁时加锁，确保线程在退出前不会再访问 holder 中的资源
        synchronized(holder) {
            pause()
            release()
        }
    }

    // 生成敌人
    private fun spawnNewEnemies() {
        synchronized(holder) {
            enemies.clear()
            enemyBitmap?.let { bitmap ->
                val screenWidthFloat = width.toFloat()
                val enemyBaseWidth = screenWidthFloat / 10f
                val newEnemyWidth = enemyBaseWidth * 0.8f
                val newEnemyHeight =
                    newEnemyWidth / (bitmap.width.toFloat() / bitmap.height.toFloat())
                val horizontalSpacing = newEnemyWidth * 0.25f
                val verticalSpacing = newEnemyHeight * 0.3f
                val newStartY = newEnemyHeight / 2f + (height * 0.05f)
                val formationRows = intArrayOf(5, 3, 1)

                var currentY = newStartY
                formationRows.forEachIndexed { rowIndex, enemiesInThisRow ->
                    val totalRowWidth =
                        (enemiesInThisRow * newEnemyWidth) + ((enemiesInThisRow - 1).coerceAtLeast(0) * horizontalSpacing)
                    var currentX = (screenWidthFloat - totalRowWidth) / 2f
                    if (currentX < newEnemyWidth / 2f) currentX = newEnemyWidth / 2f

                    for (i in 0 until enemiesInThisRow) {
                        val actualEnemyCenterX = currentX + newEnemyWidth / 2f
                        if (actualEnemyCenterX + newEnemyWidth / 2f > screenWidthFloat) break
                        enemies.add(
                            Enemy(
                                actualEnemyCenterX,
                                currentY,
                                newEnemyWidth,
                                newEnemyHeight,
                                bitmap, // 直接使用加载的位图
                                rowIndex,
                                width
                            )
                        )
                        currentX += newEnemyWidth + horizontalSpacing
                    }
                    currentY += newEnemyHeight + verticalSpacing
                }
            } ?: run {
                throw IllegalStateException("敌人图片 R.drawable.enemy 加载失败")
            }
            waitingForNextWave = false
        }
    }

    // 重新开始游戏
    private fun restartGame() {
        post {
            activityWindowRef.get()?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        synchronized(holder) {
            score = initialScore
            lives = initialLives
            currentLevel = initialLevel
            gameOver = false
            newHighScoreAchievedThisGame = false
            highScoreBlinkCount = totalBlinkStates
            focusedButtonIndex = 0
            isPaused = false

            if (::player.isInitialized) {
                player.x = width / 2f
                player.y = height - (height * 0.15f).coerceAtLeast(60f)
                // 回收玩家池中活跃子弹（Player 提供的回收方法是 recycleBullets）
                player.recycleBullets(height)
            } else {
                playerBitmap?.let { pBmp ->
                    val bulletBmp =
                        playerBulletBitmap ?: throw IllegalStateException("玩家子弹位图缺失")
                    player = Player(
                        width / 2f,
                        height - (height * 0.15f).coerceAtLeast(60f),
                        pBmp,
                        bulletBmp,
                        bulletPoolSize = playerBulletPoolSize
                    )
                } ?: run {
                    throw IllegalStateException("玩家图片 R.drawable.play 在重新开始时加载失败")
                }
            }

            playerCanShoot = true
            lastPlayerShotTime = 0L
            enemies.clear()
            enemyBulletPool.forEach { it.deactivate() }
            explosions.clear()
            spawnNewEnemies()
            waitingForNextWave = false
            nextWaveSpawnTime = 0L
        }

        // 确保重启时线程恢复运行（防止 pause 导致线程未启动）
        resume()
    }

    // 游戏主循环
    override fun run() {
        while (running) {
            try {
                if (!holder.surface.isValid) {
                    Thread.sleep(16)
                    continue
                }
                val canvas: Canvas? = holder.lockCanvas()
                if (canvas == null) {
                    Thread.sleep(16)
                    continue
                }
                try {
                    synchronized(holder) {
                        update()
                        drawGame(canvas)
                    }
                } finally {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e("GameView", "unlockCanvasAndPost failed", e)
                    }
                }
                Thread.sleep(16)
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e("GameView", "run loop exception", e)
            }
        }
    }

    // 游戏逻辑更新
    private fun update() {
        if (isPaused) return
        if (!::player.isInitialized) return
        val currentTime = System.currentTimeMillis()
        if (gameOver) {
            if (newHighScoreAchievedThisGame && highScoreBlinkCount < totalBlinkStates) {
                if (currentTime - highScoreBlinkStartTime >= blinkDurationMs) {
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
            nextWaveSpawnTime = currentTime + enemySpawnDelay
        }
        if (waitingForNextWave && currentTime >= nextWaveSpawnTime) {
            synchronized(holder) {
                spawnNewEnemies()
            }
        }

        // 玩家移动
        if (movingLeft) player.x -= playerSpeed
        if (movingRight) player.x += playerSpeed
        val playerHalfWidth = if (::player.isInitialized) player.rect.width() / 2f else 30f
        player.x = player.x.coerceIn(playerHalfWidth, width - playerHalfWidth)

        // 玩家射击
        val activePlayerBullets = player.getActiveBullets()
        if (playerCanShoot && !waitingForNextWave && activePlayerBullets.isEmpty() && currentTime - lastPlayerShotTime > singleShotInterval) {
            player.tryShoot()
            lastPlayerShotTime = currentTime
            playerCanShoot = false
        }

        // 敌人移动和射击
        enemies.forEach { it.update() }
        if (enemies.isNotEmpty() && !waitingForNextWave) {
            // 1. 计算当前活跃的敌人子弹数量
            var activeEnemyBulletCount = 0
            for (bullet in enemyBulletPool) {
                if (bullet.active) {
                    activeEnemyBulletCount++
                }
            }

            // 2. 随机选择射击的敌人
            val potentialShooterCount = minOf(enemies.size, currentLevel.coerceAtLeast(1))
            val currentEnemyBulletSpeed = baseEnemyBulletSpeed * (1f + (currentLevel - 1) * 0.10f)

            // 随机选择几个不重复的敌人进行射击
            if (enemies.isNotEmpty()) {
                for (i in 0 until potentialShooterCount) {
                    val shooterIndex = Random.nextInt(enemies.size)
                    val enemy = enemies[shooterIndex]

                    if (activeEnemyBulletCount < currentLevel + 2 && currentTime - enemy.lastShotTime > enemy.shotInterval) {
                        val bullet = enemyBulletPool.find { !it.active }
                        bullet?.let {
                            it.reset(
                                enemy.rect.centerX(),
                                enemy.rect.bottom + 10,
                                currentEnemyBulletSpeed
                            )
                            activeEnemyBulletCount++
                        }

                        enemy.lastShotTime = currentTime
                        val computedUpper =
                            (2500_000_000L / (currentLevel * 100_000L)).coerceAtLeast(1L)
                        enemy.shotInterval =
                            (1500L + Random.nextLong(0, computedUpper)).coerceAtLeast(500L)
                    }
                }
            }
        }

        // 玩家内部更新（会更新子弹位移）
        player.update()
        // 回收玩家飞出屏幕的子弹
        player.recycleBullets(height)

        // 玩家子弹与敌人碰撞检测
        if (activePlayerBullets.isNotEmpty()) {
            val bulletIter = activePlayerBullets.iterator()
            while (bulletIter.hasNext()) {
                val bullet = bulletIter.next()
                val enemiesIterator = enemies.iterator()
                while (enemiesIterator.hasNext()) {
                    val enemy = enemiesIterator.next()
                    if (RectF.intersects(bullet.rect, enemy.rect)) {
                        bullet.deactivate() // 回收子弹
                        enemiesIterator.remove() // 使用迭代器安全地移除敌人
                        score += 10
                        explosionSprite?.let {
                            explosions.add(
                                Explosion(
                                    enemy.rect.centerX(),
                                    enemy.rect.centerY(),
                                    enemy.rect.width(),
                                    it
                                )
                            )
                        }
                        playerCanShoot = true // 标记允许再次开火
                        break // 一个子弹只击中一个敌人，跳出内层循环
                    }
                }
            }
        }


        // 敌人子弹更新与碰撞检测
        enemyBulletPool.forEach { bullet ->
            if (bullet.active) {
                bullet.update()
                if (bullet.isOffScreen(height)) {
                    bullet.deactivate()
                } else if (::player.isInitialized && RectF.intersects(bullet.rect, player.rect)) {
                    bullet.deactivate()
                    lives--
                    explosionSprite?.let {
                        explosions.add(
                            Explosion(
                                player.rect.centerX(),
                                player.rect.centerY(),
                                player.rect.width(),
                                it
                            )
                        )
                    }
                    if (lives <= 0) {
                        gameOver = true
                        post {
                            activityWindowRef.get()
                                ?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        focusedButtonIndex = 0
                        newHighScoreAchievedThisGame = false
                        if (score > highScore) {
                            highScore = score
                            newHighScoreAchievedThisGame = true
                            highScoreBlinkCount = 0
                            highScoreBlinkStartTime = currentTime
                            highScoreBlinkStateVisible = true
                            val prefs =
                                context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                            prefs.edit {
                                putInt(highScoreKey, highScore)
                            }
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

        // 安全恢复射击：若场上没有玩家子弹，则允许再次射击（防止锁死）
        if (player.getActiveBullets().isEmpty()) {
            playerCanShoot = true
        }
    }

    // 游戏绘制
    private fun drawGame(canvas: Canvas) {
        // 背景绘制（循环滚动）
        backgroundBitmap?.let {
            bgDestRect1.set(0f, backgroundScrollY - height, width.toFloat(), backgroundScrollY)
            canvas.drawBitmap(it, null, bgDestRect1, null)
            bgDestRect2.set(0f, backgroundScrollY, width.toFloat(), backgroundScrollY + height)
            canvas.drawBitmap(it, null, bgDestRect2, null)
        } ?: canvas.drawColor(Color.BLACK)

        // 游戏元素绘制
        if (!gameOver) {
            if (::player.isInitialized) player.draw(canvas, paint)
            enemies.forEach { it.draw(canvas, paint) }
            enemyBulletPool.forEach {
                if (it.active) {
                    it.draw(canvas, paint)
                }
            }
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
                highScorePaint.alpha =
                    if (highScoreBlinkCount < totalBlinkStates && !highScoreBlinkStateVisible) 0 else 255
                if (highScorePaint.alpha != 0) {
                    canvas.drawText(
                        "新纪录: $highScore",
                        width / 2f,
                        highScoreTextY,
                        highScorePaint
                    )
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
            restartButtonRect.set(
                firstButtonLeft,
                buttonsY,
                firstButtonLeft + buttonWidth,
                buttonsY + buttonHeight
            )
            drawButton(canvas, restartButtonText, restartButtonRect, focusedButtonIndex == 0)

            // 退出按钮
            val exitButtonLeft = firstButtonLeft + buttonWidth + buttonSpacingHorizontal
            exitButtonRect.set(
                exitButtonLeft,
                buttonsY,
                exitButtonLeft + buttonWidth,
                buttonsY + buttonHeight
            )
            drawButton(canvas, exitButtonText, exitButtonRect, focusedButtonIndex == 1)
        }

        if (isPaused && !gameOver) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pauseOverlayPaint)
            val textY = height / 2f - (pauseTextPaint.descent() + pauseTextPaint.ascent()) / 2
            canvas.drawText("游戏暂停", width / 2f, textY, pauseTextPaint)
        }
    }

    // 按钮绘制
    private fun drawButton(canvas: Canvas, text: String, rect: RectF, isFocused: Boolean) {
        val bgPaint = if (isFocused) focusedButtonPaint else unfocusedButtonPaint
        val textPaint = if (isFocused) focusedButtonTextPaint else unfocusedButtonTextPaint
        canvas.drawRect(rect, bgPaint)
        val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, rect.centerX(), textY, textPaint)
    }

    // 游戏控制
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isPaused && !gameOver) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                isPaused = false
                performClick()
                return true
            }
        }

        if (gameOver && event.action == MotionEvent.ACTION_DOWN) {
            if (restartButtonRect.contains(event.x, event.y)) {
                focusedButtonIndex = 0
                restartGame()
                performClick()
                return true
            } else if (exitButtonRect.contains(event.x, event.y)) {
                focusedButtonIndex = 1
                (context as? MainActivity)?.finish()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (gameOver) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    focusedButtonIndex = 0; true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    focusedButtonIndex = 1; true
                }

                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE -> {
                    if (focusedButtonIndex == 0) {
                        restartGame()
                    } else {
                        (context as? MainActivity)?.finish()
                    }
                    true
                }

                else -> super.onKeyDown(keyCode, event)
            }
        }

        if (isPaused) {
            return when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE -> {
                    isPaused = false
                    true
                }

                else -> true
            }
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> {
                movingLeft = true; true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> {
                movingRight = true; true
            }

            KeyEvent.KEYCODE_BACK -> true

            else -> {
                Log.d("KeyEvent", "按键按下：$keyCode")
                super.onKeyDown(keyCode, event)
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && !gameOver) {
            if (isPaused) {
                (context as? MainActivity)?.finish()
            } else {
                isPaused = true
            }
            return true
        }

        if (isPaused || gameOver) {
            return super.onKeyUp(keyCode, event)
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> {
                movingLeft = false
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> {
                movingRight = false
                true
            }

            else -> {
                Log.d("KeyEvent", "按键松开：$keyCode")
                super.onKeyUp(keyCode, event)
            }
        }
    }

    fun pause() {
        isPaused = true
        running = false
        thread?.let {
            it.interrupt()
            try {
                it.join(500)
            } catch (_: InterruptedException) {
            }
        }
        thread = null
    }

    fun resume() {
        if (running) return
        running = true
        isPaused = false
        thread = Thread(this).also { it.start() }
    }

    fun release() {
        try {
            backgroundBitmap?.recycle()
        } catch (_: Exception) {
        }
        try {
            playerBitmap?.recycle()
        } catch (_: Exception) {
        }
        try {
            playerBulletBitmap?.recycle()
        } catch (_: Exception) {
        }
        try {
            enemyBulletBitmap?.recycle()
        } catch (_: Exception) {
        }
        try {
            enemyBitmap?.recycle()
        } catch (_: Exception) {
        }
        try {
            explosionSprite?.recycle()
        } catch (_: Exception) {
        }
        backgroundBitmap = null
        playerBitmap = null
        playerBulletBitmap = null
        enemyBulletBitmap = null
        enemyBitmap = null
        explosionSprite = null

        // 清理对 Window 的弱引用，避免残留
        activityWindowRef.clear()
    }
}