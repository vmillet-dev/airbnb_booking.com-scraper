package com.leboncoin.api.utils

import kotlinx.serialization.json.*
import kotlin.test.*

class DataUtilsTest {
    @Test
    fun testParsePriceWithEuropeanFormat() {
        assertEquals(1234.56, DataUtils.parsePrice("1.234,56"))
        assertEquals(1234.56, DataUtils.parsePrice("€1.234,56"))
        assertEquals(1234.0, DataUtils.parsePrice("1.234"))
        assertEquals(1234.56, DataUtils.parsePrice("1,234.56"))
        assertEquals(Double.POSITIVE_INFINITY, DataUtils.parsePrice(""))
        assertEquals(Double.POSITIVE_INFINITY, DataUtils.parsePrice(null))
    }

    @Test
    fun testSafeGet() {
        val data = mapOf(
            "a" to mapOf(
                "b" to mapOf(
                    "c" to "value"
                )
            )
        )
        assertEquals(
            "value",
            DataUtils.safeGet(data, listOf("a", "b", "c"))
        )
        assertNull(DataUtils.safeGet(data, listOf("x", "y")))
    }

    @Test
    fun testExtractTaxAmount() {
        assertEquals(15.0, DataUtils.extractTaxAmount("Tax amount: 15"))
        assertEquals(0.0, DataUtils.extractTaxAmount("No numbers here"))
        assertEquals(0.0, DataUtils.extractTaxAmount(null))
    }

    @Test
    fun testFindNestedAttribute() {
        val json = Json.parseToJsonElement("""
            {
                "data": {
                    "presentation": {
                        "price": "123.45"
                    },
                    "items": [
                        {"price": "99.99"},
                        {"price": "199.99"}
                    ]
                }
            }
        """.trimIndent())
        
        assertEquals(
            "123.45",
            DataUtils.findNestedAttribute(json, listOf("data", "presentation", "price"))?.jsonPrimitive?.content
        )
        assertNull(DataUtils.findNestedAttribute(json, listOf("nonexistent")))
    }
}
