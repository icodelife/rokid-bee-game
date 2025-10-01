package com.risenav.rokid.beegame

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable // 引入 Drawable

// 修改构造函数以接收一个 Drawable 对象
class Player(x: Float, y: Float, private val playerDrawable: Drawable) : GameObject(x, y) {

    override val rect: RectF
        get() = RectF(x - 30, y - 30, x + 30, y + 30) // 玩家尺寸定义：宽度60，高度60

    override fun update() { /* 玩家移动逻辑在 GameView.update() 处理 */ }

    override fun draw(canvas: Canvas, paint: Paint) {
        // 设置 Drawable 的边界
        playerDrawable.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        // 绘制 Drawable
        playerDrawable.draw(canvas)
        // paint.color = playerColor // 不再需要，因为我们使用 Drawable
        // canvas.drawRect(rect, paint) // 不再需要，因为我们使用 Drawable
    }
}
