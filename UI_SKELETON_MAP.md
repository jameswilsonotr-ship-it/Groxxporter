# UI Skeleton Map & State Binding Architecture

Blueprint and structural diagnostic of `DashboardScreen.kt` and `GrokViewModel.kt` for Jetpack Compose UI layout restructuring.

---

## 1. Compose Hierarchy Tree

```
DashboardScreen (Scaffold / LazyColumn Container)
│
├── TopAppBar (DashboardHeaderBar)
│   ├── Title: xAI Grok Sovereign Extractor & Vault Engine
│   ├── Version Badge (v3.0 C64 Pirate Edition)
│   └── Quick Actions (Help Guide, Forensics Archive)
│
├── Section 1: Ingestion & Pipeline Control Panel
│   ├── Local File Picker Button (GetContent - ZIP/JSON)
│   ├── Sample xAI Archive Loader Button
│   └── System Reset / Clear State Button
│
├── Section 2: Sovereign Status Deck (SovereignStatusCard)
│   ├── Import State Indicator (Idle / Loading / Success / Error)
│   ├── SHA-256 Byte-for-Byte Reassembly Status
│   └── PII Scrubbing & Security Flags
│
├── Section 3: Interactive Verification & Preview Card (StepByStepPreviewCard)
│   ├── Stage Selector Tab Row (Stage 1: Payload / Stage 2: Batches / Stage 3: Commit)
│   ├── Stage 1: Conversational Payload Verification
│   │   ├── Total Conversations Detected
│   │   └── Total Message Objects (Synced with Analytics)
│   ├── Stage 2: Date Range & Batch Slicing Preview
│   │   ├── Batch Strategy Filter Chips (Single, Monthly, Count 10/25)
│   │   └── Date Filter Sliders
│   └── Stage 3: Commit & Publishing Verification
│       ├── Local Target Folder Status
│       ├── Google Drive Sync Status
│       └── Direct Drive Publish Action
│
├── Section 4: Google Drive Remote Integration Card (GoogleDriveIntegrationCard)
│   ├── Auth State (Google One Tap / OAuth Access Token)
│   ├── Drive Account Info / Email
│   ├── File Browser & Remote Importer
│   └── Direct Drive Download Trigger
│
├── Section 5: Bundle Compilation & Export Control Card (ExportControlCard)
│   ├── Export Output Settings (Obsidian Vault, PII Masking, Line Numbers)
│   ├── Custom Output Directory Picker (OpenDocumentTree SAF)
│   ├── Generate Export Archive Button
│   ├── Progress Bar & Progress Message
│   └── Share / Save Decoded ZIP Button
│
├── Section 6: Grok Forensics & System Logger Panel (GrokLoggerPanel)
│   ├── Log Level Selector (ALL, INFO, WARN, ERROR, DEBUG)
│   ├── Search Query Text Input
│   ├── Scrollable Monospace Terminal Canvas
│   └── Terminal Controls (Copy Logs, Clear Console)
│
├── Section 7: Browse Parsed Conversations (LazyColumn Items)
│   ├── Search Field (Title, Conversation ID, Message Content, Reasoning)
│   └── ChatPreviewItemCard (Repeated)
│       ├── Chat Title (Auto-generated 50 chars + Msg Count)
│       ├── Thread Metadata (Timestamp, ID, Message Count)
│       └── Expandable Message Bubble List
│           ├── Role Badge (User / Grok)
│           ├── Reasoning / Thinking Trace Dropdown
│           └── Message Body Text
│
└── Section 8: Interactive Analytics Dashboard (MetricsSummaryCard)
    ├── Total Conversations Parsed & Filtered
    ├── User Prompts vs Grok AI Responses Ratio
    ├── Total Characters Extracted
    └── First & Last Conversation Timestamps
```

---

## 2. State Binding Mapping

