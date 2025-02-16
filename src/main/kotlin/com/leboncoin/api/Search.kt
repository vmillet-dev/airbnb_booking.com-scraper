package com.leboncoin.api

import com.leboncoin.api.client.HttpClient
import com.leboncoin.api.models.*
import com.leboncoin.api.scraper.AirbnbScraper
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class Search {
    private val airbnbScraper = AirbnbScraper(HttpClient.client)
    
    suspend fun searchProperties(request: PropertySearchRequest): ScrapingResponse {
        val airbnbResults = airbnbScraper.search(request)
        return ScrapingResponse(
            message = "Scraping completed successfully",
            results = CombinedResults(
                airbnb = airbnbResults,
                booking = mapOf("cheapest" to null)
            )
        )
    }
}
