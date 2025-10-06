package com.risenav.rokid.beegame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.absoluteValue
import kotlin.random.Random

class Enemy(
    x: Float,
    y: Float,
    val enemyWidth: Float,
    val enemyHeight: Float,
    private val spriteSheet: Bitmap,
    private val type: Int
) : GameObject(x, y) {

    private var vx: Float = 3f
    private var currentFrame = 0
    private val frameCount = 2
    private val frameWidth = spriteSheet.width / 6
    private val frameHeight = spriteSheet.height
    private var lastFrameChangeTime = 0L
    private val frameChangeDelay = 200L // 200ms per frame

    var shotInterval: Long = 1500L + Random.nextLong(0L, 2501L)
    var lastShotTime: Long = System.currentTimeMillis() - Random.nextLong(shotInterval / 4, shotInterval + 1L)

    override val rect: RectF
        get() = RectF(
            x - enemyWidth / 2,
            y - enemyHeight / 2,
            x + enemyWidth / 2,
            y + enemyHeight / 2
        )

    override fun update() {
        x += vx
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFrameChangeTime > frameChangeDelay) {
            currentFrame = (currentFrame + 1) % frameCount
            lastFrameChangeTime = currentTime
        }
    }

    fun checkBounds(screenWidth: Int) {
        if (x - enemyWidth / 2 < 0) {
            vx = vx.absoluteValue
        }
        if (x + enemyWidth / 2 > screenWidth) {
            vx = -vx.absoluteValue
        }
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        val srcX = (type * 2 + currentFrame) * frameWidth
        val srcRect = Rect(srcX, 0, srcX + frameWidth, frameHeight)
        canvas.drawBitmap(spriteSheet, srcRect, rect, paint)
    }
}
