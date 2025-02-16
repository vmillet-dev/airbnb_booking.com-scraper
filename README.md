# Leboncoin API Kotlin

A Kotlin library for interfacing with leboncoin's search API. This is a port of the [leboncoin-api-search](https://github.com/thomasync/leboncoin-api-search) TypeScript library.

## Features

- Search functionality with filters
- Pagination support
- Similar items search
- Ad details by ID
- Coroutines and Flow support
- Full Kotlin serialization

## Usage

```kotlin
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

// Print results
println("Found ${result.total} results")
result.ads.forEach { ad ->
    println("${ad.subject} - ${ad.price} - ${ad.url}")
}
```

## Requirements

- Java 21 or higher
- Gradle 8.5 or higher

## Dependencies

- kotlinx-coroutines-core
- ktor-client (core, cio, content-negotiation)
- kotlinx-serialization-json

## Note

The API is protected by CAPTCHA, so you may need to handle authentication and rate limiting appropriately.
