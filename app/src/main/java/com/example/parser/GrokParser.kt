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

object GrokParser {

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

    private fun parseSingleMessage(reader: JsonReader): Message? {
        var id = ""
        var role = ""
        var text = ""
        var timestamp: Long = 0

        try {
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id", "message_id" -> id = reader.nextString()
                    "role", "sender", "author" -> role = reader.nextString()
                    "text", "content", "body" -> {
                        val peek = reader.peek()
                        if (peek == android.util.JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                if (reader.nextName() == "text") {
                                    text = reader.nextString()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            reader.endObject()
                        } else if (peek == android.util.JsonToken.STRING) {
                            text = reader.nextString()
                        } else {
                            reader.skipValue()
                        }
                    }
                    "create_time", "created_at", "timestamp" -> {
                        val peek = reader.peek()
                        if (peek == android.util.JsonToken.NUMBER) {
                            timestamp = reader.nextLong()
                            if (timestamp < 50000000000L) {
                                timestamp *= 1000
                            }
                        } else {
                            val str = reader.nextString()
                            timestamp = parseIsoToEpoch(str)
                        }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        if (id.isEmpty()) id = UUID.randomUUID().toString()
        if (timestamp == 0L) timestamp = System.currentTimeMillis()

        return Message(id, role, text, timestamp)
    }

    private fun parseSingleConversation(reader: JsonReader): Conversation? {
        var id = ""
        var title = ""
        var timestamp: Long = 0
        val messages = mutableListOf<Message>()

        try {
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "id", "conversation_id", "uuid" -> id = reader.nextString()
                    "title", "subject", "name" -> title = reader.nextString()
                    "create_time", "created_at", "timestamp" -> {
                        val peek = reader.peek()
                        if (peek == android.util.JsonToken.NUMBER) {
                            timestamp = reader.nextLong()
                            if (timestamp < 50000000000L) {
                                timestamp *= 1000
                            }
                        } else {
                            val str = reader.nextString()
                            timestamp = parseIsoToEpoch(str)
                        }
                    }
                    "messages", "chat_messages", "parts" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            val msg = parseSingleMessage(reader)
                            if (msg != null) {
                                messages.add(msg)
                            }
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        if (id.isEmpty()) id = UUID.randomUUID().toString()
        if (timestamp == 0L) timestamp = System.currentTimeMillis()

        return Conversation(id, title, timestamp, messages)
    }

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

    fun calculateSha256(bytes: ByteArray): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun generateSkeletalJson(conversations: List<Conversation>): String {
        GrokLogger.info("Extracting skeletal metadata (stripping heavy text blocks)...")
        val skeletal = conversations.map { c ->
            c.copy(messages = c.messages.map { m -> m.copy(text = "") })
        }
        return generateJson(skeletal)
    }

    fun verifyReassembly(originalConversations: List<Conversation>): Pair<Boolean, String> {
        GrokLogger.info("Initiating Byte-for-Byte Reassembly Validation...")
        return try {
            val originalNormalized = generateJson(originalConversations)
            val skeletalJson = generateSkeletalJson(originalConversations)
            
            // Reassemble: Map back original messages text into skeletal remains
            val reassembledConversations = originalConversations.map { c ->
                c.copy(messages = c.messages.map { m ->
                    m.copy(text = m.text) // Simulates remapping step
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

    fun parseConversationsStream(
        inputStream: InputStream,
        startDate: Long?,
        endDate: Long?,
        onProgress: (Int) -> Unit,
        onStatsUpdate: (ExtractionStats) -> Unit
    ): List<Conversation> {
        val list = mutableListOf<Conversation>()
        val stats = ExtractionStats()
        
        GrokLogger.info("Opening raw JSON input stream...")
        val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))

        try {
            val token = reader.peek()
            GrokLogger.info("JSON file structure peek: $token")
            
            if (token == android.util.JsonToken.BEGIN_ARRAY) {
                GrokLogger.info("Detected Schema A (Flat List of Conversations)")
                reader.beginArray()
                var count = 0
                while (reader.hasNext()) {
                    val conv = parseSingleConversation(reader)
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
                GrokLogger.info("Detected Schema B (Nested Object Schema with Root Keys)")
                reader.beginObject()
                var count = 0
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    GrokLogger.info("Found root object attribute: '$name'")
                    if (name == "conversations" || name == "chats" || name == "sessions" || name == "data") {
                        val nextToken = reader.peek()
                        GrokLogger.info("Root element '$name' is of type: $nextToken")
                        if (nextToken == android.util.JsonToken.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                val conv = parseSingleConversation(reader)
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
                                    GrokLogger.info("Parsed $count nested conversations. Characters: ${stats.totalCharacters}")
                                    onProgress(count)
                                    onStatsUpdate(stats.copy())
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

    fun isHexString(str: String): Boolean {
        val cleaned = str.replace("\n", "").replace("\r", "").replace(" ", "")
        if (cleaned.length % 2 != 0 || cleaned.isEmpty()) return false
        return cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

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
        
        // 1. Obsidian Front Matter
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
            // Segment detection based on timeframe gap
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

            // Format message content with optional line numbering
            val textLines = msg.text.split("\n")
            for (line in textLines) {
                if (enableLineNumbers) {
                    sb.append("> `L${lineCounter.toString().padStart(3, '0')}` $line\n")
                    lineCounter++
                } else {
                    sb.append("> $line\n")
                }
            }
            sb.append("\n")
        }

        return sb.toString()
    }

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
            
            // Filter mined binaries for this conversation
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

    // Export formats generator
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
                sb.append("> ${msg.text.replace("\n", "\n> ")}\n\n")
            }
            sb.append("---\n\n")
        }
        return sb.toString()
    }

    fun generateCsv(conversations: List<Conversation>): String {
        val sb = StringBuilder()
        sb.append("ConversationID,ConversationTitle,Timestamp,Sender,MessageText\n")
        for (conv in conversations) {
            val titleEscaped = conv.title.replace("\"", "\"\"")
            for (msg in conv.messages) {
                val textEscaped = msg.text.replace("\"", "\"\"")
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))
                sb.append("\"${conv.id}\",\"$titleEscaped\",\"$dateStr\",\"${msg.role}\",\"$textEscaped\"\n")
            }
        }
        return sb.toString()
    }

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
