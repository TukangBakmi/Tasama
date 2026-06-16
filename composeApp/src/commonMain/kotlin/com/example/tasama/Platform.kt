package com.example.tasama

interface Platform {
    val name: String
    val canExport: Boolean get() = true
}

expect fun getPlatform(): Platform