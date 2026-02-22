package com.hrishipvt.scantopdf.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.utils.FirebaseUploadUtils
import java.io.File

class PdfAdapter(
    private val pdfList: List<File>,
    private val onShareClick: (File) -> Unit // ADDED: Lambda for sharing
) : RecyclerView.Adapter<PdfAdapter.PdfViewHolder>() {

    class PdfViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtName: TextView = itemView.findViewById(R.id.txtPdfName)
        val btnUpload: ImageView = itemView.findViewById(R.id.btnUpload)
        val btnShare: android.view.View = itemView.findViewById(R.id.btnShare)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf, parent, false)
        return PdfViewHolder(view)
    }

    override fun onBindViewHolder(holder: PdfViewHolder, position: Int) {
        val file = pdfList[position]
        holder.txtName.text = file.name

        // Handle Share Button Click
        holder.btnShare.setOnClickListener {
            onShareClick(file) // Sends the specific file back to PdfListActivity
        }

        // Existing Upload Logic
        // Updated Upload Logic with Login Check
        holder.btnUpload.setOnClickListener {
            val context = holder.itemView.context
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser

            // Check if user is logged in before attempting upload
            if (currentUser == null) {
                Toast.makeText(context, "Please login to upload files", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            FirebaseUploadUtils.uploadPdfToCloud(file.absolutePath) { success, msg ->
                if (success) {
                    holder.btnUpload.setImageResource(R.drawable.ic_cloud_done)
                    Toast.makeText(context, "Uploaded successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    // This will show your current toast error
                    Toast.makeText(context, "Error: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Existing View/Open Logic
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )


            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")

                // CRITICAL: Grant permission so the PDF viewer can read the file
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                // Ensure the viewer doesn't stay in the history stack
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

                // Required for starting activity from outside an Activity context
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "No PDF viewer found on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount(): Int = pdfList.size
}