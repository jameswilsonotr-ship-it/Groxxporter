package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.parser.Conversation
import com.example.parser.ExtractionStats
import com.example.parser.GrokParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed interface ImportState {
    object Idle : ImportState
    data class Loading(val progress: Int, val currentFile: String) : ImportState
    data class Success(val conversations: List<Conversation>, val stats: ExtractionStats) : ImportState
    data class Error(val message: String) : ImportState
}

sealed interface ExportState {
    object Idle : ExportState
    object Exporting : ExportState
    data class Success(val fileUri: Uri, val filePath: String) : ExportState
    data class Error(val message: String) : ExportState
}

class GrokViewModel : ViewModel() {

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    private val _stats = MutableStateFlow(ExtractionStats())
    val stats: StateFlow<ExtractionStats> = _stats

    private val _importProgress = MutableStateFlow(0)
    val importProgress: StateFlow<Int> = _importProgress

    var startDateFilter = MutableStateFlow<Long?>(null)
    var endDateFilter = MutableStateFlow<Long?>(null)

    var optMarkdown = MutableStateFlow(true)
    var optHtml = MutableStateFlow(true)
    var optJson = MutableStateFlow(true)
    var optCsv = MutableStateFlow(true)
    var optBinaries = MutableStateFlow(true)

    // Cached parsed list
    private var parsedConversations: List<Conversation> = emptyList()
    private var selectedSourceUri: Uri? = null

    fun resetState() {
        _importState.value = ImportState.Idle
        _exportState.value = ExportState.Idle
        _stats.value = ExtractionStats()
        _importProgress.value = 0
        parsedConversations = emptyList()
        selectedSourceUri = null
    }

    fun startImport(context: Context, uri: Uri) {
        selectedSourceUri = uri
        viewModelScope.launch {
            _importState.value = ImportState.Loading(0, "Analyzing file...")
            _importProgress.value = 0
            _stats.value = ExtractionStats()

            try {
                val resolvedConversations = withContext(Dispatchers.IO) {
                    val contentResolver = context.contentResolver
                    val fileName = getFileName(context, uri) ?: "GrokExport"

                    if (fileName.endsWith(".zip", ignoreCase = true)) {
                        // Process as ZIP
                        var foundJson = false
                        var list = emptyList<Conversation>()

                        context.contentResolver.openInputStream(uri)?.use { rawIn ->
                            ZipInputStream(BufferedInputStream(rawIn)).use { zipIn ->
                                var entry = zipIn.nextEntry
                                while (entry != null) {
                                    if (!entry.isDirectory && (entry.name.contains("conversations", ignoreCase = true) || entry.name.endsWith(".json", ignoreCase = true))) {
                                        _importState.value = ImportState.Loading(_importProgress.value, "Streaming and parsing JSON from ZIP...")
                                        list = GrokParser.parseConversationsStream(
                                            NonClosingInputStream(zipIn),
                                            startDateFilter.value,
                                            endDateFilter.value,
                                            onProgress = { count ->
                                                _importProgress.value = count
                                                _importState.value = ImportState.Loading(count, "Streaming JSON (${count} conversations)...")
                                            },
                                            onStatsUpdate = { stats ->
                                                _stats.value = stats
                                            }
                                        )
                                        foundJson = true
                                        break
                                    }
                                    entry = zipIn.nextEntry
                                }
                            }
                        }

                        if (!foundJson) {
                            throw Exception("No valid conversation JSON file (*.json) found inside the selected ZIP archive.")
                        }
                        list
                    } else {
                        // Process as raw JSON
                        _importState.value = ImportState.Loading(0, "Streaming and parsing raw JSON file...")
                        context.contentResolver.openInputStream(uri)?.use { rawIn ->
                            GrokParser.parseConversationsStream(
                                BufferedInputStream(rawIn),
                                startDateFilter.value,
                                endDateFilter.value,
                                onProgress = { count ->
                                    _importProgress.value = count
                                    _importState.value = ImportState.Loading(count, "Streaming JSON (${count} conversations)...")
                                },
                                onStatsUpdate = { stats ->
                                    _stats.value = stats
                                }
                            )
                        } ?: throw Exception("Failed to open selected JSON file.")
                    }
                }

                parsedConversations = resolvedConversations
                _importState.value = ImportState.Success(parsedConversations, _stats.value)

            } catch (e: Exception) {
                e.printStackTrace()
                _importState.value = ImportState.Error(e.localizedMessage ?: "Unknown parsing error.")
            }
        }
    }

