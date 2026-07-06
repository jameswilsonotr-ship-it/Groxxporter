package com.example.parser

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GrokParserTest {

    @Test
    fun testCalculateSha256() {
        val input = "Hello, Grok!"
        val expectedHash = "cab9a6f6b9f06ea0839514fcc7aa08be5122c9b9b778b6bf1b908ff30a4eef74" // Actual SHA-256 of "Hello, Grok!"
        val actualHash = GrokParser.calculateSha256(input)
        assertEquals(expectedHash, actualHash)
    }

    @Test
    fun testParseSchemaAFlatList() {
        val json = """
        [
          {
            "id": "conv-1",
            "title": "Quantum Computing",
            "timestamp": 1783260000000,
            "messages": [
              {
                "id": "m-1",
                "role": "user",
                "text": "What is superposition?",
                "timestamp": 1783260010000
              }
            ]
          }
        ]
        """.trimIndent()

        val stream = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
        var progressCalled = false
        val stats = ExtractionStats()
        
        val list = GrokParser.parseConversationsStream(
            inputStream = stream,
            startDate = null,
            endDate = null,
            onProgress = { progressCalled = true },
            onStatsUpdate = { updated ->
                stats.totalConversations = updated.totalConversations
                stats.filteredConversations = updated.filteredConversations
            }
        )

        assertEquals(1, list.size)
        val conv = list[0]
        assertEquals("conv-1", conv.id)
        assertEquals("Quantum Computing", conv.title)
        assertEquals(1, conv.messages.size)
        assertEquals("m-1", conv.messages[0].id)
        assertEquals("user", conv.messages[0].role)
        assertEquals("What is superposition?", conv.messages[0].text)
    }

    @Test
    fun testParseSchemaBNested() {
        val json = """
        {
          "conversations": [
            {
              "id": "conv-2",
              "title": "Astrobiology",
              "timestamp": 1783260000000,
              "messages": [
                {
                  "id": "m-2",
                  "role": "grok",
                  "text": "Life on other planets.",
                  "timestamp": 1783260010000
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val stream = ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
        val stats = ExtractionStats()
        val list = GrokParser.parseConversationsStream(
            inputStream = stream,
            startDate = null,
            endDate = null,
            onProgress = {},
            onStatsUpdate = { stats.totalConversations = it.totalConversations }
        )

        assertEquals(1, list.size)
        assertEquals("conv-2", list[0].id)
        assertEquals("Astrobiology", list[0].title)
        assertEquals("Life on other planets.", list[0].messages[0].text)
    }

    @Test
    fun testVerifyReassembly() {
        val messages = listOf(
            Message("m-1", "user", "Hello", 1000L),
            Message("m-2", "grok", "Hi there", 2000L)
        )
        val conversations = listOf(
            Conversation("conv-1", "Test Chat", 1000L, messages)
        )

        val verification = GrokParser.verifyReassembly(conversations)
        assertTrue(verification.first)
        assertFalse(verification.second.isEmpty())
    }

    @Test
    fun testDetectExtension() {
        // PNG header bytes
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        assertEquals("png", GrokParser.detectExtension(pngBytes))

        // PDF header bytes
        val pdfBytes = byteArrayOf(0x25.toByte(), 0x50.toByte(), 0x44.toByte(), 0x46.toByte())
        assertEquals("pdf", GrokParser.detectExtension(pdfBytes))

        // Text bytes
        val textBytes = "This is simple plain text".toByteArray(Charsets.UTF_8)
        assertEquals("txt", GrokParser.detectExtension(textBytes))
    }
}
