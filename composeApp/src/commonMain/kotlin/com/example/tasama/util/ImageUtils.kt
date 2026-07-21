package com.example.tasama.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

expect fun compressImage(bytes: ByteArray, quality: Int = 80): ByteArray

expect fun cropImage(
    originalBytes: ByteArray,
    scale: Float,
    offset: Offset,
    containerSize: IntSize,
    imageSize: IntSize
): ByteArray?