    fun startExport(context: Context) {
        val srcUri = selectedSourceUri ?: return
        if (parsedConversations.isEmpty()) return

        viewModelScope.launch {
            _exportState.value = ExportState.Exporting

            try {
                val outputUri = withContext(Dispatchers.IO) {
                    val cacheDir = context.cacheDir
                    val outputZipFile = File(cacheDir, "grok_processed_export.zip")
                    if (outputZipFile.exists()) outputZipFile.delete()

                    ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zipOut ->
                        // 1. Markdown
                        if (optMarkdown.value) {
                            zipOut.putNextEntry(ZipEntry("conversations.md"))
                            val mdText = GrokParser.generateMarkdown(parsedConversations)
                            zipOut.write(mdText.toByteArray(Charsets.UTF_8))
                            zipOut.closeEntry()
                        }

                        // 2. HTML
                        if (optHtml.value) {
                            zipOut.putNextEntry(ZipEntry("conversations.html"))
                            val htmlText = GrokParser.generateHtml(parsedConversations)
                            zipOut.write(htmlText.toByteArray(Charsets.UTF_8))
                            zipOut.closeEntry()
                        }

                        // 3. JSON
                        if (optJson.value) {
                            zipOut.putNextEntry(ZipEntry("conversations.json"))
                            val jsonText = GrokParser.generateJson(parsedConversations)
                            zipOut.write(jsonText.toByteArray(Charsets.UTF_8))
                            zipOut.closeEntry()
                        }

                        // 4. CSV
                        if (optCsv.value) {
                            zipOut.putNextEntry(ZipEntry("conversations.csv"))
                            val csvText = GrokParser.generateCsv(parsedConversations)
                            zipOut.write(csvText.toByteArray(Charsets.UTF_8))
                            zipOut.closeEntry()
                        }

                        // 5. Binary Extraction (if selected and source was a ZIP file)
                        val fileName = getFileName(context, srcUri) ?: "GrokExport"
                        if (optBinaries.value && fileName.endsWith(".zip", ignoreCase = true)) {
                            context.contentResolver.openInputStream(srcUri)?.use { rawIn ->
                                ZipInputStream(BufferedInputStream(rawIn)).use { zipIn ->
                                    var entry = zipIn.nextEntry
                                    var attachmentCount = 0
                                    var decodedCount = 0

                                    while (entry != null) {
                                        // Look for any files that might be binary content (excluding the main JSON files)
                                        if (!entry.isDirectory && !entry.name.endsWith(".json", ignoreCase = true)) {
                                            val nameOnly = File(entry.name).name
                                            if (nameOnly.startsWith("content", ignoreCase = true) || entry.name.contains("binary", ignoreCase = true)) {
                                                // Read entry bytes
                                                val entryBytes = zipIn.readBytes()
                                                if (entryBytes.isNotEmpty()) {
                                                    var isHexDecoded = false
                                                    var finalBytes = entryBytes

                                                    // Try decoding as hex string first
                                                    try {
                                                        val str = String(entryBytes, Charsets.UTF_8).trim()
                                                        if (GrokParser.isHexString(str)) {
                                                            finalBytes = GrokParser.hexToBytes(str)
                                                            isHexDecoded = true
                                                            decodedCount++
                                                        }
                                                    } catch (e: Exception) {}

                                                    val detectedExt = GrokParser.detectExtension(finalBytes)
                                                    val finalFileName = "attachments/${nameOnly}_decoded.${detectedExt}"

                                                    zipOut.putNextEntry(ZipEntry(finalFileName))
                                                    zipOut.write(finalBytes)
                                                    zipOut.closeEntry()

                                                    attachmentCount++
                                                }
                                            }
                                        }
                                        entry = zipIn.nextEntry
                                    }

                                    // Update stats
                                    val currentStats = _stats.value
                                    currentStats.binaryFilesProcessed = attachmentCount
                                    currentStats.hexFilesDecoded = decodedCount
                                    _stats.value = currentStats
                                }
                            }
                        }
                    }

                    // Shareable Uri via FileProvider or simple file scheme
                    val authority = "${context.packageName}.fileprovider"
                    androidx.core.content.FileProvider.getUriForFile(context, authority, outputZipFile)
                }

                _exportState.value = ExportState.Success(outputUri, "grok_processed_export.zip")

            } catch (e: Exception) {
                e.printStackTrace()
                _exportState.value = ExportState.Error(e.localizedMessage ?: "Unknown export error.")
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name
    }

    // A helper stream that ignores close() calls to keep the ZipInputStream alive
    private class NonClosingInputStream(private val stream: InputStream) : InputStream() {
        override fun read(): Int = stream.read()
        override fun read(b: ByteArray): Int = stream.read(b)
        override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
        override fun skip(n: Long): Long = stream.skip(n)
        override fun available(): Int = stream.available()
        override fun mark(readlimit: Int) = stream.mark(readlimit)
        override fun reset() = stream.reset()
        override fun markSupported(): Boolean = stream.markSupported()
        override fun close() {
            // Do nothing
        }
    }
}
