package com.risenav.rokid.beegame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

class Bullet(private val context: Context, x: Float, y: Float, val speed: Float) : GameObject(x, y) {

    override val rect: RectF
        get() = RectF(x - 4, y - 8, x + 4, y + 8)

    override fun update() { y += speed }

    override fun draw(canvas: Canvas, paint: Paint) {
        paint.color = ContextCompat.getColor(context, R.color.game_primary_color)
        canvas.drawRect(rect, paint)
    }
}
