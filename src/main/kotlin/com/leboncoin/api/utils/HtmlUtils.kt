package com.leboncoin.api.utils

import kotlinx.serialization.json.*
import org.jsoup.Jsoup

object HtmlUtils {
    fun extractJsonFromScript(html: String, scriptId: String): JsonElement? {
        val doc = Jsoup.parse(html)
        return doc.select("script#$scriptId").firstOrNull()?.data()?.let {
            try {
                val trimmedData = it.trim()
                if (trimmedData.startsWith("{") || trimmedData.startsWith("[")) {
                    Json.parseToJsonElement(trimmedData)
                } else {
                    Logger.warning("Invalid JSON format in script#$scriptId")
                    null
                }
            } catch (e: Exception) {
                Logger.warning("Failed to parse JSON from script#$scriptId: ${e.message}")
                null
            }
        }
    }

    fun extractJsonFromScriptByAttribute(html: String, attributeName: String, attributeValue: String): JsonElement? {
        val doc = Jsoup.parse(html)
        return doc.select("script[$attributeName=$attributeValue]").firstOrNull()?.data()?.let {
            try {
                val trimmedData = it.trim()
                if (trimmedData.startsWith("{") || trimmedData.startsWith("[")) {
                    Json.parseToJsonElement(trimmedData)
                } else {
                    Logger.warning("Invalid JSON format in script[$attributeName=$attributeValue]")
                    null
                }
            } catch (e: Exception) {
                Logger.warning("Failed to parse JSON from script[$attributeName=$attributeValue]: ${e.message}")
                null
            }
        }
    }
}
