package com.risenav.rokid.beegame

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.absoluteValue
import kotlin.random.Random

// Constructor parameters enemyWidth and enemyHeight are kept for compatibility,
// but the actual display size is now fixed relative to the player's size.
class Enemy(
    x: Float,
    y: Float,
    val enemyWidth: Float, // Original width, now potentially unused for rect/bounds
    val enemyHeight: Float, // Original height, now potentially unused for rect/bounds
    private val enemyDrawable: Drawable
) : GameObject(x, y) {

    companion object {
        private const val PLAYER_SIZE = 60f // Player's dimension (width/height)
        private const val ENEMY_DISPLAY_SCALE_FACTOR = 0.5f // Enemy is 50% smaller
        const val ENEMY_EFFECTIVE_SIZE = PLAYER_SIZE * ENEMY_DISPLAY_SCALE_FACTOR // This will be 30f
        private const val HALF_ENEMY_EFFECTIVE_SIZE = ENEMY_EFFECTIVE_SIZE / 2f
    }

    private var vx: Float = 3f

    var shotInterval: Long = 1500L + Random.nextLong(0L, 2501L)
    var lastShotTime: Long = System.currentTimeMillis() - Random.nextLong(shotInterval / 4, shotInterval + 1L)

    override val rect: RectF
        get() = RectF(
            x - HALF_ENEMY_EFFECTIVE_SIZE,
            y - HALF_ENEMY_EFFECTIVE_SIZE,
            x + HALF_ENEMY_EFFECTIVE_SIZE,
            y + HALF_ENEMY_EFFECTIVE_SIZE
        )

    override fun update() {
        x += vx
    }

    fun checkBounds(screenWidth: Int) {
        // Use ENEMY_EFFECTIVE_SIZE for consistent boundary checks
        if (x - HALF_ENEMY_EFFECTIVE_SIZE < 0) {
            vx = vx.absoluteValue
        }
        if (x + HALF_ENEMY_EFFECTIVE_SIZE > screenWidth) {
            vx = -vx.absoluteValue
        }
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        enemyDrawable.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        enemyDrawable.draw(canvas)
    }
}
