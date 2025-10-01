package com.risenav.rokid.beegame

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

abstract class GameObject(
    var x: Float,
    var y: Float
) {
    abstract val rect: RectF
    abstract fun update()
    abstract fun draw(canvas: Canvas, paint: Paint)
}
