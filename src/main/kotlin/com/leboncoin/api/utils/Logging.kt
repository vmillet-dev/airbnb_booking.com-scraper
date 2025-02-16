package com.leboncoin.api.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Logger {
    init {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "INFO")
    }
    
    private val logger = LoggerFactory.getLogger("PropertyScraper")
    
    fun info(message: String) = logger.info(message)
    fun warning(message: String) = logger.warn(message)
    fun error(message: String) = logger.error(message)
}

// Extension function for backward compatibility
fun Logger.warning(message: String) = warn(message)
