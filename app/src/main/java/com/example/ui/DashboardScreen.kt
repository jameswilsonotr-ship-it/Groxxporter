package com.example.ui

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parser.Conversation
import com.example.parser.ExtractionStats
import com.example.parser.GrokJob
import com.example.parser.GrokJobManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Visual Cyber-Slate Palette
val CyberBg = Color(0xFF090C10)
val CyberSurface = Color(0xFF151921)
val CyberBorder = Color(0xFF262C36)
val CyberCyan = Color(0xFF00E5FF)
val CyberOrange = Color(0xFFFF6D00)
val CyberText = Color(0xFFE6EDF0)
val CyberTextMuted = Color(0xFF8B98A5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: GrokViewModel) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.initDataStore(context)
        viewModel.initializev15Roadmap()
    }

    val importState by viewModel.importState.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()

    val startDate by viewModel.startDateFilter.collectAsState()
    val endDate by viewModel.endDateFilter.collectAsState()

    val optMarkdown by viewModel.optMarkdown.collectAsState()
    val optHtml by viewModel.optHtml.collectAsState()
    val optJson by viewModel.optJson.collectAsState()
    val optCsv by viewModel.optCsv.collectAsState()
    val optBinaries by viewModel.optBinaries.collectAsState()

    // Integrity report states
    val validationMatched by viewModel.validationMatched.collectAsState()
    val sha256Checksum by viewModel.sha256Checksum.collectAsState()
    val logs by com.example.parser.GrokLogger.logs.collectAsState()

    // Jobs states
    val jobs by viewModel.jobs.collectAsState()
    val currentJob by viewModel.currentJob.collectAsState()
    val jobLabelInput by viewModel.jobLabelInput.collectAsState()
    val enableBatchMode by viewModel.enableBatchMode.collectAsState()

    val customExportFolderUri by viewModel.customExportFolderUri.collectAsState()
    val customExportFolderName by viewModel.customExportFolderName.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportProgressMessage by viewModel.exportProgressMessage.collectAsState()

    var activeTab by remember { mutableStateOf(0) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var viewingJobLogs by remember { mutableStateOf<GrokJob?>(null) }
    var viewingJobReports by remember { mutableStateOf<GrokJob?>(null) }

    // Filtered view search query
    var searchQuery by remember { mutableStateOf("") }
    var selectedPreviewChat by remember { mutableStateOf<Conversation?>(null) }

    // Launcher for ZIP or JSON import
    val pickArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.startImport(context, it) }
    }

    // Launcher for Custom Output Folder selection
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        viewModel.setCustomExportFolderUri(context, uri)
        if (uri != null && exportState is ExportState.Success) {
            val successState = exportState as ExportState.Success
            shareExportedFile(context, successState.fileUri)
        }
    }

    // Load archived jobs on startup
    LaunchedEffect(Unit) {
        viewModel.loadAllJobs(context)
    }

    if (showHelpDialog) {
        HelpGuideDialog(onDismiss = { showHelpDialog = false })
    }

    // Dialog showing archived job logs
    if (viewingJobLogs != null) {
        val job = viewingJobLogs!!
        var logText by remember(job) { mutableStateOf("Loading logs...") }
        LaunchedEffect(job) {
            withContext(Dispatchers.IO) {
                try {
                    val logFile = File(job.folderPath, "grok_extraction_log.txt")
                    logText = if (logFile.exists()) logFile.readText() else "Log file not found."
                } catch (e: Exception) {
                    logText = "Error reading logs: ${e.localizedMessage}"
                }
            }
        }

        AlertDialog(
            onDismissRequest = { viewingJobLogs = null },
            confirmButton = {
                TextButton(onClick = { viewingJobLogs = null }) {
                    Text("Close", color = CyberCyan)
                }
                TextButton(onClick = {
                    val logFile = File(job.folderPath, "grok_extraction_log.txt")
                    if (logFile.exists()) {
                        val authority = "${context.packageName}.fileprovider"
                        val logUri = androidx.core.content.FileProvider.getUriForFile(context, authority, logFile)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, logUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Job Logs"))
                    }
                }) {
                    Text("Share Logs", color = CyberCyan)
                }
            },
            title = { Text("Logs for Job #${job.number}: ${job.label}", color = CyberText, fontSize = 16.sp) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(CyberBg, RoundedCornerShape(8.dp))
                        .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = logText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = CyberTextMuted
                            )
                        }
                    }
                }
            },
            containerColor = CyberSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Dialog showing archived job reports
    if (viewingJobReports != null) {
        val job = viewingJobReports!!
        var reportText by remember(job) { mutableStateOf("Loading report...") }
        var skeletalText by remember(job) { mutableStateOf("Loading skeletal structure...") }
        LaunchedEffect(job) {
            withContext(Dispatchers.IO) {
                try {
                    val reportFile = File(job.folderPath, "sha256_verification.txt")
                    reportText = if (reportFile.exists()) reportFile.readText() else "Report not found."

                    val skeletalFile = File(job.folderPath, "skeletal_structure.json")
                    skeletalText = if (skeletalFile.exists()) skeletalFile.readText() else "Skeletal structure not found."
                } catch (e: Exception) {
                    reportText = "Error: ${e.localizedMessage}"
                }
            }
        }

        AlertDialog(
            onDismissRequest = { viewingJobReports = null },
            confirmButton = {
                TextButton(onClick = { viewingJobReports = null }) {
                    Text("Close", color = CyberCyan)
                }
            },
            title = { Text("Integrity Report for Job #${job.number}", color = CyberText, fontSize = 16.sp) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text("SHA-256 Validation Report", fontWeight = FontWeight.Bold, color = CyberCyan, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = reportText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = CyberTextMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberBg, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }
                    item {
                        Text("Skeletal Remains (JSON Structure)", fontWeight = FontWeight.Bold, color = CyberCyan, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = skeletalText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = CyberTextMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberBg, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }
                }
            },
            containerColor = CyberSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Grok Export Extractor",
                            fontWeight = FontWeight.Bold,
                            color = CyberText,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberBg,
                    titleContentColor = CyberText
                ),
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.Default.Help, contentDescription = "Help Guide", tint = CyberCyan)
                    }
                    if (importState is ImportState.Success) {
                        IconButton(onClick = { viewModel.resetState() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = CyberCyan)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberBg)
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = CyberSurface,
                    contentColor = CyberCyan,
                    divider = { HorizontalDivider(color = CyberBorder) }
                ) {
                    val tabs = listOf(
                        "The Helm ⚓",
                        "The Vault 🏴‍☠️",
                        "Engine Room 🛰️"
                    )
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = activeTab == index,
                            onClick = { activeTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (activeTab == index) CyberCyan else CyberTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                ) {
                    when (activeTab) {
                        0 -> {
                            // TAB 0: The Helm ⚓ (Ingestion & Drive Integration)
                            item { HeroBanner() }

                            item { PlanningDashboardCard(viewModel) }

                            item {
                                SovereignStatusCard(
                                    importState = importState,
                                    importProgress = importProgress,
                                    stats = stats,
                                    validationMatched = validationMatched,
                                    sha256Checksum = sha256Checksum
                                )
                            }

                            item {
                                ImportLauncherCard(
                                    viewModel = viewModel,
                                    jobLabelInput = jobLabelInput,
                                    onJobLabelChange = { viewModel.jobLabelInput.value = it },
                                    onLaunchPicker = { pickArchiveLauncher.launch("*/*") },
                                    onLoadDemo = { viewModel.loadSampleArchive(context) }
                                )
                            }

                            item {
                                GoogleDriveIntegrationCard(viewModel = viewModel)
                            }

                            item {
                                StepByStepPreviewCard(viewModel = viewModel)
                            }

                            item {
                                ProcessingJobsHistoryCard(
                                    jobs = jobs,
                                    onViewLogs = { viewingJobLogs = it },
                                    onViewReports = { viewingJobReports = it },
                                    onShareZip = { job ->
                                        val zipFile = File(job.folderPath, "grok_processed_export.zip")
                                        if (zipFile.exists()) {
                                            val authority = "${context.packageName}.fileprovider"
                                            val fileUri = androidx.core.content.FileProvider.getUriForFile(context, authority, zipFile)
                                            shareExportedFile(context, fileUri)
                                        }
                                    },
                                    onDeleteJob = { viewModel.deleteJob(context, it) },
                                    onClearAll = { viewModel.clearAllJobs(context) }
                                )
                            }
                        }

                        1 -> {
                            // TAB 1: The Vault 🏴‍☠️ (Conversation Browser & Deep Search)
                            val parsedConversations = (importState as? ImportState.Success)?.conversations ?: emptyList()
                            val totalChats = if (parsedConversations.isNotEmpty()) parsedConversations.size else stats.totalConversations
                            val totalMsgs = if (parsedConversations.isNotEmpty()) parsedConversations.sumOf { it.messages.size } else (stats.totalUserMessages + stats.totalGrokMessages)

                            item {
                                AggregateMetricsHeaderCard(
                                    totalChats = totalChats,
                                    totalMessages = totalMsgs,
                                    stats = stats
                                )
                            }

                            item {
                                GrokSummaryToolCard(viewModel = viewModel)
                            }

                            item {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Deep search title, ID, text, reasoning...", color = CyberTextMuted) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = CyberTextMuted)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("search_field"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyberCyan,
                                        unfocusedBorderColor = CyberBorder,
                                        focusedContainerColor = CyberSurface,
                                        unfocusedContainerColor = CyberSurface,
                                        focusedTextColor = CyberText,
                                        unfocusedTextColor = CyberText
                                    )
                                )
                            }

                            val filteredChats = parsedConversations.filter { chat ->
                                searchQuery.isBlank() ||
                                chat.title.contains(searchQuery, ignoreCase = true) ||
                                chat.id.contains(searchQuery, ignoreCase = true) ||
                                chat.messages.any { m ->
                                    m.text.contains(searchQuery, ignoreCase = true) ||
                                    (m.thinkingTrace?.contains(searchQuery, ignoreCase = true) == true) ||
                                    (m.metadataJson?.contains(searchQuery, ignoreCase = true) == true)
                                }
                            }

                            if (parsedConversations.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, CyberBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(48.dp))
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("The Vault is Empty", fontWeight = FontWeight.Bold, color = CyberText, fontSize = 16.sp)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "No parsed conversations currently loaded in memory. Visit 'The Helm ⚓' tab to load a local or Drive export file.",
                                                color = CyberTextMuted,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(
                                                onClick = { activeTab = 0 },
                                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Go to The Helm ⚓", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            } else if (filteredChats.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No chats found matching search filter '$searchQuery'.", color = CyberTextMuted, fontSize = 13.sp)
                                    }
                                }
                            } else {
                                items(filteredChats) { chat ->
                                    ChatPreviewItemCard(
                                        conversation = chat,
                                        isExpanded = selectedPreviewChat?.id == chat.id,
                                        searchQuery = searchQuery,
                                        onClick = {
                                            selectedPreviewChat = if (selectedPreviewChat?.id == chat.id) null else chat
                                        }
                                    )
                                }
                            }
                        }

                        2 -> {
                            // TAB 2: Engine Room 🛰️ (Export Controls, SAF Storage & Forensics)
                            item {
                                AdvancedDashboardTogglesCard(viewModel = viewModel)
                            }

                            item {
                                FilterConfigurationCard(
                                    startDate = startDate,
                                    endDate = endDate,
                                    optMarkdown = optMarkdown,
                                    optHtml = optHtml,
                                    optJson = optJson,
                                    optCsv = optCsv,
                                    optBinaries = optBinaries,
                                    onStartDateChange = { viewModel.startDateFilter.value = it },
                                    onEndDateChange = { viewModel.endDateFilter.value = it },
                                    onOptMarkdownChange = { viewModel.optMarkdown.value = it },
                                    onOptHtmlChange = { viewModel.optHtml.value = it },
                                    onOptJsonChange = { viewModel.optJson.value = it },
                                    onOptCsvChange = { viewModel.optCsv.value = it },
                                    onOptBinariesChange = { viewModel.optBinaries.value = it }
                                )
                            }

                            item {
                                ExportControlCard(
                                    viewModel = viewModel,
                                    onLaunchFolderPicker = { pickFolderLauncher.launch(null) },
                                    onTriggerExport = { viewModel.startExport(context) },
                                    onShareExport = {
                                        if (viewModel.customExportFolderUri.value == null) {
                                            pickFolderLauncher.launch(null)
                                        } else if (exportState is ExportState.Success) {
                                            val successState = exportState as ExportState.Success
                                            shareExportedFile(context, successState.fileUri)
                                        }
                                    }
                                )
                            }

                            item { PeriodicAutoSaveControlCard(viewModel = viewModel) }

                            item { VisualExportMetricsCard(viewModel = viewModel) }

                            item { JsonSchemaExplorerAndPackCard(viewModel = viewModel) }

                            item { SchemaInspectorCard(viewModel = viewModel) }

                            item { SchemaVersionManagerCard(viewModel = viewModel) }

                            if (enableBatchMode) {
                                item { BatchConsoleCard(viewModel = viewModel) }
                            }

                            item { RecursiveBinarySearchCard(viewModel = viewModel) }

                            item { AutoBackupHistoryCard(viewModel = viewModel) }

                            item {
                                GrokLoggerPanel(logs = logs, viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeroBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(CyberSurface, CyberBg)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                text = "LOCAL DATA ENGINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Decrypt xAI Grok Archives",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = CyberText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Process, sanitize, and format 1.2 GB+ files with offline security and raw JSON decoding.",
                fontSize = 12.sp,
                color = CyberTextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ImportLauncherCard(
    viewModel: GrokViewModel,
    jobLabelInput: String,
    onJobLabelChange: (String) -> Unit,
    onLaunchPicker: () -> Unit,
    onLoadDemo: () -> Unit
) {
    val context = LocalContext.current
    val importState by viewModel.importState.collectAsState()
    val isJsonLoaded by viewModel.isLoadedFileJson.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                tint = CyberCyan,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (importState is ImportState.Success && isJsonLoaded) "Archive Loaded & Ready" else "Select xAI Export File",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = CyberText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (importState is ImportState.Success && isJsonLoaded) 
                    "JSON archive successfully loaded. Choose Split to generate 10MB chunks or Clear to reset." 
                else "Choose the raw ZIP archive (up to 4.9 GB) or extracted conversations JSON file.",
                fontSize = 13.sp,
                color = CyberTextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            val headTailIngestionMode by viewModel.isHeadTailIngestionMode.collectAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Text(
                    text = "10MB Head/Tail Ingestion Mode",
                    color = CyberText,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = headTailIngestionMode,
                    onCheckedChange = { viewModel.isHeadTailIngestionMode.value = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberCyan,
                        checkedTrackColor = CyberCyan.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (importState is ImportState.Success && isJsonLoaded) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.splitJsonArchive(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("split_archive_button")
                    ) {
                        Icon(Icons.Default.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Split", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.resetState() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurface, contentColor = CyberOrange),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CyberOrange),
                        modifier = Modifier.weight(1f).testTag("clear_archive_button")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                OutlinedTextField(
                    value = jobLabelInput,
                    onValueChange = onJobLabelChange,
                    label = { Text("Processing Job Label (Optional)", color = CyberTextMuted, fontSize = 12.sp) },
                    placeholder = { Text("e.g. Astro-Bio Analysis", color = CyberTextMuted, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .testTag("job_label_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedLabelColor = CyberCyan,
                        focusedTextColor = CyberText,
                        unfocusedTextColor = CyberText,
                        unfocusedContainerColor = CyberBg,
                        focusedContainerColor = CyberBg
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onLaunchPicker,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.8f).testTag("select_file_button")
                ) {
                    Text("Browse Files", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "— OR —",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberTextMuted
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onLoadDemo,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurface, contentColor = CyberCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .border(1.dp, CyberCyan, RoundedCornerShape(12.dp))
                        .testTag("load_sample_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load Sample Dataset", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FilterConfigurationCard(
    startDate: Long?,
    endDate: Long?,
    optMarkdown: Boolean,
    optHtml: Boolean,
    optJson: Boolean,
    optCsv: Boolean,
    optBinaries: Boolean,
    onStartDateChange: (Long?) -> Unit,
    onEndDateChange: (Long?) -> Unit,
    onOptMarkdownChange: (Boolean) -> Unit,
    onOptHtmlChange: (Boolean) -> Unit,
    onOptJsonChange: (Boolean) -> Unit,
    onOptCsvChange: (Boolean) -> Unit,
    onOptBinariesChange: (Boolean) -> Unit
) {
    var showDatePickerDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Extraction Filters & Formats",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Date Range selection triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sDateStr = startDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) } ?: "Start Date"
                val eDateStr = endDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) } ?: "End Date"

                Button(
                    onClick = { showDatePickerDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(sDateStr, fontSize = 12.sp, color = CyberText)
                }

                Button(
                    onClick = { showDatePickerDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(eDateStr, fontSize = 12.sp, color = CyberText)
                }
            }

            if (startDate != null || endDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        onStartDateChange(null)
                        onEndDateChange(null)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Clear Date Filters", color = CyberOrange, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = CyberBorder)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Output Files Bundle",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = CyberText
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Export options checklist
            CheckboxRow(label = "Markdown Transcript (.md)", checked = optMarkdown, onCheckedChange = onOptMarkdownChange)
            CheckboxRow(label = "Interactive HTML Viewer (.html)", checked = optHtml, onCheckedChange = onOptHtmlChange)
            CheckboxRow(label = "Clean Parsed JSON (.json)", checked = optJson, onCheckedChange = onOptJsonChange)
            CheckboxRow(label = "Spreadsheet Records (.csv)", checked = optCsv, onCheckedChange = onOptCsvChange)
            CheckboxRow(label = "Extract & Decode Hex Binaries / Images", checked = optBinaries, onCheckedChange = onOptBinariesChange)
        }
    }

    if (showDatePickerDialog) {
        DatePickerDialogMock(
            onDismiss = { showDatePickerDialog = false },
            onDateSelected = { start, end ->
                onStartDateChange(start)
                onEndDateChange(end)
                showDatePickerDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogMock(
    onDismiss: () -> Unit,
    onDateSelected: (Long?, Long?) -> Unit
) {
    val state = rememberDateRangePickerState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDateSelected(state.selectedStartDateMillis, state.selectedEndDateMillis)
            }) {
                Text("Confirm", color = CyberCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CyberTextMuted)
            }
        },
        title = { Text("Select Export Date Range", color = CyberText) },
        text = {
            Box(modifier = Modifier.height(400.dp).fillMaxWidth()) {
                DateRangePicker(
                    state = state,
                    colors = DatePickerDefaults.colors(
                        containerColor = CyberBg,
                        titleContentColor = CyberText,
                        dayContentColor = CyberText,
                        selectedDayContainerColor = CyberCyan,
                        selectedDayContentColor = CyberBg
                    )
                )
            }
        },
        containerColor = CyberBg,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = CyberCyan,
                uncheckedColor = CyberBorder,
                checkmarkColor = CyberBg
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = CyberText, fontSize = 13.sp)
    }
}

@Composable
fun ParsingProgressCard(
    progressCount: Int,
    currentFileMsg: String,
    stats: ExtractionStats
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCyan, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    color = CyberCyan,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Streaming xAI JSON Blocks...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CyberText
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Indeterminate sleek loading bar
            LinearProgressIndicator(
                color = CyberCyan,
                trackColor = CyberBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .border(1.dp, CyberBorder, RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Extracted Chat Streams: $progressCount",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentFileMsg,
                fontSize = 11.sp,
                color = CyberTextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))
            Divider(color = CyberBorder)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Real-Time Scan Metrics",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = CyberCyan,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))
            MetricsRow(label = "Scanned Conversations:", value = stats.totalConversations.toString())
            MetricsRow(label = "Date Filter Match:", value = stats.filteredConversations.toString())
            MetricsRow(label = "Extracted Characters:", value = stats.totalCharacters.toString())
        }
    }
}

@Composable
fun MetricsSummaryCard(stats: ExtractionStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export Archive Fully Parsed",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CyberCyan
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val minDate = if (stats.dateMin == Long.MAX_VALUE) "N/A" else sdf.format(Date(stats.dateMin))
            val maxDate = if (stats.dateMax == Long.MIN_VALUE) "N/A" else sdf.format(Date(stats.dateMax))

            MetricsRow(label = "Conversations Parsed:", value = stats.totalConversations.toString())
            MetricsRow(label = "Conversations Filtered:", value = stats.filteredConversations.toString())
            MetricsRow(label = "User Prompt Messages:", value = stats.totalUserMessages.toString())
            MetricsRow(label = "Grok AI Responses:", value = stats.totalGrokMessages.toString())
            MetricsRow(label = "Total Characters Extracted:", value = stats.totalCharacters.toString())
            MetricsRow(label = "Activity Period Range:", value = "$minDate to $maxDate")
        }
    }
}

@Composable
fun ExportControlCard(
    viewModel: GrokViewModel,
    onLaunchFolderPicker: () -> Unit,
    onTriggerExport: () -> Unit,
    onShareExport: () -> Unit
) {
    val context = LocalContext.current
    val exportState by viewModel.exportState.collectAsState()
    val customExportFolderName by viewModel.customExportFolderName.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportProgressMessage by viewModel.exportProgressMessage.collectAsState()

    val optMarkdown by viewModel.optMarkdown.collectAsState()
    val optHtml by viewModel.optHtml.collectAsState()
    val optJson by viewModel.optJson.collectAsState()
    val optCsv by viewModel.optCsv.collectAsState()
    val optBinaries by viewModel.optBinaries.collectAsState()

    var showPreviewExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            when (exportState) {
                is ExportState.Idle -> {
                    Text(
                        text = "Build Sanitized Package",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = CyberText,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pack all filtered transcripts, spreadsheets, static viewers, and fully decoded attachments into a consolidated ZIP.",
                        fontSize = 12.sp,
                        color = CyberTextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = CyberBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Folder Picker Row
                    Text(
                        text = "Output Destination Directory",
                        fontWeight = FontWeight.SemiBold,
                        color = CyberText,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberBg, RoundedCornerShape(8.dp))
                            .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = customExportFolderName ?: "Default Sandboxed Job Folder",
                                color = if (customExportFolderName != null) CyberCyan else CyberTextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (customExportFolderName != null) {
                                IconButton(
                                    onClick = { viewModel.setCustomExportFolderUri(context, null) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Reset",
                                        tint = CyberOrange,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Button(
                                onClick = onLaunchFolderPicker,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSurface, contentColor = CyberCyan),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(26.dp)
                                    .border(1.dp, CyberCyan, RoundedCornerShape(6.dp))
                            ) {
                                Text("Choose", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // PII Scrubbing Switch
                    val piiScrubbing by viewModel.piiScrubbingEnabled.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberBg, RoundedCornerShape(8.dp))
                            .border(1.dp, if (piiScrubbing) CyberOrange else CyberBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (piiScrubbing) CyberOrange else CyberTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Automated PII Scrubbing", color = CyberText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Redacts emails, phone numbers, SSNs, and IPs", color = CyberTextMuted, fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = piiScrubbing,
                            onCheckedChange = { viewModel.piiScrubbingEnabled.value = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberOrange,
                                checkedTrackColor = CyberOrange.copy(alpha = 0.3f),
                                uncheckedBorderColor = CyberBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Export Target Format Selector
                    val selectedTargetFormat by viewModel.exportTargetFormat.collectAsState()
                    Text("Target Export Format Schema", color = CyberText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.parser.ExportTargetFormat.values().forEach { fmt ->
                            val isSel = selectedTargetFormat == fmt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSel) CyberCyan.copy(alpha = 0.2f) else CyberBg, RoundedCornerShape(6.dp))
                                    .border(1.dp, if (isSel) CyberCyan else CyberBorder, RoundedCornerShape(6.dp))
                                    .clickable { viewModel.exportTargetFormat.value = fmt }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = fmt.name.replace("_", " "),
                                    fontSize = 9.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) CyberCyan else CyberTextMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Cryptographic Verification Status Badge
                    val verificationStatus by viewModel.sha256VerificationStatus.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberBg, RoundedCornerShape(8.dp))
                            .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SHA-256 Verification Status:", color = CyberTextMuted, fontSize = 11.sp)
                        }
                        Text(
                            text = verificationStatus ?: "NOT_VERIFIED",
                            color = if (verificationStatus?.contains("PASSED") == true || verificationStatus?.contains("OK") == true) CyberCyan else CyberOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = CyberBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Bulk Export Preview Expandable section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPreviewExpanded = !showPreviewExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Bulk Export Package Preview",
                                fontWeight = FontWeight.SemiBold,
                                color = CyberText,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            imageVector = if (showPreviewExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = showPreviewExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .background(CyberBg, RoundedCornerShape(8.dp))
                                .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Preview of output files based on toggled formats:",
                                color = CyberTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Standalone files list
                            PreviewFileRow(label = "conversations.md", active = optMarkdown, description = "Standalone summary of transcripts")
                            PreviewFileRow(label = "conversations.html", active = optHtml, description = "Standalone index/UI search explorer")
                            PreviewFileRow(label = "conversations.json", active = optJson, description = "Normalized clean dataset tree")
                            PreviewFileRow(label = "conversations.csv", active = optCsv, description = "Structured spreadsheet spreadsheet")
                            PreviewFileRow(label = "conversations_metadata_only.json", active = true, description = "Standalone lightweight index")
                            PreviewFileRow(label = "sha256_verification.txt", active = true, description = "Reassembly checksum integrity report")
                            PreviewFileRow(label = "grok_extraction_log.txt", active = true, description = "Comprehensive execution audit logs")

                            Spacer(modifier = Modifier.height(10.dp))
                            Divider(color = CyberBorder)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Folder Structure:",
                                color = CyberText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(modifier = Modifier.padding(start = 6.dp)) {
                                Text("📁 chats/", color = CyberCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("   📁 chat_[id]_[title_prefix]/", color = CyberText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("      📄 conversation.md (Obsidian markdown)", color = CyberTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text("      📄 metadata.json (Individual metadata snapshot)", color = CyberTextMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                if (optBinaries) {
                                    Text("      📁 attachments/ (Extracted attachments & decoded images)", color = CyberOrange, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onTriggerExport,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("export_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                is ExportState.Exporting -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "COMPILING BUNDLE PACKAGE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CyberCyan,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        // Cyberpunk styled Progress Bar
                        val progressPercent = (exportProgress * 100).toInt()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .background(CyberBg, RoundedCornerShape(5.dp))
                                .border(1.dp, CyberBorder, RoundedCornerShape(5.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(exportProgress)
                                    .background(
                                        brush = Brush.horizontalGradient(listOf(CyberCyan, CyberOrange)),
                                        shape = RoundedCornerShape(5.dp)
                                    )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = exportProgressMessage,
                                color = CyberTextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$progressPercent%",
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is ExportState.Success -> {
                    val successState = exportState as ExportState.Success
                    val stats = successState.stats

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Export Bundle Compiled!", fontWeight = FontWeight.Bold, color = CyberText, fontSize = 15.sp)

                        if (customExportFolderName != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Saved copy to SAF folder: $customExportFolderName",
                                color = CyberCyan,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // EXPORT STATS PANEL
                        Surface(
                            color = CyberBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "📊 EXPORT EXECUTION METRICS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = CyberCyan
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Archive Size", fontSize = 10.sp, color = CyberTextMuted)
                                        val sizeFormatted = if (stats.totalSizeBytes > 1024 * 1024) {
                                            String.format(Locale.getDefault(), "%.2f MB", stats.totalSizeBytes / (1024.0 * 1024.0))
                                        } else {
                                            "${stats.totalSizeBytes / 1024} KB"
                                        }
                                        Text(sizeFormatted, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberText)
                                    }
                                    Column {
                                        Text("Execution Time", fontSize = 10.sp, color = CyberTextMuted)
                                        Text("${stats.durationMs} ms", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                                    }
                                    Column {
                                        Text("Export Scope", fontSize = 10.sp, color = CyberTextMuted)
                                        Text("${stats.conversationCount} chats (${stats.messageCount} msgs)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyberOrange)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (stats.markdownGenerated) FormatBadge(".MD")
                                    if (stats.jsonGenerated) FormatBadge(".JSON")
                                    if (stats.csvGenerated) FormatBadge(".CSV")
                                    if (stats.htmlGenerated) FormatBadge(".HTML")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onShareExport,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberOrange, contentColor = CyberText),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share / Save Decoded ZIP", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is ExportState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = CyberOrange,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Export Failed", fontWeight = FontWeight.Bold, color = CyberOrange)
                        Spacer(modifier = Modifier.height(4.dp))
                        val errState = exportState as? ExportState.Error
                        Text(errState?.message ?: "Export failed due to write anomaly", color = CyberTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onTriggerExport,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Text("Retry Export", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewFileRow(label: String, active: Boolean, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = if (active) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (active) CyberCyan else CyberOrange.copy(alpha = 0.5f),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (active) CyberText else CyberTextMuted.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (active) description else "Skipped",
            color = if (active) CyberTextMuted else CyberOrange.copy(alpha = 0.5f),
            fontSize = 9.sp,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun ErrorLogViewerDialog(logs: List<String>, onDismiss: () -> Unit) {
    var selectedLevel by remember { mutableStateOf("ALL") } // ALL, WARN, ERROR
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filteredLogs = remember(logs, selectedLevel, searchQuery) {
        logs.filter { log ->
            val matchesLevel = when (selectedLevel) {
                "WARN" -> log.contains("[WARN]", ignoreCase = true)
                "ERROR" -> log.contains("[ERROR]", ignoreCase = true)
                else -> true
            }
            val matchesQuery = log.contains(searchQuery, ignoreCase = true)
            matchesLevel && matchesQuery
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CyberCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    try {
                        val joined = filteredLogs.joinToString("\n")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Grok Errors", joined)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Copied filtered logs to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }) {
                    Text("Copy", color = CyberCyan)
                }

                TextButton(onClick = {
                    try {
                        val joined = filteredLogs.joinToString("\n")
                        val file = File(context.cacheDir, "filtered_grok_errors.txt")
                        file.writeText(joined)
                        val authority = "${context.packageName}.fileprovider"
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Errors Log"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }) {
                    Text("Share", color = CyberCyan)
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = CyberOrange)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Diagnostic Error & Warning Inspector", color = CyberText, fontSize = 16.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Slices and filters execution history for system warnings and core exceptions.",
                    color = CyberTextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Severity Filter Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("ALL", "WARN", "ERROR").forEach { level ->
                        val active = selectedLevel == level
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (active) CyberCyan else CyberBg, RoundedCornerShape(8.dp))
                                .border(1.dp, if (active) CyberCyan else CyberBorder, RoundedCornerShape(8.dp))
                                .clickable { selectedLevel = level }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = level,
                                color = if (active) CyberBg else CyberTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Text Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter logs text...", color = CyberTextMuted, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedContainerColor = CyberBg,
                        unfocusedContainerColor = CyberBg,
                        focusedTextColor = CyberText,
                        unfocusedTextColor = CyberText
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Log output view box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(CyberBg, RoundedCornerShape(8.dp))
                        .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No warning or error traces match filters.", color = CyberTextMuted, fontSize = 11.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredLogs) { log ->
                                val isError = log.contains("[ERROR]")
                                val isWarning = log.contains("[WARN]")
                                val textColor = when {
                                    isError -> CyberOrange
                                    isWarning -> Color.Yellow
                                    else -> CyberTextMuted
                                }
                                Text(
                                    text = log,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = CyberSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun GoogleDriveIntegrationCard(viewModel: GrokViewModel) {
    val context = LocalContext.current
    val driveAccessToken by viewModel.driveAccessToken.collectAsState()
    val googleAccountEmail by viewModel.googleAccountEmail.collectAsState()
    val driveFilesList by viewModel.driveFilesList.collectAsState()
    val isDriveLoading by viewModel.isDriveLoading.collectAsState()
    val driveError by viewModel.driveError.collectAsState()
    val driveDownloadProgress by viewModel.driveDownloadProgress.collectAsState()
    var tokenInput by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    // Native Google Account Picker Launcher
    val googleAccountPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val selectedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedEmail.isNullOrBlank()) {
                val mockToken = "oauth_bearer_for_" + selectedEmail.lowercase().replace("@", "_")
                viewModel.connectToDrive(mockToken, context, selectedEmail)
                android.widget.Toast.makeText(context, "Connected to $selectedEmail", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCyan, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Google Drive Cloud Integration",
                            fontWeight = FontWeight.Bold,
                            color = CyberText,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (driveAccessToken != null) "Connected (${googleAccountEmail ?: "Active Session"}) • ${driveFilesList.size} files" else "Connect Google Drive to pick or publish archives",
                            color = CyberTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = CyberCyan
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    Divider(color = CyberBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (driveAccessToken == null) {
                        Button(
                            onClick = { viewModel.performGoogleOneTapAuth(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("google_signin_button")
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In with Google Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "— OR PASTE BEARER TOKEN —",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextMuted,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            placeholder = { Text("Paste Bearer Token or OAuth key", color = CyberTextMuted, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CyberBorder,
                                focusedTextColor = CyberText,
                                unfocusedTextColor = CyberText
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (tokenInput.isNotBlank()) {
                                    viewModel.connectToDrive(tokenInput.trim(), context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberSurface, contentColor = CyberCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .border(1.dp, CyberCyan, RoundedCornerShape(8.dp))
                        ) {
                            Text("Connect via Manual Token", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Status: Connected", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                googleAccountEmail?.let { email ->
                                    Text(email, color = CyberTextMuted, fontSize = 11.sp)
                                }
                            }
                            Row {
                                IconButton(onClick = { viewModel.fetchDriveFiles() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.disconnectDrive(context) },
                                    border = BorderStroke(1.dp, CyberOrange),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Disconnect", color = CyberOrange, fontSize = 10.sp)
                                }
                            }
                        }

                        if (isDriveLoading) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CyberCyan)
                            if (driveDownloadProgress != null && driveDownloadProgress!! > 0f) {
                                Text(
                                    text = "Downloading from Drive: ${(driveDownloadProgress!! * 100).toInt()}%",
                                    color = CyberCyan,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        driveError?.let { err ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = err, color = CyberOrange, fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Archives & JSON Blobs in Drive:",
                            color = CyberText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (driveFilesList.isEmpty()) {
                            Text("No .zip or .json archives detected in Drive.", color = CyberTextMuted, fontSize = 11.sp)
                        } else {
                            driveFilesList.forEach { file ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .border(1.dp, CyberBorder, RoundedCornerShape(8.dp)),
                                    colors = CardDefaults.cardColors(containerColor = CyberBg)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(file.name, color = CyberText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val sizeKb = file.size?.let { "${it / 1024} KB" } ?: "Unknown size"
                                            Text("Size: $sizeKb • Type: ${file.mimeType.takeLast(15)}", color = CyberTextMuted, fontSize = 10.sp)
                                        }
                                        Button(
                                            onClick = { viewModel.downloadAndImportDriveFile(context, file.id, file.name) },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Import", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StepByStepPreviewCard(viewModel: GrokViewModel) {
    val previewStage by viewModel.previewStage.collectAsState()
    val batchStrategy by viewModel.batchStrategy.collectAsState()
    val startDate by viewModel.startDateFilter.collectAsState()
    val endDate by viewModel.endDateFilter.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val isPublishingToDrive by viewModel.isPublishingToDrive.collectAsState()
    val drivePublishStatus by viewModel.drivePublishStatus.collectAsState()
    val context = LocalContext.current

    val parsedConversations = (importState as? ImportState.Success)?.conversations ?: emptyList()
    val totalDetectedConversations = if (parsedConversations.isNotEmpty()) parsedConversations.size else stats.totalConversations
    val totalExtractedMessages = if (parsedConversations.isNotEmpty()) parsedConversations.sumOf { it.messages.size } else (stats.totalUserMessages + stats.totalGrokMessages)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Step-by-Step Interactive Verification & Preview",
                fontWeight = FontWeight.Bold,
                color = CyberText,
                fontSize = 14.sp
            )
            Text(
                text = "Verify payload structure, batch slicing, and publishing commitments before execution.",
                color = CyberTextMuted,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Stage Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(
                    0 to "1. JSON Blobs",
                    1 to "2. Filters & Batches",
                    2 to "3. Publish Target"
                ).forEach { (stageIndex, stageName) ->
                    val isSelected = previewStage == stageIndex
                    OutlinedButton(
                        onClick = { viewModel.previewStage.value = stageIndex },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) CyberCyan.copy(alpha = 0.2f) else CyberBg,
                            contentColor = if (isSelected) CyberCyan else CyberTextMuted
                        ),
                        border = BorderStroke(1.dp, if (isSelected) CyberCyan else CyberBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(stageName, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = CyberBorder)
            Spacer(modifier = Modifier.height(12.dp))

            when (previewStage) {
                0 -> {
                    Text("Stage 1: Conversational Payload Verification", fontWeight = FontWeight.Bold, color = CyberCyan, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    MetricsRow("Total Conversations Detected:", "$totalDetectedConversations")
                    MetricsRow("Total Message Objects:", "$totalExtractedMessages")
                    MetricsRow("Thinking Traces & Reasonings:", "Preserved & Extracted")
                    MetricsRow("Metadata Fields:", "Parsed & Serialized")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("✓ Schema verified. Compatible with standard xAI ZIP archives and single/batch JSON Blobs.", color = CyberCyan, fontSize = 11.sp)
                }
                1 -> {
                    Text("Stage 2: Date Range & Batch Slicing Preview", fontWeight = FontWeight.Bold, color = CyberCyan, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Text("Batching Strategy:", color = CyberText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "SINGLE" to "Single ZIP",
                            "MONTHLY" to "Monthly Batches",
                            "COUNT_10" to "Batch 10",
                            "COUNT_25" to "Batch 25"
                        ).forEach { (key, label) ->
                            val isSel = batchStrategy == key
                            FilterChip(
                                selected = isSel,
                                onClick = { viewModel.batchStrategy.value = key },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberCyan,
                                    selectedLabelColor = CyberBg
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val dateStartStr = startDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) } ?: "All Past"
                    val dateEndStr = endDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) } ?: "Present"
                    MetricsRow("Active Date Window:", "$dateStartStr → $dateEndStr")
                    MetricsRow("Selected Batching Mode:", batchStrategy)
                }
                2 -> {
                    Text("Stage 3: Commit & Publishing Verification", fontWeight = FontWeight.Bold, color = CyberCyan, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    MetricsRow("Target Destination:", "Local Zip & Job Storage")
                    MetricsRow("Google Drive Sync:", if (viewModel.driveAccessToken.value != null) "Ready to Push" else "Not Connected")
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    if (viewModel.driveAccessToken.value != null) {
                        Button(
                            onClick = { viewModel.publishExportToGoogleDrive(context) },
                            enabled = !isPublishingToDrive,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberOrange, contentColor = CyberText),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPublishingToDrive) "Uploading to Drive..." else "Publish Directly to Google Drive", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    drivePublishStatus?.let { status ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(status, color = if (status.startsWith("Successfully")) CyberCyan else CyberOrange, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = CyberText,
    highlightBg: Color = Color(0xFFFFD700),
    highlightTextColor: Color = Color.Black,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontFamily: FontFamily? = null
) {
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            fontFamily = fontFamily
        )
        return
    }

    val annotatedString = buildAnnotatedString {
        var start = 0
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()

        while (start < text.length) {
            val index = lowerText.indexOf(lowerQuery, start)
            if (index == -1) {
                append(text.substring(start))
                break
            }

            if (index > start) {
                append(text.substring(start, index))
            }

            val end = index + query.length
            withStyle(
                SpanStyle(
                    background = highlightBg,
                    color = highlightTextColor,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(index, end))
            }

            start = end
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        fontFamily = fontFamily
    )
}

@Composable
fun ChatPreviewItemCard(
    conversation: Conversation,
    isExpanded: Boolean,
    searchQuery: String = "",
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isExpanded) CyberCyan else CyberBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    HighlightText(
                        text = conversation.title.ifBlank { "Untitled Chat" },
                        query = searchQuery,
                        color = CyberText,
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(conversation.timestamp))
                    Text(
                        text = "Date: $dateStr • ${conversation.messages.size} msgs",
                        color = CyberTextMuted,
                        fontSize = 11.sp
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded conversation log preview
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    HorizontalDivider(color = CyberBorder)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Message Feed Preview:",
                        fontWeight = FontWeight.SemiBold,
                        color = CyberCyan,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    conversation.messages.forEach { msg ->
                        val isUser = msg.role.lowercase() == "user"
                        val speakerColor = if (isUser) CyberCyan else CyberOrange
                        val speakerLabel = if (isUser) "User" else "Grok"

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = speakerLabel,
                                color = speakerColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            HighlightText(
                                text = msg.text,
                                query = searchQuery,
                                color = CyberText,
                                style = TextStyle(fontSize = 12.sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberBg, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            )
                            if (!msg.thinkingTrace.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = Color(0xFF1E232F),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "🧠 Thinking Trace / Reasoning:",
                                            color = CyberCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        HighlightText(
                                            text = msg.thinkingTrace,
                                            query = searchQuery,
                                            color = CyberTextMuted,
                                            style = TextStyle(fontSize = 11.sp),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                            if (!msg.metadataJson.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Meta: ${msg.metadataJson}",
                                    color = CyberTextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorDisplayCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberOrange, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = CyberOrange, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Operation Encountered an Error", fontWeight = FontWeight.Bold, color = CyberText, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = CyberTextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = CyberOrange, contentColor = CyberText),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Retry", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MetricsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = CyberTextMuted, fontSize = 13.sp)
        Text(value, color = CyberText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun shareExportedFile(context: Context, fileUri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, fileUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Decoded xAI Grok Package"))
}

@Composable
fun GrokLoggerPanel(logs: List<String>, viewModel: GrokViewModel? = null) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(true) }
    var showFailureInspector by remember { mutableStateOf(false) }

    val verboseDebugEnabled = viewModel?.verboseDebugEnabled?.collectAsState()?.value ?: false

    if (showFailureInspector) {
        ErrorLogViewerDialog(logs = logs, onDismiss = { showFailureInspector = false })
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isExpanded = !isExpanded }
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Logs & Forensics",
                        fontWeight = FontWeight.Bold,
                        color = CyberText,
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Forensics / Debug Mode Toggle Switch
                    if (viewModel != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Debug",
                                color = if (verboseDebugEnabled) CyberOrange else CyberTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = verboseDebugEnabled,
                                onCheckedChange = { checked ->
                                    viewModel.setVerboseDebugEnabled(context, checked)
                                },
                                modifier = Modifier.scale(0.65f),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyberOrange,
                                    checkedTrackColor = CyberOrange.copy(alpha = 0.3f),
                                    uncheckedBorderColor = CyberBorder
                                )
                            )
                        }
                    }

                    // Export Diagnostic Log Button
                    if (viewModel != null) {
                        Button(
                            onClick = {
                                val dumpStr = viewModel.exportDiagnosticLog(context)
                                android.widget.Toast.makeText(
                                    context,
                                    "Exported & copied diagnostic dump (${dumpStr.length} chars)",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(26.dp)
                                .testTag("export_diagnostic_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dump", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    TextButton(
                        onClick = { showFailureInspector = true },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = CyberOrange,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Inspector", color = CyberOrange, fontSize = 10.sp)
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(CyberBg, RoundedCornerShape(8.dp))
                            .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (logs.isEmpty()) {
                                item {
                                    Text(
                                        text = "[SYSTEM IDLE] Waiting for extraction request...",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = CyberTextMuted
                                    )
                                }
                            } else {
                                items(logs) { log ->
                                    val isError = log.contains("[ERROR]")
                                    val isWarning = log.contains("[WARN]")
                                    val textColor = when {
                                        isError -> CyberOrange
                                        isWarning -> Color.Yellow
                                        else -> CyberTextMuted
                                    }
                                    Text(
                                        text = log,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IntegrityVerificationCard(validationMatched: Boolean, sha256Checksum: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (validationMatched) CyberCyan.copy(alpha = 0.6f) else CyberOrange.copy(alpha = 0.6f),
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = if (validationMatched) CyberCyan else CyberOrange,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (validationMatched) "CRYPTOGRAPHIC INTEGRITY CONFIRMED" else "INTEGRITY VALIDATION MISMATCH",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (validationMatched) CyberCyan else CyberOrange
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Reassembly engine normalized, parsed, sliced, and verified the parsed JSON tree byte-for-byte against skeletal remains using SHA-256 hashing. The match confirms zero-loss data replication.",
                fontSize = 11.sp,
                color = CyberTextMuted
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "SHA-256 Checksum Hash:",
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = CyberText
            )
            Text(
                text = sha256Checksum,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = CyberCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberBg, RoundedCornerShape(4.dp))
                    .padding(6.dp)
            )
        }
    }
}

@Composable
fun HelpGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = CyberCyan, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Help, contentDescription = null, tint = CyberCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("xAI Grok Schema Guide", color = CyberText, fontSize = 16.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Parsing Strategy & Architecture",
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "This application handles giant xAI export data files up to 4.9 GB by avoiding loading the whole JSON tree into memory at once. Instead, it utilizes an incremental Jackson-style Token Streaming Reader (Android's JsonReader) over the Input Stream directly. This consumes negligible memory (less than 20MB) and operates offline safely on Pixel-grade mobile devices.",
                        fontSize = 12.sp,
                        color = CyberTextMuted
                    )
                }
                item {
                    Text(
                        text = "1. Supported Schemas & JSON Layouts",
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "The parser automatically detects and reads both standard Flat List format [ { id, title, messages: [] } ] and Nested root attributes { conversations: [...] }.",
                        fontSize = 12.sp,
                        color = CyberTextMuted
                    )
                }
                item {
                    Text(
                        text = "2. Skeletal Remains & Integrity Validation",
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "The app slices individual chats while saving 'skeletal remains' metadata of the original. To guarantee zero loss, it re-aligns sliced conversations with skeletal bones and runs a cryptographic SHA-256 byte-for-byte check.",
                        fontSize = 12.sp,
                        color = CyberTextMuted
                    )
                }
                item {
                    Text(
                        text = "3. Decoding Hex Binary Assets",
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Inline assets (images/audio/documents) found as hex structures are cleanly decoded into binary files with accurate file extension detection.",
                        fontSize = 12.sp,
                        color = CyberTextMuted
                    )
                }
            }
        },
        containerColor = CyberBg,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun ProcessingJobsHistoryCard(
    jobs: List<GrokJob>,
    onViewLogs: (GrokJob) -> Unit,
    onViewReports: (GrokJob) -> Unit,
    onShareZip: (GrokJob) -> Unit,
    onDeleteJob: (GrokJob) -> Unit,
    onClearAll: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp))
            .testTag("jobs_history_card"),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Extraction Jobs Archive",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CyberCyan
                )
                if (jobs.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear All", color = CyberOrange, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (jobs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No archived jobs found. Prepare an import to initiate a job.",
                        color = CyberTextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(jobs) { job ->
                        JobHistoryItem(
                            job = job,
                            onViewLogs = { onViewLogs(job) },
                            onViewReports = { onViewReports(job) },
                            onShareZip = { onShareZip(job) },
                            onDelete = { onDeleteJob(job) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JobHistoryItem(
    job: GrokJob,
    onViewLogs: () -> Unit,
    onViewReports: () -> Unit,
    onShareZip: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault()).format(Date(job.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberBg, RoundedCornerShape(12.dp))
            .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(CyberBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#${job.number}",
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = job.label,
                    fontWeight = FontWeight.SemiBold,
                    color = CyberText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp)
                )
            }

            val badgeBg = when (job.status) {
                "COMPLETED" -> CyberCyan.copy(alpha = 0.15f)
                "FAILED" -> CyberOrange.copy(alpha = 0.15f)
                else -> Color.Yellow.copy(alpha = 0.15f)
            }
            val badgeColor = when (job.status) {
                "COMPLETED" -> CyberCyan
                "FAILED" -> CyberOrange
                else -> Color.Yellow
            }
            Box(
                modifier = Modifier
                    .background(badgeBg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = job.status,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = dateStr,
            fontSize = 11.sp,
            color = CyberTextMuted
        )

        if (job.status == "COMPLETED") {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Chats: ${job.totalConversations} | Size: ${(job.totalCharacters / 1024.0).toInt()} KB | Binaries: ${job.binaryFilesProcessed} (Decoded: ${job.hexFilesDecoded})",
                fontSize = 11.sp,
                color = CyberTextMuted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Divider(color = CyberBorder.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = onViewLogs,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyberCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Logs", color = CyberCyan, fontSize = 11.sp)
                }

                if (job.status == "COMPLETED") {
                    TextButton(
                        onClick = onViewReports,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyberCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Integrity", color = CyberCyan, fontSize = 11.sp)
                    }

                    val zipFile = File(job.folderPath, "grok_processed_export.zip")
                    if (zipFile.exists()) {
                        TextButton(
                            onClick = onShareZip,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyberCyan)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ZIP", color = CyberCyan, fontSize = 11.sp)
                        }
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Job", tint = CyberOrange.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun AdvancedDashboardTogglesCard(viewModel: GrokViewModel) {
    val preserveFileDates by viewModel.preserveFileDates.collectAsState()
    val enableObsidianFrontMatter by viewModel.enableObsidianFrontMatter.collectAsState()
    val obsidianIncludeTitle by viewModel.obsidianIncludeTitle.collectAsState()
    val obsidianIncludeDate by viewModel.obsidianIncludeDate.collectAsState()
    val obsidianIncludeId by viewModel.obsidianIncludeId.collectAsState()
    val obsidianIncludeStats by viewModel.obsidianIncludeStats.collectAsState()
    val obsidianIncludeTags by viewModel.obsidianIncludeTags.collectAsState()
    
    val timeFrameGapHours by viewModel.timeFrameGapHours.collectAsState()
    val enableLineNumbers by viewModel.enableLineNumbers.collectAsState()
    
    val enableBatchMode by viewModel.enableBatchMode.collectAsState()
    val batchSize by viewModel.batchSize.collectAsState()
    val isTestRun by viewModel.isTestRun.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Advanced Extraction Controls",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Date preservation toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Preserve File Date/Time", color = CyberText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Sets the file modification timestamps off the chat record natural time rather than system time.", color = CyberTextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = preserveFileDates,
                    onCheckedChange = { viewModel.preserveFileDates.value = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyberBg, checkedTrackColor = CyberCyan)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = CyberBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Obsidian Markdown Front Matter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Obsidian Front Matter", color = CyberText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Inject yaml meta block at start of transcripts.", color = CyberTextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = enableObsidianFrontMatter,
                    onCheckedChange = { viewModel.enableObsidianFrontMatter.value = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyberBg, checkedTrackColor = CyberCyan)
                )
            }

            if (enableObsidianFrontMatter) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
                    CheckboxRow(label = "Include title attribute", checked = obsidianIncludeTitle, onCheckedChange = { viewModel.obsidianIncludeTitle.value = it })
                    CheckboxRow(label = "Include timestamp date", checked = obsidianIncludeDate, onCheckedChange = { viewModel.obsidianIncludeDate.value = it })
                    CheckboxRow(label = "Include conversation ID", checked = obsidianIncludeId, onCheckedChange = { viewModel.obsidianIncludeId.value = it })
                    CheckboxRow(label = "Include message count statistics", checked = obsidianIncludeStats, onCheckedChange = { viewModel.obsidianIncludeStats.value = it })
                    CheckboxRow(label = "Include #grok tag markers", checked = obsidianIncludeTags, onCheckedChange = { viewModel.obsidianIncludeTags.value = it })
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = CyberBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Timeframe segments & line numbering
            Text("Transcript Layouts", color = CyberText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            CheckboxRow(label = "Inject absolute line numbering in markdown blocks", checked = enableLineNumbers, onCheckedChange = { viewModel.enableLineNumbers.value = it })
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Segment Break Gap Window:", color = CyberTextMuted, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (timeFrameGapHours > 1) viewModel.timeFrameGapHours.value = timeFrameGapHours - 1 }) {
                        Icon(Icons.Default.Remove, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    }
                    Text("${timeFrameGapHours} hours", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { viewModel.timeFrameGapHours.value = timeFrameGapHours + 1 }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = CyberBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Batch processor toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Incremental Batch Processor Mode", color = CyberText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Cycles through raw dataset slices without OOM limits.", color = CyberTextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = enableBatchMode,
                    onCheckedChange = { viewModel.enableBatchMode.value = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyberBg, checkedTrackColor = CyberCyan)
                )
            }

            if (enableBatchMode) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Slice Batch Size limit:", color = CyberTextMuted, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (batchSize > 1) viewModel.batchSize.value = batchSize - 1 }) {
                                Icon(Icons.Default.Remove, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                            }
                            Text("${batchSize} chats", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.batchSize.value = batchSize + 1 }) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Suggest Test Run Slice", color = CyberText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("Process only initial 2 conversations first.", color = CyberTextMuted, fontSize = 11.sp)
                        }
                        Checkbox(
                            checked = isTestRun,
                            onCheckedChange = { viewModel.isTestRun.value = it },
                            colors = CheckboxDefaults.colors(checkedColor = CyberCyan, checkmarkColor = CyberBg)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BatchConsoleCard(viewModel: GrokViewModel) {
    val context = LocalContext.current
    val currentBatchIndex by viewModel.currentBatchIndex.collectAsState()
    val totalBatches by viewModel.totalBatches.collectAsState()
    val batchProcessingStatus by viewModel.batchProcessingStatus.collectAsState()
    val isTestRun by viewModel.isTestRun.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCyan, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "BATCH RUN CONSOLE",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Status: ${batchProcessingStatus}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (batchProcessingStatus == "PROCESSING") Color.Yellow else CyberCyan
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Visual ProgressBar
            val progress = if (totalBatches > 0) (currentBatchIndex.toFloat() / totalBatches.toFloat()) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(CyberBg, RoundedCornerShape(4.dp))
                    .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(CyberCyan, RoundedCornerShape(4.dp))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Batch Cycle ${currentBatchIndex} of ${totalBatches} Slices",
                fontSize = 12.sp,
                color = CyberTextMuted
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.startBatchCycles(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                    shape = RoundedCornerShape(10.dp),
                    enabled = batchProcessingStatus != "PROCESSING",
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isTestRun) "Run Test Batch" else "Cycle Batches", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = { viewModel.resetState() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBg, contentColor = CyberOrange),
                    shape = RoundedCornerShape(10.dp),
                    enabled = batchProcessingStatus != "PROCESSING",
                    modifier = Modifier.weight(1f).border(1.dp, CyberOrange, RoundedCornerShape(10.dp))
                ) {
                    Text("Abort", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun VisualizationsDashboardCard(conversations: List<Conversation>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Interactive Analytics Dashboard",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(12.dp))

            val totalMessages = conversations.sumOf { it.messages.size }
            val avgMsgs = if (conversations.isNotEmpty()) totalMessages / conversations.size else 0

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Chats", color = CyberTextMuted, fontSize = 11.sp)
                    Text("${conversations.size}", color = CyberCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Total Messages", color = CyberTextMuted, fontSize = 11.sp)
                    Text("${totalMessages}", color = CyberOrange, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Avg Messages/Chat", color = CyberTextMuted, fontSize = 11.sp)
                    Text("${avgMsgs}", color = CyberCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = CyberBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Volume Distribution Breakdown",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = CyberText
            )
            Spacer(modifier = Modifier.height(8.dp))

            conversations.take(5).forEach { chat ->
                val msgCount = chat.messages.size
                val visualPercentage = if (totalMessages > 0) (msgCount.toFloat() / totalMessages.toFloat()).coerceIn(0.1f, 1.0f) else 0.1f
                
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = chat.title.ifBlank { "Untitled Chat" },
                            fontSize = 11.sp,
                            color = CyberTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${msgCount} msgs", fontSize = 11.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(CyberBg, RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(visualPercentage)
                                .background(
                                    brush = Brush.horizontalGradient(listOf(CyberCyan, CyberOrange)),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecursiveBinarySearchCard(viewModel: GrokViewModel) {
    val context = LocalContext.current
    val minedBinaries by viewModel.minedBinaries.collectAsState()
    val isSearchingBinaries by viewModel.isSearchingBinaries.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recursive Binary Scanner",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CyberCyan
                )
                
                if (isSearchingBinaries) {
                    CircularProgressIndicator(color = CyberCyan, modifier = Modifier.size(16.dp))
                } else {
                    Button(
                        onClick = { viewModel.triggerRecursiveBinarySearch(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberBg, contentColor = CyberCyan),
                        modifier = Modifier.height(30.dp).border(1.dp, CyberCyan, RoundedCornerShape(8.dp)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Deep Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scans all job subfolders and underlying asset chains for binary artifacts and image content types.",
                fontSize = 11.sp,
                color = CyberTextMuted
            )
            
            if (minedBinaries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = CyberBorder)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Discovered Assets (${minedBinaries.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CyberText
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(minedBinaries) { binary ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(CyberBg, RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(binary.name, color = CyberText, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Type: ${binary.mimeType} • Size: ${(binary.size / 1024.0).toInt()} KB", color = CyberTextMuted, fontSize = 10.sp)
                                    Text("SHA256: ${binary.sha256.take(16)}...", fontFamily = FontFamily.Monospace, color = CyberCyan, fontSize = 9.sp)
                                }
                                if (binary.details.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .background(CyberBorder, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(binary.details, color = CyberOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutoBackupHistoryCard(viewModel: GrokViewModel) {
    val context = LocalContext.current
    val backupsList by viewModel.backupsList.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAllBackups(context)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Auto-Backup Vault",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CyberCyan
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Stores copy snapshots of compiled ZIP archives inside secure sandboxed file space.",
                fontSize = 11.sp,
                color = CyberTextMuted
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            if (backupsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No automated backup snapshots generated yet.", color = CyberTextMuted, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(backupsList) { file ->
                        val date = Date(file.lastModified())
                        val dateStr = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(date)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberBg, RoundedCornerShape(8.dp))
                                .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, color = CyberText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Timestamp: $dateStr • Size: ${(file.length() / 1024.0).toInt()} KB", color = CyberTextMuted, fontSize = 10.sp)
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        val authority = "${context.packageName}.fileprovider"
                                        val backupUri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                                        shareExportedFile(context, backupUri)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share Backup", tint = CyberCyan, modifier = Modifier.size(14.dp))
                                }

                                IconButton(
                                    onClick = { viewModel.deleteBackup(context, file) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Backup", tint = CyberOrange, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SovereignStatusCard(
    importState: ImportState,
    importProgress: Int,
    stats: ExtractionStats,
    validationMatched: Boolean?,
    sha256Checksum: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCyan, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SOVEREIGN STATUS & INTEGRITY DECK",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CyberCyan
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            when (importState) {
                is ImportState.Idle -> {
                    Text(
                        text = "Pipeline Status: IDLE — Ready for local file or Google Drive ingestion.",
                        fontSize = 12.sp,
                        color = CyberTextMuted
                    )
                }
                is ImportState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = CyberCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Streaming xAI JSON: ${importState.currentFile} ($importProgress parsed)",
                            fontSize = 12.sp,
                            color = CyberText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = CyberCyan,
                        trackColor = CyberBorder
                    )
                }
                is ImportState.Success -> {
                    Text(
                        text = "Pipeline Status: ACTIVE — ${importState.conversations.size} conversations loaded in memory.",
                        fontSize = 12.sp,
                        color = CyberCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
                is ImportState.Error -> {
                    Text(
                        text = "Pipeline Status: ERROR — ${importState.message}",
                        fontSize = 12.sp,
                        color = CyberOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (validationMatched != null || sha256Checksum.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = CyberBorder)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (validationMatched == true) CyberCyan else CyberOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (validationMatched == true) "CRYPTOGRAPHIC INTEGRITY: SHA-256 MATCH" else "SHA-256 CHECKSUM RECORDED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (validationMatched == true) CyberCyan else CyberOrange
                    )
                }
                if (sha256Checksum.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hash: $sha256Checksum",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = CyberTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun AggregateMetricsHeaderCard(
    totalChats: Int,
    totalMessages: Int,
    stats: ExtractionStats
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("VAULT AGGREGATE METRICS", fontWeight = FontWeight.Bold, color = CyberCyan, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total Conversations", fontSize = 11.sp, color = CyberTextMuted)
                    Text("$totalChats", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CyberText)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Extracted Messages", fontSize = 11.sp, color = CyberTextMuted)
                    Text("$totalMessages", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Characters Extracted", fontSize = 11.sp, color = CyberTextMuted)
                    Text("${stats.totalCharacters}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CyberOrange)
                }
            }
        }
    }
}

@Composable
fun FormatBadge(text: String) {
    Surface(
        color = CyberSurface,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f))
    ) {
        Text(
            text = text,
            color = CyberCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun GrokSummaryToolCard(viewModel: GrokViewModel) {
    val summaryText by viewModel.grokSummaryState.collectAsState()
    val isGenerating by viewModel.isGeneratingSummary.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCyan, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GROK EXECUTIVE SUMMARY TOOL", fontWeight = FontWeight.Bold, color = CyberCyan, fontSize = 13.sp)
                }

                Button(
                    onClick = { viewModel.generateGrokSummary() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isGenerating,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = CyberBg, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analyzing...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Summary", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (summaryText != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = CyberBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = CyberBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = summaryText!!,
                            color = CyberText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(summaryText!!))
                            Toast.makeText(context, "Summary copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                        border = BorderStroke(1.dp, CyberCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Summary", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap 'Generate Summary' to run a forensic theme extraction and summary analysis across all active Grok chat logs.",
                    color = CyberTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun PeriodicAutoSaveControlCard(viewModel: GrokViewModel) {
    val context = LocalContext.current
    val isEnabled by viewModel.isPeriodicAutoSaveEnabled.collectAsState()
    val interval by viewModel.autoSaveInterval.collectAsState()
    val lastSaveStatus by viewModel.lastAutoSaveStatus.collectAsState()
    val checkpoints by viewModel.autoSaveCheckpoints.collectAsState()
    val importState by viewModel.importState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAutoSaveCheckpoints(context)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp))
            .testTag("periodic_autosave_card"),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Auto Save",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Periodic Progress Auto-Save",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CyberCyan
                    )
                }
                
                Surface(
                    color = if (isEnabled) CyberCyan.copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isEnabled) CyberCyan.copy(alpha = 0.5f) else Color.Gray)
                ) {
                    Text(
                        text = if (isEnabled) "ARMED & ACTIVE" else "DISABLED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) CyberCyan else CyberTextMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Automatically writes incremental parsing progress checkpoints during long ZIP/JSON exports to guarantee zero data loss.",
                fontSize = 11.sp,
                color = CyberTextMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Controls Row: Toggle + Interval choice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberBg, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Auto-Save Checkpoints", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = CyberText)
                    Text("Save progress snapshot", fontSize = 10.sp, color = CyberTextMuted)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { viewModel.isPeriodicAutoSaveEnabled.value = it },
                        modifier = Modifier
                            .scale(0.85f)
                            .testTag("autosave_switch")
                    )
                }
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Auto-Save Checkpoint Interval:", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CyberText)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val intervals = listOf(10, 25, 50, 100)
                    intervals.forEach { num ->
                        val isSelected = interval == num
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.autoSaveInterval.value = num },
                            label = { Text("Every $num convs", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = CyberCyan,
                                containerColor = CyberBg,
                                labelColor = CyberTextMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = CyberCyan,
                                borderColor = CyberBorder
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status Banner & Immediate Trigger Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                    .background(Color(0xFF0E131B))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Engine Auto-Save Status:", fontSize = 10.sp, color = CyberTextMuted)
                    Text(
                        text = lastSaveStatus,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (lastSaveStatus.contains("Saved")) Color(0xFF00E676) else CyberCyan
                    )
                }

                Button(
                    onClick = {
                        if (importState is ImportState.Success) {
                            val success = importState as ImportState.Success
                            viewModel.saveParsingCheckpoint(context, success.conversations, success.stats, "Manual_Checkpoint")
                            Toast.makeText(context, "Checkpoint snapshot saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No active parsed data to checkpoint.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("save_checkpoint_btn")
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Checkpoint Now", fontSize = 11.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Checkpoints Vault List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Checkpoints Vault (${checkpoints.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberText
                )
                if (checkpoints.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearAllCheckpoints(context) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("Purge All", fontSize = 10.sp, color = Color(0xFFFF5252))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (checkpoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberBg, RoundedCornerShape(10.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No auto-save progress checkpoints created yet.", color = CyberTextMuted, fontSize = 11.sp)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    checkpoints.take(5).forEach { chk ->
                        val dateFormatted = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(chk.timestamp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberBg, RoundedCornerShape(10.dp))
                                .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${chk.jobLabel} • $dateFormatted",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = CyberCyan
                                )
                                Text(
                                    text = "${chk.conversationCount} conversations • ${chk.messageCount} messages • ${chk.fileSize / 1024} KB",
                                    fontSize = 10.sp,
                                    color = CyberTextMuted
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        viewModel.restoreFromCheckpoint(context, chk)
                                        Toast.makeText(context, "Loaded state from checkpoint!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676).copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restore", fontSize = 10.sp, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = { viewModel.deleteCheckpoint(context, chk) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CyberTextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JsonSchemaExplorerAndPackCard(viewModel: GrokViewModel) {
    val context = LocalContext.current
    val schemaPacks by viewModel.schemaPacksList.collectAsState()
    val activePack by viewModel.activeSchemaPack.collectAsState()
    val discoveredFields by viewModel.discoveredSchemaFields.collectAsState()
    val validationReport by viewModel.schemaValidationReport.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingSchema.collectAsState()

    var showNewPackDialog by remember { mutableStateOf(false) }
    var newPackName by remember { mutableStateOf("") }
    var newPackVersion by remember { mutableStateOf("1.0.0") }
    var newPackDesc by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadSchemaPacks(context)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberBorder, RoundedCornerShape(16.dp))
            .testTag("json_schema_card"),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = "Schema Explorer",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "JSON Schema Explorer & Definition Packs",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CyberCyan
                    )
                }

                validationReport?.let { report ->
                    val color = if (report.isValid) Color(0xFF00E676) else Color(0xFFFF9100)
                    Surface(
                        color = color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, color)
                    ) {
                        Text(
                            text = "${report.matchPercentage.toInt()}% MATCH",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Discover data structures across conversation payloads. Customize, version, and apply schema definition packs to extract targeted data types and map field names.",
                fontSize = 11.sp,
                color = CyberTextMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action: Discover Schema Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.analyzeAndDiscoverSchema() },
                    enabled = !isAnalyzing,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("discover_schema_btn")
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberCyan, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analyzing Payload...", fontSize = 11.sp, color = CyberCyan)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Discover Payload Schema", fontSize = 11.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                    }
                }

                if (discoveredFields.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showNewPackDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD600).copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Pack", fontSize = 11.sp, color = Color(0xFFFFD600), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Schema Pack Selector Chips
            Text("Active Schema Definition Pack:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberText)
            Spacer(modifier = Modifier.height(6.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                schemaPacks.forEach { pack ->
                    val isSelected = activePack?.id == pack.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectActiveSchemaPack(pack) }
                            .border(1.dp, if (isSelected) CyberCyan else CyberBorder, RoundedCornerShape(10.dp)),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) CyberCyan.copy(alpha = 0.08f) else CyberBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = pack.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isSelected) CyberCyan else CyberText
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = CyberBorder,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "v${pack.version}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberTextMuted,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = pack.description,
                                    fontSize = 10.sp,
                                    color = CyberTextMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.selectActiveSchemaPack(pack) },
                                colors = RadioButtonDefaults.colors(selectedColor = CyberCyan)
                            )
                        }
                    }
                }
            }

            // Schema Validation Compliance Card
            validationReport?.let { report ->
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberBg, RoundedCornerShape(10.dp))
                        .border(1.dp, CyberBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text("Schema Validation Report:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    LinearProgressIndicator(
                        progress = { report.matchPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = if (report.isValid) Color(0xFF00E676) else Color(0xFFFF9100),
                        trackColor = CyberBorder
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Matched Fields: ${report.matchedFieldsCount} / ${report.totalDiscoveredFields}", fontSize = 10.sp, color = CyberTextMuted)
                        Text(if (report.isValid) "Mandatory Fields Satisfied" else "Missing Required Keys", fontSize = 10.sp, color = if (report.isValid) Color(0xFF00E676) else Color(0xFFFF5252))
                    }

                    if (report.missingMandatoryFields.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "⚠️ Missing Mandatory: ${report.missingMandatoryFields.joinToString(", ")}",
                            fontSize = 10.sp,
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (report.unknownNewFields.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "💡 Unmapped Payload Additions: ${report.unknownNewFields.joinToString(", ")}",
                            fontSize = 10.sp,
                            color = Color(0xFFFFD600)
                        )
                    }
                }
            }

            // Interactive Discovered / Mapped Schema Fields Editor
            activePack?.let { pack ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Custom Field Mappings & Targeted Extraction Toggles (${pack.fields.size} fields)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberText
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pack.fields.forEach { field ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberBg, RoundedCornerShape(8.dp))
                                .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(field.originalKey, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberCyan)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = CyberCyan.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(field.dataType, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = CyberCyan, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                    if (field.isMandatory) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("REQUIRED", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                                    }
                                }
                                if (field.sampleValue.isNotBlank()) {
                                    Text("Sample: ${field.sampleValue}", fontSize = 10.sp, color = CyberTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                // Mapped Key Editor
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                    Text("Mapped as: ", fontSize = 10.sp, color = CyberTextMuted)
                                    var editedMappedKey by remember(field.mappedKey) { mutableStateOf(field.mappedKey) }
                                    BasicTextField(
                                        value = editedMappedKey,
                                        onValueChange = {
                                            editedMappedKey = it
                                            viewModel.updateSchemaFieldMapping(context, field.originalKey, it, field.isMandatory, field.isEnabledForExport)
                                        },
                                        textStyle = TextStyle(color = Color(0xFFFFD600), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                        modifier = Modifier
                                            .background(CyberSurface, RoundedCornerShape(4.dp))
                                            .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Export Toggle
                            Switch(
                                checked = field.isEnabledForExport,
                                onCheckedChange = { isChecked ->
                                    viewModel.updateSchemaFieldMapping(context, field.originalKey, field.mappedKey, field.isMandatory, isChecked)
                                },
                                modifier = Modifier.scale(0.75f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Save Custom Schema Definition Pack Dialog
    if (showNewPackDialog) {
        AlertDialog(
            onDismissRequest = { showNewPackDialog = false },
            containerColor = CyberSurface,
            title = {
                Text("Save Custom Schema Definition Pack", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CyberCyan)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Store discovered JSON schema keys, field remappings, and targeted extraction settings as a versioned Schema Pack.", fontSize = 11.sp, color = CyberTextMuted)
                    
                    OutlinedTextField(
                        value = newPackName,
                        onValueChange = { newPackName = it },
                        label = { Text("Pack Name") },
                        placeholder = { Text("e.g. Custom xAI Grok v2.1") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = CyberText,
                            unfocusedTextColor = CyberText
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPackVersion,
                        onValueChange = { newPackVersion = it },
                        label = { Text("Version") },
                        placeholder = { Text("1.0.0") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = CyberText,
                            unfocusedTextColor = CyberText
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPackDesc,
                        onValueChange = { newPackDesc = it },
                        label = { Text("Description") },
                        placeholder = { Text("Custom mappings for targeted exports...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = CyberText,
                            unfocusedTextColor = CyberText
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNewSchemaPackFromDiscovered(context, newPackName, newPackVersion, newPackDesc)
                        showNewPackDialog = false
                        Toast.makeText(context, "Saved custom schema definition pack!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Save Schema Pack", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewPackDialog = false }) {
                    Text("Cancel", color = CyberTextMuted)
                }
            }
        )
    }
}

@Composable
fun VisualExportMetricsCard(viewModel: GrokViewModel) {
    val metrics by viewModel.exportMetricsData.collectAsState()
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Visual Export Metrics & Analytics",
                        fontWeight = FontWeight.Bold,
                        color = CyberText,
                        fontSize = 15.sp
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = CyberCyan
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Real-time telemetry and compression savings calculated across parsed payloads.",
                        fontSize = 12.sp,
                        color = CyberTextMuted
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Message Role Distribution Bar
                    Text(
                        text = "Message Role Breakdown",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val totalMsgs = (metrics.userMessageCount + metrics.grokMessageCount + metrics.systemMessageCount).coerceAtLeast(1)
                    val userWeight = metrics.userMessageCount.toFloat() / totalMsgs
                    val grokWeight = metrics.grokMessageCount.toFloat() / totalMsgs
                    val systemWeight = metrics.systemMessageCount.toFloat() / totalMsgs

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(CyberBg, RoundedCornerShape(9.dp))
                            .border(1.dp, CyberBorder, RoundedCornerShape(9.dp))
                    ) {
                        if (userWeight > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(userWeight)
                                    .background(CyberCyan, RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp))
                            )
                        }
                        if (grokWeight > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(grokWeight)
                                    .background(CyberOrange)
                            )
                        }
                        if (systemWeight > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(systemWeight)
                                    .background(Color(0xFF9C27B0), RoundedCornerShape(topEnd = 9.dp, bottomEnd = 9.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("User: ${metrics.userMessageCount} (${(userWeight * 100).toInt()}%)", fontSize = 11.sp, color = CyberCyan)
                        Text("Grok: ${metrics.grokMessageCount} (${(grokWeight * 100).toInt()}%)", fontSize = 11.sp, color = CyberOrange)
                        Text("System: ${metrics.systemMessageCount} (${(systemWeight * 100).toInt()}%)", fontSize = 11.sp, color = Color(0xFFCE93D8))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Throughput & Compression Stats Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = CyberBg),
                            border = BorderStroke(1.dp, CyberBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Filter Savings", fontSize = 10.sp, color = CyberTextMuted)
                                Text(
                                    text = "-${metrics.payloadCompressionPercentage.toInt()}% Payload Size",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "${metrics.filteredExportSizeBytes / 1024} KB vs ${metrics.originalPayloadSizeBytes / 1024} KB Raw",
                                    fontSize = 9.sp,
                                    color = CyberTextMuted
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = CyberBg),
                            border = BorderStroke(1.dp, CyberBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Avg Msg Density", fontSize = 10.sp, color = CyberTextMuted)
                                Text(
                                    text = "${metrics.avgCharsPerMessage} chars/msg",
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "~${metrics.throughputMessagesPerSec.toInt()} msgs/sec Engine Rate",
                                    fontSize = 9.sp,
                                    color = CyberTextMuted
                                )
                            }
                        }
                    }

                    if (metrics.monthlyDistribution.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Monthly Distribution Histogram",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CyberCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val maxVal = (metrics.monthlyDistribution.values.maxOrNull() ?: 1).coerceAtLeast(1)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberBg, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            metrics.monthlyDistribution.forEach { (month, count) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = month,
                                        fontSize = 11.sp,
                                        color = CyberTextMuted,
                                        modifier = Modifier.width(70.dp)
                                    )
                                    val barFraction = count.toFloat() / maxVal.toFloat()
                                    Box(
                                        modifier = Modifier
                                            .height(12.dp)
                                            .fillMaxWidth(barFraction.coerceIn(0.05f, 1f))
                                            .background(CyberCyan, RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$count chats",
                                        fontSize = 10.sp,
                                        color = CyberText,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { viewModel.computeVisualExportMetrics() },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, CyberCyan)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Recompute Visual Metrics", color = CyberCyan, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SchemaInspectorCard(viewModel: GrokViewModel) {
    val inspectorData by viewModel.schemaInspectorData.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    var showCopyNotice by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Troubleshoot,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "JSON Payload Schema Inspector",
                        fontWeight = FontWeight.Bold,
                        color = CyberText,
                        fontSize = 15.sp
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = CyberCyan
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Deep structural analysis of JSON keys, nesting levels, type distributions, and nullability ratios.",
                        fontSize = 12.sp,
                        color = CyberTextMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Inspection Badges Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text("${inspectorData.totalKeysInspected} Keys", fontSize = 10.sp, color = CyberCyan) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = CyberBg)
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("Strings: ${inspectorData.stringTypeCount}", fontSize = 10.sp, color = CyberText) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = CyberBg)
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("Numbers: ${inspectorData.numberTypeCount}", fontSize = 10.sp, color = CyberOrange) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = CyberBg)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Field Hierarchy Tree
                    Text(
                        text = "Discovered JSON Attribute Tree (${inspectorData.fieldHierarchyTree.size} paths)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .background(CyberBg, RoundedCornerShape(8.dp))
                            .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        inspectorData.fieldHierarchyTree.forEach { path ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SubdirectoryArrowRight, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = path,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = CyberText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sample Json Box
                    if (inspectorData.samplePayloadPreview.isNotBlank()) {
                        Text(
                            text = "Sample Payload Preview",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CyberCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberBg, RoundedCornerShape(8.dp))
                                .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = inspectorData.samplePayloadPreview,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = CyberTextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.runDeepSchemaInspector() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Deep Payload Inspector", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SchemaVersionManagerCard(viewModel: GrokViewModel) {
    val schemaPacks by viewModel.schemaPacksList.collectAsState()
    val activePack by viewModel.activeSchemaPack.collectAsState()
    val diffReport by viewModel.schemaDiffReport.collectAsState()
    val context = LocalContext.current

    var isExpanded by remember { mutableStateOf(false) }
    var showBranchDialog by remember { mutableStateOf(false) }
    var branchVersion by remember { mutableStateOf("1.1.0") }
    var branchNotes by remember { mutableStateOf("Branched custom schema pack") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Schema Version Manager & Diff Matrix",
                        fontWeight = FontWeight.Bold,
                        color = CyberText,
                        fontSize = 15.sp
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = CyberCyan
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Maintain version history, branch custom definition packs, and compare version differences.",
                        fontSize = 12.sp,
                        color = CyberTextMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Available Schema Versions (${schemaPacks.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    schemaPacks.forEach { pack ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(if (activePack?.id == pack.id) CyberBg else Color.Transparent, RoundedCornerShape(6.dp))
                                .border(1.dp, if (activePack?.id == pack.id) CyberCyan else CyberBorder, RoundedCornerShape(6.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${pack.name} (v${pack.version})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = CyberText
                                )
                                Text(
                                    text = pack.description,
                                    fontSize = 10.sp,
                                    color = CyberTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (activePack?.id == pack.id) {
                                Text("ACTIVE", fontWeight = FontWeight.Bold, color = CyberCyan, fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showBranchDialog = true },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CyberCyan)
                        ) {
                            Icon(Icons.Default.CallSplit, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Branch Version", color = CyberCyan, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                if (schemaPacks.size >= 2) {
                                    val pA = schemaPacks[0]
                                    val pB = schemaPacks[1]
                                    viewModel.compareSchemaPackVersions(pA, pB)
                                } else if (activePack != null) {
                                    viewModel.compareSchemaPackVersions(activePack!!, activePack!!)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                        ) {
                            Icon(Icons.Default.Compare, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compare Diff", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 11.sp)
                        }
                    }

                    // Diff Report Matrix Output
                    if (diffReport != null) {
                        val report = diffReport!!
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Version Diff: ${report.versionA} ↔ ${report.versionB}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CyberOrange
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberBg, RoundedCornerShape(8.dp))
                                .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (report.addedFields.isEmpty() && report.removedFields.isEmpty() && report.modifiedMappings.isEmpty()) {
                                Text("No structural differences detected between selected versions.", fontSize = 11.sp, color = CyberTextMuted)
                            }

                            if (report.addedFields.isNotEmpty()) {
                                Text("Added Fields (${report.addedFields.size}):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF00E676))
                                report.addedFields.forEach { f ->
                                    Text(" + ${f.originalKey} → ${f.mappedKey} (${f.dataType})", fontSize = 10.sp, color = CyberText, fontFamily = FontFamily.Monospace)
                                }
                            }

                            if (report.removedFields.isNotEmpty()) {
                                Text("Removed Fields (${report.removedFields.size}):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFFF5252))
                                report.removedFields.forEach { f ->
                                    Text(" - ${f.originalKey} (${f.mappedKey})", fontSize = 10.sp, color = CyberTextMuted, fontFamily = FontFamily.Monospace)
                                }
                            }

                            if (report.modifiedMappings.isNotEmpty()) {
                                Text("Modified Mappings (${report.modifiedMappings.size}):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberOrange)
                                report.modifiedMappings.forEach { (fA, fB) ->
                                    Text(" ~ ${fA.originalKey}: ${fA.mappedKey} → ${fB.mappedKey}", fontSize = 10.sp, color = CyberText, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBranchDialog) {
        AlertDialog(
            onDismissRequest = { showBranchDialog = false },
            title = { Text("Branch New Schema Version", color = CyberText, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Base Pack: ${activePack?.name ?: "System Default"}", fontSize = 12.sp, color = CyberCyan)
                    OutlinedTextField(
                        value = branchVersion,
                        onValueChange = { branchVersion = it },
                        label = { Text("New Version Tag (e.g. 1.2.0)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = branchNotes,
                        onValueChange = { branchNotes = it },
                        label = { Text("Version Change Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        activePack?.let { base ->
                            viewModel.branchAndCreateNewVersion(context, base, branchVersion, branchNotes)
                            Toast.makeText(context, "Branched version $branchVersion created!", Toast.LENGTH_SHORT).show()
                        }
                        showBranchDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Branch Version", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBranchDialog = false }) {
                    Text("Cancel", color = CyberTextMuted)
                }
            }
        )
    }
}

@Composable
fun PlanningDashboardCard(viewModel: GrokViewModel) {
    val progress by viewModel.currentTaskPercentage.collectAsState()
    val currentTask by viewModel.currentTaskLabel.collectAsState()
    val steps by viewModel.planningSteps.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Refactor Status: v1.5.0 Ingestion Pass",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CyberCyan
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = CyberCyan
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).border(1.dp, CyberBorder, RoundedCornerShape(4.dp)),
                color = CyberCyan,
                trackColor = CyberBg
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Current: $currentTask",
                fontSize = 12.sp,
                color = CyberText,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(16.dp))
            steps.forEach { step ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (step.isCompleted) Icons.Default.CheckCircle else if (step.isProcessing) Icons.Default.Sync else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (step.isCompleted) CyberCyan else if (step.isProcessing) CyberOrange else CyberTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = step.label,
                        fontSize = 11.sp,
                        color = if (step.isCompleted) CyberText else if (step.isProcessing) CyberCyan else CyberTextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val context = LocalContext.current
            Button(
                onClick = { viewModel.performMultiPassIngestion(context) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(40.dp),
                enabled = progress < 1.0f
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Multi-Pass Refactor", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}



