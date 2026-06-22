package com.example.tasama.data.repository

import dev.gitlive.firebase.storage.Data

actual fun createStorageData(bytes: ByteArray): Data {
    // GitLive Firebase Storage JVM implementation might not support Data(ByteArray) directly if it's not implemented.
    // However, usually it's just a wrapper.
    return Data(bytes)
}
