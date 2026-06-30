package com.example.tasama.util

import coil3.request.ImageRequest
import coil3.request.allowHardware

actual fun ImageRequest.Builder.disableHardwareBitmaps(): ImageRequest.Builder = 
    this.allowHardware(false)
