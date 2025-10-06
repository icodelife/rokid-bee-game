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
    private val playerBitmap: Bitmap
) : GameObject(x, y) {

    companion object {
        // 为玩家设置固定的显示宽度，以确保游戏体验的一致性
        const val PLAYER_DISPLAY_WIDTH = 60f
    }

    private val playerWidth: Float
    private val playerHeight: Float

    init {
        // 保持玩家位图的宽高比
        val aspectRatio = if (playerBitmap.height > 0) playerBitmap.width.toFloat() / playerBitmap.height.toFloat() else 1f
        playerWidth = PLAYER_DISPLAY_WIDTH
        playerHeight = playerWidth / aspectRatio
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
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        // 将位图缩放到计算出的矩形中绘制
        canvas.drawBitmap(playerBitmap, null, rect, paint)
    }
}
