package com.durrr.first.domain.service

import kotlin.random.Random

object IdGenerator {
    private const val ALPHABET = "ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private const val CONSONANTS = "BCDFGHJKLMNPQRSTVWXYZ"
    private const val VOWELS = "AEIOU"
    private val itemIdPattern = Regex("^([A-Z0-9]{3})-([A-Z0-9]{3})-(\\d{3})$")
    private val categoryIdPattern = Regex("^CAT-([A-Z0-9]{3})-(\\d{3})$")

    fun newId(prefix: String): String {
        val suffix = buildString {
            repeat(16) {
                append(ALPHABET[Random.nextInt(ALPHABET.length)])
            }
        }
        return prefix + suffix
    }

    /**
     * Build item ID in format XXX-XXX-XXX
     * - 1st segment: product code from item name
     * - 2nd segment: category code from category name
     * - 3rd segment: running number within category (001..999)
     */
    fun newCatalogItemId(
        itemName: String,
        categoryName: String?,
        existingItemIds: Collection<String>,
        existingIdsInCategory: Collection<String>,
    ): String {
        val productCode = buildThreeCharCode(itemName, fallback = "ITM")
        val categoryCode = buildThreeCharCode(categoryName.orEmpty(), fallback = "CAT")
        val usedInCategory = existingIdsInCategory.mapNotNull { parseItemId(it)?.let { parsed ->
            if (parsed.categoryCode == categoryCode) parsed.serial else null
        } }.toSet()

        var serial = ((usedInCategory.maxOrNull() ?: 0) + 1).coerceAtLeast(1)
        while (serial <= 999) {
            val candidate = "$productCode-$categoryCode-${serial.toString().padStart(3, '0')}"
            if (candidate !in existingItemIds) return candidate
            serial++
        }

        for (fallback in 1..999) {
            if (fallback !in usedInCategory) {
                val candidate = "$productCode-$categoryCode-${fallback.toString().padStart(3, '0')}"
                if (candidate !in existingItemIds) return candidate
            }
        }

        // Safety fallback when all 001..999 are occupied for this category.
        return "$productCode-$categoryCode-999"
    }

    /**
     * Build category ID in format CAT-XXX-XXX for new user-defined groups.
     */
    fun newCategoryId(
        categoryName: String,
        existingGroupIds: Collection<String>,
    ): String {
        val categoryCode = buildThreeCharCode(categoryName, fallback = "CAT")
        val usedSerials = existingGroupIds.mapNotNull { id ->
            val match = categoryIdPattern.matchEntire(id) ?: return@mapNotNull null
            val code = match.groupValues[1]
            if (code == categoryCode) {
                match.groupValues[2].toIntOrNull()
            } else {
                null
            }
        }.toSet()
        var serial = ((usedSerials.maxOrNull() ?: 0) + 1).coerceAtLeast(1)
        while (serial <= 999) {
            val candidate = "CAT-$categoryCode-${serial.toString().padStart(3, '0')}"
            if (candidate !in existingGroupIds) return candidate
            serial++
        }
        for (fallback in 1..999) {
            if (fallback !in usedSerials) {
                val candidate = "CAT-$categoryCode-${fallback.toString().padStart(3, '0')}"
                if (candidate !in existingGroupIds) return candidate
            }
        }
        return "CAT-$categoryCode-999"
    }

    fun buildThreeCharCode(
        raw: String,
        fallback: String = "XXX",
    ): String {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return normalize(fallback).padEnd(3, 'X').take(3)

        val first = normalized.first()
        val second = pickPseudoRandomConsonant(normalized)
        val third = findFirstConsonantFromLastSyllable(normalized)
            ?: pickPseudoRandomConsonant(normalized.reversed())
        return "$first$second$third"
    }

    private fun parseItemId(id: String): ParsedItemId? {
        val match = itemIdPattern.matchEntire(id) ?: return null
        val productCode = match.groupValues[1]
        val categoryCode = match.groupValues[2]
        val serial = match.groupValues[3].toIntOrNull() ?: return null
        return ParsedItemId(productCode, categoryCode, serial)
    }

    private fun normalize(value: String): String {
        return value.uppercase()
            .filter { it in 'A'..'Z' || it in '0'..'9' }
    }

    private fun isConsonant(char: Char): Boolean = char in 'A'..'Z' && char !in VOWELS

    private fun pickPseudoRandomConsonant(source: String): Char {
        val consonants = source.filter(::isConsonant)
        if (consonants.isNotEmpty()) {
            val index = pseudoSeed(source) % consonants.length
            return consonants[index]
        }
        return CONSONANTS[pseudoSeed(source) % CONSONANTS.length]
    }

    /**
     * Last syllable approximation:
     * start from char right after the second-last vowel and pick first consonant.
     * Example: MAKANAN -> MA-KA-NAN -> N
     */
    private fun findFirstConsonantFromLastSyllable(source: String): Char? {
        val letters = source.filter { it in 'A'..'Z' }
        if (letters.isBlank()) return null
        val vowelIndexes = letters.mapIndexedNotNull { index, char ->
            if (char in VOWELS) index else null
        }
        val startIndex = if (vowelIndexes.size >= 2) {
            vowelIndexes[vowelIndexes.size - 2] + 1
        } else {
            0
        }
        letters.substring(startIndex).firstOrNull(::isConsonant)?.let { return it }
        return letters.firstOrNull(::isConsonant)
    }

    private fun pseudoSeed(source: String): Int {
        var acc = 0
        source.forEachIndexed { index, char ->
            acc += (index + 3) * char.code
        }
        return if (acc < 0) -acc else acc
    }

    private data class ParsedItemId(
        val productCode: String,
        val categoryCode: String,
        val serial: Int,
    )
}
