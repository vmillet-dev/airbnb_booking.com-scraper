package com.leboncoin.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val pets: Int = 0,
    val childrenAges: List<Int> = emptyList()
)

@Serializable
data class ScrapingResponse(
    val message: String = "Scraping completed successfully",
    val results: CombinedResults
)

@Serializable
data class CombinedResults(
    val airbnb: Map<String, PropertyListing?>,
    val booking: Map<String, PropertyListing?>
)

@Serializable
data class PropertyResult(
    val cheapest: PropertyListing?
)

@Serializable
data class PropertyListing(
    @SerialName("Listing ID") val listingId: String,
    @SerialName("Listing Type") val listingType: String? = null,
    @SerialName("Name") val name: String,
    @SerialName("Title") val title: String,
    @SerialName("Average Rating") val averageRating: String,
    @SerialName("Discounted Price") val discountedPrice: String = "",
    @SerialName("Original Price") val originalPrice: String = "",
    @SerialName("Total Price") val totalPrice: String,
    @SerialName("Picture") val picture: String,
    @SerialName("Website") val website: String,
    @SerialName("Price") val price: Double,
    @SerialName("Listing URL") val listingUrl: String? = null
)
