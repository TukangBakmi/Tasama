package com.example.tasama.data.service

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.tasama.domain.service.FileService
import java.io.File
import java.io.FileOutputStream

class AndroidFileService(private val context: Context) : FileService {
    override suspend fun saveAndShareFile(fileName: String, content: ByteArray, mimeType: String) {
        val cacheDir = File(context.cacheDir, "exports")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val file = File(cacheDir, fileName)
        FileOutputStream(file).use { it.write(content) }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(intent, "Share Export").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
