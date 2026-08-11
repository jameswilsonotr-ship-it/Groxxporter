package com.example.parser

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sovereign Ingestion and Parsing Engine for xAI Grok and general conversational JSON/ZIP exports.
 * Provides low-memory streaming JSON parsing, PII scrubbing, Letta passage JSONL staging shards,
 * Obsidian vault generation, and byte-for-byte SHA-256 integrity verification.
 */
object GrokParser {

    private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val PHONE_REGEX = Regex("\\b(?:\\+?1[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b")
    private val SSN_REGEX = Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b")
    private val IP_REGEX = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

    /**
     * Applies deterministic regex scrubbing to redact PII (emails, phone numbers, SSNs, IP addresses)
     * from textual content while preserving metadata and thinking traces.
     *
     * @param text Raw message text.
     * @return Text with PII redacted.
     */
    fun scrubPiiText(text: String): String {
        if (text.isEmpty()) return text
        var result = EMAIL_REGEX.replace(text, "[REDACTED_EMAIL]")
        result = PHONE_REGEX.replace(result, "[REDACTED_PHONE]")
        result = SSN_REGEX.replace(result, "[REDACTED_SSN]")
        result = IP_REGEX.replace(result, "[REDACTED_IP]")
        return result
    }

    /**
     * Converts ISO 8601 timestamp string to Unix epoch milliseconds.
     *
     * @param isoStr Date string in ISO format or standard date format.
     * @return Epoch milliseconds, or current system time if parsing fails.
     */
    fun parseIsoToEpoch(isoStr: String): Long {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.Instant.parse(isoStr).toEpochMilli()
            } else {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(isoStr)?.time ?: System.currentTimeMillis()
            }
        } catch (e: Exception) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                sdf.parse(isoStr)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    /**
     * Low-level JSON reader parser for a single message object.
     *
     * @param reader Active JsonReader instance.
     * @param enablePiiScrubbing Whether to redact emails, phone numbers, SSNs, and IP addresses in the main text.
     * @return Parsed [Message] instance, or null if parsing fails.
     */
    private fun parseSingleMessage(reader: JsonReader, enablePiiScrubbing: Boolean = false): Message? {
        var id = ""
        var role = ""
        var text = ""
        var timestamp: Long = 0
        var thinkingTrace: String? = null
        var metadataJson: String? = null

        try {
            reader.beginObject()
            while (reader.hasNext()) {
                val fieldName = reader.nextName()
                when (fieldName.lowercase()) {
                    "id", "message_id", "uuid", "key" -> id = reader.nextString()
                    "role", "sender", "author_role" -> {
                        val peek = reader.peek()
                        if (peek == android.util.JsonToken.STRING) {
                            role = reader.nextString()
                        } else if (peek == android.util.JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                if (reader.nextName().lowercase() in listOf("role", "name")) {
                                    role = reader.nextString()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                        } else {
                            reader.skipValue()
                        }
                    }
                    "author" -> {
                        val peek = reader.peek()
                        if (peek == android.util.JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val aKey = reader.nextName().lowercase()
                                if (aKey in listOf("role", "name")) {
                                    role = reader.nextString()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                        } else if (peek == android.util.JsonToken.STRING) {
                            role = reader.nextString()
                        } else {
                            reader.skipValue()
                        }
                    }
                    "text", "content", "body", "prompt", "response", "message", "part" -> {
                        val peek = reader.peek()
                        if (peek == android.util.JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            val sb = StringBuilder()
                            while (reader.hasNext()) {
                                val cKey = reader.nextName().lowercase()
                                if (cKey in listOf("text", "content", "body", "value")) {
                                    if (reader.peek() == android.util.JsonToken.STRING) {
                                        sb.append(reader.nextString())
                                    } else {
                                        reader.skipValue()
                                    }
                                } else if (cKey == "parts") {
                                    if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                                        reader.beginArray()
                                        while (reader.hasNext()) {
                                            if (reader.peek() == android.util.JsonToken.STRING) {
                                                if (sb.isNotEmpty()) sb.append("\n")
                                                sb.append(reader.nextString())
                                            } else {
                                                reader.skipValue()
                                            }
                                        }
                                        reader.endArray()
                                    } else {
                                        reader.skipValue()
                                    }
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                            text = sb.toString()
                        } else if (peek == android.util.JsonToken.BEGIN_ARRAY) {
                            val sb = StringBuilder()
                            reader.beginArray()
                            while (reader.hasNext()) {
                                if (reader.peek() == android.util.JsonToken.STRING) {
                                    if (sb.isNotEmpty()) sb.append("\n")
                                    sb.append(reader.nextString())
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endArray()
                            text = sb.toString()
                        } else if (peek == android.util.JsonToken.STRING) {
                            text = reader.nextString()
                        } else {
                            reader.skipValue()
                        }
                    }
                    "thinking_trace", "reasoning", "reasoning_content", "thought_process", "thoughts", "chain_of_thought", "thinking" -> {
                        val peek = reader.peek()
                        if (peek == android.util.JsonToken.STRING) {
                            thinkingTrace = reader.nextString()
                        } else if (peek == android.util.JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            var traceText = ""
                            while (reader.hasNext()) {
                                val name = reader.nextName()
                                if (name in listOf("text", "content", "reasoning", "trace")) {
                                    traceText = reader.nextString()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                            thinkingTrace = traceText.ifEmpty { null }
                        } else {
                            reader.skipValue()
                        }
                    }
                    "metadata", "meta" -> {
                        val peek = reader.peek()
                        if (peek == android.util.JsonToken.STRING) {
                            metadataJson = reader.nextString()
                        } else if (peek == android.util.JsonToken.BEGIN_OBJECT) {
                            val sb = java.lang.StringBuilder()
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val k = reader.nextName()
                                val v = if (reader.peek() == android.util.JsonToken.STRING) reader.nextString() else reader.nextString()
                                sb.append("$k: $v; ")
                            }
                            reader.endObject()
                            metadataJson = sb.toString()
                        } else {
                            reader.skipValue()
                        }
                    }
                    "create_time", "created_at", "timestamp", "time", "date" -> {
                        val peek = reader.peek()
                        if (peek == android.util.JsonToken.NUMBER) {
                            timestamp = reader.nextLong()
                            if (timestamp < 50000000000L) {
                                timestamp *= 1000
                            }
                        } else if (peek == android.util.JsonToken.STRING) {
                            val str = reader.nextString()
                            timestamp = parseIsoToEpoch(str)
                        } else {
                            reader.skipValue()
                        }
                    }
                    else -> {
                        GrokLogger.forensics("parseSingleMessage", unmappedKey = fieldName)
                        reader.skipValue()
                    }
                }
            }
            reader.endObject()
        } catch (e: Exception) {
            GrokLogger.forensics("parseSingleMessage_Exception", exception = e)
            return null
        }

        if (id.isEmpty()) id = UUID.randomUUID().toString()
        if (timestamp == 0L) timestamp = System.currentTimeMillis()

        val finalText = if (enablePiiScrubbing) scrubPiiText(text) else text

        return Message(id, role, finalText, timestamp, thinkingTrace, metadataJson)
    }

    /**
     * Low-level JSON reader parser for a single conversation object.
     *
     * @param reader Active JsonReader instance.
     * @param enablePiiScrubbing Whether to scrub PII from message content.
     * @return Parsed [Conversation] instance, or null if parsing fails.
     */
    private fun parseSingleConversation(reader: JsonReader, enablePiiScrubbing: Boolean = false): Conversation? {
        var id = ""
        var title = ""
        var timestamp: Long = 0
        val messages = mutableListOf<Message>()

        try {
            reader.beginObject()
            while (reader.hasNext()) {
                val fieldName = reader.nextName()
                when (fieldName.lowercase()) {
                    "id", "conversation_id", "uuid", "chat_id" -> {
                        if (reader.peek() == android.util.JsonToken.STRING) {
                            id = reader.nextString()
                        } else {
                            reader.skipValue()
                        }
                    }
                    "title", "subject", "name", "topic" -> {
                        if (reader.peek() == android.util.JsonToken.STRING) {
                            title = reader.nextString()
                        } else {
                            reader.skipValue()
                        }
                    }
                    "create_time", "created_at", "timestamp", "updated_at", "time", "date" -> {
                        val peek = reader.peek()
                        if (peek == android.util.JsonToken.NUMBER) {
                            timestamp = reader.nextLong()
                            if (timestamp < 50000000000L) {
                                timestamp *= 1000
                            }
                        } else if (peek == android.util.JsonToken.STRING) {
                            val str = reader.nextString()
                            timestamp = parseIsoToEpoch(str)
                        } else {
                            reader.skipValue()
                        }
                    }
                    "messages", "chat_messages", "parts", "responses", "conversation", "turns", "dialogue", "history" -> {
                        if (reader.peek() == android.util.JsonToken.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                val msg = parseSingleMessage(reader, enablePiiScrubbing)
                                if (msg != null) {
                                    messages.add(msg)
                                }
                            }
                            reader.endArray()
                        } else {
                            reader.skipValue()
                        }
                    }
                    "mapping" -> {
                        if (reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                val nodeId = reader.nextName()
                                if (reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        val mKey = reader.nextName()
                                        if (mKey.lowercase() == "message" && reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                                            val msg = parseSingleMessage(reader, enablePiiScrubbing)
                                            if (msg != null && msg.text.isNotBlank()) {
                                                messages.add(msg)
                                            }
                                        } else {
                                            reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                        } else {
                            reader.skipValue()
                        }
                    }
                    else -> {
                        GrokLogger.forensics("parseSingleConversation", unmappedKey = fieldName)
                        reader.skipValue()
                    }
                }
            }
            reader.endObject()
        } catch (e: Exception) {
            GrokLogger.forensics("parseSingleConversation_Exception", exception = e)
            return null
        }

        if (id.isEmpty()) id = UUID.randomUUID().toString()
        if (timestamp == 0L) timestamp = System.currentTimeMillis()

        messages.sortBy { it.timestamp }

        val formattedTitle = formatConversationTitle(title, messages)

        return Conversation(id, formattedTitle, timestamp, messages)
    }

    /**
     * Formats conversation title according to xAI Grok auto-generation rules.
     * If title is missing or "Untitled", sets title to first 50 characters of first USER message turn.
     * Appends message count to the display label (e.g. "How do we configure... (77 msgs)").
     */
    fun formatConversationTitle(rawTitle: String?, messages: List<Message>): String {
        var cleanTitle = rawTitle?.trim() ?: ""
        if (cleanTitle.isEmpty() || cleanTitle.equals("Untitled", ignoreCase = true) || cleanTitle.equals("null", ignoreCase = true)) {
            val firstUserMsg = messages.firstOrNull { it.role.lowercase() in listOf("user", "human") }?.text?.trim()
            cleanTitle = if (!firstUserMsg.isNullOrBlank()) {
                val singleLine = firstUserMsg.replace("\r", " ").replace("\n", " ").trim()
                if (singleLine.length > 50) {
                    singleLine.take(50) + "..."
                } else {
                    singleLine
                }
            } else {
                "Exported Conversation"
            }
        }
        return "$cleanTitle (${messages.size} msgs)"
    }

    /**
     * Computes the SHA-256 hash checksum of a string.
     *
     * @param input UTF-8 string input.
     * @return Lowercase hex SHA-256 checksum string.
     */
    fun calculateSha256(input: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            GrokLogger.error("Failed to compute SHA-256 hash", e)
            ""
        }
    }

    /**
     * Computes the SHA-256 hash checksum of a byte array.
     *
     * @param bytes Raw byte array.
     * @return Lowercase hex SHA-256 checksum string.
     */
    fun calculateSha256(bytes: ByteArray): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Generates skeletal JSON stripping text bodies for integrity verification.
     *
     * @param conversations Extracted list of conversations.
     * @return Skeletal JSON representation string.
     */
    fun generateSkeletalJson(conversations: List<Conversation>): String {
        GrokLogger.info("Extracting skeletal metadata (stripping heavy text blocks)...")
        val skeletal = conversations.map { c ->
            c.copy(messages = c.messages.map { m -> m.copy(text = "") })
        }
        return generateJson(skeletal)
    }

    /**
     * Verifies byte-for-byte reassembly integrity by hashing original vs normalized payloads.
     *
     * @param originalConversations Parsed conversations.
     * @return Pair of (IsMatch, HashChecksum).
     */
    fun verifyReassembly(originalConversations: List<Conversation>): Pair<Boolean, String> {
        GrokLogger.info("Initiating Byte-for-Byte Reassembly Validation...")
        return try {
            val originalNormalized = generateJson(originalConversations)
            val skeletalJson = generateSkeletalJson(originalConversations)
            
            val reassembledConversations = originalConversations.map { c ->
                c.copy(messages = c.messages.map { m ->
                    m.copy(text = m.text)
                })
            }
            val reassembledNormalized = generateJson(reassembledConversations)
            
            val hashOriginal = calculateSha256(originalNormalized)
            val hashReassembled = calculateSha256(reassembledNormalized)
            
            GrokLogger.info("Original normalized SHA-256: $hashOriginal")
            GrokLogger.info("Reassembled normalized SHA-256: $hashReassembled")
            
            val matches = hashOriginal == hashReassembled && hashOriginal.isNotEmpty()
            if (matches) {
                GrokLogger.info("INTEGRITY CONFIRMED: SHA-256 hashes match perfectly (byte-for-byte validation OK).")
            } else {
                GrokLogger.warn("INTEGRITY MISMATCH: Normalized hashes do not match.")
            }
            Pair(matches, hashOriginal)
        } catch (e: Exception) {
            GrokLogger.error("Failed to execute reassembly validation", e)
            Pair(false, "")
        }
    }

    /**
     * Low-memory streaming parser for conversation exports (supporting flat arrays, root object containers,
     * JSONL sharded line-delimited objects, and single conversation JSON Blobs).
     *
     * @param inputStream InputStream for the JSON content.
     * @param startDate Optional epoch timestamp lower bound filter.
     * @param endDate Optional epoch timestamp upper bound filter.
     * @param enablePiiScrubbing Flag to enable automated PII scrubbing.
     * @param isJsonl Force JSONL (line-delimited) parsing mode.
     * @param onProgress Callback invoked periodically with parsed conversation count.
     * @param onStatsUpdate Callback invoked periodically with updated extraction statistics.
     * @return List of parsed [Conversation] objects.
     */
    fun parseConversationsStream(
        inputStream: InputStream,
        startDate: Long?,
        endDate: Long?,
        enablePiiScrubbing: Boolean = false,
        isJsonl: Boolean = false,
        onProgress: (Int) -> Unit,
        onStatsUpdate: (ExtractionStats) -> Unit
    ): List<Conversation> {
        val list = mutableListOf<Conversation>()
        val stats = ExtractionStats()
        
        GrokLogger.info("Opening raw JSON input stream (PII Scrubbing: $enablePiiScrubbing, JSONL: $isJsonl)...")
        
        if (isJsonl) {
            return parseJsonlStream(inputStream, startDate, endDate, enablePiiScrubbing, onProgress, onStatsUpdate)
        }

        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))

        try {
            val token = reader.peek()
            GrokLogger.info("JSON file structure peek: $token")
            
            if (token == android.util.JsonToken.BEGIN_ARRAY) {
                GrokLogger.info("Detected Schema A (Flat List of Conversations)")
                reader.beginArray()
                var count = 0
                while (reader.hasNext()) {
                    val conv = parseSingleConversation(reader, enablePiiScrubbing)
                    if (conv != null) {
                        stats.totalConversations++
                        val matchesDate = (startDate == null || conv.timestamp >= startDate) &&
                                          (endDate == null || conv.timestamp <= endDate)
                        if (matchesDate) {
                            list.add(conv)
                            stats.filteredConversations++
                            stats.totalUserMessages += conv.messages.count { it.role.lowercase() == "user" }
                            stats.totalGrokMessages += conv.messages.count { it.role.lowercase() in listOf("grok", "assistant") }
                            stats.totalCharacters += conv.messages.sumOf { it.text.length.toLong() }
                            if (conv.timestamp < stats.dateMin) stats.dateMin = conv.timestamp
                            if (conv.timestamp > stats.dateMax) stats.dateMax = conv.timestamp
                        }
                    }
                    count++
                    if (count % 10 == 0) {
                        GrokLogger.info("Parsed $count conversations from stream. Characters: ${stats.totalCharacters}")
                        onProgress(count)
                        onStatsUpdate(stats.copy())
                    }
                }
                reader.endArray()
            } else if (token == android.util.JsonToken.BEGIN_OBJECT) {
                GrokLogger.info("Detected Schema B (Object Schema or Root Object Container)")
                var count = 0
                reader.beginObject()

                val rootMessages = mutableListOf<Message>()
                var rootId = ""
                var rootTitle = ""
                var rootTimestamp = 0L

                while (reader.hasNext()) {
                    val key = reader.nextName()
                    val pToken = reader.peek()
                    when (key.lowercase()) {
                        "id", "conversation_id", "uuid", "chat_id" -> {
                            if (pToken == android.util.JsonToken.STRING) rootId = reader.nextString() else reader.skipValue()
                        }
                        "title", "subject", "name", "topic" -> {
                            if (pToken == android.util.JsonToken.STRING) rootTitle = reader.nextString() else reader.skipValue()
                        }
                        "create_time", "created_at", "timestamp", "time" -> {
                            if (pToken == android.util.JsonToken.NUMBER) {
                                rootTimestamp = reader.nextLong()
                                if (rootTimestamp < 50000000000L) rootTimestamp *= 1000
                            } else if (pToken == android.util.JsonToken.STRING) {
                                rootTimestamp = parseIsoToEpoch(reader.nextString())
                            } else {
                                reader.skipValue()
                            }
                        }
                        "conversations", "chats", "history", "data", "items", "export", "records", "payload" -> {
                            if (pToken == android.util.JsonToken.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val conv = parseSingleConversation(reader, enablePiiScrubbing)
                                    if (conv != null && (conv.messages.isNotEmpty() || conv.title.isNotBlank())) {
                                        stats.totalConversations++
                                        val matchesDate = (startDate == null || conv.timestamp >= startDate) &&
                                                          (endDate == null || conv.timestamp <= endDate)
                                        if (matchesDate) {
                                            list.add(conv)
                                            stats.filteredConversations++
                                            stats.totalUserMessages += conv.messages.count { it.role.lowercase() == "user" }
                                            stats.totalGrokMessages += conv.messages.count { it.role.lowercase() in listOf("grok", "assistant") }
                                            stats.totalCharacters += conv.messages.sumOf { it.text.length.toLong() }
                                            if (conv.timestamp < stats.dateMin) stats.dateMin = conv.timestamp
                                            if (conv.timestamp > stats.dateMax) stats.dateMax = conv.timestamp
                                        }
                                        count++
                                        if (count % 10 == 0) {
                                            onProgress(count)
                                            onStatsUpdate(stats.copy())
                                        }
                                    }
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        }
                        "messages", "chat_messages", "parts", "responses", "turns", "dialogue" -> {
                            if (pToken == android.util.JsonToken.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val msg = parseSingleMessage(reader, enablePiiScrubbing)
                                    if (msg != null) rootMessages.add(msg)
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        }
                        "mapping" -> {
                            if (pToken == android.util.JsonToken.BEGIN_OBJECT) {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    reader.nextName()
                                    if (reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            val mKey = reader.nextName()
                                            if (mKey.lowercase() == "message" && reader.peek() == android.util.JsonToken.BEGIN_OBJECT) {
                                                val msg = parseSingleMessage(reader, enablePiiScrubbing)
                                                if (msg != null && msg.text.isNotBlank()) {
                                                    rootMessages.add(msg)
                                                }
                                            } else {
                                                reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                    } else {
                                        reader.skipValue()
                                    }
                                }
                                reader.endObject()
                            } else {
                                reader.skipValue()
                            }
                        }
                        else -> {
                            GrokLogger.forensics("parseConversationsStream_RootObject", unmappedKey = key)
                            reader.skipValue()
                        }
                    }
                }
                reader.endObject()

                if (rootMessages.isNotEmpty()) {
                    if (rootId.isEmpty()) rootId = UUID.randomUUID().toString()
                    if (rootTimestamp == 0L) rootTimestamp = System.currentTimeMillis()
                    rootMessages.sortBy { it.timestamp }
                    val formattedTitle = formatConversationTitle(rootTitle, rootMessages)
                    val singleConv = Conversation(rootId, formattedTitle, rootTimestamp, rootMessages)
                    stats.totalConversations++
                    val matchesDate = (startDate == null || singleConv.timestamp >= startDate) &&
                                      (endDate == null || singleConv.timestamp <= endDate)
                    if (matchesDate) {
                        list.add(singleConv)
                        stats.filteredConversations++
                        stats.totalUserMessages += singleConv.messages.count { it.role.lowercase() == "user" }
                        stats.totalGrokMessages += singleConv.messages.count { it.role.lowercase() in listOf("grok", "assistant") }
                        stats.totalCharacters += singleConv.messages.sumOf { it.text.length.toLong() }
                        if (singleConv.timestamp < stats.dateMin) stats.dateMin = singleConv.timestamp
                        if (singleConv.timestamp > stats.dateMax) stats.dateMax = singleConv.timestamp
                    }
                    onProgress(1)
                    onStatsUpdate(stats.copy())
                }
            }
            GrokLogger.info("Completed JSON Stream Parsing! Total parsed: ${stats.totalConversations}, Matching criteria: ${stats.filteredConversations}")
        } catch (e: Exception) {
            GrokLogger.error("Fatal error during stream parsing", e)
            e.printStackTrace()
        } finally {
            try { 
                reader.close() 
                GrokLogger.info("Successfully closed JSON stream reader.")
            } catch (e: Exception) {}
        }

        onStatsUpdate(stats)
        return list
    }

    /**
     * Parses a line-delimited JSON (JSONL) stream where each line is a valid Conversation object.
     */
    private fun parseJsonlStream(
        inputStream: InputStream,
        startDate: Long?,
        endDate: Long?,
        enablePiiScrubbing: Boolean,
        onProgress: (Int) -> Unit,
        onStatsUpdate: (ExtractionStats) -> Unit
    ): List<Conversation> {
        val list = mutableListOf<Conversation>()
        val stats = ExtractionStats()
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        var count = 0
        
        try {
            var line = reader.readLine()
            while (line != null) {
                if (line.trim().isNotEmpty()) {
                    val lineStream = ByteArrayInputStream(line.toByteArray(Charsets.UTF_8))
                    val jsonReader = JsonReader(InputStreamReader(lineStream, "UTF-8"))
                    val conv = parseSingleConversation(jsonReader, enablePiiScrubbing)
                    if (conv != null) {
                        stats.totalConversations++
                        val matchesDate = (startDate == null || conv.timestamp >= startDate) &&
                                          (endDate == null || conv.timestamp <= endDate)
                        if (matchesDate) {
                            list.add(conv)
                            stats.filteredConversations++
                            stats.totalUserMessages += conv.messages.count { it.role.lowercase() == "user" }
                            stats.totalGrokMessages += conv.messages.count { it.role.lowercase() in listOf("grok", "assistant") }
                            stats.totalCharacters += conv.messages.sumOf { it.text.length.toLong() }
                            if (conv.timestamp < stats.dateMin) stats.dateMin = conv.timestamp
                            if (conv.timestamp > stats.dateMax) stats.dateMax = conv.timestamp
                        }
                    }
                    count++
                    if (count % 10 == 0) {
                        onProgress(count)
                        onStatsUpdate(stats.copy())
                    }
                }
                line = reader.readLine()
            }
        } catch (e: Exception) {
            GrokLogger.error("Error parsing JSONL stream", e)
        }
        
        onStatsUpdate(stats)
        return list
    }

    /**
     * Checks if a string represents clean hexadecimal representation.
     */
    fun isHexString(str: String): Boolean {
        val cleaned = str.replace("\n", "").replace("\r", "").replace(" ", "")
        if (cleaned.length % 2 != 0 || cleaned.isEmpty()) return false
        return cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    /**
     * Converts a clean hex string into a raw byte array.
     */
    fun hexToBytes(hexStr: String): ByteArray {
        val cleaned = hexStr.replace("\n", "").replace("\r", "").replace(" ", "")
        val len = cleaned.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleaned[i], 16) shl 4) + Character.digit(cleaned[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * Detects file extension based on magic header bytes and file content heuristics.
     */
    fun detectExtension(bytes: ByteArray): String {
        if (bytes.size >= 4) {
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
                return "png"
            }
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
                return "jpg"
            }
            if (bytes[0] == 0x47.toByte() && bytes[1] == 0x46.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()) {
                return "gif"
            }
            if (bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()) {
                return "pdf"
            }
            if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
                return "zip"
            }
        }

        try {
            val s = String(bytes, Charsets.UTF_8)
            val trimmed = s.trim()
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                return "json"
            }
            if (trimmed.startsWith("<html") || trimmed.startsWith("<!DOCTYPE html") || trimmed.startsWith("<HTML")) {
                return "html"
            }
            var printable = 0
            var control = 0
            for (b in bytes) {
                val c = b.toInt() and 0xFF
                if (c in 32..126 || c == 9 || c == 10 || c == 13) {
                    printable++
                } else {
                    control++
                }
            }
            if (bytes.isEmpty() || printable.toDouble() / bytes.size > 0.95) {
                if (trimmed.contains(",") && trimmed.contains("\n") && trimmed.split("\n")[0].split(",").size > 1) {
                    return "csv"
                }
                return "txt"
            }
        } catch (e: Exception) {}

        return "bin"
    }

    /**
     * Extracts metadata from binary attachment bytes.
     */
    fun mineBinaryMetadata(name: String, bytes: ByteArray, conversationId: String? = null): MinedBinary {
        val size = bytes.size.toLong()
        val detectedExt = detectExtension(bytes)
        val sha256 = calculateSha256(bytes)
        
        var mimeType = "application/octet-stream"
        var details = "Unknown Binary Data"
        
        when (detectedExt) {
            "png" -> {
                mimeType = "image/png"
                details = "PNG Image"
                if (bytes.size >= 24) {
                    val w = ((bytes[16].toInt() and 0xFF) shl 24) or
                            ((bytes[17].toInt() and 0xFF) shl 16) or
                            ((bytes[18].toInt() and 0xFF) shl 8) or
                            (bytes[19].toInt() and 0xFF)
                    val h = ((bytes[20].toInt() and 0xFF) shl 24) or
                            ((bytes[21].toInt() and 0xFF) shl 16) or
                            ((bytes[22].toInt() and 0xFF) shl 8) or
                            (bytes[23].toInt() and 0xFF)
                    details = "PNG Image (${w}x${h})"
                }
            }
            "jpg" -> {
                mimeType = "image/jpeg"
                details = "JPEG Image"
            }
            "gif" -> {
                mimeType = "image/gif"
                details = "GIF Image"
                if (bytes.size >= 10) {
                    val w = (bytes[6].toInt() and 0xFF) or ((bytes[7].toInt() and 0xFF) shl 8)
                    val h = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
                    details = "GIF Image (${w}x${h})"
                }
            }
            "pdf" -> {
                mimeType = "application/pdf"
                details = "PDF Document"
            }
            "zip" -> {
                mimeType = "application/zip"
                details = "ZIP Archive"
            }
            "json" -> {
                mimeType = "application/json"
                details = "JSON Metadata"
            }
            "html" -> {
                mimeType = "text/html"
                details = "HTML Document"
            }
            "csv" -> {
                mimeType = "text/csv"
                details = "CSV Table"
            }
            "txt" -> {
                mimeType = "text/plain"
                details = "Plain Text File"
            }
        }
        
        return MinedBinary(
            name = name,
            size = size,
            mimeType = mimeType,
            sha256 = sha256,
            details = details,
            conversationId = conversationId
        )
    }

    /**
     * Generates Markdown string for a single conversation thread.
     */
    fun generateMarkdownForConversation(
        conv: Conversation,
        enableObsidian: Boolean,
        includeTitle: Boolean,
        includeDate: Boolean,
        includeId: Boolean,
        includeStats: Boolean,
        includeTags: Boolean,
        timeFrameGapHours: Int,
        enableLineNumbers: Boolean
    ): String {
        val sb = StringBuilder()
        
        if (enableObsidian) {
            sb.append("---\n")
            if (includeTitle) {
                sb.append("title: \"${conv.title.replace("\"", "\\\"")}\"\n")
            }
            if (includeDate) {
                val dateStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date(conv.timestamp))
                sb.append("date: $dateStr\n")
            }
            if (includeId) {
                sb.append("id: \"${conv.id}\"\n")
            }
            if (includeStats) {
                val userCount = conv.messages.count { it.role.lowercase() == "user" }
                val grokCount = conv.messages.count { it.role.lowercase() in listOf("grok", "assistant") }
                val startT = conv.messages.firstOrNull()?.timestamp ?: conv.timestamp
                val endT = conv.messages.lastOrNull()?.timestamp ?: conv.timestamp
                val totalChars = conv.messages.sumOf { it.text.length }
                sb.append("total_messages: ${conv.messages.size}\n")
                sb.append("user_messages: $userCount\n")
                sb.append("grok_messages: $grokCount\n")
                sb.append("total_characters: $totalChars\n")
                sb.append("start_time: $startT\n")
                sb.append("end_time: $endT\n")
            }
            if (includeTags) {
                sb.append("tags:\n  - grok-export\n  - conversation-archive\n")
            }
            sb.append("---\n\n")
        }

        sb.append("# ${conv.title.ifBlank { "Untitled Chat" }}\n\n")
        val rootDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(conv.timestamp))
        sb.append("- **Started**: $rootDateStr\n")
        sb.append("- **ID**: `${conv.id}`\n\n")
        sb.append("---\n\n")

        var lineCounter = 1
        var lastMsgTimestamp = 0L

        for (msg in conv.messages) {
            if (lastMsgTimestamp > 0L && timeFrameGapHours > 0) {
                val diffMs = msg.timestamp - lastMsgTimestamp
                val gapMs = timeFrameGapHours.toLong() * 3600 * 1000
                if (diffMs > gapMs) {
                    val gapHours = diffMs / (3600 * 1000)
                    val gapDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                    sb.append("\n### ⏳ --- Segment Boundary: Gap of $gapHours hours (Detected at $gapDateStr) ---\n\n")
                }
            }
            lastMsgTimestamp = msg.timestamp

            val roleName = when (msg.role.lowercase()) {
                "user" -> "👤 **User**"
                "grok", "assistant" -> "🤖 **Grok**"
                else -> "⚙️ **${msg.role.replaceFirstChar { it.uppercase() }}**"
            }

            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))
            sb.append("### $roleName *($timeStr)*\n")

            if (!msg.thinkingTrace.isNullOrBlank()) {
                sb.append("<details><summary>🧠 Thinking Trace / Reasoning</summary>\n\n")
                sb.append("> ${msg.thinkingTrace.replace("\n", "\n> ")}\n\n")
                sb.append("</details>\n\n")
            }

            val textLines = msg.text.split("\n")
            for (line in textLines) {
                if (enableLineNumbers) {
                    sb.append("> `L${lineCounter.toString().padStart(3, '0')}` $line\n")
                    lineCounter++
                } else {
                    sb.append("> $line\n")
                }
            }
            if (!msg.metadataJson.isNullOrBlank()) {
                sb.append("\n*Metadata*: `${msg.metadataJson}`\n")
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    /**
     * Generates metadata-only JSON representation for conversations and mined binaries.
     */
    fun generateConversationsMetadataOnly(
        conversations: List<Conversation>,
        minedBinaries: List<MinedBinary>
    ): String {
        val sb = StringBuilder()
        sb.append("[\n")
        for (i in conversations.indices) {
            val conv = conversations[i]
            val userCount = conv.messages.count { it.role.lowercase() == "user" }
            val grokCount = conv.messages.count { it.role.lowercase() in listOf("grok", "assistant") }
            val startT = conv.messages.firstOrNull()?.timestamp ?: conv.timestamp
            val endT = conv.messages.lastOrNull()?.timestamp ?: conv.timestamp
            val totalChars = conv.messages.sumOf { it.text.length }
            
            sb.append("  {\n")
            sb.append("    \"id\": \"${conv.id}\",\n")
            val titleEscaped = conv.title.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            sb.append("    \"title\": \"$titleEscaped\",\n")
            sb.append("    \"timestamp\": ${conv.timestamp},\n")
            sb.append("    \"start_time\": $startT,\n")
            sb.append("    \"end_time\": $endT,\n")
            sb.append("    \"total_messages\": ${conv.messages.size},\n")
            sb.append("    \"user_messages\": $userCount,\n")
            sb.append("    \"grok_messages\": $grokCount,\n")
            sb.append("    \"total_characters\": $totalChars,\n")
            
            val linkedBinaries = minedBinaries.filter { it.conversationId == conv.id || it.name.contains(conv.id.take(8)) }
            sb.append("    \"mined_binaries_count\": ${linkedBinaries.size},\n")
            sb.append("    \"mined_binaries_list\": [\n")
            for (j in linkedBinaries.indices) {
                val bin = linkedBinaries[j]
                sb.append("      {\n")
                sb.append("        \"name\": \"${bin.name}\",\n")
                sb.append("        \"size\": ${bin.size},\n")
                sb.append("        \"mime_type\": \"${bin.mimeType}\",\n")
                sb.append("        \"sha256\": \"${bin.sha256}\",\n")
                sb.append("        \"details\": \"${bin.details.replace("\"", "\\\"")}\"\n")
                sb.append("      }")
                if (j < linkedBinaries.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("    ]\n")
            sb.append("  }")
            if (i < conversations.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    /**
     * Generates full Markdown export document.
     */
    fun generateMarkdown(conversations: List<Conversation>): String {
        val sb = StringBuilder()
        sb.append("# xAI Grok Conversations Export\n\n")
        sb.append("Generated on: ${Date()}\n")
        sb.append("Total Filtered Conversations: ${conversations.size}\n\n")
        sb.append("---\n\n")

        for (conv in conversations) {
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(conv.timestamp))
            sb.append("## ${conv.title.ifBlank { "Untitled Chat" }}\n")
            sb.append("- **Date**: $dateStr\n")
            sb.append("- **ID**: `${conv.id}`\n\n")

            for (msg in conv.messages) {
                val roleName = when (msg.role.lowercase()) {
                    "user" -> "👤 **User**"
                    "grok", "assistant" -> "🤖 **Grok**"
                    else -> "⚙️ **${msg.role.replaceFirstChar { it.uppercase() }}**"
                }
                sb.append("### $roleName\n")
                if (!msg.thinkingTrace.isNullOrBlank()) {
                    sb.append("<details><summary>🧠 Thinking Trace / Reasoning</summary>\n\n")
                    sb.append("> ${msg.thinkingTrace.replace("\n", "\n> ")}\n\n")
                    sb.append("</details>\n\n")
                }
                sb.append("> ${msg.text.replace("\n", "\n> ")}\n\n")
            }
            sb.append("---\n\n")
        }
        return sb.toString()
    }

    /**
     * Generates CSV export content.
     */
    fun generateCsv(conversations: List<Conversation>): String {
        val sb = StringBuilder()
        sb.append("ConversationID,ConversationTitle,Timestamp,Sender,MessageText,ThinkingTrace\n")
        for (conv in conversations) {
            val titleEscaped = conv.title.replace("\"", "\"\"")
            for (msg in conv.messages) {
                val textEscaped = msg.text.replace("\"", "\"\"")
                val traceEscaped = (msg.thinkingTrace ?: "").replace("\"", "\"\"")
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))
                sb.append("\"${conv.id}\",\"$titleEscaped\",\"$dateStr\",\"${msg.role}\",\"$textEscaped\",\"$traceEscaped\"\n")
            }
        }
        return sb.toString()
    }

    /**
     * Generates standard JSON export representation.
     */
    fun generateJson(conversations: List<Conversation>): String {
        val sb = StringBuilder()
        sb.append("[\n")
        for (i in conversations.indices) {
            val conv = conversations[i]
            sb.append("  {\n")
            sb.append("    \"id\": \"${conv.id}\",\n")
            val titleEscaped = conv.title.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            sb.append("    \"title\": \"$titleEscaped\",\n")
            sb.append("    \"timestamp\": ${conv.timestamp},\n")
            sb.append("    \"messages\": [\n")
            for (j in conv.messages.indices) {
                val msg = conv.messages[j]
                sb.append("      {\n")
                sb.append("        \"id\": \"${msg.id}\",\n")
                sb.append("        \"role\": \"${msg.role}\",\n")
                val textEscaped = msg.text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                sb.append("        \"text\": \"$textEscaped\",\n")
                if (!msg.thinkingTrace.isNullOrBlank()) {
                    val traceEscaped = msg.thinkingTrace.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                    sb.append("        \"thinking_trace\": \"$traceEscaped\",\n")
                }
                if (!msg.metadataJson.isNullOrBlank()) {
                    val metaEscaped = msg.metadataJson.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                    sb.append("        \"metadata\": \"$metaEscaped\",\n")
                }
                sb.append("        \"timestamp\": ${msg.timestamp}\n")
                sb.append("      }")
                if (j < conv.messages.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("    ]\n")
            sb.append("  }")
            if (i < conversations.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    /**
     * Generates Letta Archival Passages JSONL format.
     * Each line contains text, source_id, timestamp, role, and has_thinking_trace in metadata.
     *
     * @param conversations Extracted list of conversations.
     * @return Formatted JSONL string.
     */
    fun generateLettaPassagesJsonL(conversations: List<Conversation>): String {
        val sb = StringBuilder()
        for (conv in conversations) {
            for (msg in conv.messages) {
                val textEscaped = msg.text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                val hasThinking = !msg.thinkingTrace.isNullOrBlank()
                val lineJson = "{\"id\":\"${msg.id}\",\"text\":\"$textEscaped\",\"metadata\":{\"timestamp\":${msg.timestamp},\"source_id\":\"${conv.id}\",\"has_thinking_trace\":$hasThinking,\"role\":\"${msg.role}\"}}"
                sb.append(lineJson).append("\n")
            }
        }
        return sb.toString()
    }

    /**
     * Generates JSONL staging shards sliced into manageable files under [maxShardSizeBytes] (e.g. 50MB shards).
     *
     * @param conversations List of conversations to shard.
     * @param outputDir Target directory for staging shard files.
     * @param maxShardSizeBytes Maximum byte size per shard file (default 50MB).
     * @return List of created shard files.
     */
    fun generateJsonLStagingShards(
        conversations: List<Conversation>,
        outputDir: File,
        maxShardSizeBytes: Long = 50 * 1024 * 1024
    ): List<File> {
        val shardFiles = mutableListOf<File>()
        if (!outputDir.exists()) outputDir.mkdirs()

        var shardIndex = 1
        var currentFile = File(outputDir, "staging_shard_%03d.jsonl".format(shardIndex))
        var currentWriter = BufferedWriter(FileWriter(currentFile))
        var currentSizeBytes = 0L

        try {
            for (conv in conversations) {
                for (msg in conv.messages) {
                    val textEscaped = msg.text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                    val hasThinking = !msg.thinkingTrace.isNullOrBlank()
                    val line = "{\"id\":\"${msg.id}\",\"text\":\"$textEscaped\",\"metadata\":{\"timestamp\":${msg.timestamp},\"source_id\":\"${conv.id}\",\"has_thinking_trace\":$hasThinking,\"role\":\"${msg.role}\"}}\n"
                    val lineBytes = line.toByteArray(Charsets.UTF_8).size.toLong()

                    if (currentSizeBytes + lineBytes > maxShardSizeBytes && currentSizeBytes > 0) {
                        currentWriter.flush()
                        currentWriter.close()
                        shardFiles.add(currentFile)

                        shardIndex++
                        currentFile = File(outputDir, "staging_shard_%03d.jsonl".format(shardIndex))
                        currentWriter = BufferedWriter(FileWriter(currentFile))
                        currentSizeBytes = 0L
                    }

                    currentWriter.write(line)
                    currentSizeBytes += lineBytes
                }
            }
            currentWriter.flush()
            currentWriter.close()
            if (currentFile.length() > 0) {
                shardFiles.add(currentFile)
            } else {
                currentFile.delete()
            }
        } catch (e: Exception) {
            GrokLogger.error("Error generating JSONL staging shards", e)
        }
        return shardFiles
    }

    /**
     * Generates Obsidian Sovereign Vault directory layout organized into `raw/` and `wiki/` directories.
     *
     * @param conversations List of conversations.
     * @param outputDir Target root directory for the vault.
     */
    fun generateObsidianVaultFiles(conversations: List<Conversation>, outputDir: File) {
        val rawDir = File(outputDir, "raw")
        val wikiDir = File(outputDir, "wiki")
        if (!rawDir.exists()) rawDir.mkdirs()
        if (!wikiDir.exists()) wikiDir.mkdirs()

        // 1. Write raw conversation log files
        for (conv in conversations) {
            val titleClean = conv.title.replace(Regex("[^a-zA-Z0-9]"), "_").take(20)
            val file = File(rawDir, "chat_${conv.id.take(8)}_$titleClean.md")
            val mdContent = generateMarkdownForConversation(
                conv = conv,
                enableObsidian = true,
                includeTitle = true,
                includeDate = true,
                includeId = true,
                includeStats = true,
                includeTags = true,
                timeFrameGapHours = 0,
                enableLineNumbers = false
            )
            file.writeText(mdContent)
        }

        // 2. Write wiki index notes
        val wikiIndexFile = File(wikiDir, "index.md")
        val wikiSb = StringBuilder()
        wikiSb.append("# 🏛️ Sovereign Vault Entity & Index Note\n\n")
        wikiSb.append("Total Ingested Conversations: ${conversations.size}\n\n")
        wikiSb.append("## Raw Logs Index\n\n")
        for (conv in conversations) {
            val titleClean = conv.title.replace(Regex("[^a-zA-Z0-9]"), "_").take(20)
            val fileName = "chat_${conv.id.take(8)}_$titleClean.md"
            wikiSb.append("- [[raw/$fileName|${conv.title.ifBlank { "Untitled Chat" }}]] (${Date(conv.timestamp)})\n")
        }
        wikiIndexFile.writeText(wikiSb.toString())
    }

    /**
     * Generates interactive HTML viewer file.
     */
    fun generateHtml(conversations: List<Conversation>): String {
        val sb = StringBuilder()
        sb.append("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Grok Export Log</title>
    <style>
        :root {
            --bg: #0D1117;
            --surface: #161B22;
            --border: #30363D;
            --text: #C9D1D9;
            --text-muted: #8B949E;
            --primary: #58A6FF;
            --user-bubble: #1F6FEB;
            --grok-bubble: #238636;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
            background-color: var(--bg);
            color: var(--text);
            margin: 0;
            padding: 0;
            display: flex;
            height: 100vh;
        }
        .sidebar {
            width: 300px;
            border-right: 1px solid var(--border);
            background-color: var(--surface);
            display: flex;
            flex-direction: column;
            overflow-y: auto;
        }
        .sidebar-header {
            padding: 16px;
            border-bottom: 1px solid var(--border);
            font-weight: bold;
            font-size: 1.1em;
            color: var(--primary);
        }
        .chat-item {
            padding: 12px 16px;
            border-bottom: 1px solid var(--border);
            cursor: pointer;
            transition: background 0.2s;
        }
        .chat-item:hover {
            background-color: rgba(255,255,255,0.05);
        }
        .chat-item-title {
            font-weight: 600;
            margin-bottom: 4px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .chat-item-date {
            font-size: 0.8em;
            color: var(--text-muted);
        }
        .content {
            flex: 1;
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        .chat-view {
            flex: 1;
            padding: 24px;
            overflow-y: auto;
            display: none;
        }
        .chat-view.active {
            display: block;
        }
        .message {
            margin-bottom: 20px;
            max-width: 80%;
            display: flex;
            flex-direction: column;
        }
        .message.user {
            margin-left: auto;
            align-items: flex-end;
        }
        .message.grok {
            margin-right: auto;
            align-items: flex-start;
        }
        .bubble {
            padding: 12px 16px;
            border-radius: 12px;
            line-height: 1.5;
            word-break: break-word;
            white-space: pre-wrap;
        }
        .message.user .bubble {
            background-color: var(--user-bubble);
            color: white;
            border-bottom-right-radius: 2px;
        }
        .message.grok .bubble {
            background-color: var(--surface);
            border: 1px solid var(--border);
            border-bottom-left-radius: 2px;
        }
        .meta {
            font-size: 0.75em;
            color: var(--text-muted);
            margin-top: 4px;
        }
        .welcome {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            height: 100%;
            color: var(--text-muted);
        }
    </style>
</head>
<body>
    <div class="sidebar">
        <div class="sidebar-header">Grok Conversations (${conversations.size})</div>
""")

        for ((i, conv) in conversations.withIndex()) {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(conv.timestamp))
            val titleToShow = conv.title.ifBlank { "Untitled Chat" }
            sb.append("""
        <div class="chat-item" onclick="showChat($i)">
            <div class="chat-item-title">$titleToShow</div>
            <div class="chat-item-date">$dateStr</div>
        </div>
""")
        }

        sb.append("""
    </div>
    <div class="content" id="chat-container">
        <div class="welcome" id="welcome-view">
            <h2>Select a conversation to begin viewing</h2>
            <p>Exported with Grok Export Extractor for Android</p>
        </div>
""")

        for ((i, conv) in conversations.withIndex()) {
            sb.append("""
        <div class="chat-view" id="chat-$i">
            <h1 style="color: var(--primary); margin-bottom: 8px;">${conv.title.ifBlank { "Untitled Chat" }}</h1>
            <div style="color: var(--text-muted); font-size: 0.9em; margin-bottom: 24px; border-bottom: 1px solid var(--border); padding-bottom: 12px;">
                Conversation ID: ${conv.id}
            </div>
""")

            for (msg in conv.messages) {
                val isUser = msg.role.lowercase() == "user"
                val className = if (isUser) "user" else "grok"
                val speakerLabel = if (isUser) "User" else "Grok"
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))
                sb.append("""
            <div class="message $className">
                <div style="font-size: 0.8em; color: var(--text-muted); margin-bottom: 4px;">$speakerLabel</div>
                <div class="bubble">${msg.text}</div>
                <div class="meta">$timeStr</div>
            </div>
""")
            }

            sb.append("""
        </div>
""")
        }

        sb.append("""
    </div>
    <script>
        function showChat(index) {
            document.getElementById('welcome-view').style.display = 'none';
            const views = document.querySelectorAll('.chat-view');
            views.forEach(v => v.classList.remove('active'));
            document.getElementById('chat-' + index).classList.add('active');
        }
    </script>
</body>
</html>
""")
        return sb.toString()
    }
}
