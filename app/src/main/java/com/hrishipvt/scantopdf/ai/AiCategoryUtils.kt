package com.hrishipvt.scantopdf.ai

object AiCategoryUtils {

    fun detect(text: String): String {
        val t = text.lowercase()

        // Using a more structured approach for better maintenance
        return when {
            containsAny(t, "invoice", "gst", "tax", "purchase order") -> "Invoice"
            containsAny(t, "note", "chapter", "lecture", "homework") -> "Notes"
            containsAny(t, "aadhaar", "pan card", "passport", "license") -> "Identity"
            containsAny(t, "bill", "receipt", "total amount", "paid") -> "Bill"
            else -> "Other"
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        // Returns true if any of the provided keywords are found in the text
        return keywords.any { text.contains(it) }
    }
}