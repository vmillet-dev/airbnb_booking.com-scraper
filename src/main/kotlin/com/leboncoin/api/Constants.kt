package com.leboncoin.api

object Constants {
    const val BASE_URL = "https://api.leboncoin.fr"
    
    val HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Accept" to "application/json",
        "Content-Type" to "application/json",
        "Accept-Language" to "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7",
        "Origin" to BASE_URL,
        "Referer" to "$BASE_URL/"
    )
}
