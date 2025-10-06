package com.risenav.rokid.beegame

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * 游戏对象
 *
 * @property x 游戏对象的 x 坐标
 * @property y 游戏对象的 y 坐标
 */
abstract class GameObject(
    var x: Float,
    var y: Float
) {
    /**
     * 游戏对象的碰撞矩形
     */
    abstract val rect: RectF

    /**
     * 更新游戏对象的状态
     */
    abstract fun update()

    /**
     * 绘制游戏对象
     *
     * @param canvas 画布
     * @param paint 画笔
     */
    abstract fun draw(canvas: Canvas, paint: Paint)
}
