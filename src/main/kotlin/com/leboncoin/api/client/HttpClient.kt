package com.leboncoin.api.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object HttpClient {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                prettyPrint = true
                encodeDefaults = true
                explicitNulls = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10000L
            connectTimeoutMillis = 10000L
            socketTimeoutMillis = 10000L
        }
        engine {
            requestTimeout = 10000L // 10 seconds
            maxConnectionsCount = 100
            endpoint {
                connectTimeout = 10000
                requestTimeout = 10000
                socketTimeout = 10000
            }
        }
    }
}
