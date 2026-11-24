package com.example.core.engine

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class DocumentBlock(
    val text: String,
    val isHeading: Boolean = false,
    val headingLevel: Int = 0,
    val isCode: Boolean = false,
)

data class ParsedDocument(
    val title: String,
    val author: String = "",
    val blocks: List<DocumentBlock>,
    val metadata: Map<String, String> = emptyMap(),
)

data class SheetData(
    val name: String,
    val rows: List<List<String>>,
)

data class SlideData(
    val number: Int,
    val title: String,
    val bodyLines: List<String>,
    val notes: String = "",
)

data class EbookChapter(
    val title: String,
    val paragraphs: List<String>,
)

data class ParsedEbook(
    val title: String,
    val author: String,
    val language: String = "",
    val publisher: String = "",
    val chapters: List<EbookChapter>,
    val coverBytes: ByteArray? = null,
)

object OfficeAndEbookEngine {

    suspend fun parseDocx(file: File): ParsedDocument = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val docEntry = zip.getEntry("word/document.xml")
                ?: throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
            val blocks = mutableListOf<DocumentBlock>()
            zip.getInputStream(docEntry).use { stream ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    setInput(stream, "UTF-8")
                }
                var inParagraph = false
                var isHeading = false
                var headingLevel = 0
                val currentPara = StringBuilder()
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    when (event) {
                        XmlPullParser.START_TAG -> when (name) {
                            "p" -> {
                                inParagraph = true
                                isHeading = false
                                headingLevel = 0
                                currentPara.setLength(0)
                            }
                            "pStyle" -> if (inParagraph) {
                                val style = parser.getAttributeValue(null, "val")
                                    ?: parser.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "val")
                                    ?: ""
                                if (style.contains("Heading", ignoreCase = true) || style.equals("Title", ignoreCase = true)) {
                                    isHeading = true
                                    headingLevel = style.filter { it.isDigit() }.toIntOrNull() ?: 1
                                }
                            }
                            "t" -> if (inParagraph) {
                                currentPara.append(parser.nextText())
                            }
                            "tab" -> if (inParagraph) currentPara.append("    ")
                            "br" -> if (inParagraph) currentPara.append("\n")
                        }
                        XmlPullParser.END_TAG -> if (name == "p" && inParagraph) {
                            val text = currentPara.toString().trim()
                            if (text.isNotEmpty()) {
                                blocks += DocumentBlock(text, isHeading, headingLevel)
                            }
                            inParagraph = false
                        }
                    }
                    event = parser.next()
                }
            }

            val meta = mutableMapOf<String, String>()
            zip.getEntry("docProps/core.xml")?.let { core ->
                zip.getInputStream(core).use { s ->
                    val textMap = parseSimpleXmlTags(s, setOf("title", "creator", "subject", "description", "created", "modified"))
                    meta.putAll(textMap)
                }
            }
            ParsedDocument(
                title = meta["title"]?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                author = meta["creator"].orEmpty(),
                blocks = blocks,
                metadata = meta,
            )
        }
    }

    suspend fun parseOdt(file: File): ParsedDocument = withContext(Dispatchers.IO) {
        ZipFile(file).use { zip ->
            val contentEntry = zip.getEntry("content.xml")
                ?: throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
            val blocks = mutableListOf<DocumentBlock>()
            zip.getInputStream(contentEntry).use { stream ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    setInput(stream, "UTF-8")
                }
                var capturing = false
                var isHeading = false
                var headingLevel = 0
                val sb = StringBuilder()
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name
                    when (event) {
                        XmlPullParser.START_TAG -> when (tag) {
                            "h" -> {
                                capturing = true
                                isHeading = true
                                headingLevel = parser.getAttributeValue(null, "outline-level")?.toIntOrNull() ?: 1
                                sb.setLength(0)
                            }
                            "p" -> {
                                capturing = true
                                isHeading = false
                                headingLevel = 0
                                sb.setLength(0)
                            }
                            "s" -> if (capturing) sb.append(" ")
                            "tab" -> if (capturing) sb.append("    ")
                            "line-break" -> if (capturing) sb.append("\n")
                        }
                        XmlPullParser.TEXT -> if (capturing) sb.append(parser.text)
                        XmlPullParser.END_TAG -> if ((tag == "p" || tag == "h") && capturing) {
                            val t = sb.toString().trim()
                            if (t.isNotEmpty()) blocks += DocumentBlock(t, isHeading, headingLevel)
                            capturing = false
                        }
                    }
                    event = parser.next()
                }
            }
            ParsedDocument(title = file.nameWithoutExtension, blocks = blocks)
        }
    }

    suspend fun parseRtf(file: File): ParsedDocument = withContext(Dispatchers.IO) {
        val raw = file.readText(Charsets.ISO_8859_1)
        val out = StringBuilder()
        var i = 0
        var depth = 0
        var skipDepth = Int.MAX_VALUE
        val skipGroups = setOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "header", "footer", "xmlnstbl")

        while (i < raw.length) {
            val ch = raw[i]
            when (ch) {
                '{' -> {
                    depth++
                    i++
                }
                '}' -> {
                    if (depth <= skipDepth) skipDepth = Int.MAX_VALUE
                    depth = (depth - 1).coerceAtLeast(0)
                    i++
                }
                '\\' -> {
                    if (i + 1 >= raw.length) break
                    val next = raw[i + 1]
                    when {
                        next == '\\' || next == '{' || next == '}' -> {
                            if (depth < skipDepth) out.append(next)
                            i += 2
                        }
                        next == '\'' && i + 3 < raw.length -> {
                            val code = raw.substring(i + 2, i + 4).toIntOrNull(16)
                            if (code != null && depth < skipDepth) out.append(code.toChar())
                            i += 4
                        }
                        next == '*' -> {
                            skipDepth = minOf(skipDepth, depth)
                            i += 2
                        }
                        next == '\r' || next == '\n' -> {
                            if (depth < skipDepth) out.append('\n')
                            i += 2
                        }
                        next.isLetter() -> {
                            var j = i + 1
                            while (j < raw.length && raw[j].isLetter()) j++
                            val word = raw.substring(i + 1, j)
                            var paramStr = ""
                            if (j < raw.length && (raw[j] == '-' || raw[j].isDigit())) {
                                val startP = j
                                if (raw[j] == '-') j++
                                while (j < raw.length && raw[j].isDigit()) j++
                                paramStr = raw.substring(startP, j)
                            }
                            if (j < raw.length && raw[j] == ' ') j++

                            if (word in skipGroups) {
                                skipDepth = minOf(skipDepth, depth)
                            } else if (depth < skipDepth) {
                                when (word) {
                                    "par", "line" -> out.append('\n')
                                    "tab" -> out.append("    ")
                                    "u" -> {
                                        val code = paramStr.toIntOrNull()
                                        if (code != null) {
                                            val cp = if (code < 0) code + 65536 else code
                                            out.append(cp.toChar())
                                        }
                                        if (j < raw.length && raw[j] == '?') j++
                                    }
                                }
                            }
                            i = j
                        }
                        else -> i += 2
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    if (depth < skipDepth) out.append(ch)
                    i++
                }
            }
        }
        val blocks = out.toString()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { DocumentBlock(it) }
        ParsedDocument(title = file.nameWithoutExtension, blocks = blocks)
    }

    suspend fun parseHtmlToBlocks(htmlText: String, title: String): ParsedDocument = withContext(Dispatchers.Default) {
        val plain = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY).toString()
        val blocks = plain.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { DocumentBlock(it) }
        ParsedDocument(title = title, blocks = blocks)
    }

    suspend fun parseMarkdownToBlocks(mdText: String, title: String): ParsedDocument = withContext(Dispatchers.Default) {
        val blocks = mutableListOf<DocumentBlock>()
        var inCode = false
        val codeBuf = StringBuilder()
        for (line in mdText.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCode) {
                    blocks += DocumentBlock(codeBuf.toString().trimEnd(), isCode = true)
                    codeBuf.setLength(0)
                    inCode = false
                } else {
                    inCode = true
                }
                continue
            }
            if (inCode) {
                codeBuf.appendLine(line)
                continue
            }
            when {
                trimmed.startsWith("### ") -> blocks += DocumentBlock(trimmed.removePrefix("### ").trim(), isHeading = true, headingLevel = 3)
                trimmed.startsWith("## ") -> blocks += DocumentBlock(trimmed.removePrefix("## ").trim(), isHeading = true, headingLevel = 2)
                trimmed.startsWith("# ") -> blocks += DocumentBlock(trimmed.removePrefix("# ").trim(), isHeading = true, headingLevel = 1)
                trimmed.isNotEmpty() -> {
                    val clean = trimmed
                        .replace("**", "")
                        .replace("__", "")
                        .replace("`", "")
                    blocks += DocumentBlock(clean)
                }
            }
        }
        if (codeBuf.isNotEmpty()) blocks += DocumentBlock(codeBuf.toString().trimEnd(), isCode = true)
        ParsedDocument(title = title, blocks = blocks)
    }

    suspend fun parseSpreadsheet(file: File, formatId: String): List<SheetData> = withContext(Dispatchers.IO) {
        when (formatId) {
            "csv" -> listOf(SheetData("Sheet 1", parseDelimitedText(file.readText(), ',')))
            "tsv" -> listOf(SheetData("Sheet 1", parseDelimitedText(file.readText(), '\t')))
            "ods" -> parseOds(file)
            else -> parseXlsx(file)
        }
    }

    private fun parseDelimitedText(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    currentRow += cell.toString()
                    cell.setLength(0)
                }
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    currentRow += cell.toString()
                    cell.setLength(0)
                    if (currentRow.any { it.isNotEmpty() }) rows += currentRow.toList()
                    currentRow.clear()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow += cell.toString()
            rows += currentRow.toList()
        }
        return rows
    }

    private fun parseXlsx(file: File): List<SheetData> {
        ZipFile(file).use { zip ->
            val sharedStrings = mutableListOf<String>()
            zip.getEntry("xl/sharedStrings.xml")?.let { sst ->
                zip.getInputStream(sst).use { s ->
                    val parser = Xml.newPullParser().apply {
                        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                        setInput(s, "UTF-8")
                    }
                    var inSi = false
                    var inT = false
                    val sb = StringBuilder()
                    var ev = parser.eventType
                    while (ev != XmlPullParser.END_DOCUMENT) {
                        when (ev) {
                            XmlPullParser.START_TAG -> {
                                if (parser.name == "si") { inSi = true; sb.setLength(0) }
                                else if (parser.name == "t" && inSi) inT = true
                            }
                            XmlPullParser.TEXT -> if (inSi && inT) sb.append(parser.text)
                            XmlPullParser.END_TAG -> {
                                if (parser.name == "t") inT = false
                                else if (parser.name == "si") { sharedStrings += sb.toString(); inSi = false }
                            }
                        }
                        ev = parser.next()
                    }
                }
            }

            val sheetNames = mutableListOf<String>()
            zip.getEntry("xl/workbook.xml")?.let { wb ->
                zip.getInputStream(wb).use { s ->
                    val parser = Xml.newPullParser().apply {
                        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                        setInput(s, "UTF-8")
                    }
                    var ev = parser.eventType
                    while (ev != XmlPullParser.END_DOCUMENT) {
                        if (ev == XmlPullParser.START_TAG && parser.name == "sheet") {
                            sheetNames += (parser.getAttributeValue(null, "name") ?: "Sheet ${sheetNames.size + 1}")
                        }
                        ev = parser.next()
                    }
                }
            }

            val sheetEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
                .sortedBy { it.name.filter { c -> c.isDigit() }.toIntOrNull() ?: 999 }
                .toList()

            if (sheetEntries.isEmpty()) throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")

            return sheetEntries.mapIndexed { idx, entry ->
                val rows = mutableListOf<List<String>>()
                zip.getInputStream(entry).use { s ->
                    val parser = Xml.newPullParser().apply {
                        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                        setInput(s, "UTF-8")
                    }
                    var currentRow = mutableListOf<String>()
                    var currentColIdx = 0
                    var cellType = ""
                    var inV = false
                    var inIs = false
                    val valBuf = StringBuilder()
                    var ev = parser.eventType
                    while (ev != XmlPullParser.END_DOCUMENT) {
                        when (ev) {
                            XmlPullParser.START_TAG -> when (parser.name) {
                                "row" -> {
                                    currentRow = mutableListOf()
                                    currentColIdx = 0
                                }
                                "c" -> {
                                    val ref = parser.getAttributeValue(null, "r").orEmpty()
                                    val colIdx = columnRefToIndex(ref)
                                    while (currentRow.size < colIdx) currentRow += ""
                                    currentColIdx = colIdx
                                    cellType = parser.getAttributeValue(null, "t").orEmpty()
                                    valBuf.setLength(0)
                                }
                                "v" -> { inV = true; valBuf.setLength(0) }
                                "is" -> { inIs = true; valBuf.setLength(0) }
                            }
                            XmlPullParser.TEXT -> if (inV || inIs) valBuf.append(parser.text)
                            XmlPullParser.END_TAG -> when (parser.name) {
                                "v" -> inV = false
                                "is" -> inIs = false
                                "c" -> {
                                    val rawVal = valBuf.toString()
                                    val resolved = when (cellType) {
                                        "s" -> rawVal.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: rawVal
                                        "b" -> if (rawVal == "1") "TRUE" else "FALSE"
                                        else -> rawVal
                                    }
                                    while (currentRow.size <= currentColIdx) currentRow += ""
                                    currentRow[currentColIdx] = resolved
                                    currentColIdx++
                                }
                                "row" -> if (currentRow.isNotEmpty()) rows += currentRow
                            }
                        }
                        ev = parser.next()
                    }
                }
                SheetData(sheetNames.getOrElse(idx) { "Sheet ${idx + 1}" }, rows)
            }
        }
    }

    private fun parseOds(file: File): List<SheetData> {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("content.xml")
                ?: throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
            val sheets = mutableListOf<SheetData>()
            zip.getInputStream(entry).use { s ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    setInput(s, "UTF-8")
                }
                var sheetName = "Sheet 1"
                var rows = mutableListOf<List<String>>()
                var currentRow = mutableListOf<String>()
                var inCell = false
                var inP = false
                val sb = StringBuilder()
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    when (ev) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "table" -> {
                                sheetName = parser.getAttributeValue(null, "name") ?: "Sheet ${sheets.size + 1}"
                                rows = mutableListOf()
                            }
                            "table-row" -> currentRow = mutableListOf()
                            "table-cell" -> { inCell = true; sb.setLength(0) }
                            "p" -> if (inCell) inP = true
                        }
                        XmlPullParser.TEXT -> if (inCell && inP) sb.append(parser.text)
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "p" -> inP = false
                            "table-cell" -> {
                                currentRow += sb.toString()
                                inCell = false
                            }
                            "table-row" -> if (currentRow.any { it.isNotEmpty() }) rows += currentRow
                            "table" -> sheets += SheetData(sheetName, rows)
                        }
                    }
                    ev = parser.next()
                }
            }
            return sheets
        }
    }

    suspend fun parsePresentation(file: File, formatId: String): List<SlideData> = withContext(Dispatchers.IO) {
        if (formatId == "odp") return@withContext parseOdp(file)
        ZipFile(file).use { zip ->
            val slideEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
                .sortedBy { it.name.filter { c -> c.isDigit() }.toIntOrNull() ?: 999 }
                .toList()
            if (slideEntries.isEmpty()) throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")

            slideEntries.mapIndexed { idx, entry ->
                val num = entry.name.filter { it.isDigit() }.toIntOrNull() ?: (idx + 1)
                val paragraphs = zip.getInputStream(entry).use { extractDrawingMlParagraphs(it) }
                val notesEntry = zip.getEntry("ppt/notesSlides/notesSlide$num.xml")
                val notes = notesEntry?.let { ne ->
                    zip.getInputStream(ne).use { extractDrawingMlParagraphs(it).joinToString("\n") }
                }.orEmpty()
                SlideData(
                    number = num,
                    title = paragraphs.firstOrNull().orEmpty().ifBlank { "Slide $num" },
                    bodyLines = paragraphs.drop(1),
                    notes = notes,
                )
            }
        }
    }

    private fun parseOdp(file: File): List<SlideData> {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("content.xml")
                ?: throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
            val slides = mutableListOf<SlideData>()
            zip.getInputStream(entry).use { s ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    setInput(s, "UTF-8")
                }
                var inPage = false
                var inP = false
                val lines = mutableListOf<String>()
                val sb = StringBuilder()
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    when (ev) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "page" -> { inPage = true; lines.clear() }
                            "p", "h" -> if (inPage) { inP = true; sb.setLength(0) }
                        }
                        XmlPullParser.TEXT -> if (inPage && inP) sb.append(parser.text)
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "p", "h" -> if (inP) {
                                val t = sb.toString().trim()
                                if (t.isNotEmpty()) lines += t
                                inP = false
                            }
                            "page" -> if (inPage) {
                                val n = slides.size + 1
                                slides += SlideData(
                                    number = n,
                                    title = lines.firstOrNull() ?: "Slide $n",
                                    bodyLines = lines.drop(1),
                                )
                                inPage = false
                            }
                        }
                    }
                    ev = parser.next()
                }
            }
            return slides
        }
    }

    suspend fun parseEbook(file: File, formatId: String): ParsedEbook = withContext(Dispatchers.IO) {
        when (formatId) {
            "fb2" -> parseFb2(file)
            "mobi" -> parseMobi(file)
            else -> parseEpub(file)
        }
    }

    private fun parseEpub(file: File): ParsedEbook {
        ZipFile(file).use { zip ->
            val container = zip.getEntry("META-INF/container.xml")
                ?: throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
            var opfPath = ""
            zip.getInputStream(container).use { s ->
                val p = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    setInput(s, "UTF-8")
                }
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && p.name == "rootfile") {
                        opfPath = p.getAttributeValue(null, "full-path").orEmpty()
                        break
                    }
                    ev = p.next()
                }
            }
            val opfEntry = zip.getEntry(opfPath)
                ?: throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
            val baseDir = opfPath.substringBeforeLast('/', "")
            fun resolveHref(href: String): String =
                if (baseDir.isEmpty()) href else "$baseDir/${href.trimStart('/')}"

            var title = file.nameWithoutExtension
            var author = ""
            var language = ""
            var publisher = ""
            val manifest = mutableMapOf<String, String>()
            val spineIds = mutableListOf<String>()
            var coverHref: String? = null

            zip.getInputStream(opfEntry).use { s ->
                val p = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    setInput(s, "UTF-8")
                }
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG) {
                        when (p.name) {
                            "title" -> title = p.nextText().ifBlank { title }
                            "creator" -> if (author.isBlank()) author = p.nextText()
                            "language" -> language = p.nextText()
                            "publisher" -> publisher = p.nextText()
                            "item" -> {
                                val id = p.getAttributeValue(null, "id").orEmpty()
                                val href = p.getAttributeValue(null, "href").orEmpty()
                                val props = p.getAttributeValue(null, "properties").orEmpty()
                                if (id.isNotEmpty() && href.isNotEmpty()) {
                                    manifest[id] = resolveHref(href)
                                    if (props.contains("cover-image") || id.contains("cover", ignoreCase = true) && href.matches(Regex(".*\\.(jpg|jpeg|png|webp)$", RegexOption.IGNORE_CASE))) {
                                        coverHref = resolveHref(href)
                                    }
                                }
                            }
                            "itemref" -> {
                                val idref = p.getAttributeValue(null, "idref").orEmpty()
                                if (idref.isNotEmpty()) spineIds += idref
                            }
                        }
                    }
                    ev = p.next()
                }
            }

            val chapters = mutableListOf<EbookChapter>()
            spineIds.forEachIndexed { idx, id ->
                val path = manifest[id] ?: return@forEachIndexed
                val entry = zip.getEntry(path) ?: return@forEachIndexed
                val html = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
                val paras = plain.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (paras.isNotEmpty()) {
                    val chTitle = paras.first().take(72)
                    chapters += EbookChapter(
                        title = if (paras.size > 1) chTitle else "Section ${idx + 1}",
                        paragraphs = paras,
                    )
                }
            }

            val coverBytes = coverHref?.let { zip.getEntry(it) }?.let { ce ->
                zip.getInputStream(ce).use { it.readBytes() }
            }

            return ParsedEbook(title, author, language, publisher, chapters, coverBytes)
        }
    }

    private fun parseFb2(file: File): ParsedEbook {
        val chapters = mutableListOf<EbookChapter>()
        var title = file.nameWithoutExtension
        var author = ""
        FileInputStream(file).use { s ->
            val p = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(s, null)
            }
            var inTitleInfo = false
            var inSection = false
            var currentTitle = ""
            val paras = mutableListOf<String>()
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "title-info" -> inTitleInfo = true
                        "book-title" -> if (inTitleInfo) title = p.nextText().ifBlank { title }
                        "first-name", "last-name" -> if (inTitleInfo) {
                            author = (author + " " + p.nextText()).trim()
                        }
                        "section" -> {
                            inSection = true
                            currentTitle = ""
                            paras.clear()
                        }
                        "title" -> if (inSection) currentTitle = p.nextText().trim()
                        "p" -> if (inSection) {
                            val t = p.nextText().trim()
                            if (t.isNotEmpty()) paras += t
                        }
                    }
                    XmlPullParser.END_TAG -> when (p.name) {
                        "title-info" -> inTitleInfo = false
                        "section" -> if (inSection) {
                            if (paras.isNotEmpty()) {
                                chapters += EbookChapter(
                                    title = currentTitle.ifBlank { "Chapter ${chapters.size + 1}" },
                                    paragraphs = paras.toList(),
                                )
                            }
                            inSection = false
                        }
                    }
                }
                ev = p.next()
            }
        }
        return ParsedEbook(title = title, author = author, chapters = chapters)
    }

    private fun parseMobi(file: File): ParsedEbook {
        val bytes = file.readBytes()
        if (bytes.size < 84) throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val nameBytes = ByteArray(32)
        buf.get(nameBytes)
        val bookTitle = String(nameBytes, Charsets.UTF_8).trim('\u0000', ' ')
            .ifBlank { file.nameWithoutExtension }

        buf.position(76)
        val numRecords = buf.short.toInt() and 0xFFFF
        if (numRecords < 2) throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")

        val offsets = IntArray(numRecords)
        for (i in 0 until numRecords) {
            offsets[i] = buf.int
            buf.int // attributes + uniqueId
        }

        val rec0Start = offsets[0]
        val rec0End = offsets[1]
        if (rec0End <= rec0Start || rec0End > bytes.size) {
            throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
        }

        val rec0 = ByteBuffer.wrap(bytes, rec0Start, rec0End - rec0Start).order(ByteOrder.BIG_ENDIAN)
        val compression = rec0.short.toInt() and 0xFFFF
        rec0.short // unused
        rec0.int // text length
        val textRecordCount = rec0.short.toInt() and 0xFFFF
        rec0.short // record size
        val encryptionType = rec0.short.toInt() and 0xFFFF
        if (encryptionType != 0) {
            throw IllegalStateException("This MOBI/AZW file is DRM-encrypted and cannot be opened offline.")
        }
        if (compression == 17480) {
            throw IllegalStateException("HUFF/CDIC compressed MOBI is not supported; standard and PalmDOC MOBI files are supported.")
        }

        val out = ByteArrayOutputStream()
        val limit = minOf(textRecordCount, numRecords - 1)
        for (r in 1..limit) {
            val start = offsets[r]
            val end = if (r + 1 < numRecords) offsets[r + 1] else bytes.size
            if (start < 0 || end <= start || end > bytes.size) continue
            val chunk = bytes.copyOfRange(start, end)
            when (compression) {
                1 -> out.write(chunk)
                2 -> out.write(decompressPalmDoc(chunk))
                else -> out.write(chunk)
            }
        }
        val htmlText = String(out.toByteArray(), Charsets.UTF_8)
        val plain = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY).toString()
        val allParas = plain.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val chapters = allParas.chunked(35).mapIndexed { idx, chunk ->
            EbookChapter("Section ${idx + 1}", chunk)
        }
        return ParsedEbook(title = bookTitle, author = "", chapters = chapters)
    }

    private fun decompressPalmDoc(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < data.size) {
            val c = data[i++].toInt() and 0xFF
            when {
                c == 0 -> out.write(0)
                c in 1..8 -> {
                    val count = minOf(c, data.size - i)
                    out.write(data, i, count)
                    i += count
                }
                c <= 0x7F -> out.write(c)
                c >= 0xC0 -> {
                    out.write(' '.code)
                    out.write(c xor 0x80)
                }
                else -> {
                    if (i >= data.size) break
                    val next = data[i++].toInt() and 0xFF
                    val pair = (c shl 8) or next
                    val distance = (pair shr 3) and 0x07FF
                    val length = (pair and 0x07) + 3
                    val current = out.toByteArray()
                    val startPos = current.size - distance
                    for (k in 0 until length) {
                        val b = if (startPos + k in current.indices) current[startPos + k] else ' '.code.toByte()
                        out.write(b.toInt() and 0xFF)
                    }
                }
            }
        }
        return out.toByteArray()
    }

    suspend fun writeDocx(
        paragraphs: List<String>,
        title: String,
        outFile: File,
    ) = withContext(Dispatchers.IO) {
        val escapedParas = buildString {
            if (title.isNotBlank()) {
                append("<w:p><w:r><w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr><w:t xml:space=\"preserve\">")
                append(escapeXml(title))
                append("</w:t></w:r></w:p>")
            }
            paragraphs.forEach { p ->
                append("<w:p><w:r><w:t xml:space=\"preserve\">")
                append(escapeXml(p))
                append("</w:t></w:r></w:p>")
            }
        }
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
        val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>$escapedParas<w:sectPr/></w:body>
</w:document>"""

        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            writeZipTextEntry(zos, "[Content_Types].xml", contentTypes)
            writeZipTextEntry(zos, "_rels/.rels", rels)
            writeZipTextEntry(zos, "word/document.xml", docXml)
        }
    }

    suspend fun writeXlsx(sheets: List<SheetData>, outFile: File) = withContext(Dispatchers.IO) {
        val safeSheets = if (sheets.isEmpty()) listOf(SheetData("Sheet1", emptyList())) else sheets
        val sheetOverrides = safeSheets.mapIndexed { idx, _ ->
            """<Override PartName="/xl/worksheets/sheet${idx + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }.joinToString("")
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  $sheetOverrides
</Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
        val wbSheets = safeSheets.mapIndexed { idx, s ->
            """<sheet name="${escapeXml(s.name.take(31))}" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>"""
        }.joinToString("")
        val wbXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>$wbSheets</sheets>
</workbook>"""
        val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  ${safeSheets.mapIndexed { idx, _ -> """<Relationship Id="rId${idx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${idx + 1}.xml"/>""" }.joinToString("")}
</Relationships>"""

        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            writeZipTextEntry(zos, "[Content_Types].xml", contentTypes)
            writeZipTextEntry(zos, "_rels/.rels", rels)
            writeZipTextEntry(zos, "xl/workbook.xml", wbXml)
            writeZipTextEntry(zos, "xl/_rels/workbook.xml.rels", wbRels)
            safeSheets.forEachIndexed { idx, sheet ->
                val rowsXml = buildString {
                    sheet.rows.forEachIndexed { rIdx, row ->
                        append("<row r=\"${rIdx + 1}\">")
                        row.forEachIndexed { cIdx, cellVal ->
                            val ref = "${indexToColumnRef(cIdx)}${rIdx + 1}"
                            val num = cellVal.toDoubleOrNull()
                            if (num != null && cellVal.isNotBlank()) {
                                append("<c r=\"$ref\"><v>$cellVal</v></c>")
                            } else {
                                append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${escapeXml(cellVal)}</t></is></c>")
                            }
                        }
                        append("</row>")
                    }
                }
                val sheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>$rowsXml</sheetData>
</worksheet>"""
                writeZipTextEntry(zos, "xl/worksheets/sheet${idx + 1}.xml", sheetXml)
            }
        }
    }

    suspend fun writeCsv(rows: List<List<String>>, delimiter: Char, outFile: File) = withContext(Dispatchers.IO) {
        val text = rows.joinToString("\n") { row ->
            row.joinToString(delimiter.toString()) { cell ->
                if (cell.contains(delimiter) || cell.contains('"') || cell.contains('\n')) {
                    "\"${cell.replace("\"", "\"\"")}\""
                } else cell
            }
        }
        outFile.writeText(text, Charsets.UTF_8)
    }

    suspend fun writeEpub(ebook: ParsedEbook, outFile: File) = withContext(Dispatchers.IO) {
        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            // EPUB spec requires uncompressed mimetype as first entry
            val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                crc = CRC32().apply { update(mimeBytes) }.value
            }
            zos.putNextEntry(mimeEntry)
            zos.write(mimeBytes)
            zos.closeEntry()

            val container = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
            writeZipTextEntry(zos, "META-INF/container.xml", container)

            val manifestItems = ebook.chapters.mapIndexed { idx, _ ->
                """<item id="ch$idx" href="chapter$idx.xhtml" media-type="application/xhtml+xml"/>"""
            }.joinToString("\n")
            val spineItems = ebook.chapters.mapIndexed { idx, _ ->
                """<itemref idref="ch$idx"/>"""
            }.joinToString("\n")
            val opf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">urn:uuid:monodocs-${System.currentTimeMillis()}</dc:identifier>
    <dc:title>${escapeXml(ebook.title)}</dc:title>
    <dc:creator>${escapeXml(ebook.author.ifBlank { "Mono Docs" })}</dc:creator>
    <dc:language>${ebook.language.ifBlank { "en" }}</dc:language>
  </metadata>
  <manifest>$manifestItems</manifest>
  <spine>$spineItems</spine>
</package>"""
            writeZipTextEntry(zos, "OEBPS/content.opf", opf)

            ebook.chapters.forEachIndexed { idx, ch ->
                val body = ch.paragraphs.joinToString("\n") { "<p>${escapeXml(it)}</p>" }
                val xhtml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>${escapeXml(ch.title)}</title></head>
<body><h1>${escapeXml(ch.title)}</h1>$body</body>
</html>"""
                writeZipTextEntry(zos, "OEBPS/chapter$idx.xhtml", xhtml)
            }
        }
    }

    private fun extractDrawingMlParagraphs(stream: InputStream): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(stream, "UTF-8")
        }
        var inP = false
        val sb = StringBuilder()
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "p" -> { inP = true; sb.setLength(0) }
                    "t" -> if (inP) sb.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "p" && inP) {
                    val t = sb.toString().trim()
                    if (t.isNotEmpty()) result += t
                    inP = false
                }
            }
            ev = parser.next()
        }
        return result
    }

    private fun parseSimpleXmlTags(stream: InputStream, keys: Set<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(stream, "UTF-8")
        }
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name in keys) {
                val key = parser.name
                val text = runCatching { parser.nextText() }.getOrDefault("")
                if (text.isNotBlank()) map[key] = text
            }
            ev = parser.next()
        }
        return map
    }

    private fun writeZipTextEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun columnRefToIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        var result = 0
        for (ch in letters) {
            result = result * 26 + (ch - 'A' + 1)
        }
        return (result - 1).coerceAtLeast(0)
    }

    private fun indexToColumnRef(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
