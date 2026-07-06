package com.example.parser

data class Message(
    val id: String,
    val role: String, // "user", "grok", "system", etc.
    val text: String,
    val timestamp: Long // Unix epoch milliseconds
)

data class Conversation(
    val id: String,
    val title: String,
    val timestamp: Long, // Unix epoch milliseconds
    val messages: List<Message>
)

data class ExtractionStats(
    var totalConversations: Int = 0,
    var filteredConversations: Int = 0,
    var totalUserMessages: Int = 0,
    var totalGrokMessages: Int = 0,
    var totalCharacters: Long = 0,
    var dateMin: Long = Long.MAX_VALUE,
    var dateMax: Long = Long.MIN_VALUE,
    var binaryFilesProcessed: Int = 0,
    var hexFilesDecoded: Int = 0
)

data class MinedBinary(
    val name: String,
    val size: Long,
    val mimeType: String,
    val sha256: String,
    val details: String, // e.g. "PNG Image 800x600" or "PDF Document"
    val conversationId: String? = null,
    val path: String = ""
)

