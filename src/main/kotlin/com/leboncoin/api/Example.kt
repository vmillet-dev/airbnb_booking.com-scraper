package com.leboncoin.api

import com.leboncoin.api.models.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val search = Search()
    val result = search.search(SearchFilters(
        filters = SearchCriteria(
            category = CategoryRef(Category.CONSOLES.value),
            keywords = KeywordSearch(
                text = "Atari",
                type = "subject_only"
            ),
            enums = mapOf(
                "item_condition" to listOf("1")
            ),
            ranges = mapOf(
                "price" to mapOf(
                    "min" to 30,
                    "max" to 60
                )
            )
        ),
        sortBy = SortBy.TIME,
        sortOrder = SortOrder.DESC,
        limit = 35
    ))
    println("Found ${result.total} results")
    result.ads.forEach { ad ->
        println("${ad.subject} - ${ad.price} - ${ad.url}")
    }
}
