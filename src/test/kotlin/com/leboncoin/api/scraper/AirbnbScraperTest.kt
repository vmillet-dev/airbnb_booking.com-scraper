package com.leboncoin.api.scraper

import com.leboncoin.api.models.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class AirbnbScraperTest {
    @Test
    fun testSearch() = runBlocking {
        val request = PropertySearchRequest(
            checkIn = DateInfo("2025-04-20"),
            checkOut = DateInfo("2025-04-28"),
            destination = "France",
            guests = GuestInfo(adults = 2),
            propertyType = listOf("apartment", "house", "hotel"),
            bedrooms = 1,
            bathrooms = 1,
            hasPool = true
        )

        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"niobeMinimalClientData": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val mockClient = HttpClient(mockEngine)
        val scraper = AirbnbScraper(mockClient)
        val result = scraper.search(request)

        assertNotNull(result["cheapest"])
        assertEquals("airbnb", result["cheapest"]?.website)
    }
}
