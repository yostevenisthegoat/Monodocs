package com.example.core.model

import android.net.Uri
import android.os.Build

enum class FileCategory(val title: String) {
    DOCUMENTS("Documents"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    ARCHIVES("Archives"),
    EBOOKS("E-books"),
    SPREADSHEETS("Spreadsheets"),
    PRESENTATIONS("Presentations"),
    DEVELOPER("Developer Files"),
    MODELS_3D("3D Models"),
    FONTS("Fonts"),
    UNKNOWN("Unknown"),
}

data class FormatSpec(
    val id: String,
    val displayName: String,
    val primaryExtension: String,
    val extensions: Set<String>,
    val mimeTypes: Set<String>,
    val category: FileCategory,
    val isViewable: Boolean,
    val supportedOutputs: List<String>,
    val note: String? = null,
)

data class FileInspectionResult(
    val uriString: String,
    val fileName: String,
    val declaredExtension: String,
    val detectedFormatId: String,
    val detectedFormatName: String,
    val detectedExtension: String,
    val mimeType: String,
    val category: FileCategory,
    val sizeBytes: Long,
    val isSupported: Boolean,
    val hasExtensionMismatch: Boolean,
    val magicHexPreview: String,
    val sha256: String = "",
    val supportedOutputs: List<String> = emptyList(),
    val statusNote: String? = null,
) {
    val uri: Uri get() = Uri.parse(uriString)
}

object SupportedFormatsRegistry {

    val formats: List<FormatSpec> = listOf(
        // DOCUMENTS
        FormatSpec("pdf", "PDF Document", "pdf", setOf("pdf"), setOf("application/pdf"), FileCategory.DOCUMENTS, true, listOf("docx", "txt", "html", "png", "jpg")),
        FormatSpec("docx", "Word Document (DOCX)", "docx", setOf("docx", "docm", "dotx"), setOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), FileCategory.DOCUMENTS, true, listOf("pdf", "txt", "html", "md")),
        FormatSpec("odt", "OpenDocument Text", "odt", setOf("odt", "ott"), setOf("application/vnd.oasis.opendocument.text"), FileCategory.DOCUMENTS, true, listOf("docx", "pdf", "txt", "html")),
        FormatSpec("rtf", "Rich Text Format", "rtf", setOf("rtf"), setOf("application/rtf", "text/rtf"), FileCategory.DOCUMENTS, true, listOf("pdf", "txt", "docx")),
        FormatSpec("txt", "Plain Text", "txt", setOf("txt", "text"), setOf("text/plain"), FileCategory.DOCUMENTS, true, listOf("pdf", "docx", "html", "md")),
        FormatSpec("md", "Markdown", "md", setOf("md", "markdown"), setOf("text/markdown", "text/x-markdown"), FileCategory.DOCUMENTS, true, listOf("pdf", "html", "docx", "txt")),
        FormatSpec("html", "HTML Document", "html", setOf("html", "htm"), setOf("text/html"), FileCategory.DOCUMENTS, true, listOf("pdf", "txt", "docx")),
        FormatSpec("xml", "XML Document", "xml", setOf("xml"), setOf("application/xml", "text/xml"), FileCategory.DOCUMENTS, true, listOf("txt", "pdf")),

        // SPREADSHEETS
        FormatSpec("xlsx", "Excel Workbook (XLSX)", "xlsx", setOf("xlsx", "xlsm"), setOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), FileCategory.SPREADSHEETS, true, listOf("csv", "tsv", "pdf")),
        FormatSpec("ods", "OpenDocument Spreadsheet", "ods", setOf("ods", "ots"), setOf("application/vnd.oasis.opendocument.spreadsheet"), FileCategory.SPREADSHEETS, true, listOf("xlsx", "csv", "pdf")),
        FormatSpec("csv", "Comma-Separated Values", "csv", setOf("csv"), setOf("text/csv", "text/comma-separated-values"), FileCategory.SPREADSHEETS, true, listOf("xlsx", "tsv", "pdf")),
        FormatSpec("tsv", "Tab-Separated Values", "tsv", setOf("tsv"), setOf("text/tab-separated-values"), FileCategory.SPREADSHEETS, true, listOf("csv", "xlsx", "pdf")),

        // PRESENTATIONS
        FormatSpec("pptx", "PowerPoint Presentation (PPTX)", "pptx", setOf("pptx", "pptm", "ppsx"), setOf("application/vnd.openxmlformats-officedocument.presentationml.presentation"), FileCategory.PRESENTATIONS, true, listOf("pdf", "txt")),
        FormatSpec("odp", "OpenDocument Presentation", "odp", setOf("odp", "otp"), setOf("application/vnd.oasis.opendocument.presentation"), FileCategory.PRESENTATIONS, true, listOf("pdf", "txt")),

