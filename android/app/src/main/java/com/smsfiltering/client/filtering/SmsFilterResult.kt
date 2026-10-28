package com.smsfiltering.client.filtering

/**
 * Bir SMS'in filtreleme ve risk analizi sonucu.
 */
data class SmsFilterResult(
    val sender: String,
    val body: String,
    val decision: SmsFilterDecision,
    val riskScore: Double,
    val isDangerousSender: Boolean,
    val matchedCategories: Map<String, List<String>>,
    val matchedTopics: Map<String, List<String>>,
    val extractedUrls: List<String>,
    val reasons: List<String>
)
