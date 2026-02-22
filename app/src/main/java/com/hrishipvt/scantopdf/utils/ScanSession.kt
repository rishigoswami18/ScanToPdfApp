package com.hrishipvt.scantopdf.utils

import android.graphics.Bitmap

object ScanSession {

    val bitmaps: MutableList<Bitmap> = mutableListOf()

    var extractedText: String = ""

    fun clear() {
        bitmaps.clear()
        extractedText = ""
    }
}
