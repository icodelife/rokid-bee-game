package com.risenav.rokid.beegame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.absoluteValue
import kotlin.random.Random

/**
 * 敌人
 *
 * @property x 敌人的 x 坐标
 * @property y 敌人的 y 坐标
 * @property enemyWidth 敌人的宽度
 * @property enemyHeight 敌人的高度
 * @property spriteSheet 敌人的雪碧图
 * @property type 敌人的类型
 */
class Enemy(
    x: Float,
    y: Float,
    val enemyWidth: Float,
    val enemyHeight: Float,
    private val spriteSheet: Bitmap,
    private val type: Int,
    private val screenWidth: Int
) : GameObject(x, y) {

    private var vx: Float = 3f
    private var currentFrame = 0
    private val frameCount = 2 // 每个敌人类型有 2 帧动画
    private val frameWidth = spriteSheet.width / (3 * frameCount) // 雪碧图包含 3 种敌人类型
    private val frameHeight = spriteSheet.height
    private var lastFrameChangeTime = 0L
    private val frameChangeDelay = 200L // 每帧 200 毫秒

    var shotInterval: Long = Random.nextLong(1500L, 4001L)
    var lastShotTime: Long = System.currentTimeMillis() - Random.nextLong(0, shotInterval)

    override val rect: RectF
        get() = RectF(
            x - enemyWidth / 2,
            y - enemyHeight / 2,
            x + enemyWidth / 2,
            y + enemyHeight / 2
        )

    override fun update() {
        x += vx

        // 边界检查
        if (x - enemyWidth / 2 < 0 || x + enemyWidth / 2 > screenWidth) {
            vx = -vx
        }

        // 更新动画帧
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFrameChangeTime > frameChangeDelay) {
            currentFrame = (currentFrame + 1) % frameCount
            lastFrameChangeTime = currentTime
        }
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        val srcX = (type * frameCount + currentFrame) * frameWidth
        val srcRect = Rect(srcX, 0, srcX + frameWidth, frameHeight)
        canvas.drawBitmap(spriteSheet, srcRect, rect, paint)
    }
}
