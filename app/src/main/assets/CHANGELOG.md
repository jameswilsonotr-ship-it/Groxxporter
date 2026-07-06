# Changelog

All notable changes to the **Grok Export Extractor** project will be documented in this file.

## [v1.2.0] - 2026-07-05
### Added
- **Dynamic Custom Output Folder Picker**: Users can now select an external, persistable directory via Android's Document Provider API to save a direct copy of the compiled ZIP package.
- **Granular Export Progress Tracking**: Added a customized, cyberpunk-themed progress indicator and live status stream showcasing exactly what step of the compilation is active (e.g., decoding, zipping, copying).
- **Expandable Bulk Export Package Preview**: Users can now inspect the file tree hierarchy and output templates (Markdown, HTML, JSON, CSV) before initiating the build.
- **Diagnostic Error & Warning Inspector**: A dedicated log inspector with severity filters (ALL, WARN, ERROR), search functionality, clipboard copying, and share intents.
- **Enhanced Extraction Pipeline Logs**: Added explicit tracking of mined attachments, stream metrics, and individual chat compilation logs.

## [v1.1.0] - 2026-06-25
### Added
- **Multi-Format Compilation Engine**: Support for exporting parsed xAI data to clean standalone Markdown summaries, interactive index HTML explorers, structured spreadsheets (CSV), and raw JSON structures.
- **Cyberpunk Visual Overhaul**: Fully customized high-contrast UI theme with modern color palettes (`CyberCyan`, `CyberOrange`), modern status chips, and glowing typography.
- **Base64 / Hex Binary Attachment Decoder**: Automated extraction of embedded images and media inside the zip to individual asset folders.

## [v1.0.0] - 2026-05-10
### Added
- **Initial Launch**: Core local parse engine for xAI Grok `.zip` archive exports.
- **Local SQLite Integration**: Initial database schema for caching parsed conversation history.
- **Log Panel**: Simple sandboxed logs viewer.
