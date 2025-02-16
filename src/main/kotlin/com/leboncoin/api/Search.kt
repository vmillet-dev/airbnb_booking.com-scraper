package com.leboncoin.api

import com.leboncoin.api.client.HttpClient
import com.leboncoin.api.models.*
import com.leboncoin.api.scraper.PropertyScraper
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class Search {
    private val propertyScraper = PropertyScraper(HttpClient.client)
    suspend fun search(filters: SearchFilters): SearchResult {
        return HttpClient.client.post("${Constants.BASE_URL}/finder/search") {
            headers {
                Constants.HEADERS.forEach { (key, value) ->
                    append(key, value)
                }
            }
            contentType(ContentType.Application.Json)
            setBody(filters)
        }.body()
    }

    fun searchMultiple(filters: SearchFilters, cycles: Int = 1): Flow<SearchResult> = flow {
        var lastPivot: String? = null
        repeat(cycles) { cycle ->
            val result = search(filters.copy(pivot = lastPivot))
            emit(result)
            lastPivot = result.pivot
            if (result.ads.isEmpty() || lastPivot == null) {
                return@flow
            }
        }
    }

    suspend fun searchById(listId: Long): Ad? {
        return HttpClient.client.post("https://api.leboncoin.fr/api/adfinder/v1/myads") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("ids" to listOf(listId)))
        }.body<List<Ad>>().firstOrNull()
    }

    suspend fun similar(id: Long, limit: Int = 10): List<Ad> {
        return HttpClient.client.get("https://api.leboncoin.fr/api/same/v4/search/$id") {
            parameter("size", limit)
        }.body<SimilarResult<Unit>>().ads
    }

    suspend fun searchProperties(request: PropertySearchRequest): ScrapingResponse {
        return propertyScraper.search(request)
    }
}
