# Grok / xAI Sovereign Export Schema Guide (v1.4.0)

This guide documents the evolving JSON schema for xAI Grok exports as of August 2026, and how the Groxxporter engine resolves dynamic keys.

## Core Schema Resolution

The engine utilizes a fuzzy matching system to handle schema drift across different Grok model versions.

### 1. Conversation Root
- **Aliases**: `id`, `conversation_id`, `uuid`, `chat_id`
- **Title Aliases**: `title`, `subject`, `name`, `topic`
- **Message List Aliases**: `messages`, `chat_messages`, `turns`, `dialogue`, `history`

### 2. Message Object
- **Role Aliases**: `role`, `sender`, `author_role`
- **Content Aliases**: `text`, `content`, `body`, `prompt`, `response`
- **Thinking Trace Aliases**: `thinking_trace`, `reasoning_content`, `thought_process`, `chain_of_thought`

## Performance & Large Exports

### 10MB Head/Tail Chunking
For massive archives (>100MB), the engine provides a "Head/Tail" mode which extracts the first and last 10MB of the raw JSON array. This allows for rapid triage of the earliest and most recent context without loading the entire multi-GB payload into memory.

### Integrity Verification
The engine calculates SHA-256 checksums at each stage:
1. Raw archive ingest
2. Normalized message reassembly
3. Skeletal metadata extraction

## Export Formats
- **Markdown**: Optimized for Obsidian and Logseq.
- **HTML**: High-fidelity conversational view.
- **JSONL**: Optimized for Letta and LLM fine-tuning pipelines.
- **CSV**: Structured data analysis.
