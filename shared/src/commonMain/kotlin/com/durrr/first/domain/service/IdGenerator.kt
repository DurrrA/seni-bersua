package com.durrr.first.domain.service

import kotlin.random.Random

object IdGenerator {
    private const val ALPHABET = "ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    fun newId(prefix: String): String {
        val suffix = buildString {
            repeat(16) {
                append(ALPHABET[Random.nextInt(ALPHABET.length)])
            }
        }
        return prefix + suffix
    }
}
