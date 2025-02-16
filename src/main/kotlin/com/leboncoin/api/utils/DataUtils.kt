package com.leboncoin.api.utils

import kotlinx.serialization.json.*

object DataUtils {
    fun safeGet(data: Map<*, *>, keys: List<String>, default: Any? = null): Any? {
        var current: Any? = data
        for (key in keys) {
            current = when (current) {
                is Map<*, *> -> (current as Map<*, *>)[key]
                else -> return default
            }
        }
        return current
    }

    fun safeGetFromJson(element: JsonElement?, keys: List<String>, default: JsonElement? = null): JsonElement? {
        var current = element
        for (key in keys) {
            current = when {
                current is JsonObject -> current[key]
                else -> return default
            }
        }
        return current
    }

    fun parsePrice(priceStr: String?): Double {
        if (priceStr.isNullOrEmpty()) return Double.POSITIVE_INFINITY
        
        try {
            val cleanStr = priceStr.replace(Regex("[^\\d.,]"), "")
            if (cleanStr.isEmpty()) return Double.POSITIVE_INFINITY

            // Handle European format with comma as decimal (e.g., "1.234,56")
            return when {
                cleanStr.contains(",") && cleanStr.contains(".") -> {
                    if (cleanStr.indexOf(",") > cleanStr.indexOf(".")) {
                        // European format (e.g., "1.234,56" -> 1234.56)
                        cleanStr.replace(".", "").replace(",", ".").toDouble()
                    } else {
                        // US format with thousands separator (e.g., "1,234.56")
                        cleanStr.replace(",", "").toDouble()
                    }
                }
                cleanStr.contains(",") -> {
                    val parts = cleanStr.split(",")
                    if (parts.size == 2 && parts[1].all { it.isDigit() }) {
                        // Comma as decimal separator (e.g., "1234,56" -> 1234.56)
                        "${parts[0]}.${parts[1]}".toDouble()
                    } else {
                        // Comma as thousands separator
                        cleanStr.replace(",", "").toDouble()
                    }
                }
                cleanStr.contains(".") -> {
                    val parts = cleanStr.split(".")
                    if (parts.size == 2 && parts[1].length == 3) {
                        // Period as thousands separator (e.g., "1.234" -> 1234)
                        cleanStr.replace(".", "").toDouble()
                    } else {
                        // Period as decimal separator
                        cleanStr.toDouble()
                    }
                }
                else -> cleanStr.toDouble()
            }
        } catch (e: Exception) {
            return Double.POSITIVE_INFINITY
        }
    }

    fun findNestedAttribute(data: JsonElement?, keys: List<String>): JsonElement? {
        if (data == null || keys.isEmpty()) return null
        
        return when (data) {
            is JsonArray -> {
                data.firstNotNullOfOrNull { findNestedAttribute(it, keys) }
            }
            is JsonObject -> {
                val key = keys.first()
                if (key in data) {
                    if (keys.size == 1) data[key]
                    else findNestedAttribute(data[key], keys.drop(1))
                } else {
                    data.values.firstNotNullOfOrNull { findNestedAttribute(it, keys) }
                }
            }
            else -> null
        }
    }

    fun extractTaxAmount(translation: String?): Double {
        if (translation.isNullOrEmpty()) return 0.0
        
        return Regex("\\d+").find(translation)?.value?.toDoubleOrNull() ?: 0.0
    }
}
