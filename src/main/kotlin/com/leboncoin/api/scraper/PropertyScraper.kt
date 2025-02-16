package com.leboncoin.api.scraper

import io.ktor.client.*
import com.leboncoin.api.models.*

class PropertyScraper(private val client: HttpClient) {
    suspend fun search(request: PropertySearchRequest): ScrapingResponse {
        val airbnbScraper = AirbnbScraper(client)
        val airbnbResults = airbnbScraper.search(request)
        
        return ScrapingResponse(
            message = "Scraping completed successfully",
            results = CombinedResults(
                airbnb = PropertyResult(cheapest = airbnbResults["cheapest"]),
                booking = PropertyResult(cheapest = null)
            )
        )
    }
}
