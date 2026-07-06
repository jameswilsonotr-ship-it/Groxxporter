# xAI Grok Data Export Schema & Processing Guide

This document defines the JSON structure of xAI Grok data exports, data types, and the high-efficiency architecture used by the **Grok Export Extractor** to parse and decode archives up to 4.9 GB securely on device.

---

## 1. Expected JSON Schemas & Structures

Depending on the version of the Google/xAI archive, conversations are formatted in one of the following two schemas. The extractor dynamically detects and processes both.

### Schema A: Conversation Array (Flat List)
```json
[
  {
    "id": "7abc-1234-def5-6789",
    "title": "Quantum Computing Basics",
    "create_time": 1719878400000,
    "messages": [
      {
        "id": "msg-001",
        "role": "user",
        "text": "What is superposition?",
        "create_time": 1719878410000
      },
      {
        "id": "msg-002",
        "role": "grok",
        "text": "Superposition is the ability of a quantum system to be in multiple states simultaneously...",
        "create_time": 1719878430000
      }
    ]
  }
]
```

### Schema B: Nested Object with Root Attribute
```json
{
  "conversations": [
    {
      "conversation_id": "9abc-5678-def1-2345",
      "title": "Android Custom View Optimization",
      "timestamp": "2026-07-05T15:46:26.000Z",
      "chat_messages": [
        {
          "message_id": "m-100",
          "sender": "user",
          "content": {
            "text": "How do I optimize Canvas draw operations?"
          },
          "timestamp": "2026-07-05T15:46:30.000Z"
        }
      ]
    }
  ]
}
```

---

## 2. On-Device Stream Parsing Strategy

To handle a **900 MB - 1.2 GB raw JSON blob** inside a standard Android heap (like a Pixel 9a which may impose a 256MB or 512MB per-app JVM memory cap), standard JSON parsers like Gson, Jackson, or standard Kotlinx.Serialization will throw an `OutOfMemoryError` (OOM) because they construct the entire object graph in memory.

### Our Solution: `android.util.JsonReader` Streaming
Instead of loading the entire string, we stream the characters directly from the compressed ZIP stream:

1. **Non-Closing Wrap**: We read from `ZipInputStream` using a custom non-closing stream wrap so that closing the JSON reader does not prematurely terminate the ZIP input stream.
2. **Token-by-Token Navigation**: We evaluate keys (`id`, `messages`, `text`) one token at a time.
3. **Lazy Allocation**: We compile and yield single `Conversation` items, allowing the garbage collector to reclaim processed items continuously.
4. **Filtered Selection**: If date filters are active, we skip building full structures for non-matching records immediately at the parser level, saving massive allocations.

---

## 3. Skeletal Remains & Byte-For-Byte Reassembly

To ensure complete metadata retention and validation, the extraction pipeline outputs:
- **`skeletal_structure.json`**: Keeps all conversation frameworks, message counts, IDs, roles, and timestamps intact, but wipes out the heavy string bodies.
- **`conversations.md` / `conversations.html`**: The human-readable structured logs.
- **SHA-256 Checksum Verification**: We compute the cryptographic digest of the primary incoming JSON file and store it in `sha256_verification.txt`. This can be verified on any machine to guarantee zero file tampering.
