package com.leboncoin.api.utils

import kotlin.math.round
import kotlinx.serialization.json.*

fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return round(this * multiplier) / multiplier
}

fun findResultsInJson(json: JsonElement): List<JsonObject>? {
    return when (json) {
        is JsonObject -> {
            if (json.containsKey("results")) {
                json["results"]?.jsonArray?.mapNotNull { it as? JsonObject }
            } else {
                json.values.firstNotNullOfOrNull { findResultsInJson(it) }
            }
        }
        is JsonArray -> {
            json.firstNotNullOfOrNull { findResultsInJson(it) }
        }
        else -> null
    }
}
