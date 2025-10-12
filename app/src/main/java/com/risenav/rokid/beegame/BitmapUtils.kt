package com.risenav.rokid.beegame

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.core.content.ContextCompat

/**
 * 位图加载工具类：根据目标大小计算最佳 inSampleSize
 */
object BitmapUtils {

    /**
     * 从资源中按需加载 Bitmap，自动根据目标宽高缩放
     */
    fun decodeSampledBitmapFromResource(
        res: android.content.res.Resources,
        resId: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeResource(res, resId, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        return BitmapFactory.decodeResource(res, resId, options)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun decodeVectorToBitmap(
        context: Context,
        drawableId: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap {
        val vectorDrawable = ContextCompat.getDrawable(context, drawableId)
            ?: throw IllegalArgumentException("Invalid drawable resource ID")

        val bitmap = Bitmap.createBitmap(
            reqWidth.coerceAtLeast(1),
            reqHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
        vectorDrawable.draw(canvas)
        return bitmap
    }
}