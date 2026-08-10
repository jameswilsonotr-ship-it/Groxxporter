# Grok Export Extractor & Schema Engine (C64 Pirate Edition)

A retro-futuristic C64-inspired Android application combining ANSI/ASCII aesthetics, 1800s oriental pirate ship motifs, and high-performance xAI Grok chat export parsing, JSON schema discovery, deep inspection telemetry, and DataStore persistence.

## Features
- **Dynamic Stream Parser**: Token-based streaming parser supporting multi-gigabyte xAI export archives (ZIP & raw JSON).
- **Split & Chunk Engine**: Split raw JSON archives into first 10MB and last 10MB chunks with book-format transcripts.
- **DataStore Persistence**: Robust local preferences saving auto-save intervals, schema definitions, and export filters across sessions.
- **Schema Inspector & Version Manager**: Deep payload analysis, version diff matrix, and custom schema pack branching.
- **Export & Storage Integration**: Native Storage Access Framework (SAF) folder selection and Google Drive backup integration.

## Architecture
Built with Kotlin, Jetpack Compose, Coroutines, Flow, Room, and DataStore following MVVM architecture.
