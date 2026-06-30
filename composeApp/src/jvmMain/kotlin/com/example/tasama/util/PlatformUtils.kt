package com.example.tasama.util

import coil3.request.ImageRequest

actual fun ImageRequest.Builder.disableHardwareBitmaps(): ImageRequest.Builder = this
