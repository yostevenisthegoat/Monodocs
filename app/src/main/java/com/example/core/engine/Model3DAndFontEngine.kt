package com.example.core.engine

import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Vec3(val x: Float, val y: Float, val z: Float)

data class TriangleFace(val a: Int, val b: Int, val c: Int)

data class Mesh3DData(
    val formatLabel: String,
    val vertices: List<Vec3>,
    val faces: List<TriangleFace>,
    val vertexCount: Int,
    val triangleCount: Int,
)

data class FontMetadataInfo(
    val familyName: String,
    val subfamily: String,
    val fullName: String,
    val version: String,
    val copyright: String,
    val numGlyphs: Int,
    val unitsPerEm: Int,
    val typeface: Typeface,
)

object Model3DAndFontEngine {

    suspend fun parse3DModel(file: File, formatId: String): Mesh3DData = withContext(Dispatchers.IO) {
        val raw = when (formatId) {
            "obj" -> parseObj(file)
            "stl" -> parseStl(file)
            "glb" -> parseGlb(file)
            else -> throw IllegalStateException("This file format is not currently supported by Mono Docs.")
        }
        normalizeMesh(raw)
    }

    suspend fun exportMeshToObj(mesh: Mesh3DData, outFile: File) = withContext(Dispatchers.IO) {
        outFile.bufferedWriter().use { w ->
            w.appendLine("# Exported by Mono Docs (Mono Ecosystem)")
            mesh.vertices.forEach { v ->
                w.appendLine("v ${v.x} ${v.y} ${v.z}")
            }
            mesh.faces.forEach { f ->
                w.appendLine("f ${f.a + 1} ${f.b + 1} ${f.c + 1}")
            }
        }
    }

