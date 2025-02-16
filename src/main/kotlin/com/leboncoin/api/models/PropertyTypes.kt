package com.leboncoin.api.models

import kotlinx.serialization.*
import com.leboncoin.api.serialization.PropertyListingSerializer

@Serializable(with = PropertyListingSerializer::class)
data class PropertyListing(
    val listingId: String,
    val listingType: String?,
    val name: String,
    val title: String,
    val averageRating: String,
    val discountedPrice: String,
    val originalPrice: String,
    val totalPrice: String,
    val picture: String,
    val website: String,
    val price: Double,
    val listingUrl: String?
)

@Serializable
data class PropertySearchRequest(
    val checkIn: DateInfo,
    val checkOut: DateInfo,
    val destination: String,
    val guests: GuestInfo,
    val propertyType: List<String>,
    val bedrooms: Int,
    val bathrooms: Int,
    val hasPool: Boolean
)

@Serializable
data class DateInfo(
    val date: String
)

@Serializable
data class GuestInfo(
    val adults: Int,
    val children: Int = 0,
    val pets: Int = 0
)

@Serializable
data class ScrapingResponse(
    val message: String,
    val results: CombinedResults
)

@Serializable
data class CombinedResults(
    val airbnb: PropertyResult,
    val booking: PropertyResult
)

@Serializable
data class PropertyResult(
    val cheapest: PropertyListing?
)
