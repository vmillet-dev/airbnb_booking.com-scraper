package com.leboncoin.api.serialization

import com.leboncoin.api.models.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

@OptIn(ExperimentalSerializationApi::class)
object PropertyListingSerializer : KSerializer<PropertyListing> {
    override val descriptor = buildClassSerialDescriptor("PropertyListing") {
        element("Listing ID", String.serializer().descriptor)
        element("Listing Type", String.serializer().nullable.descriptor)
        element("Name", String.serializer().descriptor)
        element("Title", String.serializer().descriptor)
        element("Average Rating", String.serializer().descriptor)
        element("Discounted Price", String.serializer().descriptor)
        element("Original Price", String.serializer().descriptor)
        element("Total Price", String.serializer().descriptor)
        element("Picture", String.serializer().descriptor)
        element("Website", String.serializer().descriptor)
        element("Price", Double.serializer().descriptor)
        element("Listing URL", String.serializer().nullable.descriptor)
    }

    override fun serialize(encoder: Encoder, value: PropertyListing) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.listingId)
            encodeNullableSerializableElement(descriptor, 1, String.serializer(), value.listingType)
            encodeStringElement(descriptor, 2, value.name)
            encodeStringElement(descriptor, 3, value.title)
            encodeStringElement(descriptor, 4, value.averageRating)
            encodeStringElement(descriptor, 5, value.discountedPrice)
            encodeStringElement(descriptor, 6, value.originalPrice)
            encodeStringElement(descriptor, 7, value.totalPrice)
            encodeStringElement(descriptor, 8, value.picture)
            encodeStringElement(descriptor, 9, value.website)
            encodeDoubleElement(descriptor, 10, value.price)
            encodeNullableSerializableElement(descriptor, 11, String.serializer(), value.listingUrl)
        }
    }

    override fun deserialize(decoder: Decoder): PropertyListing {
        return decoder.decodeStructure(descriptor) {
            var listingId = ""
            var listingType: String? = null
            var name = ""
            var title = ""
            var averageRating = "0,0"
            var discountedPrice = ""
            var originalPrice = ""
            var totalPrice = "0"
            var picture = ""
            var website = ""
            var price = 0.0
            var listingUrl: String? = null

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> listingId = decodeStringElement(descriptor, 0)
                    1 -> listingType = decodeNullableSerializableElement(descriptor, 1, String.serializer(), null)
                    2 -> name = decodeStringElement(descriptor, 2)
                    3 -> title = decodeStringElement(descriptor, 3)
                    4 -> averageRating = decodeStringElement(descriptor, 4)
                    5 -> discountedPrice = decodeStringElement(descriptor, 5)
                    6 -> originalPrice = decodeStringElement(descriptor, 6)
                    7 -> totalPrice = decodeStringElement(descriptor, 7)
                    8 -> picture = decodeStringElement(descriptor, 8)
                    9 -> website = decodeStringElement(descriptor, 9)
                    10 -> price = decodeDoubleElement(descriptor, 10)
                    11 -> listingUrl = decodeNullableSerializableElement(descriptor, 11, String.serializer(), null)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }

            PropertyListing(
                listingId = listingId,
                listingType = listingType,
                name = name,
                title = title,
                averageRating = averageRating,
                discountedPrice = discountedPrice,
                originalPrice = originalPrice,
                totalPrice = totalPrice,
                picture = picture,
                website = website,
                price = price,
                listingUrl = listingUrl
            )
        }
    }
}