    private fun parseObj(file: File): Mesh3DData {
        val verts = mutableListOf<Vec3>()
        val faces = mutableListOf<TriangleFace>()
        file.bufferedReader().useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.startsWith("v ")) {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        val x = parts[1].toFloatOrNull() ?: 0f
                        val y = parts[2].toFloatOrNull() ?: 0f
                        val z = parts[3].toFloatOrNull() ?: 0f
                        verts += Vec3(x, y, z)
                    }
                } else if (line.startsWith("f ")) {
                    val tokens = line.split(Regex("\\s+")).drop(1)
                    val idxs = tokens.mapNotNull { tok ->
                        val vIdx = tok.substringBefore('/').toIntOrNull() ?: return@mapNotNull null
                        if (vIdx < 0) verts.size + vIdx else vIdx - 1
                    }.filter { it in verts.indices }
                    for (i in 1 until idxs.size - 1) {
                        faces += TriangleFace(idxs[0], idxs[i], idxs[i + 1])
                    }
                }
            }
        }
        if (verts.isEmpty()) throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
        return Mesh3DData("Wavefront OBJ", verts, faces, verts.size, faces.size)
    }

    private fun parseStl(file: File): Mesh3DData {
        val bytes = file.readBytes()
        if (bytes.size >= 84) {
            val triCount = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (triCount > 0 && 84L + triCount.toLong() * 50L == bytes.size.toLong()) {
                val verts = ArrayList<Vec3>(minOf(triCount * 3, 90_000))
                val faces = ArrayList<TriangleFace>(minOf(triCount, 30_000))
                val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                buf.position(84)
                val step = maxOf(1, triCount / 18_000)
                for (t in 0 until triCount) {
                    buf.position(84 + t * 50 + 12) // skip normal
                    val x1 = buf.float; val y1 = buf.float; val z1 = buf.float
                    val x2 = buf.float; val y2 = buf.float; val z2 = buf.float
                    val x3 = buf.float; val y3 = buf.float; val z3 = buf.float
                    if (t % step == 0) {
                        val base = verts.size
                        verts += Vec3(x1, y1, z1)
                        verts += Vec3(x2, y2, z2)
                        verts += Vec3(x3, y3, z3)
                        faces += TriangleFace(base, base + 1, base + 2)
                    }
                }
                return Mesh3DData("Binary STL", verts, faces, verts.size, triCount)
            }
        }

        // ASCII STL
        val verts = mutableListOf<Vec3>()
        val faces = mutableListOf<TriangleFace>()
        val text = String(bytes, Charsets.UTF_8)
        val triVerts = mutableListOf<Vec3>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("vertex ")) {
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 4) {
                    triVerts += Vec3(
                        parts[1].toFloatOrNull() ?: 0f,
                        parts[2].toFloatOrNull() ?: 0f,
                        parts[3].toFloatOrNull() ?: 0f,
                    )
                    if (triVerts.size == 3) {
                        val base = verts.size
                        verts.addAll(triVerts)
                        faces += TriangleFace(base, base + 1, base + 2)
                        triVerts.clear()
                    }
                }
            }
        }
        if (verts.isEmpty()) throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
        return Mesh3DData("ASCII STL", verts, faces, verts.size, faces.size)
    }

    private fun parseGlb(file: File): Mesh3DData {
        val bytes = file.readBytes()
        if (bytes.size < 20) throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        if (magic != 0x46546C67) throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
        buf.int // version
        buf.int // total length

        val jsonLen = buf.int
        val jsonType = buf.int
        if (jsonType != 0x4E4F534A || buf.remaining() < jsonLen) {
            throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
        }
        val jsonBytes = ByteArray(jsonLen)
        buf.get(jsonBytes)
        val json = JSONObject(String(jsonBytes, Charsets.UTF_8))

        if (buf.remaining() < 8) throw IllegalStateException("GLB contains no geometry buffer.")
        val binLen = buf.int
        val binType = buf.int
        if (binType != 0x004E4942 || buf.remaining() < binLen) {
            throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
        }
        val binBytes = ByteArray(binLen)
        buf.get(binBytes)
        val binBuf = ByteBuffer.wrap(binBytes).order(ByteOrder.LITTLE_ENDIAN)

        val accessors = json.optJSONArray("accessors")
        val bufferViews = json.optJSONArray("bufferViews")
        val meshes = json.optJSONArray("meshes")
        if (accessors == null || bufferViews == null || meshes == null || meshes.length() == 0) {
            throw IllegalStateException("GLB contains no meshes.")
        }

        val verts = mutableListOf<Vec3>()
        val faces = mutableListOf<TriangleFace>()

        val prims = meshes.getJSONObject(0).optJSONArray("primitives")
            ?: throw IllegalStateException("GLB mesh has no primitives.")
        for (p in 0 until prims.length()) {
            val prim = prims.getJSONObject(p)
            val attrs = prim.optJSONObject("attributes") ?: continue
            if (!attrs.has("POSITION")) continue
            val posAccIdx = attrs.getInt("POSITION")
            val posAcc = accessors.getJSONObject(posAccIdx)
            val posView = bufferViews.getJSONObject(posAcc.getInt("bufferView"))
            val posOffset = posView.optInt("byteOffset", 0) + posAcc.optInt("byteOffset", 0)
            val posCount = posAcc.getInt("count")
            val posStride = posView.optInt("byteStride", 12).coerceAtLeast(12)

            val baseVert = verts.size
            for (i in 0 until posCount) {
                val off = posOffset + i * posStride
                if (off + 12 <= binBytes.size) {
                    verts += Vec3(binBuf.getFloat(off), binBuf.getFloat(off + 4), binBuf.getFloat(off + 8))
                }
            }

            if (prim.has("indices")) {
                val idxAcc = accessors.getJSONObject(prim.getInt("indices"))
                val idxView = bufferViews.getJSONObject(idxAcc.getInt("bufferView"))
                val idxOffset = idxView.optInt("byteOffset", 0) + idxAcc.optInt("byteOffset", 0)
                val idxCount = idxAcc.getInt("count")
                val compType = idxAcc.getInt("componentType") // 5121 ubyte, 5123 ushort, 5125 uint
                val readIdx: (Int) -> Int = { k ->
                    when (compType) {
                        5121 -> binBytes[idxOffset + k].toInt() and 0xFF
                        5123 -> binBuf.getShort(idxOffset + k * 2).toInt() and 0xFFFF
                        5125 -> binBuf.getInt(idxOffset + k * 4)
                        else -> 0
                    }
                }
                var k = 0
                while (k + 2 < idxCount) {
                    val a = baseVert + readIdx(k)
                    val b = baseVert + readIdx(k + 1)
                    val c = baseVert + readIdx(k + 2)
                    if (a in verts.indices && b in verts.indices && c in verts.indices) {
                        faces += TriangleFace(a, b, c)
                    }
                    k += 3
                }
            } else {
                var k = 0
                while (k + 2 < posCount) {
                    faces += TriangleFace(baseVert + k, baseVert + k + 1, baseVert + k + 2)
                    k += 3
                }
            }
        }
        if (verts.isEmpty()) throw IllegalStateException("Mono Docs couldn't read this file. It may be damaged.")
        return Mesh3DData("Binary glTF (GLB)", verts, faces, verts.size, faces.size)
    }

    private fun normalizeMesh(mesh: Mesh3DData): Mesh3DData {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        mesh.vertices.forEach { v ->
            if (v.x < minX) minX = v.x; if (v.x > maxX) maxX = v.x
            if (v.y < minY) minY = v.y; if (v.y > maxY) maxY = v.y
            if (v.z < minZ) minZ = v.z; if (v.z > maxZ) maxZ = v.z
        }
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val cz = (minZ + maxZ) / 2f
        val maxSpan = maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(1e-5f)
        val scale = 1.6f / maxSpan
        val normalizedVerts = mesh.vertices.map { v ->
            Vec3((v.x - cx) * scale, (v.y - cy) * scale, (v.z - cz) * scale)
        }
        // Decimate faces if massive so Compose Canvas runs at 60fps
        val cappedFaces = if (mesh.faces.size > 8000) {
            val step = mesh.faces.size / 8000
            mesh.faces.filterIndexed { index, _ -> index % step == 0 }
        } else mesh.faces

        return mesh.copy(vertices = normalizedVerts, faces = cappedFaces)
    }

    suspend fun parseFont(file: File): FontMetadataInfo = withContext(Dispatchers.IO) {
        val typeface = Typeface.createFromFile(file)
        var family = file.nameWithoutExtension
        var subfamily = "Regular"
        var fullName = file.nameWithoutExtension
        var version = ""
        var copyright = ""
        var numGlyphs = 0
        var unitsPerEm = 1000

        runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val sfnt = raf.readInt() // 0x00010000 or 'OTTO'
                val numTables = raf.readUnsignedShort()
                raf.skipBytes(6)
                val tables = mutableMapOf<String, Pair<Int, Int>>()
                for (i in 0 until numTables) {
                    val tagBytes = ByteArray(4)
                    raf.readFully(tagBytes)
                    val tag = String(tagBytes, Charsets.US_ASCII)
                    raf.readInt() // checksum
                    val offset = raf.readInt()
                    val length = raf.readInt()
                    tables[tag] = offset to length
                }
                tables["head"]?.let { (off, _) ->
                    raf.seek(off.toLong() + 18)
                    unitsPerEm = raf.readUnsignedShort()
                }
                tables["maxp"]?.let { (off, _) ->
                    raf.seek(off.toLong() + 4)
                    numGlyphs = raf.readUnsignedShort()
                }
                tables["name"]?.let { (off, _) ->
                    raf.seek(off.toLong())
                    raf.readUnsignedShort() // format
                    val count = raf.readUnsignedShort()
                    val stringOffset = raf.readUnsignedShort()
                    val strBase = off + stringOffset
                    for (i in 0 until count) {
                        val platformId = raf.readUnsignedShort()
                        raf.readUnsignedShort() // encodingId
                        raf.readUnsignedShort() // languageId
                        val nameId = raf.readUnsignedShort()
                        val length = raf.readUnsignedShort()
                        val sOff = raf.readUnsignedShort()
                        val pos = raf.filePointer
                        if (nameId in setOf(0, 1, 2, 4, 5) && length in 1..512) {
                            raf.seek((strBase + sOff).toLong())
                            val strBytes = ByteArray(length)
                            raf.readFully(strBytes)
                            val decoded = if (platformId == 3 || platformId == 0) {
                                String(strBytes, Charsets.UTF_16BE).trim('\u0000')
                            } else {
                                String(strBytes, Charsets.ISO_8859_1).trim('\u0000')
                            }
                            if (decoded.isNotBlank()) {
                                when (nameId) {
                                    0 -> if (copyright.isBlank()) copyright = decoded
                                    1 -> family = decoded
                                    2 -> subfamily = decoded
                                    4 -> fullName = decoded
                                    5 -> version = decoded
                                }
                            }
                        }
                        raf.seek(pos)
                    }
                }
            }
        }

        FontMetadataInfo(
            familyName = family,
            subfamily = subfamily,
            fullName = fullName,
            version = version,
            copyright = copyright,
            numGlyphs = numGlyphs,
            unitsPerEm = unitsPerEm,
            typeface = typeface,
        )
    }
}
