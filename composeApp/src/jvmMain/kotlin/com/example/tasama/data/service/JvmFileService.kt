package com.example.tasama.data.service

import com.example.tasama.domain.service.FileService
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream

class JvmFileService : FileService {
    override suspend fun saveAndShareFile(fileName: String, content: ByteArray, mimeType: String) {
        val userHome = System.getProperty("user.home")
        val exportDir = File(userHome, "TasamaExports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val file = File(exportDir, fileName)
        FileOutputStream(file).use { it.write(content) }

        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (file.exists()) {
                desktop.open(file)
            }
        }
    }
}
