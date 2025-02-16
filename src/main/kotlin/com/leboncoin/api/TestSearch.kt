package com.leboncoin.api

import com.leboncoin.api.models.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main() = runBlocking {
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

    val search = Search()
    val result = search.searchProperties(request)
    println("Response:")
    println(Json { prettyPrint = true }.encodeToString(result))
}
