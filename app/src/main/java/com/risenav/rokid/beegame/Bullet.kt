package com.risenav.rokid.beegame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * 子弹类型
 *
 * @property PLAYER 玩家子弹
 * @property ENEMY 敌人子弹
 */
sealed class BulletType {
    object PLAYER : BulletType()
    object ENEMY : BulletType()
}

/**
 * 子弹类（支持对象池复用）
 *
 * @property bitmap 子弹的位图
 * @property x 子弹的 x 坐标
 * @property y 子弹的 y 坐标
 * @property speed 子弹的速度
 * @property type 子弹的类型
 */
class Bullet(
    private val bitmap: Bitmap,
    x: Float,
    y: Float,
    var speed: Float,
    private val type: BulletType
) : GameObject(x, y) {

    companion object {
        // 玩家子弹的显示宽度
        private const val PLAYER_BULLET_DISPLAY_WIDTH = 7f
        // 玩家子弹的备用宽高比
        private const val PLAYER_BULLET_ASPECT_RATIO = 6f
        // 敌人子弹的宽度
        private const val ENEMY_BULLET_WIDTH = 12f
        // 敌人子弹的高度
        private const val ENEMY_BULLET_HEIGHT = 12f
    }

    private val bulletWidth: Float
    private val bulletHeight: Float
    var active: Boolean = false // 是否在使用中（用于对象池控制）

    init {
        when (type) {
            is BulletType.PLAYER -> {
                // 根据玩家的尺寸计算玩家子弹的大小，以获得更好的视觉比例
                val aspectRatio = if (bitmap.width > 0) bitmap.height.toFloat() / bitmap.width.toFloat() else PLAYER_BULLET_ASPECT_RATIO
                bulletWidth = PLAYER_BULLET_DISPLAY_WIDTH
                bulletHeight = bulletWidth * aspectRatio
            }
            is BulletType.ENEMY -> {
                // 为敌人子弹设置固定尺寸
                bulletWidth = ENEMY_BULLET_WIDTH
                bulletHeight = ENEMY_BULLET_HEIGHT
            }
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
        if (!active) return
        y += speed
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        if (active) canvas.drawBitmap(bitmap, null, rect, paint)
    }

    /**
     * 检查子弹是否在屏幕外
     *
     * @param screenHeight 屏幕高度
     * @return 如果子弹在屏幕外，则为 true，否则为 false
     */
    fun isOffScreen(screenHeight: Int): Boolean {
        return rect.bottom < 0 || rect.top > screenHeight
    }

    /**
     * 重置子弹位置（用于对象池复用）
     */
    fun reset(newX: Float, newY: Float, newSpeed: Float) {
        x = newX
        y = newY
        speed = newSpeed
        active = true
    }

    /**
     * 失效（回收）
     */
    fun deactivate() {
        active = false
    }
}