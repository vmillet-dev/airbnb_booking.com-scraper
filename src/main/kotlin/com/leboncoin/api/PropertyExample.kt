package com.leboncoin.api

import com.leboncoin.api.models.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val search = Search()
    val result = search.searchProperties(
        PropertySearchRequest(
            checkIn = DateInfo("2025-04-20"),
            checkOut = DateInfo("2025-04-28"),
            destination = "France",
            guests = GuestInfo(adults = 2),
            propertyType = listOf("apartment", "house", "hotel"),
            bedrooms = 1,
            bathrooms = 1,
            hasPool = true
        )
    )

    println("=== Property Search Results ===")
    println("\nAirbnb Cheapest Option:")
    result.results.airbnb["cheapest"]?.let { listing ->
        println("Name: ${listing.name}")
        println("Price: ${listing.totalPrice}")
        println("Rating: ${listing.averageRating}")
        println("URL: ${listing.listingUrl ?: "N/A"}")
    } ?: println("No listing found")

    println("\nBooking.com Cheapest Option:")
    result.results.booking["cheapest"]?.let { listing ->
        println("Name: ${listing.name}")
        println("Price: ${listing.totalPrice}")
        println("Rating: ${listing.averageRating}")
        println("URL: ${listing.listingUrl ?: "N/A"}")
    } ?: println("No listing found")
}
