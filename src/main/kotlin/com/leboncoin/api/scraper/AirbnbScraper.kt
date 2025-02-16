package com.leboncoin.api.scraper

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.jsoup.Jsoup
import com.leboncoin.api.models.*
import com.leboncoin.api.utils.*

class AirbnbScraper(private val client: HttpClient) {
    companion object {
        private val logger = Logger
        
        private val HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept-Language" to "en-US,en;q=0.9",
            "Accept-Encoding" to "gzip, deflate, br"
        )
        
        private val PROPERTY_TYPE_MAPPING = mapOf(
            "apartment" to "3",
            "house" to "1",
            "guesthouse" to "2",
            "hotel" to "4"
        )
    }
    
    suspend fun search(filters: PropertySearchRequest): Map<String, PropertyListing?> {
        return try {
            val baseUrl = buildUrl(filters)
            val cursorUrl = "$baseUrl&cursor=eyJzZWN0aW9uX29mZnNldCI6MCwiaXRlbXNfb2Zmc2V0IjoxOCwidmVyc2lvbiI6MX0%3D"
            
            coroutineScope {
                val results = listOf(
                    async { fetchListings(baseUrl) },
                    async { fetchListings(cursorUrl) }
                ).awaitAll().flatten()
                
                val cheapest = results.minByOrNull { it.price }
                if (cheapest != null) {
                    mapOf("cheapest" to cheapest)
                } else {
                    mapOf("cheapest" to PropertyListing(
                        listingId = "",
                        name = "No listings found",
                        title = "No listings found",
                        averageRating = "0.0",
                        totalPrice = "0",
                        picture = "",
                        website = "airbnb",
                        price = 0.0
                    ))
                }
            }
        } catch (e: Exception) {
            logger.error("Critical error in bot execution: ${e.message}")
            mapOf("error" to PropertyListing(
                listingId = "",
                name = "Error: ${e.message}",
                title = "Error: ${e.message}",
                averageRating = "0.0",
                totalPrice = "0",
                picture = "",
                website = "airbnb",
                price = 0.0
            ))
        }
    }
    
    private suspend fun fetchListings(url: String): List<PropertyListing> {
        return try {
            val html = fetchListingsHtml(url)
            if (html != null) {
                extractListingData(html)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.error("Critical fetch failure: ${e.message}")
            emptyList()
        }
    }
    
    private suspend fun fetchListingsHtml(url: String): String? {
        return try {
            client.get(url) {
                HEADERS.forEach { (key, value) ->
                    headers.append(key, value)
                }
                timeout {
                    requestTimeoutMillis = 10000
                    connectTimeoutMillis = 10000
                    socketTimeoutMillis = 10000
                }
            }.bodyAsText()
        } catch (e: Exception) {
            when (e) {
                is io.ktor.client.plugins.HttpRequestTimeoutException -> {
                    logger.warning("Request timed out after 10 seconds - no content received")
                }
                else -> {
                    logger.error("Request failed: ${e.message}")
                }
            }
            null
        }
    }
    
    private fun extractListingData(html: String): List<PropertyListing> {
        val soup = Jsoup.parse(html)
        var listingData = emptyList<PropertyListing>()
        
        // Method 1: Try official JSON data source
        soup.getElementById("data-deferred-state-0")?.let { script ->
            try {
                val scriptData = Json.parseToJsonElement(script.data())
                listingData = extractFromNiobeData(scriptData)
                if (listingData.isNotEmpty()) return listingData
            } catch (e: Exception) {
                logger.warning("Failed to parse official JSON data: ${e.message}")
            }
        }
        
        // Method 2: Search for alternative data patterns
        if (listingData.isEmpty()) {
            soup.select("script").find { it.data().contains("niobeMinimalClientData") }?.let { script ->
                try {
                    val scriptData = Json.parseToJsonElement(script.data())
                    listingData = extractFromNiobeData(scriptData)
                    if (listingData.isNotEmpty()) return listingData
                } catch (e: Exception) {
                    logger.warning("Failed to parse alternative JSON data: ${e.message}")
                }
            }
        }
        
        // Method 3: Fallback to HTML parsing
        if (listingData.isEmpty()) {
            listingData = extractFromHtml(soup)
        }
        
        return listingData
    }
    
    private fun extractFromNiobeData(scriptData: JsonElement): List<PropertyListing> {
        val listings = mutableListOf<PropertyListing>()
        
        try {
            for (item in DataUtils.safeGet(scriptData.jsonObject, listOf("niobeMinimalClientData")) as? JsonArray ?: return emptyList()) {
                if (item !is JsonArray || item.size <= 1) continue
                
                val results = DataUtils.safeGet(item[1].jsonObject, 
                    listOf("data", "presentation", "staysSearch", "results", "searchResults")
                ) as? JsonArray ?: continue
                
                for (result in results) {
                    if (result !is JsonObject) continue
                    if (result["__typename"]?.jsonPrimitive?.content != "StaySearchResult") continue
                    
                    try {
                        val listing = result["listing"]?.jsonObject ?: continue
                        val totalPriceStr = findNestedAttribute(result, listOf("secondaryLine", "price"))
                            ?.jsonPrimitive?.content
                        
                        if (totalPriceStr == null) {
                            logger.warning("Price not found in the JSON structure")
                            continue
                        }
                        
                        val numericPrice = parsePrice(totalPriceStr)
                        val discountedPrice = if (numericPrice != Double.POSITIVE_INFINITY) {
                            numericPrice * 0.85
                        } else {
                            numericPrice
                        }
                        
                        val currencySymbol = if (totalPriceStr.contains("€")) "€" else "$"
                        val formattedDiscountedPrice = if (discountedPrice != Double.POSITIVE_INFINITY) {
                            "$currencySymbol%.2f".format(discountedPrice)
                        } else {
                            totalPriceStr.ifEmpty { "N/A" }
                        }
                        
                        val pictureUrl = result["contextualPictures"]?.jsonArray
                            ?.firstOrNull()
                            ?.jsonObject
                            ?.get("picture")
                            ?.jsonPrimitive
                            ?.content
                        
                        listings.add(PropertyListing(
                            listingId = listing["id"]?.jsonPrimitive?.content ?: "",
                            listingType = listing["listingObjType"]?.jsonPrimitive?.content,
                            name = listing["name"]?.jsonPrimitive?.content ?: "",
                            title = listing["title"]?.jsonPrimitive?.content ?: "",
                            averageRating = result["avgRatingLocalized"]?.jsonPrimitive?.content ?: "0.0",
                            discountedPrice = "",
                            originalPrice = "",
                            totalPrice = formattedDiscountedPrice,
                            picture = pictureUrl ?: "",
                            website = "airbnb",
                            price = discountedPrice,
                            listingUrl = "https://www.airbnb.es/rooms/${listing["id"]?.jsonPrimitive?.content}"
                        ))
                    } catch (e: Exception) {
                        logger.warning("Error processing listing: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error extracting from Niobe data: ${e.message}")
        }
        
        return listings
    }
    
    private fun extractFromHtml(soup: org.jsoup.nodes.Document): List<PropertyListing> {
        val listings = mutableListOf<PropertyListing>()
        
        try {
            for (card in soup.select("[data-testid=card-container]")) {
                try {
                    val listingId = card.attr("data-id")
                    val name = card.select("[data-testid=listing-card-title]").firstOrNull()?.text() ?: ""
                    val totalPrice = card.select("._1jo4hgw").firstOrNull()?.text() ?: ""
                    val url = card.select("a").firstOrNull()?.attr("href") ?: ""
                    
                    listings.add(PropertyListing(
                        listingId = listingId,
                        name = name,
                        title = name,
                        averageRating = "0.0",
                        totalPrice = totalPrice,
                        picture = "",
                        website = "airbnb",
                        price = parsePrice(totalPrice),
                        listingUrl = if (url.startsWith("http")) url else "https://www.airbnb.es$url"
                    ))
                } catch (e: Exception) {
                    logger.warning("HTML fallback parse error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing HTML: ${e.message}")
        }
        
        return listings
    }
    
    private fun parsePrice(priceStr: String?): Double {
        TODO("Not yet implemented")
    }
    
    private fun findNestedAttribute(data: JsonElement?, keys: List<String>): JsonElement? {
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
    
    private fun buildUrl(filters: PropertySearchRequest): String {
        // Base URL and destination
        val baseUrl = "https://www.airbnb.es/s/${filters.destination.trim('`')}/homes?"
        
        // Initialize query parameters with common parameters
        val queryParams = mutableMapOf<String, List<String>>(
            "refinement_paths[]" to listOf("/homes"),
            "flexible_trip_lengths[]" to listOf("one_week"),
            "price_filter_input_type" to listOf("0"),
            "channel" to listOf("EXPLORE"),
            "source" to listOf("structured_search_input_header"),
            "search_type" to listOf("filter_change"),
            "search_mode" to listOf("regular_search"),
            "date_picker_type" to listOf("calendar")
        )
        
        // Add check-in and check-out dates
        filters.checkIn.date?.let { queryParams["checkin"] = listOf(it) }
        filters.checkOut.date?.let { queryParams["checkout"] = listOf(it) }
        
        // Add guests information
        queryParams["adults"] = listOf(filters.guests.adults.toString())
        if (filters.guests.children > 0) {
            queryParams["children"] = listOf(filters.guests.children.toString())
        }
        if (filters.guests.pets > 0) {
            queryParams["pets"] = listOf(filters.guests.pets.toString())
        }
        
        // Property Types
        val selectedFilterOrder = mutableListOf<String>()
        filters.propertyType.forEach { propType ->
            PROPERTY_TYPE_MAPPING[propType.lowercase()]?.let { propId ->
                queryParams.getOrPut("l2_property_type_ids[]") { mutableListOf() }
                    .let { list ->
                        (list as MutableList<String>).add(propId)
                    }
                selectedFilterOrder.add("l2_property_type_ids%3A$propId")
            }
        }
        
        // Bedrooms
        if (filters.bedrooms > 0) {
            queryParams["min_bedrooms"] = listOf(filters.bedrooms.toString())
            selectedFilterOrder.add("min_bedrooms%3A${filters.bedrooms}")
        }
        
        // Pool
        if (filters.hasPool) {
            queryParams.getOrPut("amenities[]") { mutableListOf() }
                .let { list ->
                    (list as MutableList<String>).add("7")
                }
            selectedFilterOrder.add("amenities%3A7")
        }
        
        // Bathrooms
        if (filters.bathrooms > 0) {
            queryParams["min_bathrooms"] = listOf(filters.bathrooms.toString())
            selectedFilterOrder.add("min_bathrooms%3A${filters.bathrooms}")
        }
        
        // Add selected filter order
        if (selectedFilterOrder.isNotEmpty()) {
            queryParams["selected_filter_order[]"] = selectedFilterOrder
        }
        
        // Build the final URL with encoded parameters
        return buildString {
            append(baseUrl)
            queryParams.forEach { (key, values) ->
                values.forEach { value ->
                    if (length > baseUrl.length) append('&')
                    append(key)
                    append('=')
                    append(value.encodeURLParameter())
                }
            }
        }
    }
    
    private fun String.encodeURLParameter(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
            .replace("+", "%20")
            .replace("%2F", "/")
            .replace("%25", "%")
    }
}
