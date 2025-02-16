package com.leboncoin.api.scraper

import com.leboncoin.api.models.*
import com.leboncoin.api.utils.DataUtils
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import com.leboncoin.api.utils.Logger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class PropertyScraper(private val client: HttpClient) {
    private val airbnbScraper = AirbnbScraper(client)
    private val bookingScraper = BookingScraper(client)

    suspend fun search(request: PropertySearchRequest): ScrapingResponse = coroutineScope {
        val airbnbDeferred = async { airbnbScraper.search(request) }
        val bookingDeferred = async { bookingScraper.search(request) }

        val airbnbResult = airbnbDeferred.await()
        val bookingResult = bookingDeferred.await()

        ScrapingResponse(
            results = CombinedResults(
                airbnb = mapOf("cheapest" to airbnbResult.cheapest),
                booking = mapOf("cheapest" to bookingResult.cheapest)
            )
        )
    }
}

class AirbnbScraper(private val client: HttpClient) {
    private suspend fun fetchListingsHtml(url: String): String? {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Accept-Language" to "en-US,en;q=0.9",
            "Accept-Encoding" to "gzip, deflate, br"
        )
        
        try {
            val response = client.get(url) {
                headers.forEach { (key, value) -> 
                    header(key, value)
                }
                timeout {
                    requestTimeoutMillis = 10000
                }
            }
            if (response.status.value in 200..299) {
                return response.bodyAsText()
            }
            Logger.warning("Request failed with status ${response.status.value}")
        } catch (e: Exception) {
            when (e) {
                is io.ktor.client.plugins.HttpRequestTimeoutException ->
                    Logger.warning("Request timed out after 10 seconds - no content received")
                else -> Logger.error("Request failed: ${e.message}")
            }
        }
        return null
    }

    private fun extractListingData(html: String): List<PropertyListing> {
        val doc = Jsoup.parse(html)
        val listings = mutableListOf<PropertyListing>()
        
        // Method 1: Try official JSON data source
        doc.select("script#data-deferred-state-0").firstOrNull()?.let { script ->
            try {
                val scriptData = Json.parseToJsonElement(script.data())
                val results = DataUtils.safeGetFromJson(
                    scriptData,
                    listOf("niobeMinimalClientData")
                )?.jsonArray?.firstOrNull { it is JsonArray && it.size > 1 }?.let { item ->
                    DataUtils.safeGetFromJson(
                        (item as JsonArray)[1],
                        listOf("data", "presentation", "staysSearch", "results", "searchResults")
                    )?.jsonArray
                }

                results?.forEach { result ->
                    if (result.jsonObject["__typename"]?.jsonPrimitive?.content == "StaySearchResult") {
                        try {
                            val listing = result.jsonObject["listing"]?.jsonObject
                            val totalPriceStr = DataUtils.findNestedAttribute(
                                result,
                                listOf("secondaryLine", "price")
                            )?.jsonPrimitive?.content

                            val numericPrice = DataUtils.parsePrice(totalPriceStr)
                            val discountedPrice = if (numericPrice != Double.POSITIVE_INFINITY) {
                                numericPrice * 0.85
                            } else {
                                numericPrice
                            }

                            val currencySymbol = if (totalPriceStr?.contains("€") == true) "€" else "$"
                            val formattedDiscountedPrice = if (discountedPrice != Double.POSITIVE_INFINITY) {
                                "$currencySymbol${String.format("%.2f", discountedPrice)}"
                            } else {
                                totalPriceStr ?: "N/A"
                            }

                            listings.add(
                                PropertyListing(
                                    listingId = listing?.get("id")?.jsonPrimitive?.content ?: "",
                                    listingType = listing?.get("listingObjType")?.jsonPrimitive?.content,
                                    name = listing?.get("name")?.jsonPrimitive?.content ?: "",
                                    title = listing?.get("title")?.jsonPrimitive?.content ?: "",
                                    averageRating = result.jsonObject["avgRatingLocalized"]?.jsonPrimitive?.content ?: "0.0",
                                    discountedPrice = "",
                                    originalPrice = "",
                                    totalPrice = formattedDiscountedPrice,
                                    picture = result.jsonObject["contextualPictures"]?.jsonArray?.firstOrNull()
                                        ?.jsonObject?.get("picture")?.jsonPrimitive?.content ?: "",
                                    website = "airbnb",
                                    price = discountedPrice
                                )
                            )
                        } catch (e: Exception) {
                            Logger.warning("Error processing listing: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.warning("Failed to parse official data source: ${e.message}")
            }
        }
        
        // Method 2: Search for alternative data patterns
        if (listings.isEmpty()) {
            doc.select("script").forEach { script ->
                if (script.data().contains("niobeMinimalClientData")) {
                    try {
                        val scriptData = Json.parseToJsonElement(script.data())
                        extractListingData(scriptData.toString()) // Recursive call with new data
                    } catch (e: Exception) {
                        Logger.warning("Failed to parse alternative data source: ${e.message}")
                    }
                }
            }
        }
        
        // Method 3: Fallback to HTML parsing
        if (listings.isEmpty()) {
            doc.select("[data-testid=card-container]").forEach { card ->
                try {
                    val priceStr = card.select("._1jo4hgw").text()
                    listings.add(
                        PropertyListing(
                            listingId = card.attr("data-id"),
                            name = card.select("[data-testid=listing-card-title]").text(),
                            title = card.select("[data-testid=listing-card-title]").text(),
                            averageRating = "0.0",
                            totalPrice = priceStr,
                            picture = card.select("img").attr("src"),
                            website = "airbnb",
                            price = DataUtils.parsePrice(priceStr)
                        )
                    )
                } catch (e: Exception) {
                    Logger.warning("HTML fallback parse error: ${e.message}")
                }
            }
        }
        
        return listings
    }

    private suspend fun enhancedFetchListings(url: String): List<PropertyListing> {
        try {
            val html = fetchListingsHtml(url)
            return if (html != null) extractListingData(html) else emptyList()
        } catch (e: Exception) {
            Logger.error("Critical fetch failure: ${e.message}")
            return emptyList()
        }
    }

    suspend fun search(request: PropertySearchRequest): PropertyResult = coroutineScope {
        try {
            // Base URL and common parameters
            val baseUrl = "https://www.airbnb.es/s/${URLEncoder.encode(request.destination.trim().trimEnd('`'), StandardCharsets.UTF_8)}/homes?"
            
            val commonParams = mapOf(
                "refinement_paths[]" to "/homes",
                "flexible_trip_lengths[]" to "one_week",
                "price_filter_input_type" to "0",
                "channel" to "EXPLORE",
                "source" to "structured_search_input_header",
                "search_type" to "filter_change",
                "search_mode" to "regular_search",
                "date_picker_type" to "calendar"
            )

            val queryParams = commonParams.toMutableMap()
            val selectedFilterOrder = mutableListOf<String>()

            // Add dates
            queryParams["checkin"] = request.checkIn.date
            queryParams["checkout"] = request.checkOut.date

            // Add guests
            queryParams["adults"] = request.guests.adults.toString()
            queryParams["children"] = request.guests.children.toString()
            if (request.guests.pets > 0) {
                queryParams["pets"] = request.guests.pets.toString()
            }

            // Property Types
            val propertyTypeMapping = mapOf(
                "apartment" to "3",
                "house" to "1",
                "guesthouse" to "2",
                "hotel" to "4"
            )

            request.propertyType.forEach { propType ->
                val propTypeLower = propType.lowercase()
                propertyTypeMapping[propTypeLower]?.let { propId ->
                    queryParams["l2_property_type_ids[]"] = propId
                    selectedFilterOrder.add("l2_property_type_ids:$propId")
                }
            }

            // Add bedrooms
            if (request.bedrooms > 0) {
                queryParams["min_bedrooms"] = request.bedrooms.toString()
                selectedFilterOrder.add("min_bedrooms:${request.bedrooms}")
            }

            // Add pool
            if (request.hasPool) {
                queryParams["amenities[]"] = "7"
                selectedFilterOrder.add("amenities:7")
            }

            // Add bathrooms
            if (request.bathrooms > 0) {
                queryParams["min_bathrooms"] = request.bathrooms.toString()
                selectedFilterOrder.add("min_bathrooms:${request.bathrooms}")
            }

            // Add selected filter order
            if (selectedFilterOrder.isNotEmpty()) {
                queryParams["selected_filter_order[]"] = selectedFilterOrder.joinToString(";")
            }

            // Build URLs
            val originalUrl = baseUrl + queryParams.entries.joinToString("&") { (key, value) ->
                "$key=$value"
            }
            val cursorUrl = "$originalUrl&cursor=eyJzZWN0aW9uX29mZnNldCI6MCwiaXRlbXNfb2Zmc2V0IjoxOCwidmVyc2lvbiI6MX0%3D"

            // Fetch from multiple pages concurrently
            val results = listOf(
                async { enhancedFetchListings(originalUrl) },
                async { enhancedFetchListings(cursorUrl) }
            ).awaitAll().flatten()

            // Find best options
            val validListings = results.filter { it.price != Double.POSITIVE_INFINITY }
            val cheapest = validListings.minByOrNull { it.price }

            if (cheapest != null) {
                PropertyResult(cheapest = cheapest.copy(
                    listingUrl = "https://www.airbnb.es/rooms/${cheapest.listingId}"
                ))
            } else {
                PropertyResult(
                    cheapest = PropertyListing(
                        listingId = "",
                        name = "No listings found",
                        title = "No listings found",
                        averageRating = "0.0",
                        totalPrice = "0",
                        picture = "",
                        website = "airbnb",
                        price = 0.0,
                        listingUrl = null
                    )
                )
            }
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is java.net.UnknownHostException -> "Network error: Unable to connect to host"
                is java.net.SocketTimeoutException -> "Network error: Connection timed out"
                is io.ktor.client.plugins.HttpRequestTimeoutException -> "Network error: Request timed out"
                else -> "Error: ${e.message}"
            }
            Logger.error("Critical error in bot execution: $errorMessage")
            PropertyResult(
                cheapest = PropertyListing(
                    listingId = "",
                    name = errorMessage,
                    title = "Error occurred",
                    averageRating = "0.0",
                    totalPrice = "0",
                    picture = "",
                    website = "airbnb",
                    price = 0.0,
                    listingUrl = null
                )
            )
        }
    }

    private suspend fun fetchListings(url: String): List<PropertyListing> {
        try {
            val html = fetchHtml(url)
            return extractListingData(html)
        } catch (e: Exception) {
            Logger.error("Critical fetch failure: ${e.message}")
            return emptyList()
        }
    }

    private suspend fun fetchHtml(url: String, maxRetries: Int = 3): String {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                val response = client.get(url) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    header("Accept-Language", "en-US,en;q=0.9")
                    header("Accept-Encoding", "gzip, deflate, br")
                    // Timeout handled by HttpClient configuration
                }
                if (response.status.value in 200..299) {
                    return response.bodyAsText()
                }
                Logger.warning("Request failed with status ${response.status.value} (attempt ${attempt + 1}/$maxRetries)")
                lastException = Exception("HTTP ${response.status.value}")
            } catch (e: Exception) {
                lastException = e
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "Unable to connect to host"
                    is java.net.SocketTimeoutException -> "Connection timed out"
                    is io.ktor.client.plugins.HttpRequestTimeoutException -> "Request timed out"
                    else -> e.message ?: "Unknown error"
                }
                Logger.warning("Request failed: $errorMessage (attempt ${attempt + 1}/$maxRetries)")
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay((attempt + 1) * 1000L) // Exponential backoff
                }
            }
        }
        throw lastException ?: Exception("Failed to fetch HTML after $maxRetries attempts")
    }

    private fun extractListingData(html: String): List<PropertyListing> {
        val doc = Jsoup.parse(html)
        val listings = mutableListOf<PropertyListing>()
        
        // Method 1: Try official JSON data source
        doc.select("script#data-deferred-state-0").firstOrNull()?.let { script ->
            try {
                val scriptData = Json.parseToJsonElement(script.data())
                extractFromScriptData(scriptData, listings)
            } catch (e: Exception) {
                Logger.warning("Failed to parse official data source: ${e.message}")
            }
        }
        
        // Method 2: Search for alternative data patterns
        if (listings.isEmpty()) {
            doc.select("script").forEach { script ->
                if (script.data().contains("niobeMinimalClientData")) {
                    try {
                        val scriptData = Json.parseToJsonElement(script.data())
                        extractFromScriptData(scriptData, listings)
                    } catch (e: Exception) {
                        Logger.warning("Failed to parse alternative data source: ${e.message}")
                    }
                }
            }
        }
        
        // Method 3: Fallback to HTML parsing
        if (listings.isEmpty()) {
            doc.select("[data-testid=card-container]").forEach { card ->
                try {
                    listings.add(
                        PropertyListing(
                            listingId = card.attr("data-id"),
                            name = card.select("[data-testid=listing-card-title]").text(),
                            title = card.select("[data-testid=listing-card-title]").text(),
                            averageRating = "0.0",
                            totalPrice = card.select("._1jo4hgw").text(),
                            picture = card.select("img").attr("src"),
                            website = "airbnb",
                            price = DataUtils.parsePrice(card.select("._1jo4hgw").text())
                        )
                    )
                } catch (e: Exception) {
                    Logger.warning("HTML fallback parse error: ${e.message}")
                }
            }
        }
        
        return listings
    }

    private fun extractFromScriptData(scriptData: JsonElement, listings: MutableList<PropertyListing>) {
        DataUtils.safeGetFromJson(scriptData, listOf("niobeMinimalClientData"))?.jsonArray?.forEach { item ->
            if (item is JsonArray && item.size > 1) {
                val results = DataUtils.safeGetFromJson(
                    item[1],
                    listOf("data", "presentation", "staysSearch", "results", "searchResults")
                )
                results?.jsonArray?.forEach { result ->
                    if (result.jsonObject["__typename"]?.jsonPrimitive?.content == "StaySearchResult") {
                        try {
                            val listing = result.jsonObject["listing"]?.jsonObject
                            val totalPriceStr = DataUtils.findNestedAttribute(
                                result,
                                listOf("secondaryLine", "price")
                            )?.jsonPrimitive?.content
                            
                            val numericPrice = DataUtils.parsePrice(totalPriceStr)
                            val discountedPrice = if (numericPrice != Double.POSITIVE_INFINITY) {
                                numericPrice * 0.85
                            } else {
                                numericPrice
                            }
                            
                            val currencySymbol = if (totalPriceStr?.contains("€") == true) "€" else "$"
                            val formattedDiscountedPrice = if (discountedPrice != Double.POSITIVE_INFINITY) {
                                "$currencySymbol${String.format("%.2f", discountedPrice)}"
                            } else {
                                totalPriceStr ?: "N/A"
                            }
                            
                            listings.add(
                                PropertyListing(
                                    listingId = listing?.get("id")?.jsonPrimitive?.content ?: "",
                                    listingType = listing?.get("listingObjType")?.jsonPrimitive?.content,
                                    name = listing?.get("name")?.jsonPrimitive?.content ?: "",
                                    title = listing?.get("title")?.jsonPrimitive?.content ?: "",
                                    averageRating = result.jsonObject["avgRatingLocalized"]?.jsonPrimitive?.content ?: "0.0",
                                    discountedPrice = "",
                                    originalPrice = "",
                                    totalPrice = formattedDiscountedPrice,
                                    picture = result.jsonObject["contextualPictures"]?.jsonArray?.firstOrNull()
                                        ?.jsonObject?.get("picture")?.jsonPrimitive?.content ?: "",
                                    website = "airbnb",
                                    price = discountedPrice
                                )
                            )
                        } catch (e: Exception) {
                            Logger.warning("Error processing listing: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun buildBaseUrl(request: PropertySearchRequest): String {
        val destination = request.destination.trim().trimEnd('`')
        return "https://www.airbnb.es/s/${URLEncoder.encode(destination, StandardCharsets.UTF_8)}/homes?"
    }

    private fun buildSearchUrl(baseUrl: String, request: PropertySearchRequest): String {
        val params = mutableMapOf(
            "refinement_paths[]" to "/homes",
            "flexible_trip_lengths[]" to "one_week",
            "price_filter_input_type" to "0",
            "channel" to "EXPLORE",
            "source" to "structured_search_input_header",
            "search_type" to "filter_change",
            "search_mode" to "regular_search",
            "date_picker_type" to "calendar",
            "checkin" to request.checkIn.date,
            "checkout" to request.checkOut.date,
            "adults" to request.guests.adults.toString(),
            "children" to (request.guests.children ?: 0).toString()
        )
        
        if (request.guests.pets ?: 0 > 0) {
            params["pets"] = request.guests.pets.toString()
        }
        
        val propertyTypeMapping = mapOf(
            "apartment" to "3",
            "house" to "1",
            "guesthouse" to "2",
            "hotel" to "4"
        )
        
        val selectedFilterOrder = mutableListOf<String>()
        
        request.propertyType.forEach { type ->
            val propTypeLower = type.lowercase()
            propertyTypeMapping[propTypeLower]?.let { propId ->
                params["l2_property_type_ids[]"] = propId
                selectedFilterOrder.add("l2_property_type_ids:$propId")
            }
        }
        
        if (request.bedrooms > 0) {
            params["min_bedrooms"] = request.bedrooms.toString()
            selectedFilterOrder.add("min_bedrooms:${request.bedrooms}")
        }
        
        if (request.hasPool) {
            params["amenities[]"] = "7"
            selectedFilterOrder.add("amenities:7")
        }
        
        if (request.bathrooms > 0) {
            params["min_bathrooms"] = request.bathrooms.toString()
            selectedFilterOrder.add("min_bathrooms:${request.bathrooms}")
        }
        
        if (selectedFilterOrder.isNotEmpty()) {
            params["selected_filter_order[]"] = selectedFilterOrder.joinToString(";")
        }
        
        return buildString {
            append(baseUrl)
            append(params.entries.joinToString("&") { (key, value) ->
                "$key=$value"
            })
        }
    }
}

class BookingScraper(private val client: HttpClient) {
    // Logger is handled by the Logger object
    
    suspend fun search(request: PropertySearchRequest): PropertyResult = try {
        val baseUrl = "https://www.booking.com/searchresults.html?aid=817353&"
        val queryParams = buildQueryParams(request)
        val finalUrl = baseUrl + queryParams + "&selected_currency=EUR"
        
        // Step 1: Fetch HTML content
        val html = fetchHtml(finalUrl)
        
        // Step 2: Parse HTML and extract results
        val listings = if (html != null) {
            parseHtmlAndExtractResults(html)
        } else {
            emptyList()
        }
        
        // Find best options
        val validListings = listings.filter { it.price != Double.POSITIVE_INFINITY }
        val cheapest = validListings.minByOrNull { it.price }
        
        if (cheapest != null) {
            val listingId = cheapest.listingId
            val listingUrl = findLinkWithListingId(html ?: "", listingId)
            PropertyResult(cheapest = cheapest.copy(listingUrl = listingUrl))
        } else {
            PropertyResult(
                cheapest = PropertyListing(
                    listingId = "",
                    name = "No listings found",
                    title = "No listings found",
                    averageRating = "0.0",
                    totalPrice = "0",
                    picture = "",
                    website = "booking",
                    price = 0.0
                )
            )
        }
    } catch (e: Exception) {
        Logger.error("Error: ${e.message}")
        PropertyResult(
            cheapest = PropertyListing(
                listingId = "",
                name = "Error: ${e.message}",
                title = "Error occurred",
                averageRating = "0.0",
                totalPrice = "0",
                picture = "",
                website = "booking",
                price = 0.0
            )
        )
    }

    private suspend fun fetchHtml(url: String, maxRetries: Int = 3): String? {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                val response = client.get(url) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    // Timeout handled by HttpClient configuration
                }
                if (response.status.value in 200..299) {
                    return response.bodyAsText()
                }
                Logger.warning("Request failed with status ${response.status.value} (attempt ${attempt + 1}/$maxRetries)")
                lastException = Exception("HTTP ${response.status.value}")
            } catch (e: Exception) {
                lastException = e
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "Unable to connect to host"
                    is java.net.SocketTimeoutException -> "Connection timed out"
                    is io.ktor.client.plugins.HttpRequestTimeoutException -> "Request timed out"
                    else -> e.message ?: "Unknown error"
                }
                Logger.warning("Request failed: $errorMessage (attempt ${attempt + 1}/$maxRetries)")
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay((attempt + 1) * 1000L) // Exponential backoff
                }
            }
        }
        Logger.error("Failed to fetch HTML after $maxRetries attempts: ${lastException?.message}")
        return null
    }

    private fun parseHtmlAndExtractResults(html: String): List<PropertyListing> = buildList {
        val doc = Jsoup.parse(html)
        var extractionSuccess = false
        
        // Method 1: Try Apollo data script
        doc.select("script[data-capla-store-data=apollo]").firstOrNull()?.let { script ->
            if (script.data().contains("\"results\":")) {
                try {
                    val scriptData = Json.parseToJsonElement(script.data().trim())
                    val results = findResultsInJson(scriptData)
                    if (results != null) {
                        Logger.info("Successfully extracted ${results.size} results from Apollo data")
                        extractionSuccess = true
                        results.forEach { result ->
                            try {
                                val basicProperty = result.jsonObject["basicPropertyData"]?.jsonObject ?: return@forEach
                                val priceInfo = result.jsonObject["priceDisplayInfoIrene"]
                                    ?.jsonObject?.get("displayPrice")
                                    ?.jsonObject?.get("amountPerStay")?.jsonObject
                                val reviews = basicProperty["reviews"]?.jsonObject
                                val photos = basicProperty["photos"]?.jsonObject
                                    ?.get("main")?.jsonObject
                                    ?.get("highResUrl")?.jsonObject
                                
                                // Process image URL
                                val relativeUrl = photos?.get("relativeUrl")?.jsonPrimitive?.content
                                val fullImageUrl = relativeUrl?.let {
                                    "https://cf.bstatic.com${it.replace(Regex("max[^/]+"), "max800")}"
                                } ?: ""
                                
                                // Extract tax amount
                                val chargesInfo = findChargesInfo(result)
                                val taxAmount = if (chargesInfo != null) {
                                    val translation = chargesInfo.jsonObject["translation"]?.jsonPrimitive?.content
                                    DataUtils.extractTaxAmount(translation)
                                } else 0.0
                                
                                // Calculate prices
                                val amountUnformatted = priceInfo?.get("amountUnformatted")?.jsonPrimitive?.double ?: 0.0
                                val totalAmount = amountUnformatted + taxAmount
                                val discountedPrice = (totalAmount * 0.85).round(2)
                                val currencySymbol = if (priceInfo?.get("amount")?.jsonPrimitive?.content?.contains("€") == true) "€" else "$"
                                
                                add(
                                    PropertyListing(
                                        listingId = basicProperty["id"]?.jsonPrimitive?.content ?: "",
                                        listingType = null,
                                        name = result.jsonObject["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "",
                                        title = result.jsonObject["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "",
                                        averageRating = "${reviews?.get("totalScore")?.jsonPrimitive?.content ?: 0} (${reviews?.get("reviewsCount")?.jsonPrimitive?.content ?: 0})",
                                        discountedPrice = "",
                                        originalPrice = "",
                                        totalPrice = "$currencySymbol${String.format("%.2f", discountedPrice)}",
                                        picture = fullImageUrl,
                                        website = "booking",
                                        price = discountedPrice
                                    )
                                )
                            } catch (e: Exception) {
                                val errorMessage = when (e) {
                                    is NullPointerException -> "Missing required field in result data"
                                    is IllegalArgumentException -> "Invalid data format: ${e.message}"
                                    else -> "Error processing result: ${e.message}"
                                }
                                Logger.error(errorMessage)
                            }
                        }
                    } else {
                        Logger.warning("No results found in Apollo data")
                    }
                } catch (e: Exception) {
                    Logger.error("Failed to decode Apollo data: ${e.message}")
                }
            }
        }
        
        // Method 2: Try alternative script tags if Apollo data failed
        if (!extractionSuccess) {
            doc.select("script").forEach { script ->
                if (!extractionSuccess && script.data().contains("\"results\":")) {
                    try {
                        val scriptData = Json.parseToJsonElement(script.data().trim())
                        val results = findResultsInJson(scriptData)
                        if (results != null) {
                            Logger.info("Successfully extracted ${results.size} results from alternative script")
                            extractionSuccess = true
                            results.forEach { result ->
                                try {
                                    val basicProperty = result.jsonObject["basicPropertyData"]?.jsonObject ?: return@forEach
                                    val priceInfo = result.jsonObject["priceDisplayInfoIrene"]
                                        ?.jsonObject?.get("displayPrice")
                                        ?.jsonObject?.get("amountPerStay")?.jsonObject
                                    val reviews = basicProperty["reviews"]?.jsonObject
                                    val photos = basicProperty["photos"]?.jsonObject
                                        ?.get("main")?.jsonObject
                                        ?.get("highResUrl")?.jsonObject
                                    
                                    // Process image URL
                                    val relativeUrl = photos?.get("relativeUrl")?.jsonPrimitive?.content
                                    val fullImageUrl = relativeUrl?.let {
                                        "https://cf.bstatic.com${it.replace(Regex("max[^/]+"), "max800")}"
                                    } ?: ""
                                    
                                    // Extract tax amount
                                    val chargesInfo = findChargesInfo(result)
                                    val taxAmount = if (chargesInfo != null) {
                                        val translation = chargesInfo.jsonObject["translation"]?.jsonPrimitive?.content
                                        DataUtils.extractTaxAmount(translation)
                                    } else 0.0
                                    
                                    // Calculate prices
                                    val amountUnformatted = priceInfo?.get("amountUnformatted")?.jsonPrimitive?.double ?: 0.0
                                    val totalAmount = amountUnformatted + taxAmount
                                    val discountedPrice = (totalAmount * 0.85).round(2)
                                    val currencySymbol = if (priceInfo?.get("amount")?.jsonPrimitive?.content?.contains("€") == true) "€" else "$"
                                    
                                    add(
                                        PropertyListing(
                                            listingId = basicProperty["id"]?.jsonPrimitive?.content ?: "",
                                            listingType = null,
                                            name = result.jsonObject["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "",
                                            title = result.jsonObject["displayName"]?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "",
                                            averageRating = "${reviews?.get("totalScore")?.jsonPrimitive?.content ?: 0} (${reviews?.get("reviewsCount")?.jsonPrimitive?.content ?: 0})",
                                            discountedPrice = "",
                                            originalPrice = "",
                                            totalPrice = "$currencySymbol${String.format("%.2f", discountedPrice)}",
                                            picture = fullImageUrl,
                                            website = "booking",
                                            price = discountedPrice
                                        )
                                    )
                                } catch (e: Exception) {
                                    Logger.error("Error processing result: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.warning("Failed to parse alternative script: ${e.message}")
                    }
                }
            }
        }
    }

    private fun findResultsInJson(data: JsonElement): List<JsonElement>? = when (data) {
        is JsonObject -> {
            if ("results" in data && data["results"] is JsonArray) {
                data["results"]?.jsonArray?.toList()
            } else {
                data.values.firstNotNullOfOrNull { findResultsInJson(it) }
            }
        }
        is JsonArray -> {
            data.firstNotNullOfOrNull { findResultsInJson(it) }
        }
        else -> null
    }

    private fun findChargesInfo(obj: JsonElement): JsonObject? = when (obj) {
        is JsonObject -> {
            if ("chargesInfo" in obj) {
                obj["chargesInfo"]?.jsonObject
            } else {
                obj.values.firstNotNullOfOrNull { findChargesInfo(it) }
            }
        }
        is JsonArray -> {
            obj.firstNotNullOfOrNull { findChargesInfo(it) }
        }
        else -> null
    }

    private fun findLinkWithListingId(html: String, listingId: String): String? {
        val doc = Jsoup.parse(html)
        val idStr = listingId.toString()
        
        return doc.select("a[href]").firstOrNull { a ->
            val href = a.attr("href")
            val parsedUrl = java.net.URI(href)
            val queryParams = parsedUrl.query?.split("&")?.associate {
                val parts = it.split("=")
                parts[0] to parts.getOrElse(1) { "" }
            } ?: emptyMap()
            
            queryParams.values.any { it.contains(idStr) }
        }?.attr("href")
    }

    private fun buildQueryParams(request: PropertySearchRequest): String {
        val params = mutableMapOf(
            "ss" to request.destination.trim().trimEnd('`'),
            "lang" to "en-us",
            "group_adults" to request.guests.adults.toString(),
            "no_rooms" to "1",
            "group_children" to (request.guests.children ?: 0).toString()
        )
        
        // Check-in date
        val checkinDate = request.checkIn.date
        params["checkin"] = checkinDate
        
        // Check-out date
        val checkoutDate = request.checkOut.date
        params["checkout"] = checkoutDate
        
        // Handle children ages
        if (request.guests.children > 0) {
            request.guests.childrenAges.forEachIndexed { index, age ->
                params["age"] = age.toString()
            }
        }
        
        // Build nflt filters
        val nfltFilters = mutableListOf<String>()
        
        // Pets
        if (request.guests.pets > 0) {
            nfltFilters.add("hotelfacility=4")
        }
        
        // Swimming Pool
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
        
        if (nfltFilters.isNotEmpty()) {
            params["nflt"] = nfltFilters.joinToString(";")
        }
        
        return params.entries.joinToString("&") { (key, value) -> "$key=$value" }
    }
    
    private fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}
