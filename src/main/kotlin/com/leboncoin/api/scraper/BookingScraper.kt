package com.leboncoin.api.scraper

import com.leboncoin.api.models.*
import com.leboncoin.api.utils.Logger
import com.leboncoin.api.utils.HtmlUtils
import com.leboncoin.api.utils.EmptyListing
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import java.net.URLEncoder
import kotlin.math.round

class BookingScraper(private val client: HttpClient) {
    companion object {
        private const val BASE_URL = "https://www.booking.com/searchresults.html?aid=817353"
        private val HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept-Language" to "en-US,en;q=0.9",
            "Accept-Encoding" to "gzip, deflate, br"
        )
    }

    private suspend fun fetchHtmlFromUrl(url: String): String? {
        return try {
            val response = client.get(url) {
                headers {
                    HEADERS.forEach { (key, value) -> 
                        append(key, value)
                    }
                }
            }
            response.bodyAsText()
        } catch (e: Exception) {
            Logger.error("Request failed: ${e.message}")
            null
        }
    }

    private fun findResultsInJson(data: JsonElement): List<JsonObject>? {
        return when (data) {
            is JsonObject -> {
                if (data.containsKey("results") && data["results"] is JsonArray) {
                    data["results"]?.jsonArray?.mapNotNull { it as? JsonObject }
                } else {
                    data.values.firstNotNullOfOrNull { findResultsInJson(it) }
                }
            }
            is JsonArray -> {
                data.firstNotNullOfOrNull { findResultsInJson(it) }
            }
            else -> null
        }
    }

    private fun extractTaxAmount(translation: String?): Double {
        if (translation.isNullOrEmpty()) return 0.0
        return "\\d+".toRegex().find(translation)?.value?.toDoubleOrNull() ?: 0.0
    }

    private fun findChargesInfo(data: JsonElement): JsonObject? {
        return when (data) {
            is JsonObject -> {
                if (data.containsKey("chargesInfo")) {
                    data["chargesInfo"]?.jsonObject
                } else {
                    data.values.firstNotNullOfOrNull { findChargesInfo(it) }
                }
            }
            is JsonArray -> {
                data.firstNotNullOfOrNull { findChargesInfo(it) }
            }
            else -> null
        }
    }

    private fun findLinkWithListingId(html: String, listingId: String): String? {
        val soup = Jsoup.parse(html)
        return soup.select("a[href]")
            .find { a -> 
                val href = a.attr("href")
                href.contains(listingId)
            }
            ?.attr("href")
    }

    suspend fun search(request: PropertySearchRequest): Map<String, PropertyListing?> {
        try {
            val queryParams = mutableMapOf<String, String>()
            
            // Destination
            queryParams["ss"] = request.destination
            
            // Language
            queryParams["lang"] = "en-us"
            
            // Dates
            queryParams["checkin"] = request.checkIn.date
            queryParams["checkout"] = request.checkOut.date
            
            // Guests
            queryParams["group_adults"] = request.guests.adults.toString()
            queryParams["no_rooms"] = "1"
            queryParams["group_children"] = request.guests.children.toString()
            
            // Initialize nflt filters
            val nfltFilters = mutableListOf<String>()
            
            // Pool
            if (request.hasPool) {
                nfltFilters.add("hotelfacility=433")
            }
            
            // Bedrooms
            if (request.bedrooms > 0) {
                nfltFilters.add("entire_place_bedroom_count=${request.bedrooms}")
            }
            
            // Bathrooms
            if (request.bathrooms > 0) {
                nfltFilters.add("min_bathrooms=${request.bathrooms}")
            }
            
            // Property Types
            val propertyTypeMapping = mapOf(
                "apartment" to "ht_id=201",
                "guesthouse" to "ht_id=216",
                "hotel" to "ht_id=204",
                "house" to "privacy_type=3"
            )
            
            request.propertyType.forEach { type ->
                propertyTypeMapping[type.lowercase()]?.let { nfltFilters.add(it) }
            }
            
            // Add nflt to query params
            if (nfltFilters.isNotEmpty()) {
                queryParams["nflt"] = nfltFilters.joinToString(";")
            }
            
            val queryString = queryParams.entries.joinToString("&") { (key, value) ->
                "$key=${URLEncoder.encode(value, "UTF-8")}"
            }
            
            val finalUrl = "$BASE_URL&$queryString&selected_currency=EUR&ucfs=1&arphpl=1"
            val html = fetchHtmlFromUrl(finalUrl) ?: return mapOf("cheapest" to EmptyListing.create("booking"))
            
            val listings = parseHtmlAndExtractResults(html)
            val cheapest = listings.minByOrNull { it.price }
            
            if (cheapest != null) {
                val listingUrl = findLinkWithListingId(html, cheapest.listingId)
                return mapOf("cheapest" to cheapest.copy(
                    listingUrl = listingUrl,
                    averageRating = cheapest.averageRating.replace(".", ",")
                ))
            }
            
            return mapOf("cheapest" to EmptyListing.create("booking"))
            
        } catch (e: Exception) {
            Logger.error("Error in booking search: ${e.message}")
            return mapOf("cheapest" to PropertyListing(
                listingId = "",
                name = "No listings found",
                title = "No listings found",
                averageRating = "0,0",
                totalPrice = "0",
                picture = "",
                website = "booking",
                price = 0.0,
                listingType = null,
                discountedPrice = "",
                originalPrice = "",
                listingUrl = null
            ))
        }
    }

    private fun parseHtmlAndExtractResults(html: String): List<PropertyListing> {
        val listings = mutableListOf<PropertyListing>()
        val soup = Jsoup.parse(html)
        
        soup.select("script[data-capla-store-data=apollo]").firstOrNull()?.let { script ->
            try {
                val jsonData = Json.parseToJsonElement(script.data())
                val results = findResultsInJson(jsonData)
                
                results?.forEach { result ->
                    try {
                        val basicProperty = result["basicPropertyData"]?.jsonObject
                        val priceInfo = result["priceDisplayInfoIrene"]?.jsonObject
                            ?.get("displayPrice")?.jsonObject
                            ?.get("amountPerStay")?.jsonObject
                        val reviews = basicProperty?.get("reviews")?.jsonObject
                        val photos = basicProperty?.get("photos")?.jsonObject
                            ?.get("main")?.jsonObject
                            ?.get("highResUrl")?.jsonObject
                        
                        val relativeUrl = photos?.get("relativeUrl")?.jsonPrimitive?.content
                        val fullImageUrl = relativeUrl?.let {
                            "https://cf.bstatic.com${it.replace(Regex("max[^/]+"), "max800")}"
                        } ?: ""
                        
                        val chargesInfo = findChargesInfo(result)
                        val taxAmount = extractTaxAmount(chargesInfo?.get("translation")?.jsonPrimitive?.content)
                        
                        val amountUnformatted = priceInfo?.get("amountUnformatted")?.jsonPrimitive?.double ?: 0.0
                        val totalAmount = amountUnformatted + taxAmount
                        val discountedPrice = round(totalAmount * 0.85 * 100) / 100
                        val currencySymbol = if (priceInfo?.get("amount")?.jsonPrimitive?.content?.contains("€") == true) "€" else "$"
                        
                        listings.add(
                            PropertyListing(
                                listingId = basicProperty?.get("id")?.jsonPrimitive?.content ?: "",
                                listingType = null,
                                name = result["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "",
                                title = result["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "",
                                averageRating = "${reviews?.get("totalScore")?.jsonPrimitive?.content?.replace(".", ",") ?: "0,0"} (${reviews?.get("reviewsCount")?.jsonPrimitive?.content ?: "0"})",
                                discountedPrice = "",
                                originalPrice = "",
                                totalPrice = "$currencySymbol${String.format("%.2f", discountedPrice).replace(",", ".")}",
                                picture = fullImageUrl,
                                website = "booking",
                                price = discountedPrice,
                                listingUrl = null
                            )
                        )
                    } catch (e: Exception) {
                        Logger.error("Error processing result: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Logger.error("Error parsing script content: ${e.message}")
            }
        }
        
        return listings
    }
}
