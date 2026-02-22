package com.hrishipvt.scantopdf.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object AiMetadataRepository {

    fun save(
        name: String,
        category: String,
        text: String,
        pdfUrl: String
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        val db = FirebaseFirestore.getInstance()

        val data = hashMapOf(
            "name" to name,
            "category" to category,
            "text" to text,
            "url" to pdfUrl,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(user.uid)
            .collection("documents")
            .add(data)
    }
}
