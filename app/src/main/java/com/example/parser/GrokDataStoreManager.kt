package com.example.parser

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.grokDataStore by preferencesDataStore(name = "grok_engine_preferences")

data class GrokPersistedSettings(
    val isPeriodicAutoSaveEnabled: Boolean = true,
    val autoSaveInterval: Int = 25,
    val optMarkdown: Boolean = true,
    val optHtml: Boolean = true,
    val optJson: Boolean = true,
    val optCsv: Boolean = true,
    val optBinaries: Boolean = true,
    val piiScrubbingEnabled: Boolean = false,
    val preserveFileDates: Boolean = true,
    val timeFrameGapHours: Int = 24,
    val enableBatchMode: Boolean = false,
    val batchSize: Int = 5,
    val startDateFilter: Long? = null,
    val endDateFilter: Long? = null,
    val activeSchemaPackId: String = "system_default_grok_v1",
    val activeSchemaVersion: String = "1.0.0",
    val lastParsedConversationCount: Int = 0,
    val lastParsedMessageCount: Int = 0,
    val lastParsedTimestamp: Long = 0L,
    val lastAutoSaveStatus: String = "DataStore Ready"
)

class GrokDataStoreManager(private val context: Context) {

    companion object {
        private val IS_AUTO_SAVE_ENABLED = booleanPreferencesKey("is_auto_save_enabled")
        private val AUTO_SAVE_INTERVAL = intPreferencesKey("auto_save_interval")
        private val OPT_MARKDOWN = booleanPreferencesKey("opt_markdown")
        private val OPT_HTML = booleanPreferencesKey("opt_html")
        private val OPT_JSON = booleanPreferencesKey("opt_json")
        private val OPT_CSV = booleanPreferencesKey("opt_csv")
        private val OPT_BINARIES = booleanPreferencesKey("opt_binaries")
        private val PII_SCRUBBING_ENABLED = booleanPreferencesKey("pii_scrubbing_enabled")
        private val PRESERVE_FILE_DATES = booleanPreferencesKey("preserve_file_dates")
        private val TIME_FRAME_GAP_HOURS = intPreferencesKey("time_frame_gap_hours")
        private val ENABLE_BATCH_MODE = booleanPreferencesKey("enable_batch_mode")
        private val BATCH_SIZE = intPreferencesKey("batch_size")
        private val START_DATE_FILTER = longPreferencesKey("start_date_filter")
        private val END_DATE_FILTER = longPreferencesKey("end_date_filter")
        private val ACTIVE_SCHEMA_PACK_ID = stringPreferencesKey("active_schema_pack_id")
        private val ACTIVE_SCHEMA_VERSION = stringPreferencesKey("active_schema_version")
        private val LAST_PARSED_CONVERSATION_COUNT = intPreferencesKey("last_parsed_conversation_count")
        private val LAST_PARSED_MESSAGE_COUNT = intPreferencesKey("last_parsed_message_count")
        private val LAST_PARSED_TIMESTAMP = longPreferencesKey("last_parsed_timestamp")
        private val LAST_AUTO_SAVE_STATUS = stringPreferencesKey("last_auto_save_status")
    }

    val settingsFlow: Flow<GrokPersistedSettings> = context.grokDataStore.data.map { prefs ->
        GrokPersistedSettings(
            isPeriodicAutoSaveEnabled = prefs[IS_AUTO_SAVE_ENABLED] ?: true,
            autoSaveInterval = prefs[AUTO_SAVE_INTERVAL] ?: 25,
            optMarkdown = prefs[OPT_MARKDOWN] ?: true,
            optHtml = prefs[OPT_HTML] ?: true,
            optJson = prefs[OPT_JSON] ?: true,
            optCsv = prefs[OPT_CSV] ?: true,
            optBinaries = prefs[OPT_BINARIES] ?: true,
            piiScrubbingEnabled = prefs[PII_SCRUBBING_ENABLED] ?: false,
            preserveFileDates = prefs[PRESERVE_FILE_DATES] ?: true,
            timeFrameGapHours = prefs[TIME_FRAME_GAP_HOURS] ?: 24,
            enableBatchMode = prefs[ENABLE_BATCH_MODE] ?: false,
            batchSize = prefs[BATCH_SIZE] ?: 5,
            startDateFilter = prefs[START_DATE_FILTER]?.takeIf { it > 0 },
            endDateFilter = prefs[END_DATE_FILTER]?.takeIf { it > 0 },
            activeSchemaPackId = prefs[ACTIVE_SCHEMA_PACK_ID] ?: "system_default_grok_v1",
            activeSchemaVersion = prefs[ACTIVE_SCHEMA_VERSION] ?: "1.0.0",
            lastParsedConversationCount = prefs[LAST_PARSED_CONVERSATION_COUNT] ?: 0,
            lastParsedMessageCount = prefs[LAST_PARSED_MESSAGE_COUNT] ?: 0,
            lastParsedTimestamp = prefs[LAST_PARSED_TIMESTAMP] ?: 0L,
            lastAutoSaveStatus = prefs[LAST_AUTO_SAVE_STATUS] ?: "DataStore Standby"
        )
    }

    suspend fun saveAutoSaveSettings(enabled: Boolean, interval: Int) {
        context.grokDataStore.edit { prefs ->
            prefs[IS_AUTO_SAVE_ENABLED] = enabled
            prefs[AUTO_SAVE_INTERVAL] = interval
        }
    }

    suspend fun saveExportFilterPreferences(
        optMarkdown: Boolean,
        optHtml: Boolean,
        optJson: Boolean,
        optCsv: Boolean,
        optBinaries: Boolean,
        piiScrubbingEnabled: Boolean,
        preserveFileDates: Boolean,
        timeFrameGapHours: Int,
        enableBatchMode: Boolean,
        batchSize: Int
    ) {
        context.grokDataStore.edit { prefs ->
            prefs[OPT_MARKDOWN] = optMarkdown
            prefs[OPT_HTML] = optHtml
            prefs[OPT_JSON] = optJson
            prefs[OPT_CSV] = optCsv
            prefs[OPT_BINARIES] = optBinaries
            prefs[PII_SCRUBBING_ENABLED] = piiScrubbingEnabled
            prefs[PRESERVE_FILE_DATES] = preserveFileDates
            prefs[TIME_FRAME_GAP_HOURS] = timeFrameGapHours
            prefs[ENABLE_BATCH_MODE] = enableBatchMode
            prefs[BATCH_SIZE] = batchSize
        }
    }

    suspend fun saveDateFilters(startDate: Long?, endDate: Long?) {
        context.grokDataStore.edit { prefs ->
            if (startDate != null) prefs[START_DATE_FILTER] = startDate else prefs.remove(START_DATE_FILTER)
            if (endDate != null) prefs[END_DATE_FILTER] = endDate else prefs.remove(END_DATE_FILTER)
        }
    }

    suspend fun saveSchemaPackSelection(packId: String, version: String) {
        context.grokDataStore.edit { prefs ->
            prefs[ACTIVE_SCHEMA_PACK_ID] = packId
            prefs[ACTIVE_SCHEMA_VERSION] = version
        }
    }

    suspend fun saveParsingProgressSnapshot(convCount: Int, msgCount: Int, status: String) {
        context.grokDataStore.edit { prefs ->
            prefs[LAST_PARSED_CONVERSATION_COUNT] = convCount
            prefs[LAST_PARSED_MESSAGE_COUNT] = msgCount
            prefs[LAST_PARSED_TIMESTAMP] = System.currentTimeMillis()
            prefs[LAST_AUTO_SAVE_STATUS] = status
        }
    }
}
