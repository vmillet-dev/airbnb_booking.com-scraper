package com.leboncoin.api.scraper

import com.leboncoin.api.models.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.serialization.json.*

class PropertyScraperTest {
    private lateinit var mockEngine: MockEngine
    private lateinit var mockClient: HttpClient
    private lateinit var scraper: PropertyScraper

    @BeforeTest
    fun setup() {
        mockEngine = MockEngine { request ->
            respond(
                content = """{"results": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        mockClient = HttpClient(mockEngine)
        scraper = PropertyScraper(mockClient)
    }

    @AfterTest
    fun tearDown() {
        mockClient.close()
    }

    @Test
    fun testPropertySearch() = runBlocking {
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
                content = """{"results": []}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val mockClient = HttpClient(mockEngine)
        val scraper = PropertyScraper(mockClient)
        val result = scraper.search(request)

        assertNotNull(result)
        assertEquals("Scraping completed successfully", result.message)
        assertNotNull(result.results)
        assertNotNull(result.results.airbnb)
        assertNotNull(result.results.booking)
    }

    @Test
    fun testErrorHandling() = runBlocking {
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
            throw java.net.UnknownHostException("Test error")
        }

        val mockClient = HttpClient(mockEngine)
        val scraper = PropertyScraper(mockClient)
        val result = scraper.search(request)

        assertNotNull(result)
        assertNotNull(result.results.airbnb["cheapest"])
        assertEquals("Unable to connect to host", result.results.airbnb["cheapest"]?.name)
    }
}
