package com.example.tasama

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform