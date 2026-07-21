package com.example.tasama.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import java.io.ByteArrayOutputStream

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    return bitmap?.asImageBitmap()
}

actual fun compressImage(bytes: ByteArray, quality: Int): ByteArray {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val outStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
    return outStream.toByteArray()
}

actual fun cropImage(
    originalBytes: ByteArray,
    scale: Float,
    offset: Offset,
    containerSize: IntSize,
    imageSize: IntSize
): ByteArray? {
    return try {
        val options = BitmapFactory.Options().apply {
            inMutable = true
        }
        val originalBitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options) ?: return null
        
        val minContainerDim = minOf(containerSize.width, containerSize.height).toFloat()
        
        // Calculate how much the image is scaled by ContentScale.Fit
        val scaleFit = minOf(
            minContainerDim / imageSize.width,
            minContainerDim / imageSize.height
        )
        
        val finalScale = scale * scaleFit
        
        val cropSizeInImage = minContainerDim / finalScale
        
        val centerXInImage = imageSize.width / 2f - (offset.x / finalScale)
        val centerYInImage = imageSize.height / 2f - (offset.y / finalScale)
        
        val left = (centerXInImage - cropSizeInImage / 2f).toInt()
        val top = (centerYInImage - cropSizeInImage / 2f).toInt()
        val size = cropSizeInImage.toInt()
        
        val sourceRect = Rect(left, top, left + size, top + size)
        val destBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(destBitmap)
        
        canvas.drawBitmap(originalBitmap, sourceRect, Rect(0, 0, size, size), null)
        
        val outStream = ByteArrayOutputStream()
        destBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outStream)
        outStream.toByteArray()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
