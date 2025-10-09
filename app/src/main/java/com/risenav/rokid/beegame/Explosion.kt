package com.risenav.rokid.beegame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * 爆炸效果
 *
 * @property x 爆炸效果的 x 坐标
 * @property y 爆炸效果的 y 坐标
 * @property size 爆炸效果的尺寸
 * @property spriteSheet 爆炸效果的雪碧图
 */
class Explosion(
    x: Float,
    y: Float,
    private val size: Float,
    private val spriteSheet: Bitmap
) : GameObject(x, y) {

    private var currentFrame = 0
    private val frameCount = 16 // 爆炸效果是 16 帧动画
    private val frameWidth = spriteSheet.width / frameCount
    private val frameHeight = spriteSheet.height

    var isFinished = false

    private val srcRect = Rect()

    override val rect: RectF
        get() {
            val explosionSize = size * 1.2f
            return RectF(
                x - explosionSize / 2,
                y - explosionSize / 2,
                x + explosionSize / 2,
                y + explosionSize / 2
            )
        }

    override fun update() {
        currentFrame++
        if (currentFrame >= frameCount) {
            isFinished = true
        }
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        if (!isFinished) {
            val localPaint = Paint(paint)
            localPaint.isFilterBitmap = false

            val srcX = currentFrame * frameWidth
            srcRect.set(srcX, 0, srcX + frameWidth, frameHeight)
            canvas.drawBitmap(spriteSheet, srcRect, rect, localPaint)
        }
    }
}
