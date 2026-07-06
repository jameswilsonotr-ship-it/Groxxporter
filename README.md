# Grok Export Extractor & Integrity Suite

An offline-first, high-fidelity developer companion and utility built for extracting, chunking, parsing, and verifying xAI Grok conversation zip archives with a cyberpunk neon interface.

---

## 🌌 Architectural Design Philosophy

### 1. Zero Token-Munching Rider Protection
Traditional apps often "munch" user tokens by background-routing all parsed data streams directly through cloud AI endpoints. Under this codebase:
- **Core Pipeline is 100% Local**: Parsing, CSV/Markdown/HTML compilation, attachment hex-decoding, and database persistence occur entirely on-device, offline.
- **Opt-In Integrity Verification**: Cloud-based or on-device AI audits are strictly **opt-in per session/conversation**, isolated from the primary parsing loop. Your data is kept private, fast, and token-efficient.

### 2. On-Device Gemini Nano Co-Processing (Android 15+ / 16 / 17 Beta)
Designed to run beautifully on high-tier silicon like the Tensor G4 inside the **Pixel 9a**:
- Uses the system-level **Google AI Edge SDK** (formerly AICore) to load **Gemini Nano** directly into the on-device NPU.
- Performs full chunking validation, boundary inspection, and slicing diagnostics with **0ms network overhead**, complete offline operation, and **zero API costs**.

---

## 🛠️ Features

*   **Cyberpunk Interface**: A gorgeous dark UI theme with glowing cyan and orange accents, status micro-chips, and dynamic metrics.
*   **Granular Parser Status Dashboard**: A centralized status tracking dashboard integrating a live diagnostic error inspector, backup snapshot controller, and priority metrics.
*   **Gemini Integrity Sandbox**: Toggleable cloud-based (Gemini 3.5 Flash) and local (Gemini Nano) verification decks to audit slicing integrity, boundary loss, and recombination perfectness.
*   **Extensible Todo & Changelog Roadmap**: A dynamically synchronized release checklist featuring high, medium, and low priority indicators.

---

## ⚙️ Key Configuration & Key Binding

The application leverages a developer-supplied API key for cloud verification checks, isolated from client account configurations:
- **Developer Key Binding**: Set your keys in the AI Studio Secrets panel. The system injects this into `BuildConfig.GEMINI_API_KEY` at runtime.
- **Client Security**: No end-user login or account creation is required to use the pipeline. If no API key is supplied, the Integrity Sandbox cleanly falls back to high-fidelity on-device local simulation reports.

---

## 📈 Developer Roadmap & Milestone Status

Active tasks are tracked in `/TODO.md` and parsed directly into the status dashboard with dynamic count badges:
- 🔴 **High Priority**: Advanced attachment filtering, in-app markdown viewer, local search index indexing.
- 🔵 **Medium Priority**: Scheduled backups via WorkManager, custom HTML styling, Google Drive cloud integration.
- ⚪ **Low Priority**: Media slideshow decks, JSON Schema validation.
