package com.hrishipvt.scantopdf.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hrishipvt.scantopdf.data.Note

object FirebaseNoteBackup {

    fun backup(note: Note) {

        val uid = FirebaseAuth.getInstance().uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("notes")
            .add(note)
    }
}
