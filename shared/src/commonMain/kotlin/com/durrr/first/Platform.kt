package com.durrr.first

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform