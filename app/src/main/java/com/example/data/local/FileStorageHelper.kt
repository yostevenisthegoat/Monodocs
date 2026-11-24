package com.example.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileStorageHelper {

    fun workDir(context: Context): File =
        File(context.cacheDir, "mono_work").apply { mkdirs() }

    fun outputsDir(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "MonoDocs")
            .apply { mkdirs() }

    suspend fun copyUriToWorkFile(
        context: Context,
        uri: Uri,
        targetName: String,
    ): File = withContext(Dispatchers.IO) {
        val safeName = targetName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dest = File(workDir(context), "${System.currentTimeMillis()}_$safeName")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
        input.use { stream ->
            FileOutputStream(dest).use { out ->
                stream.copyTo(out, bufferSize = 32768)
            }
        }
        dest
    }

    suspend fun saveFileToUri(
        context: Context,
        sourceFile: File,
        targetUri: Uri,
    ) = withContext(Dispatchers.IO) {
        if (!hasEnoughSpace(context, sourceFile.length())) {
            throw IllegalStateException("Not enough storage space to complete this operation.")
        }
        val out = context.contentResolver.openOutputStream(targetUri)
            ?: throw IllegalStateException("Could not open output destination.")
        out.use { output ->
            FileInputStream(sourceFile).use { input ->
                input.copyTo(output, bufferSize = 32768)
            }
        }
    }

    suspend fun saveFileToTreeUri(
        context: Context,
        treeUri: Uri,
        sourceFile: File,
        displayName: String,
        mimeType: String,
    ): Uri? = withContext(Dispatchers.IO) {
        if (!hasEnoughSpace(context, sourceFile.length())) {
            throw IllegalStateException("Not enough storage space to complete this operation.")
        }
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val createdUri = DocumentsContract.createDocument(
            context.contentResolver,
            parentDocUri,
            mimeType,
            displayName,
        ) ?: return@withContext null

        saveFileToUri(context, sourceFile, createdUri)
        createdUri
    }

    suspend fun saveToLocalMonoFolder(
        context: Context,
        sourceFile: File,
        desiredName: String,
    ): File = withContext(Dispatchers.IO) {
        if (!hasEnoughSpace(context, sourceFile.length())) {
            throw IllegalStateException("Not enough storage space to complete this operation.")
        }
        val dir = outputsDir(context)
        val safe = desiredName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        var dest = File(dir, safe)
        if (dest.exists()) {
            val base = dest.nameWithoutExtension
            val ext = dest.extension
            dest = File(dir, if (ext.isBlank()) "${base}_${System.currentTimeMillis()}" else "${base}_${System.currentTimeMillis()}.$ext")
        }
        sourceFile.copyTo(dest, overwrite = true)
        dest
    }

    fun hasEnoughSpace(context: Context, requiredBytes: Long): Boolean {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes > requiredBytes + (2 * 1024 * 1024)
        } catch (_: Exception) {
            true
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share via Mono Docs").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var idx = 0
        while (value >= 1024.0 && idx < units.lastIndex) {
            value /= 1024.0
            idx++
        }
        val df = DecimalFormat(if (idx == 0) "#,##0" else "#,##0.0")
        return "${df.format(value)} ${units[idx]}"
    }

    fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val sdfTime = SimpleDateFormat("h:mm a", Locale.getDefault())
        val sdfDate = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return when {
            diff < 86_400_000L -> "Today, ${sdfTime.format(Date(timestamp))}"
            diff < 172_800_000L -> "Yesterday, ${sdfTime.format(Date(timestamp))}"
            else -> sdfDate.format(Date(timestamp))
        }
    }
}
