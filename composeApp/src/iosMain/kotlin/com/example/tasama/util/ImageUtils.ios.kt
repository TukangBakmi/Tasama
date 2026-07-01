package com.example.tasama.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.*
import platform.posix.memcpy

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

@OptIn(ExperimentalForeignApi::class)
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    val skiaImage = Image.makeFromEncoded(bytes)
    return skiaImage.toComposeImageBitmap()
}

@OptIn(ExperimentalForeignApi::class)
actual fun cropImage(
    originalBytes: ByteArray,
    scale: Float,
    offset: Offset,
    containerSize: IntSize,
    imageSize: IntSize
): ByteArray? {
    val data = originalBytes.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), originalBytes.size.toULong())
    }
    val image = UIImage.imageWithData(data) ?: return null
    val cgImage = image.CGImage ?: return null

    val minContainerDim = minOf(containerSize.width.toFloat(), containerSize.height.toFloat())
    val scaleFit = minOf(
        minContainerDim / imageSize.width,
        minContainerDim / imageSize.height
    )
    val finalScale = scale * scaleFit
    val cropSizeInImage = minContainerDim / finalScale
    
    val centerXInImage = imageSize.width / 2f - (offset.x / finalScale)
    val centerYInImage = imageSize.height / 2f - (offset.y / finalScale)
    
    val left = (centerXInImage - cropSizeInImage / 2f).toDouble()
    val top = (centerYInImage - cropSizeInImage / 2f).toDouble()
    val size = cropSizeInImage.toDouble()

    val cropRect = CGRectMake(left, top, size, size)
    val croppedCgImage = CGImageCreateWithImageInRect(cgImage, cropRect) ?: return null
    val croppedUiImage = UIImage.imageWithCGImage(croppedCgImage)
    
    val croppedData = UIImageJPEGRepresentation(croppedUiImage, 0.9) ?: return null
    
    val result = ByteArray(croppedData.length.toInt())
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), croppedData.bytes, croppedData.length)
    }
    return result
}
