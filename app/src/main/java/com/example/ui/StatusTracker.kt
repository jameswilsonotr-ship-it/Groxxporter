package com.example.ui

import android.content.Context
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.InputStream
import java.util.Locale

// Data structures for Status Deck
data class TodoItem(
    val id: String,
    val title: String,
    val priority: String, // "High", "Medium", "Low"
    val completed: Boolean,
    val isCustom: Boolean = false
)

data class ChangelogEntry(
    val type: String, // "Added", "Improved", "Fixed"
    val description: String
)

data class ChangelogVersion(
    val version: String,
    val date: String,
    val entries: List<ChangelogEntry>
)

// Parsers to read from Assets
fun parseChangelog(context: Context): List<ChangelogVersion> {
    val list = mutableListOf<ChangelogVersion>()
    try {
        val inputStream: InputStream = context.assets.open("CHANGELOG.md")
        val lines = inputStream.bufferedReader().readLines()
        var currentVersion: String? = null
        var currentDate: String? = null
        var currentType: String? = null
        var currentEntries = mutableListOf<ChangelogEntry>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("## [")) {
                if (currentVersion != null) {
                    list.add(ChangelogVersion(currentVersion, currentDate ?: "", currentEntries.toList()))
                    currentEntries = mutableListOf()
                }
                val versionPart = trimmed.substringAfter("## [").substringBefore("]")
                val datePart = trimmed.substringAfter("] - ").trim()
                currentVersion = versionPart
                currentDate = datePart
            } else if (trimmed.startsWith("### ")) {
                currentType = trimmed.substringAfter("### ").trim()
            } else if (trimmed.startsWith("- ")) {
                val desc = trimmed.substringAfter("- ").trim()
                if (currentVersion != null && currentType != null) {
                    currentEntries.add(ChangelogEntry(currentType, desc))
                }
            }
        }
        if (currentVersion != null) {
            list.add(ChangelogVersion(currentVersion, currentDate ?: "", currentEntries.toList()))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun parseTodos(context: Context): List<TodoItem> {
    val list = mutableListOf<TodoItem>()
    try {
        val inputStream: InputStream = context.assets.open("TODO.md")
        val lines = inputStream.bufferedReader().readLines()
        var currentPriority = "Medium"

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("## ")) {
                val header = trimmed.substringAfter("## ").lowercase(Locale.ROOT)
                currentPriority = when {
                    header.contains("high") -> "High"
                    header.contains("medium") -> "Medium"
                    header.contains("low") -> "Low"
                    header.contains("backlog") -> "Low"
                    else -> "Medium"
                }
            } else if (trimmed.startsWith("- [") && trimmed.length > 5) {
                val completed = trimmed[3] == 'x' || trimmed[3] == 'X'
                val title = trimmed.substring(5).trim()
                val id = "todo_" + title.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase(Locale.ROOT).take(30)
                list.add(TodoItem(id, title, currentPriority, completed))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

@Composable
fun StatusTabSelector(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberSurface, RoundedCornerShape(12.dp))
            .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val tabs = listOf(
            "PIPELINE EXTRACTOR" to Icons.Default.Build,
            "SYSTEM STATUS DECK" to Icons.Default.Info
        )

        tabs.forEachIndexed { index, (label, icon) ->
            val active = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (active) CyberCyan else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (active) CyberBg else CyberTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        color = if (active) CyberBg else CyberText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatusTrackerDashboard(
    viewModel: GrokViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val todoList by viewModel.todoList.collectAsState()
    val changelogList by viewModel.changelogList.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val backupsList by viewModel.backupsList.collectAsState()

    val optMarkdown by viewModel.optMarkdown.collectAsState()
    val optHtml by viewModel.optHtml.collectAsState()
    val optJson by viewModel.optJson.collectAsState()
    val optCsv by viewModel.optCsv.collectAsState()
    val optBinaries by viewModel.optBinaries.collectAsState()
    val customExportFolder by viewModel.customExportFolderName.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedPriorityFilter by remember { mutableStateOf("ALL") } // ALL, High, Medium, Low
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // ALL, Pending, Completed

    // Todo add inputs
    var newTodoTitle by remember { mutableStateOf("") }
    var newTodoPriority by remember { mutableStateOf("Medium") }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadStatusTrackerData(context)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: System Metrics Cards
        Text(
            text = "SYSTEM METRICS & CHANNELS",
            color = CyberCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusMetricCard(
                title = "CORE CORE",
                value = "OPERATIONAL",
                color = CyberCyan,
                icon = Icons.Default.CheckCircle,
                modifier = Modifier.weight(1f)
            )
            StatusMetricCard(
                title = "SAVED ARCHIVES",
                value = "${jobs.size} RUNS",
                color = CyberCyan,
                icon = Icons.Default.History,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusMetricCard(
                title = "SNAPSHOTS",
                value = "${backupsList.size} BACKUPS",
                color = CyberOrange,
                icon = Icons.Default.Folder,
                modifier = Modifier.weight(1f)
            )
            val pipelineText = buildString {
                if (optMarkdown) append("MD ")
                if (optHtml) append("HTML ")
                if (optJson) append("JSON ")
                if (optCsv) append("CSV ")
                if (optBinaries) append("BIN")
                if (isEmpty()) append("NONE")
            }.trim()
            StatusMetricCard(
                title = "PIPELINE CHANNELS",
                value = pipelineText,
                color = CyberText,
                icon = Icons.Default.Build,
                modifier = Modifier.weight(1f)
            )
        }

        // Section: Outputs Target Location
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "COMPILE DESTINATION TARGET",
                        color = CyberTextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = customExportFolder ?: "Default Application Sandbox Environment",
                        color = CyberText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Section: Todo & Roadmap List
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DEVELOPER ROADMAP & FOLLOW-UPS",
                            color = CyberText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = CyberBg),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Task", modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search roadmap milestones...", color = CyberTextMuted, fontSize = 11.sp) },
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

                Spacer(modifier = Modifier.height(10.dp))

                // Filter Row 1: Priority
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("ALL", "High", "Medium", "Low").forEach { p ->
                        val active = selectedPriorityFilter == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (active) CyberCyan else CyberBg, RoundedCornerShape(6.dp))
                                .border(1.dp, if (active) CyberCyan else CyberBorder, RoundedCornerShape(6.dp))
                                .clickable { selectedPriorityFilter = p }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p,
                                color = if (active) CyberBg else CyberTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Filter Row 2: Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val statusOpts = listOf(
                        "ALL" to "All Status",
                        "Pending" to "Pending",
                        "Completed" to "Completed"
                    )
                    statusOpts.forEach { (key, display) ->
                        val active = selectedStatusFilter == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (active) CyberOrange else CyberBg, RoundedCornerShape(6.dp))
                                .border(1.dp, if (active) CyberOrange else CyberBorder, RoundedCornerShape(6.dp))
                                .clickable { selectedStatusFilter = key }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = display,
                                color = if (active) CyberBg else CyberTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter Tasks List
                val filteredTodos = todoList.filter { todo ->
                    val matchesQuery = todo.title.contains(searchQuery, ignoreCase = true)
                    val matchesPriority = selectedPriorityFilter == "ALL" || todo.priority == selectedPriorityFilter
                    val matchesStatus = when (selectedStatusFilter) {
                        "Pending" -> !todo.completed
                        "Completed" -> todo.completed
                        else -> true
                    }
                    matchesQuery && matchesPriority && matchesStatus
                }

                if (filteredTodos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No roadmap milestones match filters.", color = CyberTextMuted, fontSize = 11.sp)
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredTodos.forEach { todo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberBg, RoundedCornerShape(8.dp))
                                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.toggleTodoCompleted(context, todo.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (todo.completed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (todo.completed) CyberCyan else CyberOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = todo.title,
                                            color = if (todo.completed) CyberTextMuted else CyberText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                textDecoration = if (todo.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Priority Chip
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        when (todo.priority) {
                                                            "High" -> CyberOrange.copy(alpha = 0.2f)
                                                            "Medium" -> CyberCyan.copy(alpha = 0.2f)
                                                            else -> CyberTextMuted.copy(alpha = 0.2f)
                                                        },
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = todo.priority.uppercase(Locale.ROOT),
                                                    color = when (todo.priority) {
                                                        "High" -> CyberOrange
                                                        "Medium" -> CyberCyan
                                                        else -> CyberTextMuted
                                                    },
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (todo.isCustom) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color.Yellow.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "USER",
                                                        color = Color.Yellow,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (todo.isCustom) {
                                    IconButton(
                                        onClick = { viewModel.deleteCustomTodoTask(context, todo.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = CyberOrange,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Changelog Reconstruction Audit Logs
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RECONSTRUCTION AUDIT LOGS (CHANGELOG)",
                        color = CyberText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (changelogList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Reading changelog audits...", color = CyberTextMuted, fontSize = 11.sp)
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        changelogList.forEach { versionInfo ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberBg, RoundedCornerShape(8.dp))
                                    .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RELEASE ${versionInfo.version}",
                                        color = CyberCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = versionInfo.date,
                                        color = CyberTextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = CyberBorder)
                                Spacer(modifier = Modifier.height(8.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    versionInfo.entries.forEach { entry ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .background(
                                                        when (entry.type) {
                                                            "Added" -> CyberCyan.copy(alpha = 0.15f)
                                                            "Improved" -> Color.Yellow.copy(alpha = 0.15f)
                                                            else -> CyberOrange.copy(alpha = 0.15f)
                                                        },
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        when (entry.type) {
                                                            "Added" -> CyberCyan
                                                            "Improved" -> Color.Yellow
                                                            else -> CyberOrange
                                                        },
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = entry.type.uppercase(Locale.ROOT),
                                                    color = when (entry.type) {
                                                        "Added" -> CyberCyan
                                                        "Improved" -> Color.Yellow
                                                        else -> CyberOrange
                                                    },
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = entry.description,
                                                color = CyberText,
                                                fontSize = 11.sp,
                                                modifier = Modifier.weight(1f)
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
    }

    // Add Todo Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTodoTitle.isNotBlank()) {
                            viewModel.addTodoTask(context, newTodoTitle, newTodoPriority)
                            newTodoTitle = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add Task", color = CyberCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = CyberTextMuted)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Custom Milestone", color = CyberText, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Add a custom developer milestone or bug tracker task to your roadmap.",
                        color = CyberTextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newTodoTitle,
                        onValueChange = { newTodoTitle = it },
                        placeholder = { Text("Milestone title...", color = CyberTextMuted, fontSize = 12.sp) },
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

                    Text("Priority Level:", color = CyberText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("High", "Medium", "Low").forEach { p ->
                            val active = newTodoPriority == p
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (active) CyberCyan else CyberBg, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (active) CyberCyan else CyberBorder, RoundedCornerShape(8.dp))
                                    .clickable { newTodoPriority = p }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = p,
                                    color = if (active) CyberBg else CyberTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            containerColor = CyberSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun StatusMetricCard(
    title: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = CyberTextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
