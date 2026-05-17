package com.durrr.first.domain.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdGeneratorCatalogFormatTest {
    @Test
    fun buildThreeCharCode_usesRequestedCategoryShape() {
        val code = IdGenerator.buildThreeCharCode("MAKANAN")
        assertEquals(3, code.length)
        assertEquals('M', code[0])
        // Last syllable approximation MA-KA-NAN -> first consonant in last syllable is N.
        assertEquals('N', code[2])
    }

    @Test
    fun newCatalogItemId_usesThreeSegmentPatternAndCategorySequence() {
        val categoryCode = IdGenerator.buildThreeCharCode("Makanan")
        val generated = IdGenerator.newCatalogItemId(
            itemName = "Kentang Mustofa",
            categoryName = "Makanan",
            existingItemIds = listOf(
                "KTN-$categoryCode-001",
                "SAT-$categoryCode-002",
                "ESP-KPT-001",
            ),
            existingIdsInCategory = listOf(
                "KTN-$categoryCode-001",
                "SAT-$categoryCode-002",
            ),
        )
        assertTrue(generated.matches(Regex("^[A-Z0-9]{3}-[A-Z0-9]{3}-\\d{3}$")))
        assertTrue(generated.endsWith("-003"))
    }

    @Test
    fun newCategoryId_usesCatPrefixAndIncrementsPerCategoryCode() {
        val first = IdGenerator.newCategoryId(
            categoryName = "Makanan",
            existingGroupIds = emptyList(),
        )
        val second = IdGenerator.newCategoryId(
            categoryName = "Makanan",
            existingGroupIds = listOf(first),
        )

        assertTrue(first.matches(Regex("^CAT-[A-Z0-9]{3}-001$")))
        assertTrue(second.matches(Regex("^CAT-[A-Z0-9]{3}-002$")))
        assertEquals(first.substring(4, 7), second.substring(4, 7))
    }
}
