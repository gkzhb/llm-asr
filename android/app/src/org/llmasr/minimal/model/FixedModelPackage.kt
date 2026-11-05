package org.llmasr.minimal.model

import java.io.IOException
import java.security.MessageDigest

/** APK trust anchor, never inferred from untrusted package hashes. Fixture policies are JVM-only callers. */
class FixedModelPackage @Throws(IOException::class) constructor(
    @JvmField val manifest: ModelManifest,
    @JvmField val modelId: String,
    @JvmField val engine: String,
    @JvmField val archiveLimit: Long = Math.addExact(total(manifest), 1024L * 1024)
) {
    @JvmField val totalBytes = total(manifest)
    init {
        if (manifest.entries().size != 7 || manifest.entries().any { it.file() == JSON_NAME || it.file().length > 128 } ||
            modelId.length !in 1..128 || engine.length !in 1..128 || archiveLimit < 22 ||
            totalBytes > Long.MAX_VALUE - ModelRepository.SPACE_PAD - JSON_LIMIT) throw IOException(FORMAT_ERROR)
    }

    @Throws(IOException::class)
    fun verifyJson(bytes: ByteArray) {
        if (bytes.size > JSON_LIMIT) throw IOException(FORMAT_ERROR)
        Reader(bytes).verify(this)
    }

    private class Reader(private val bytes: ByteArray) {
        private var pos = 0
        private fun fail(): Nothing = throw IOException(FORMAT_ERROR)
        private fun ws() { while (pos < bytes.size && when (bytes[pos].toInt()) { 32, 9, 10, 13 -> true; else -> false }) pos++ }
        private fun take(c: Char) { ws(); if (pos >= bytes.size || bytes[pos++].toInt() != c.code) fail() }
        private fun next(c: Char): Boolean { ws(); return pos < bytes.size && bytes[pos].toInt() == c.code }
        private fun string(): String {
            take('"')
            val start = pos
            while (pos < bytes.size && bytes[pos].toInt() != 34) {
                val n = bytes[pos++].toInt()
                if (n !in 32..126 || n == 92 || pos - start > 128) fail()
            }
            if (pos >= bytes.size) fail()
            val text = String(bytes, start, pos - start, Charsets.US_ASCII)
            pos++
            return text
        }
        private fun number(): Long {
            ws()
            val start = pos
            var value = 0L
            while (pos < bytes.size && bytes[pos].toInt() in 48..57) {
                val d = bytes[pos++].toInt() - 48
                if (value > (Long.MAX_VALUE - d) / 10) fail()
                value = value * 10 + d
            }
            if (pos == start || (pos - start > 1 && bytes[start].toInt() == 48)) fail()
            return value
        }
        private fun fields(body: (String) -> Unit) {
            take('{')
            val seen = HashSet<String>()
            if (!next('}')) while (true) {
                val key = string()
                if (!seen.add(key)) fail()
                take(':'); body(key)
                if (next('}')) break
                take(',')
            }
            take('}')
        }
        fun verify(policy: FixedModelPackage) {
            var schema: Long? = null
            var id: String? = null
            var engine: String? = null
            var files: Map<String, Pair<Long, String>>? = null
            fields { key ->
                when (key) {
                    "schema" -> schema = number()
                    "modelId" -> id = string()
                    "engine" -> engine = string()
                    "files" -> files = files()
                    else -> fail()
                }
            }
            ws()
            if (pos != bytes.size || schema != 1L || id != policy.modelId || engine != policy.engine || files?.size != 7) fail()
            for (entry in policy.manifest.entries()) {
                val tuple = files!![entry.file()] ?: fail()
                if (tuple.first != entry.bytes() || tuple.second != entry.sha256()) fail()
            }
        }
        private fun files(): Map<String, Pair<Long, String>> {
            val result = HashMap<String, Pair<Long, String>>()
            take('[')
            if (!next(']')) while (true) {
                if (result.size >= 7) fail()
                var file: String? = null
                var size: Long? = null
                var sha: String? = null
                fields { key ->
                    when (key) {
                        "file" -> file = string()
                        "bytes" -> size = number()
                        "sha256" -> sha = string()
                        else -> fail()
                    }
                }
                val name = file ?: fail()
                val hash = sha ?: fail()
                if (!hash.matches(Regex("[0-9a-f]{64}")) || result.put(name, Pair(size ?: fail(), hash)) != null) fail()
                if (next(']')) break
                take(',')
            }
            take(']')
            return result
        }
    }

    companion object {
        const val JSON_NAME = "model-package.json"
        const val JSON_LIMIT = 16384
        const val FORMAT_ERROR = "模型包清单格式不支持/不匹配"
        const val MODEL_ID = "qwen3-asr-0.6b-mnn-fp16-p0-1"
        const val ENGINE = "mnn"
        const val FINGERPRINT = "02624d4f3f12ce44ce8d1191d925ff2dddd3eedbe731692716bb017a284521a6"
        private fun total(manifest: ModelManifest): Long {
            var n = 0L
            for (e in manifest.entries()) n = Math.addExact(n, e.bytes())
            return n
        }
        @JvmStatic @Throws(IOException::class)
        fun mnn(manifest: ModelManifest): FixedModelPackage {
            val canonical = manifest.entries().joinToString("") { "${it.file()}:${it.bytes()}:${it.sha256()}\n" }
            val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            if (hash != FINGERPRINT) throw IOException(FORMAT_ERROR)
            return FixedModelPackage(manifest, MODEL_ID, ENGINE)
        }
    }
}