        // IMAGES
        FormatSpec("jpg", "JPEG Image", "jpg", setOf("jpg", "jpeg"), setOf("image/jpeg"), FileCategory.IMAGES, true, listOf("png", "webp", "bmp", "pdf")),
        FormatSpec("png", "PNG Image", "png", setOf("png"), setOf("image/png"), FileCategory.IMAGES, true, listOf("jpg", "webp", "bmp", "pdf")),
        FormatSpec("webp", "WebP Image", "webp", setOf("webp"), setOf("image/webp"), FileCategory.IMAGES, true, listOf("jpg", "png", "bmp", "pdf")),
        FormatSpec("gif", "GIF Image", "gif", setOf("gif"), setOf("image/gif"), FileCategory.IMAGES, true, listOf("png", "jpg", "webp", "pdf")),
        FormatSpec("bmp", "Bitmap Image", "bmp", setOf("bmp"), setOf("image/bmp", "image/x-ms-bmp"), FileCategory.IMAGES, true, listOf("png", "jpg", "webp", "pdf")),
        FormatSpec("ico", "Icon Image (ICO)", "ico", setOf("ico"), setOf("image/x-icon", "image/vnd.microsoft.icon"), FileCategory.IMAGES, true, listOf("png", "jpg", "webp")),
        FormatSpec("svg", "SVG Vector Graphic", "svg", setOf("svg"), setOf("image/svg+xml"), FileCategory.IMAGES, true, listOf("png", "jpg", "webp", "pdf")),
        FormatSpec("heic", "HEIC / HEIF Image", "heic", setOf("heic", "heif"), setOf("image/heic", "image/heif"), FileCategory.IMAGES, Build.VERSION.SDK_INT >= 28, listOf("jpg", "png", "webp", "pdf")),
        FormatSpec("avif", "AVIF Image", "avif", setOf("avif"), setOf("image/avif"), FileCategory.IMAGES, Build.VERSION.SDK_INT >= 31, listOf("jpg", "png", "webp", "pdf")),

        // AUDIO
        FormatSpec("mp3", "MP3 Audio", "mp3", setOf("mp3"), setOf("audio/mpeg"), FileCategory.AUDIO, true, listOf("wav", "m4a")),
        FormatSpec("wav", "WAV Audio", "wav", setOf("wav"), setOf("audio/wav", "audio/x-wav"), FileCategory.AUDIO, true, listOf("m4a")),
        FormatSpec("flac", "FLAC Audio", "flac", setOf("flac"), setOf("audio/flac"), FileCategory.AUDIO, true, listOf("wav", "m4a")),
        FormatSpec("aac", "AAC Audio", "aac", setOf("aac"), setOf("audio/aac"), FileCategory.AUDIO, true, listOf("wav", "m4a")),
        FormatSpec("ogg", "OGG Vorbis Audio", "ogg", setOf("ogg", "oga"), setOf("audio/ogg"), FileCategory.AUDIO, true, listOf("wav", "m4a")),
        FormatSpec("opus", "Opus Audio", "opus", setOf("opus"), setOf("audio/opus"), FileCategory.AUDIO, true, listOf("wav", "m4a")),
        FormatSpec("m4a", "M4A Audio", "m4a", setOf("m4a"), setOf("audio/mp4", "audio/x-m4a"), FileCategory.AUDIO, true, listOf("wav")),

        // VIDEO
        FormatSpec("mp4", "MP4 Video", "mp4", setOf("mp4", "m4v"), setOf("video/mp4"), FileCategory.VIDEOS, true, listOf("m4a", "wav", "gif")),
        FormatSpec("mkv", "Matroska Video (MKV)", "mkv", setOf("mkv"), setOf("video/x-matroska"), FileCategory.VIDEOS, true, listOf("mp4", "m4a", "wav", "gif")),
        FormatSpec("mov", "QuickTime Video (MOV)", "mov", setOf("mov"), setOf("video/quicktime"), FileCategory.VIDEOS, true, listOf("mp4", "m4a", "wav", "gif")),
        FormatSpec("webm", "WebM Video", "webm", setOf("webm"), setOf("video/webm"), FileCategory.VIDEOS, true, listOf("mp4", "m4a", "wav", "gif")),
        FormatSpec("3gp", "3GP Video", "3gp", setOf("3gp"), setOf("video/3gpp"), FileCategory.VIDEOS, true, listOf("mp4", "m4a", "wav", "gif")),

