package com.example.parser

/**
 * Represents a single message within an xAI Grok or chat conversation payload.
 *
 * @property id Unique identifier for the message.
 * @property role Role of the message author (e.g. "user", "grok", "assistant", "system").
 * @property text The primary textual content of the message.
 * @property timestamp Unix epoch timestamp in milliseconds when the message was created.
 * @property thinkingTrace Optional internal reasoning or chain-of-thought trace extracted from the model payload.
 * @property metadataJson Optional serialized metadata JSON string containing supplementary payload details.
 */
data class Message(
    val id: String,
    val role: String,
    val text: String,
    val timestamp: Long,
    val thinkingTrace: String? = null,
    val metadataJson: String? = null
)

/**
 * Represents a complete conversation thread containing ordered messages.
 *
 * @property id Unique identifier for the conversation thread.
 * @property title Title or subject header of the conversation thread.
 * @property timestamp Unix epoch timestamp in milliseconds when the thread was initialized.
 * @property messages Chronological list of [Message] objects in the conversation thread.
 */
data class Conversation(
    val id: String,
    val title: String,
    val timestamp: Long,
    val messages: List<Message>
)

/**
 * Aggregated telemetry and extraction statistics for an import run.
 *
 * @property totalConversations Total number of conversation objects encountered.
 * @property filteredConversations Total number of conversations matching date/filter criteria.
 * @property totalUserMessages Count of messages originating from user prompts.
 * @property totalGrokMessages Count of messages originating from Grok/Assistant responses.
 * @property totalCharacters Total cumulative character count across extracted messages.
 * @property dateMin Oldest message creation timestamp encountered.
 * @property dateMax Most recent message creation timestamp encountered.
 * @property binaryFilesProcessed Total count of binary attachment files processed.
 * @property hexFilesDecoded Total count of hex-encoded attachment blobs decoded.
 */
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

/**
 * Represents an extracted or mined binary attachment file embedded in the conversational payload.
 *
 * @property name File name or identifier of the binary attachment.
 * @property size Size in bytes.
 * @property mimeType Detected MIME content type.
 * @property sha256 Computed SHA-256 hash checksum for cryptographic verification.
 * @property details Human-readable metadata description (e.g., "PNG Image 1920x1080").
 * @property conversationId Associated conversation ID if linked to a specific thread.
 * @property path Local file path on disk.
 */
data class MinedBinary(
    val name: String,
    val size: Long,
    val mimeType: String,
    val sha256: String,
    val details: String,
    val conversationId: String? = null,
    val path: String = ""
)

/**
 * Supported target export formats for Project Iron Pearl data pipeline outputs.
 */
enum class ExportTargetFormat {
    /** Standard formatted Markdown conversation logs */
    MARKDOWN,

    /** Fully compliant JSON export containing conversation arrays and messages */
    JSON,

    /** Letta Archival Passages JSONL staging format with metadata and source IDs */
    LETTA_PASSAGES,

    /** Obsidian Sovereign Vault directory structure with raw logs and wiki indexes */
    OBSIDIAN_VAULT
}

/**
 * Represents a periodic auto-save checkpoint snapshot for in-flight parsing operations.
 */
data class AutoSaveCheckpoint(
    val id: String,
    val timestamp: Long,
    val conversationCount: Int,
    val messageCount: Int,
    val totalCharacters: Long,
    val fileSize: Long,
    val filePath: String,
    val jobLabel: String
)

/**
 * Represents a single JSON field definition discovered or mapped within a conversational payload.
 */
data class SchemaFieldDefinition(
    val originalKey: String,
    val mappedKey: String,
    val dataType: String,
    val isMandatory: Boolean = false,
    val isEnabledForExport: Boolean = true,
    val sampleValue: String = "",
    val description: String = ""
)

/**
 * Versioned schema definition pack containing customized field mappings and extraction toggles.
 */
data class SchemaPack(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val createdAt: Long,
    val fields: List<SchemaFieldDefinition>,
    val isSystemPreset: Boolean = false
)

/**
 * Diagnostic validation report comparing payload contents against an active SchemaPack.
 */
data class SchemaValidationReport(
    val isValid: Boolean,
    val matchPercentage: Float,
    val totalDiscoveredFields: Int,
    val matchedFieldsCount: Int,
    val missingMandatoryFields: List<String>,
    val unknownNewFields: List<String>,
    val fieldTypeMismatches: List<String>
)

/**
 * Inspection telemetry for deep JSON payload attribute analysis.
 */
data class SchemaInspectorData(
    val totalKeysInspected: Int = 0,
    val stringTypeCount: Int = 0,
    val numberTypeCount: Int = 0,
    val objectTypeCount: Int = 0,
    val arrayTypeCount: Int = 0,
    val booleanTypeCount: Int = 0,
    val maxNestingDepth: Int = 1,
    val nullabilityPercentage: Float = 0f,
    val samplePayloadPreview: String = "",
    val fieldHierarchyTree: List<String> = emptyList()
)

/**
 * Field difference report between two SchemaPack versions.
 */
data class SchemaDiffReport(
    val versionA: String,
    val versionB: String,
    val addedFields: List<SchemaFieldDefinition> = emptyList(),
    val removedFields: List<SchemaFieldDefinition> = emptyList(),
    val modifiedMappings: List<Pair<SchemaFieldDefinition, SchemaFieldDefinition>> = emptyList()
)

/**
 * Detailed telemetry metrics for visual charts and graphs.
 */
data class ExportMetricsData(
    val userMessageCount: Int = 0,
    val grokMessageCount: Int = 0,
    val systemMessageCount: Int = 0,
    val avgCharsPerMessage: Int = 0,
    val throughputMessagesPerSec: Float = 0f,
    val originalPayloadSizeBytes: Long = 0L,
    val filteredExportSizeBytes: Long = 0L,
    val payloadCompressionPercentage: Float = 0f,
    val monthlyDistribution: Map<String, Int> = emptyMap()
)

/**
 * Supported processing pass types for multi-stage ingestion.
 */
enum class PassType {
    INVENTORY_SCAN,
    HEAD_TAIL_EXTRACT,
    DETAILED_SLICE,
    COMPILATION
}

/**
 * Represents a day-level inventory snapshot from a Grok export.
 */
data class DayInventory(
    val date: String,
    val folderPath: String,
    val inventoryPath: String,
    val driveId: String? = null,
    val driveLink: String? = null,
    val fileSize: Long = 0,
    val messageCount: Int = 0,
    val processed: Boolean = false
)




