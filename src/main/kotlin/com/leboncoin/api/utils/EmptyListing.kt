package com.leboncoin.api.utils

import com.leboncoin.api.models.PropertyListing

object EmptyListing {
    fun create(website: String) = PropertyListing(
        listingId = "",
        listingType = null,
        name = "No listings found",
        title = "No listings found",
        averageRating = "0,0",
        discountedPrice = "",
        originalPrice = "",
        totalPrice = "0",
        picture = "",
        website = website,
        price = 0.0,
        listingUrl = null
    )
}
