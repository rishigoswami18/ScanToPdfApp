package com.hrishipvt.scantopdf.firebase

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.File

object FirebaseUploadUtils {

    fun uploadPdf(
        pdfFile: File,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onError(Exception("User not logged in"))
            return
        }

        val storage = FirebaseStorage.getInstance()
        val pdfUri = Uri.fromFile(pdfFile)

        val ref = storage.reference
            .child("pdfs")
            .child(user.uid)
            .child(pdfFile.name)

        ref.putFile(pdfUri)
            .addOnSuccessListener {
                ref.downloadUrl
                    .addOnSuccessListener { uri ->
                        onSuccess(uri.toString())
                    }
                    .addOnFailureListener { e ->
                        onError(e)
                    }
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }
}
