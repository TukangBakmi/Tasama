package com.example.tasama.data.repository

import dev.gitlive.firebase.storage.Data

actual fun createStorageData(bytes: ByteArray): Data {
    return Data(bytes)
}
