package com.leboncoin.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

@Serializable
enum class SortBy(val value: String) {
    @SerialName("date") TIME("date"),
    @SerialName("price") PRICE("price"),
    @SerialName("relevance") RELEVANCE("relevance")
}

@Serializable
enum class SortOrder(val value: String) {
    @SerialName("asc") ASC("asc"),
    @SerialName("desc") DESC("desc")
}

@Serializable
enum class OwnerType(val value: String) {
    @SerialName("all") ALL("all"),
    @SerialName("private") PRIVATE("private"),
    @SerialName("pro") PROFESSIONAL("pro")
}

@Serializable
enum class Category(val value: String) {
    @SerialName("0") ALL("0"),
    @SerialName("1") VEHICULES("1"),
    @SerialName("2") VOITURES("2"),
    @SerialName("3") MOTOS("3"),
    @SerialName("43") CONSOLES("43")
}

@Serializable
data class SearchFilters(
    val filters: SearchCriteria,
    val limit: Int = 30,
    @SerialName("owner_type") val ownerType: OwnerType = OwnerType.ALL,
    @SerialName("sort_by") val sortBy: SortBy = SortBy.TIME,
    @SerialName("sort_order") val sortOrder: SortOrder = SortOrder.DESC,
    val offset: Int? = null,
    val pivot: String? = null
)

@Serializable
data class SearchCriteria(
    val category: CategoryRef? = null,
    val enums: Map<String, List<String>>? = null,
    val ranges: Map<String, Map<String, Int>>? = null,
    val keywords: KeywordSearch? = null,
    val location: LocationFilter = LocationFilter()
)

@Serializable
data class CategoryRef(
    val id: String
)

@Serializable
data class KeywordSearch(
    val text: String,
    val type: String
)

@Serializable
data class LocationFilter(
    val locations: List<Location>? = null,
    val shippable: Boolean = true
)

@Serializable
data class SearchResult(
    val total: Int = 0,
    @SerialName("total_all") val totalAll: Int = 0,
    @SerialName("total_pro") val totalPro: Int = 0,
    @SerialName("total_private") val totalPrivate: Int = 0,
    @SerialName("max_pages") val maxPages: Int = 1,
    @SerialName("referrer_id") val referrerId: String? = null,
    @SerialName("human_readable_applied_condition") val humanReadableAppliedCondition: String? = null,
    val pivot: String? = null,
    val ads: List<Ad> = emptyList()
)

@Serializable
data class ResultMultiples(
    val ads: List<Ad>,
    val pivot: String,
    val total: Int
)

@Serializable
data class Ad(
    @SerialName("list_id") val listId: Long,
    @SerialName("first_publication_date") val firstPublicationDate: String,
    @SerialName("expiration_date") val expirationDate: String,
    @SerialName("index_date") val indexDate: String,
    val status: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("category_name") val categoryName: String,
    val subject: String,
    val body: String,
    val brand: String,
    @SerialName("ad_type") val adType: String,
    val url: String,
    val price: List<Int>,
    @SerialName("price_cents") val priceCents: Int,
    val images: Images,
    val attributes: List<AdAttribute>,
    val location: Location,
    val owner: Owner,
    @SerialName("has_phone") val hasPhone: Boolean,
    @SerialName("is_boosted") val isBoosted: Boolean
)

@Serializable
data class Images(
    val urls: List<String>,
    @SerialName("urls_thumb") val thumbUrls: List<String>,
    @SerialName("urls_large") val largeUrls: List<String>,
    @SerialName("thumb_url") val thumbUrl: String,
    @SerialName("small_url") val smallUrl: String,
    @SerialName("nb_images") val nbImages: Int
)

@Serializable
data class AdAttribute(
    val key: String,
    val value: String,
    val values: List<String>,
    @SerialName("value_label") val valueLabel: String,
    val generic: Boolean
)

@Serializable
data class Location(
    @SerialName("country_id") val countryId: String,
    @SerialName("region_id") val regionId: String,
    @SerialName("region_name") val regionName: String,
    @SerialName("department_id") val departmentId: String,
    @SerialName("department_name") val departmentName: String,
    @SerialName("city_label") val cityLabel: String,
    val city: String,
    val zipcode: String,
    val lat: Double,
    val lng: Double,
    val source: String,
    val provider: String,
    @SerialName("is_shape") val isShape: Boolean,
    val feature: Map<String, Boolean>
)

@Serializable
data class SimilarResult<T>(
    @SerialName("similar_model_version") val similarModelVersion: String,
    @SerialName("referer_type") val refererType: String,
    @SerialName("referer_id") val refererId: String,
    val ads: List<Ad>
)

@Serializable
data class Owner(
    @SerialName("store_id") val storeId: String,
    @SerialName("user_id") val userId: String,
    val type: OwnerType,
    val name: String,
    val siren: String,
    @SerialName("no_salesmen") val noSalesmen: Boolean,
    @SerialName("activity_sector") val activitySector: String
)
