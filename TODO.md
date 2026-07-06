# Future Roadmap & TODO List

The following are planned enhancements, optimizations, and features for upcoming releases of the **Grok Export Extractor** application.

## 🚀 High Priority (Near-Term)
- [ ] **Advanced Attachment Filter**: Allow users to filter attachment extractions by file type (e.g., exclude large video binaries to save space).
- [ ] **In-App Markdown Viewer**: Add a lightweight syntax-highlighted editor to preview and read generated `conversation.md` files directly in-app.
- [ ] **Search Engine Index**: Implement a fast, local indexing system (TF-IDF or Sqlite FTS5) to perform fast full-text search across all extracted histories.

## 🛠️ Medium Priority (Medium-Term)
- [ ] **Scheduled Automated Backups**: Set up a background worker (using WorkManager) to automatically poll, verify, and pack the latest chat histories.
- [ ] **Custom HTML Templates**: Allow advanced users to select custom styling templates or stylesheets for the compiled `conversations.html` explorer.
- [ ] **Cloud Drive Integration**: Direct export and sync capabilities to personal cloud storage (Google Drive, OneDrive, Proton Drive) via secure OAuth.

## 💤 Low Priority (Backlog)
- [ ] **Media Slideshow Deck**: Add a full-screen dynamic gallery deck to browse and scroll through all mined Grok image attachments sequentially.
- [ ] **JSON Schema Validation**: Standardize incoming formats with schema enforcement to gracefully alert users about changes in xAI's export formats.
