package com.hrishipvt.scantopdf.utils

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage


import java.io.File

object FirebaseUtils {

    fun uploadPdf(file: File) {
        val ref = FirebaseStorage.getInstance()
            .reference.child("pdfs/${file.name}")

        ref.putFile(Uri.fromFile(file))
    }
}