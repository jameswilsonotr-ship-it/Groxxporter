package com.example.parser

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

data class GrokJob(
    val number: Int,
    val label: String,
    val timestamp: Long,
    val status: String, // "RUNNING", "COMPLETED", "FAILED"
    val originalChecksum: String = "",
    val reassembledChecksum: String = "",
    val totalConversations: Int = 0,
    val totalCharacters: Long = 0,
    val binaryFilesProcessed: Int = 0,
    val hexFilesDecoded: Int = 0,
    val folderPath: String = ""
)

object GrokJobManager {
    private const val JOBS_DIR = "grok_jobs"

    private fun getJobsRoot(context: Context): File {
        val root = File(context.filesDir, JOBS_DIR)
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }

    fun getAllJobs(context: Context): List<GrokJob> {
        val root = getJobsRoot(context)
        val jobDirs = root.listFiles { file -> file.isDirectory && file.name.startsWith("Job_") } ?: emptyArray()
        
        val jobs = mutableListOf<GrokJob>()
        for (dir in jobDirs) {
            val infoFile = File(dir, "job_info.json")
            if (infoFile.exists()) {
                try {
                    val job = readJobInfo(infoFile, dir.absolutePath)
                    jobs.add(job)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return jobs.sortedByDescending { it.number }
    }

    fun createNewJob(context: Context, label: String): Pair<GrokJob, File> {
        val root = getJobsRoot(context)
        val existingJobs = getAllJobs(context)
        val nextNumber = if (existingJobs.isEmpty()) 1 else existingJobs.maxOf { it.number } + 1
        
        val timestamp = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
        val labelClean = if (label.isBlank()) "GrokExport" else label
        val safeLabel = labelClean.replace(Regex("[^a-zA-Z0-9_]"), "_").take(15)
        val folderName = "Job_${nextNumber}_${safeLabel}_$dateStr"
        val jobDir = File(root, folderName)
        jobDir.mkdirs()

        val job = GrokJob(
            number = nextNumber,
            label = if (label.isBlank()) "Job #$nextNumber - Extraction" else label,
            timestamp = timestamp,
            status = "RUNNING",
            folderPath = jobDir.absolutePath
        )

        val infoFile = File(jobDir, "job_info.json")
        writeJobInfo(infoFile, job)

        return Pair(job, jobDir)
    }

    fun updateJob(context: Context, job: GrokJob) {
        val jobDir = File(job.folderPath)
        if (jobDir.exists()) {
            val infoFile = File(jobDir, "job_info.json")
            writeJobInfo(infoFile, job)
        }
    }

    fun deleteJob(context: Context, job: GrokJob): Boolean {
        val jobDir = File(job.folderPath)
        return if (jobDir.exists()) {
            jobDir.deleteRecursively()
        } else {
            false
        }
    }

    fun clearAllJobs(context: Context) {
        val root = getJobsRoot(context)
        root.deleteRecursively()
        root.mkdirs()
    }

    private fun writeJobInfo(file: File, job: GrokJob) {
        FileWriter(file).use { writer ->
            val jsonWriter = JsonWriter(writer)
            jsonWriter.setIndent("  ")
            jsonWriter.beginObject()
            
            jsonWriter.name("number").value(job.number)
            jsonWriter.name("label").value(job.label)
            jsonWriter.name("timestamp").value(job.timestamp)
            jsonWriter.name("status").value(job.status)
            jsonWriter.name("originalChecksum").value(job.originalChecksum)
            jsonWriter.name("reassembledChecksum").value(job.reassembledChecksum)
            jsonWriter.name("totalConversations").value(job.totalConversations)
            jsonWriter.name("totalCharacters").value(job.totalCharacters)
            jsonWriter.name("binaryFilesProcessed").value(job.binaryFilesProcessed)
            jsonWriter.name("hexFilesDecoded").value(job.hexFilesDecoded)
            jsonWriter.name("folderPath").value(job.folderPath)
            
            jsonWriter.endObject()
            jsonWriter.flush()
        }
    }

    private fun readJobInfo(file: File, folderPath: String): GrokJob {
        FileReader(file).use { reader ->
            val jsonReader = JsonReader(reader)
            var number = 0
            var label = ""
            var timestamp = 0L
            var status = ""
            var originalChecksum = ""
            var reassembledChecksum = ""
            var totalConversations = 0
            var totalCharacters = 0L
            var binaryFilesProcessed = 0
            var hexFilesDecoded = 0
            
            jsonReader.beginObject()
            while (jsonReader.hasNext()) {
                val name = jsonReader.nextName()
                when (name) {
                    "number" -> number = jsonReader.nextInt()
                    "label" -> label = jsonReader.nextString()
                    "timestamp" -> timestamp = jsonReader.nextLong()
                    "status" -> status = jsonReader.nextString()
                    "originalChecksum" -> originalChecksum = jsonReader.nextString()
                    "reassembledChecksum" -> reassembledChecksum = jsonReader.nextString()
                    "totalConversations" -> totalConversations = jsonReader.nextInt()
                    "totalCharacters" -> totalCharacters = jsonReader.nextLong()
                    "binaryFilesProcessed" -> binaryFilesProcessed = jsonReader.nextInt()
                    "hexFilesDecoded" -> hexFilesDecoded = jsonReader.nextInt()
                    else -> jsonReader.skipValue()
                }
            }
            jsonReader.endObject()
            
            return GrokJob(
                number = number,
                label = label,
                timestamp = timestamp,
                status = status,
                originalChecksum = originalChecksum,
                reassembledChecksum = reassembledChecksum,
                totalConversations = totalConversations,
                totalCharacters = totalCharacters,
                binaryFilesProcessed = binaryFilesProcessed,
                hexFilesDecoded = hexFilesDecoded,
                folderPath = folderPath
            )
        }
    }
}
