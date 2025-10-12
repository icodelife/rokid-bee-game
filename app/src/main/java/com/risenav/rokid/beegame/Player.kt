package com.risenav.rokid.beegame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * 玩家
 *
 * @property x 玩家的 x 坐标
 * @property y 玩家的 y 坐标
 * @property playerBitmap 玩家的位图
 */
class Player(
    x: Float,
    y: Float,
    private val playerBitmap: Bitmap,
    private val playerBulletBitmap: Bitmap, // 玩家子弹位图
    private val bulletPoolSize: Int = 30    // 子弹池大小
) : GameObject(x, y) {

    companion object {
        // 为玩家设置固定的显示宽度，以确保游戏体验的一致性
        const val PLAYER_DISPLAY_WIDTH = 60f
        private const val PLAYER_BULLET_SPEED = -15f
    }

    private val playerWidth: Float
    private val playerHeight: Float

    // 子弹对象池
    private val bullets: MutableList<Bullet> = mutableListOf()

    // 射击冷却时间控制
    private var lastShootTime = 0L
    private val shootInterval = 200L // 每200毫秒发射一次

    init {
        // 保持玩家位图的宽高比
        val aspectRatio = if (playerBitmap.height > 0) playerBitmap.width.toFloat() / playerBitmap.height.toFloat() else 1f
        playerWidth = PLAYER_DISPLAY_WIDTH
        playerHeight = playerWidth / aspectRatio

        // 初始化子弹池
        repeat(bulletPoolSize) {
            bullets.add(Bullet(playerBulletBitmap, 0f, 0f, PLAYER_BULLET_SPEED, BulletType.PLAYER).apply { active = false })
        }
    }

    override val rect: RectF
        get() = RectF(
            x - playerWidth / 2,
            y - playerHeight / 2,
            x + playerWidth / 2,
            y + playerHeight / 2
        )

    override fun update() {
        // 玩家位置在 GameView 中根据用户输入进行更新
        bullets.forEach { bullet ->
            if (bullet.active) {
                bullet.update()
            }
        }
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        // 绘制玩家
        canvas.drawBitmap(playerBitmap, null, rect, paint)
        // 绘制活跃子弹
        bullets.forEach { bullet ->
            if (bullet.active) bullet.draw(canvas, paint)
        }
    }

    /**
     * 尝试发射子弹（支持冷却时间）
     */
    fun tryShoot() {
        val now = System.currentTimeMillis()
        if (now - lastShootTime < shootInterval) return

        // 从对象池中获取一个空闲子弹
        val bullet = bullets.find { !it.active } ?: return
        bullet.reset(x, y - playerHeight / 2, PLAYER_BULLET_SPEED)
        lastShootTime = now
    }

    /**
     * 回收飞出屏幕的子弹
     *
     * @param screenHeight 屏幕高度
     */
    fun recycleBullets(screenHeight: Int) {
        bullets.forEach {
            if (it.active && it.isOffScreen(screenHeight)) {
                it.deactivate()
            }
        }
    }

    /**
     * 获取当前活跃子弹（便于碰撞检测）
     */
    fun getActiveBullets(): List<Bullet> = bullets.filter { it.active }
}
