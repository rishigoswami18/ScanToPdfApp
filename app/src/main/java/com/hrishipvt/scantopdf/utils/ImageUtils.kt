package com.hrishipvt.scantopdf.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint

object ImageUtils {

    // Convert image to Black & White
    fun toGray(bitmap: Bitmap): Bitmap {
        val grayBitmap = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(grayBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return grayBitmap
    }

    // Rotate image 90 degrees
    fun rotate(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(90f)

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    // Simple safe crop (center)
    fun manualCrop(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        return Bitmap.createBitmap(
            bitmap,
            w / 10,
            h / 10,
            w * 8 / 10,
            h * 8 / 10
        )
    }
}
