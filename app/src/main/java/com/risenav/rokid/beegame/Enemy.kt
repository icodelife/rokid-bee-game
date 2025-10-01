package com.risenav.rokid.beegame

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable // 引入 Drawable
import kotlin.math.absoluteValue
import kotlin.random.Random

// 修改构造函数以接收宽度、高度和 Drawable
class Enemy(
    x: Float,
    y: Float,
    val enemyWidth: Float,
    val enemyHeight: Float,
    private val enemyDrawable: Drawable // 修改为 Drawable
) : GameObject(x, y) {

    private var vx: Float = 3f // 敌人水平移动速度

    // 初始化射击间隔，例如1.5秒到4秒
    var shotInterval: Long = 1500L + Random.nextLong(0L, 2501L)

    // 初始化lastShotTime，使其首次射击时间更分散
    var lastShotTime: Long = System.currentTimeMillis() - Random.nextLong(shotInterval / 4, shotInterval + 1L)

    override val rect: RectF
        get() = RectF(x - enemyWidth / 2, y - enemyHeight / 2, x + enemyWidth / 2, y + enemyHeight / 2)

    override fun update() {
        x += vx // 更新敌人X坐标
    }

    // 检查敌人是否碰到屏幕边界
    fun checkBounds(screenWidth: Int) {
        if (x - enemyWidth / 2 < 0) { // 碰到左边界
            vx = vx.absoluteValue // 改为向右移动
        }
        if (x + enemyWidth / 2 > screenWidth) { // 碰到右边界
            vx = -vx.absoluteValue // 改为向左移动
        }
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        // 设置 Drawable 的边界
        enemyDrawable.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        // 绘制 Drawable
        enemyDrawable.draw(canvas)
        // paint.color = enemyColor // 不再需要
        // canvas.drawRect(rect, paint) // 不再需要
    }
}
