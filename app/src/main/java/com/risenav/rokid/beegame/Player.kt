package com.risenav.rokid.beegame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

class Player(
    x: Float,
    y: Float,
    private val playerBitmap: Bitmap
) : GameObject(x, y) {

    companion object {
        // Set a fixed display width for the player for consistent gameplay
        const val PLAYER_DISPLAY_WIDTH = 60f
    }

    private val playerWidth: Float
    private val playerHeight: Float

    init {
        // Maintain the aspect ratio of the player's bitmap
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
        // Player position is updated in GameView based on user input
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        // Draw the bitmap scaled to the calculated rect
        canvas.drawBitmap(playerBitmap, null, rect, paint)
    }
}
