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

    // Filtered view search query
    var searchQuery by remember { mutableStateOf("") }
    var selectedPreviewChat by remember { mutableStateOf<Conversation?>(null) }

    // Launcher for ZIP or JSON import
    val pickArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.startImport(context, it) }
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

                // Main State Router
                when (val state = importState) {
                    is ImportState.Idle -> {
                        item {
                            ImportLauncherCard(
                                onLaunchPicker = {
                                    pickArchiveLauncher.launch("*/*")
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
                    }

                    is ImportState.Loading -> {
                        item {
                            ParsingProgressCard(
                                progressCount = state.progress,
                                currentFileMsg = state.currentFile,
                                stats = stats
                            )
                        }
                    }

                    is ImportState.Success -> {
                        item {
                            MetricsSummaryCard(stats = state.stats)
                        }

                        // Export Actions Card
                        item {
                            ExportControlCard(
                                exportState = exportState,
                                onTriggerExport = { viewModel.startExport(context) },
                                onShareExport = {
                                    if (exportState is ExportState.Success) {
                                        val successState = exportState as ExportState.Success
                                        shareExportedFile(context, successState.fileUri)
                                    }
                                }
                            )
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
fun ImportLauncherCard(onLaunchPicker: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable { onLaunchPicker() },
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
            Button(
                onClick = onLaunchPicker,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("select_file_button")
            ) {
                Text("Browse Files", fontWeight = FontWeight.Bold)
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
            CircularProgressIndicator(
                color = CyberCyan,
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Processing Locally...",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = CyberText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentFileMsg,
                fontSize = 12.sp,
                color = CyberTextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
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
    exportState: ExportState,
    onTriggerExport: () -> Unit,
    onShareExport: () -> Unit
) {
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (exportState) {
                is ExportState.Idle -> {
                    Text(
                        text = "Build Sanitized Package",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = CyberText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pack all filtered transcripts, spreadsheets, static viewers, and fully decoded attachments into a consolidated ZIP.",
                        fontSize = 12.sp,
                        color = CyberTextMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onTriggerExport,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("export_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Now", fontWeight = FontWeight.Bold)
                    }
                }

                is ExportState.Exporting -> {
                    CircularProgressIndicator(color = CyberCyan, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Creating package, decoding hex binaries...", color = CyberTextMuted, fontSize = 13.sp)
                }

                is ExportState.Success -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Export Bundle Ready!", fontWeight = FontWeight.Bold, color = CyberText)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onShareExport,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberOrange, contentColor = CyberText),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share / Save Decoded ZIP", fontWeight = FontWeight.Bold)
                    }
                }

                is ExportState.Error -> {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CyberOrange, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Export Failed", fontWeight = FontWeight.Bold, color = CyberOrange)
                    Text(exportState.message, color = CyberTextMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onTriggerExport,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Retry Export", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
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