| UI Component / Block | ViewModel `StateFlow` Variables | State Description & UI Control |
| :--- | :--- | :--- |
| **Ingestion Controls** | `importState` (`ImportState`) | Disables inputs during `ImportState.Loading`, displays success stats on `Success`. |
| **Sovereign Status Deck** | `importState`, `sha256VerificationStatus`, `validationMatched` | Controls integrity badge color (Cyan for verified match, Orange for skip/error). |
| **Stage 1 Verification Card** | `importState`, `stats` | Computes `totalDetectedConversations` and `totalExtractedMessages` directly from `ImportState.Success.conversations` list and `ExtractionStats`. |
| **Stage 2 Batch Slicing** | `batchStrategy`, `startDateFilter`, `endDateFilter` | Controls batch filter chip selection and active date window display. |
| **Stage 3 Commit & Publish** | `driveAccessToken`, `isPublishingToDrive`, `drivePublishStatus` | Manages direct upload trigger button state and progress feedback. |
| **Google Drive Card** | `driveAccessToken`, `driveUserEmail`, `isDriveLoading`, `driveFiles` | Renders sign-in flow when `driveAccessToken` is null; shows file list when authenticated. |
| **Export Control Card** | `exportState` (`ExportState`), `exportProgress`, `exportProgressMessage`, `customExportFolderUri` | Shows progress bar during compilation; displays "Share / Save Decoded ZIP" on `ExportState.Success`. |
| **Forensics Logger Panel** | `logs` (`GrokLogger.logs`), `verboseDebugEnabled` | Feeds stream of diagnostic logs to terminal UI; controls log verbosity. |
| **Browse Parsed Chats** | `importState` (`conversations`), `searchQuery` | Filters conversation list by scanning `title`, `id`, `message.text`, `thinkingTrace`, and `metadataJson`. |
| **Analytics Dashboard** | `stats`, `importState` | Renders aggregate user message counts, character volume, and timeline dates. |

---

## 3. User Interaction Flow Sequences

### Flow A: Loading a File (Local vs. Drive)
1. **Local File Load:**
   - User taps **"Load Archive / JSON"** in UI.
   - Triggers `ActivityResultContracts.GetContent()`.
   - User selects `.zip` or `.json` file from Android system picker.
   - Callback receives `Uri` -> calls `viewModel.startImport(context, uri)`.
   - `_importState` shifts to `ImportState.Loading`.
   - `GrokParser.parseConversationsStream()` streams and parses payload on `Dispatchers.IO`.
   - On completion, `_importState` updates to `ImportState.Success(conversations, stats)`.

2. **Google Drive Remote Load:**
   - User taps **"Import Selected File"** inside Google Drive Integration Card.
   - Calls `viewModel.importFileFromDrive(context, fileId, fileName)`.
   - `isDriveLoading` set to `true`.
   - File downloaded via Google Drive REST API.
   - Streamed directly into `GrokParser.parseConversationsStream()`.
   - `_importState` updates to `ImportState.Success`.

### Flow B: Authenticating with Google Drive
1. User taps **"Sign in with Google"** inside `GoogleDriveIntegrationCard`.
2. Triggers `CredentialManager` Google ID option request on `context`.
3. User completes OAuth consent dialog.
4. ViewModel obtains authorization code / ID token.
5. `_driveAccessToken` is set to active OAuth token string.
6. `viewModel.fetchDriveFiles()` queries Google Drive API for `.zip` and `.json` files.
7. `driveFiles` `StateFlow` populated; UI displays downloadable remote xAI exports.

### Flow C: Triggering JSON Stream Parse
1. Invoked automatically during Local or Remote file import.
2. `GrokParser.parseConversationsStream()` initializes low-memory `JsonReader`.
3. Streams JSON array or object items without loading full file into memory.
4. Auto-generates chat titles: if missing or `"Untitled"`, sets title to first 50 characters of first `USER` message turn + appends message count `"(X msgs)"`.
5. Progress callback updates `_importProgress` state and `_stats` every 10 conversations.
6. Execution calculates SHA-256 byte-for-byte reassembly checksum.
7. `_importState` updated with parsed `List<Conversation>`.

### Flow D: Executing Stage 3 Commit & Publish Sequence
1. User navigates to **Stage 3: Commit & Publishing Verification** tab in `StepByStepPreviewCard`.
2. User taps **"Publish Directly to Google Drive"** or **"Share / Save Decoded ZIP"**.
3. **If Google Drive Publish:**
   - Calls `viewModel.publishExportToGoogleDrive(context)`.
   - `isPublishingToDrive` set to `true`.
   - Background coroutine packages Obsidian Markdown / JSON archive into ZIP stream.
   - Uploads ZIP directly to user's Google Drive root/folder via Drive Multipart API.
   - `drivePublishStatus` updated with success confirmation.
4. **If Local Share / Save Decoded ZIP:**
   - User taps **"Share / Save Decoded ZIP"**.
   - If `customExportFolderUri` is null, triggers `ActivityResultContracts.OpenDocumentTree()` folder picker first.
   - User selects SAF output directory on device.
   - `viewModel.setCustomExportFolderUri(context, uri)` saves persistable URI permissions.
   - Package saved to target SAF directory and Android system share sheet launched.
