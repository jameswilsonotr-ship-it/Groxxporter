# Changelog

## [1.4.0] - 2026-08-10
- **Production Refactor**: Purged legacy "Iron Pearl" branding and retro C64 aesthetic debris.
- **Memory-Efficient Chunking**: Refactored JSON Head/Tail splitter to use non-blocking streams and `FileDescriptor` seeking, supporting multi-GB exports without memory spikes.
- **UI Refresh**: Implemented "10MB Head/Tail Ingestion Mode" toggle in Dashboard for large-scale archive triage.
- **Robustness**: Enhanced dynamic key resolution in `GrokParser` to handle evolving xAI schema changes.
- **Stability**: Incremented to version 1.4.0 (Code 6) for production stability baseline.

## [0.0.5] - 2026-08-10
- Added split and chunk engine for raw JSON archives (first 10MB & last 10MB chunking).
- Added toggleable Split/Clear state in ImportLauncherCard upon successful JSON archive load.
- Integrated Jetpack DataStore preferences manager for persistent user settings across sessions.
- Added Advanced Schema Inspector and Schema Version Diff Manager.
- Updated versionCode to 5 for Play Console publishing compliance.
