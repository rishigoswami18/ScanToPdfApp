package com.hrishipvt.scantopdf.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.hrishipvt.scantopdf.R
import com.hrishipvt.scantopdf.ui.AiChatActivity
import com.hrishipvt.scantopdf.utils.FirebaseUploadUtils
import java.io.File

class PdfAdapter(
    private val pdfList: List<File>,
    private val onShareClick: (File) -> Unit
) : RecyclerView.Adapter<PdfAdapter.PdfViewHolder>() {

    class PdfViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtName: TextView = itemView.findViewById(R.id.txtPdfName)
        val txtPath: TextView = itemView.findViewById(R.id.txtPdfPath)
        val btnUpload: ImageButton = itemView.findViewById(R.id.btnUpload)
        val btnShare: ImageButton = itemView.findViewById(R.id.btnShare)
        val btnAiAction: ImageButton = itemView.findViewById(R.id.btnAiAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf, parent, false)
        return PdfViewHolder(view)
    }

    override fun onBindViewHolder(holder: PdfViewHolder, position: Int) {
        val file = pdfList[position]
        holder.txtName.text = file.name
        holder.txtPath.text = "${(file.length() / 1024)} KB"

        holder.btnShare.setOnClickListener {
            onShareClick(file)
        }

        holder.btnAiAction.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, AiChatActivity::class.java).apply {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                putExtra("pdf_uri_from_list", uri)
            }
            context.startActivity(intent)
        }

        holder.btnUpload.setOnClickListener {
            val context = holder.itemView.context
            val currentUser = FirebaseAuth.getInstance().currentUser

            if (currentUser == null) {
                Toast.makeText(context, "Please login to upload files", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            FirebaseUploadUtils.uploadPdfToCloud(file.absolutePath) { success, msg ->
                if (success) {
                    Toast.makeText(context, "Uploaded successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error: $msg", Toast.LENGTH_SHORT).show()
                }
            }
        }

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "No PDF viewer found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount(): Int = pdfList.size
}
