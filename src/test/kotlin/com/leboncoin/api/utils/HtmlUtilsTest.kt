package com.leboncoin.api.utils

import kotlinx.serialization.json.*
import kotlin.test.*

class HtmlUtilsTest {
    @Test
    fun testExtractJsonFromScript() {
        val html = """
            <html>
                <head>
                    <script id="data-deferred-state-0">{"test": "value"}</script>
                </head>
            </html>
        """.trimIndent()
        
        val result = HtmlUtils.extractJsonFromScript(html, "data-deferred-state-0")
        assertNotNull(result)
        assertTrue(result is JsonObject)
        assertEquals("value", result.jsonObject["test"]?.jsonPrimitive?.content)
    }

    @Test
    fun testExtractJsonFromScriptByAttribute() {
        val html = """
            <html>
                <head>
                    <script data-capla-store-data="apollo">{"test": "value"}</script>
                </head>
            </html>
        """.trimIndent()
        
        val result = HtmlUtils.extractJsonFromScriptByAttribute(html, "data-capla-store-data", "apollo")
        assertNotNull(result)
        assertTrue(result is JsonObject)
        assertEquals("value", result.jsonObject["test"]?.jsonPrimitive?.content)
    }

    @Test
    fun testExtractJsonFromScriptWithInvalidJson() {
        val html = """
            <html>
                <head>
                    <script id="data-deferred-state-0">not a json object at all</script>
                </head>
            </html>
        """.trimIndent()
        
        val result = HtmlUtils.extractJsonFromScript(html, "data-deferred-state-0")
        assertNull(result, "Expected null for invalid JSON")
    }

    @Test
    fun testExtractJsonFromScriptWithMissingScript() {
        val html = """
            <html>
                <head>
                    <script id="other-script">{"test": "value"}</script>
                </head>
            </html>
        """.trimIndent()
        
        val result = HtmlUtils.extractJsonFromScript(html, "data-deferred-state-0")
        assertNull(result, "Expected null for missing script")
    }

    @Test
    fun testExtractJsonFromScriptWithEmptyContent() {
        val html = """
            <html>
                <head>
                    <script id="data-deferred-state-0"></script>
                </head>
            </html>
        """.trimIndent()
        
        val result = HtmlUtils.extractJsonFromScript(html, "data-deferred-state-0")
        assertNull(result, "Expected null for empty script content")
    }

    @Test
    fun testExtractJsonFromScriptWithMalformedJson() {
        val html = """
            <html>
                <head>
                    <script id="data-deferred-state-0">{"test": "value",}</script>
                </head>
            </html>
        """.trimIndent()
        
        val result = HtmlUtils.extractJsonFromScript(html, "data-deferred-state-0")
        assertNull(result, "Expected null for malformed JSON")
    }
}
