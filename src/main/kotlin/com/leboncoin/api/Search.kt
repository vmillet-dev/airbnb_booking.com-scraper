package com.leboncoin.api

import com.leboncoin.api.client.HttpClient
import com.leboncoin.api.models.*
import com.leboncoin.api.scraper.AirbnbScraper
import com.leboncoin.api.scraper.BookingScraper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class Search {
    private val airbnbScraper = AirbnbScraper(HttpClient.client)
    private val bookingScraper = BookingScraper(HttpClient.client)
    
    suspend fun searchProperties(request: PropertySearchRequest): ScrapingResponse {
        return coroutineScope {
            val airbnbResults = async { airbnbScraper.search(request) }
            val bookingResults = async { bookingScraper.search(request) }
            
            ScrapingResponse(
                message = "Scraping completed successfully",
                results = CombinedResults(
                    airbnb = PropertyResult(cheapest = airbnbResults.await()["cheapest"]),
                    booking = PropertyResult(cheapest = bookingResults.await()["cheapest"])
                )
            ).also {
                println(Json {
                    prettyPrint = true
                    encodeDefaults = true
                    explicitNulls = true
                }.encodeToString(it))
            }
        }
    }
}
