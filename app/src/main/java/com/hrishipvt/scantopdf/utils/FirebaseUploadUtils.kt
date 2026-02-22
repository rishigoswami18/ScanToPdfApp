package com.hrishipvt.scantopdf.utils

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.File

object FirebaseUploadUtils {


    fun uploadPdfToCloud(localPath: String, onComplete: (Boolean, String?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onComplete(false, "User not logged in")
            return
        }

        val file = Uri.fromFile(File(localPath))
        val storageRef = FirebaseStorage.getInstance().reference
        // Organize files by User ID for security
        val pdfRef = storageRef.child("users/${user.uid}/pdfs/${file.lastPathSegment}")

        pdfRef.putFile(file)
            .addOnSuccessListener {
                pdfRef.downloadUrl.addOnSuccessListener { url ->
                    onComplete(true, url.toString())
                }
            }
            .addOnFailureListener {
                onComplete(false, it.message)
            }
    }
}