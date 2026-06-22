package com.example.tasama.data.repository

import dev.gitlive.firebase.storage.Data

expect fun createStorageData(bytes: ByteArray): Data
