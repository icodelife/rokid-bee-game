package com.risenav.rokid.beegame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

class Bullet(
    private val bitmap: Bitmap,
    x: Float,
    y: Float,
    val speed: Float,
    isPlayerBullet: Boolean // Add a flag to distinguish bullet types
) : GameObject(x, y) {

    private val bulletWidth: Float
    private val bulletHeight: Float

    init {
        if (isPlayerBullet) {
            // Calculate player bullet size relative to the player's size for better visual scale
            val aspectRatio = if (bitmap.width > 0) bitmap.height.toFloat() / bitmap.width.toFloat() else 6f
            bulletWidth = Player.PLAYER_DISPLAY_WIDTH * 0.2f // Bullet width is 20% of player width
            bulletHeight = bulletWidth * aspectRatio
        } else {
            // Set a fixed size for enemy bullets (since it's 9x9, width and height are the same)
            bulletWidth = 12f
            bulletHeight = 12f
        }
    }

    override val rect: RectF
        get() = RectF(
            x - bulletWidth / 2,
            y - bulletHeight / 2,
            x + bulletWidth / 2,
            y + bulletHeight / 2
        )

    override fun update() {
        y += speed
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawBitmap(bitmap, null, rect, paint)
    }
}
