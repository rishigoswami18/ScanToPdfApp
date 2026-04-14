package com.hrishipvt.scantopdf.view

import androidx.annotation.ColorRes
import com.hrishipvt.scantopdf.R

data class PdfTool(
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    @ColorRes val accentColorRes: Int = R.color.primary_warm
)
