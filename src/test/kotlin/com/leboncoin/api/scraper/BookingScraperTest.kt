package com.leboncoin.api.scraper

import com.leboncoin.api.models.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class BookingScraperTest {
    private lateinit var mockEngine: MockEngine
    private lateinit var mockClient: HttpClient
    private lateinit var scraper: BookingScraper

    @BeforeTest
    fun setup() {
        mockEngine = MockEngine { request ->
            respond(
                content = """
                    {"results": [
                        {
                            "basicPropertyData": {
                                "id": "123456",
                                "reviews": {
                                    "totalScore": "8.5",
                                    "reviewsCount": "100"
                                },
                                "photos": {
                                    "main": {
                                        "highResUrl": {
                                            "relativeUrl": "/path/to/image.jpg"
                                        }
                                    }
                                }
                            },
                            "displayName": {
                                "text": "Test Hotel"
                            },
                            "priceDisplayInfoIrene": {
                                "displayPrice": {
                                    "amountPerStay": {
                                        "amount": "€100",
                                        "amountUnformatted": 100.0
                                    }
                                }
                            }
                        }
                    ]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        mockClient = HttpClient(mockEngine)
        scraper = BookingScraper(mockClient)
    }

    @AfterTest
    fun tearDown() {
        mockClient.close()
    }

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

        val result = scraper.search(request)
        assertNotNull(result["cheapest"])
        assertEquals("booking", result["cheapest"]?.website)
        assertEquals("Test Hotel", result["cheapest"]?.name)
        assertEquals("8.5 (100)", result["cheapest"]?.averageRating)
    }
}
