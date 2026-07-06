package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Header Hero Banner
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    HeroBanner()
                }

                // Tab Selector
                item {
                    StatusTabSelector(
                        selectedTab = activeTab,
                        onTabSelected = { activeTab = it }
                    )
                }

                if (activeTab == 0) {
                    // Main State Router
                    when (val state = importState) {
                    is ImportState.Idle -> {
                        item {
                            ImportLauncherCard(
                                jobLabelInput = jobLabelInput,
                                onJobLabelChange = { viewModel.jobLabelInput.value = it },
                                onLaunchPicker = {
                                    pickArchiveLauncher.launch("*/*")
                                },
                                onLoadDemo = {
                                    viewModel.loadSampleArchive(context)
                                }
                            )
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
                            AdvancedDashboardTogglesCard(viewModel = viewModel)
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

                        item {
                            AutoBackupHistoryCard(viewModel = viewModel)
                        }

                        item {
                            GrokLoggerPanel(logs = logs)
                        }
                    }

                    is ImportState.Loading -> {
                        item {
                            ParsingProgressCard(
                                progressCount = state.progress,
                                currentFileMsg = state.currentFile,
                                stats = stats
                            )
                        }

                        item {
                            GrokLoggerPanel(logs = logs)
                        }
                    }

                    is ImportState.Success -> {
                        item {
                            MetricsSummaryCard(stats = state.stats)
                        }

                        item {
                            VisualizationsDashboardCard(conversations = state.conversations)
                        }

                        if (enableBatchMode) {
                            item {
                                BatchConsoleCard(viewModel = viewModel)
                            }
                        }

                        item {
                            RecursiveBinarySearchCard(viewModel = viewModel)
                        }

                        // Cryptographic verification details if available
                        validationMatched?.let { matched ->
                            item {
                                IntegrityVerificationCard(
                                    validationMatched = matched,
                                    sha256Checksum = sha256Checksum
                                )
                            }
                        }

                        // Export Actions Card
                        item {
                            ExportControlCard(
                                viewModel = viewModel,
                                onLaunchFolderPicker = { pickFolderLauncher.launch(null) },
                                onTriggerExport = { viewModel.startExport(context) },
                                onShareExport = {
                                    if (exportState is ExportState.Success) {
                                        val successState = exportState as ExportState.Success
                                        shareExportedFile(context, successState.fileUri)
                                    }
                                }
                            )
                        }

                        item {
                            GrokLoggerPanel(logs = logs)
                        }

                        // Local Conversations Search and Browser
                        item {
                            Text(
                                text = "Browse Parsed Chats",
                                style = MaterialTheme.typography.titleMedium,
                                color = CyberCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search title, ID, or text...", color = CyberTextMuted) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan) },
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

                        val filteredChats = state.conversations.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                            it.id.contains(searchQuery, ignoreCase = true) ||
                            it.messages.any { m -> m.text.contains(searchQuery, ignoreCase = true) }
                        }

                        if (filteredChats.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No chats found matching search filter.", color = CyberTextMuted)
                                }
                            }
                        } else {
                            items(filteredChats) { chat ->
                                ChatPreviewItemCard(
                                    conversation = chat,
                                    isExpanded = selectedPreviewChat?.id == chat.id,
                                    onClick = {
                                        selectedPreviewChat = if (selectedPreviewChat?.id == chat.id) null else chat
                                    }
                                )
                            }
                        }
                    }

                    is ImportState.Error -> {
                        item {
                            ErrorDisplayCard(
                                message = state.message,
                                onRetry = { viewModel.resetState() }
                            )
                        }

                        item {
                            GrokLoggerPanel(logs = logs)
                        }
                    }
                }
                } else {
                    item {
                        StatusTrackerDashboard(viewModel = viewModel)
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
    jobLabelInput: String,
    onJobLabelChange: (String) -> Unit,
    onLaunchPicker: () -> Unit,
    onLoadDemo: () -> Unit
) {
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
                text = "Select xAI Export File",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = CyberText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose the raw ZIP archive (up to 4.9 GB) or extracted conversations JSON file.",
                fontSize = 13.sp,
                color = CyberTextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Job label text field inside File Importer UI
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Export Bundle Ready!", fontWeight = FontWeight.Bold, color = CyberText, fontSize = 15.sp)
                        
                        if (customExportFolderName != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Also saved copy to custom directory: $customExportFolderName",
                                color = CyberCyan,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
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
fun ChatPreviewItemCard(
    conversation: Conversation,
    isExpanded: Boolean,
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
                    Text(
                        text = conversation.title.ifBlank { "Untitled Chat" },
                        fontWeight = FontWeight.Bold,
                        color = CyberText,
                        fontSize = 14.sp,
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
                    Divider(color = CyberBorder)
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
                            Text(
                                text = msg.text,
                                color = CyberText,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberBg, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            )
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
fun GrokLoggerPanel(logs: List<String>) {
    var isExpanded by remember { mutableStateOf(true) }
    var showFailureInspector by remember { mutableStateOf(false) }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Real-Time Action Logs",
                        fontWeight = FontWeight.Bold,
                        color = CyberText,
                        fontSize = 13.sp
                    )
                }

                TextButton(
                    onClick = { showFailureInspector = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = CyberOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Inspector", color = CyberOrange, fontSize = 11.sp)
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
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