        // ARCHIVES
        FormatSpec("zip", "ZIP Archive", "zip", setOf("zip"), setOf("application/zip"), FileCategory.ARCHIVES, true, emptyList()),
        FormatSpec("rar", "RAR Archive", "rar", setOf("rar", "cbr"), setOf("application/vnd.rar", "application/x-rar-compressed"), FileCategory.ARCHIVES, true, listOf("zip")),
        FormatSpec("7z", "7-Zip Archive", "7z", setOf("7z"), setOf("application/x-7z-compressed"), FileCategory.ARCHIVES, true, listOf("zip")),
        FormatSpec("tar", "TAR Archive", "tar", setOf("tar"), setOf("application/x-tar"), FileCategory.ARCHIVES, true, listOf("zip")),
        FormatSpec("gz", "GZIP Archive", "gz", setOf("gz", "tgz"), setOf("application/gzip"), FileCategory.ARCHIVES, true, listOf("zip")),
        FormatSpec("bz2", "BZip2 Archive", "bz2", setOf("bz2", "tbz2"), setOf("application/x-bzip2"), FileCategory.ARCHIVES, true, listOf("zip")),
        FormatSpec("xz", "XZ Archive", "xz", setOf("xz", "txz"), setOf("application/x-xz"), FileCategory.ARCHIVES, true, listOf("zip")),
        FormatSpec("jar", "Java Archive (JAR)", "jar", setOf("jar"), setOf("application/java-archive"), FileCategory.ARCHIVES, true, listOf("zip")),
        FormatSpec("cbz", "Comic Book ZIP (CBZ)", "cbz", setOf("cbz"), setOf("application/vnd.comicbook+zip"), FileCategory.EBOOKS, true, listOf("pdf", "zip")),

        // E-BOOKS
        FormatSpec("epub", "EPUB E-Book", "epub", setOf("epub"), setOf("application/epub+zip"), FileCategory.EBOOKS, true, listOf("pdf", "txt")),
        FormatSpec("fb2", "FictionBook (FB2)", "fb2", setOf("fb2"), setOf("application/x-fictionbook+xml"), FileCategory.EBOOKS, true, listOf("epub", "txt", "pdf")),
        FormatSpec("mobi", "Mobipocket E-Book (DRM-free)", "mobi", setOf("mobi", "azw"), setOf("application/x-mobipocket-ebook"), FileCategory.EBOOKS, true, listOf("epub", "txt", "pdf")),

        // DEVELOPER FILES
        FormatSpec("json", "JSON File", "json", setOf("json"), setOf("application/json"), FileCategory.DEVELOPER, true, listOf("txt", "pdf")),
        FormatSpec("yaml", "YAML File", "yaml", setOf("yaml", "yml"), setOf("application/x-yaml", "text/yaml"), FileCategory.DEVELOPER, true, listOf("txt", "pdf")),
        FormatSpec("toml", "TOML Config", "toml", setOf("toml"), setOf("application/toml"), FileCategory.DEVELOPER, true, listOf("txt", "pdf")),
        FormatSpec("sql", "SQL Script", "sql", setOf("sql"), setOf("application/sql"), FileCategory.DEVELOPER, true, listOf("txt", "pdf")),
        FormatSpec("code", "Source Code / Config", "txt", setOf("js", "ts", "css", "py", "lua", "java", "kt", "kts", "c", "cpp", "h", "hpp", "log", "ini", "cfg", "sh", "gradle", "properties"), setOf("text/plain"), FileCategory.DEVELOPER, true, listOf("txt", "pdf")),

        // 3D MODELS
        FormatSpec("obj", "Wavefront OBJ 3D Model", "obj", setOf("obj"), setOf("model/obj", "text/plain"), FileCategory.MODELS_3D, true, emptyList()),
        FormatSpec("stl", "STL 3D Mesh", "stl", setOf("stl"), setOf("model/stl", "application/sla"), FileCategory.MODELS_3D, true, listOf("obj")),
        FormatSpec("glb", "Binary glTF 3D Model", "glb", setOf("glb"), setOf("model/gltf-binary"), FileCategory.MODELS_3D, true, listOf("obj")),

        // FONTS
        FormatSpec("ttf", "TrueType Font", "ttf", setOf("ttf"), setOf("font/ttf", "application/x-font-ttf"), FileCategory.FONTS, true, emptyList()),
        FormatSpec("otf", "OpenType Font", "otf", setOf("otf"), setOf("font/otf", "application/x-font-opentype"), FileCategory.FONTS, true, emptyList()),
    )

    fun findById(id: String): FormatSpec? = formats.firstOrNull { it.id == id }

    fun findByExtension(ext: String): FormatSpec? {
        val clean = ext.lowercase().trimStart('.')
        return formats.firstOrNull { clean in it.extensions }
    }

    fun supportedExtensionsForCategory(category: FileCategory): List<String> =
        formats.filter { it.category == category && it.isViewable }
            .flatMap { it.extensions }
            .distinct()
            .sorted()
            .map { it.uppercase() }
}
