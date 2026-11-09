package org.llmasr.minimal.model

import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedHashMap

/** Closed package/config policy. Self-reported content integrity is NOT authenticated provenance. */
class ModelPackagePolicy private constructor(
    @JvmField val manifest: ModelManifest,
    @JvmField val modelId: String,
    @JvmField val displayName: String,
    @JvmField val precision: String,
    @JvmField val contentSha256: String,
    @JvmField val trustedLegacy: Boolean,
    private val legacy: FixedModelPackage?
) {
    @JvmField val totalBytes: Long = manifest.entries().fold(0L) { sum, e -> Math.addExact(sum, e.bytes()) }
    @JvmField val archiveLimit: Long = if (trustedLegacy) legacy!!.archiveLimit else MAX_ARCHIVE

    fun requireConfig(name: String, bytes: ByteArray) {
        if (trustedLegacy) return // Legacy tuples were authenticated by the APK policy.
        if (name == "config.json") {
            need(bytes.size == BASE_CONFIG_BYTES && sha(bytes) == BASE_CONFIG_SHA, "不支持的运行配置")
        } else if (name == "llm_config.json") validateLlmConfig(bytes, manifest.require("llm.mnn.weight").bytes())
    }

    fun json(): ByteArray {
        val files = manifest.entries().joinToString(",") { "{\"file\":\"${it.file()}\",\"bytes\":${it.bytes()},\"sha256\":\"${it.sha256()}\"}" }
        val extra = if (trustedLegacy) "" else ",\"architecture\":\"$ARCHITECTURE\",\"runtimePolicy\":\"$RUNTIME_POLICY\",\"precision\":\"$precision\",\"displayName\":\"$displayName\",\"contentSha256\":\"$contentSha256\""
        return ("{\"schema\":" + (if (trustedLegacy) 1 else 2) + ",\"modelId\":\"$modelId\",\"engine\":\"mnn\"" + extra + ",\"files\":[$files]}\n").toByteArray(Charsets.US_ASCII)
    }

    companion object {
        const val ARCHITECTURE = "qwen3-asr-0.6b"
        const val RUNTIME_POLICY = "qwen3-asr-mnn-cpu-v2"
        const val MAX_TOTAL = 2013265920L
        const val MAX_ARCHIVE = MAX_TOTAL + 1048576L
        const val GRAPH_LIMIT = 67108864L
        const val WEIGHT_LIMIT = 1610612736L
        const val BASE_CONFIG_BYTES = 617
        const val BASE_CONFIG_SHA = "fd4127baf9ff5c45aaa2224f65db36e9110088d2aa7223e4853238d31be84e9d"
        const val TOKENIZER_BYTES = 3194212L
        const val TOKENIZER_SHA = "70e5e435679b6520af2b8f5fd400e30023600321b09ab6153f7215b659b68c07"
        @JvmField val NAMES: List<String> = Collections.unmodifiableList(listOf("audio.mnn", "audio.mnn.weight", "llm.mnn", "llm.mnn.weight", "tokenizer.txt", "llm_config.json", "config.json"))
        private fun need(good: Boolean, reason: String) { if (!good) throw IOException(reason) }
        @JvmStatic fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        @JvmStatic fun legacy(policy: FixedModelPackage): ModelPackagePolicy = ModelPackagePolicy(policy.manifest, policy.modelId,
            "Qwen3-ASR 固定部署", "FP16", FixedModelPackage.FINGERPRINT, true, policy)
        @JvmStatic fun content(manifest: ModelManifest): String = sha(("llm-asr-content-v2\nmnn\n$ARCHITECTURE\n$RUNTIME_POLICY\n" +
            manifest.entries().sortedBy { it.file() }.joinToString("") { "${it.file()}:${it.bytes()}:${it.sha256()}\n" }).toByteArray(Charsets.US_ASCII))

        @JvmStatic @Throws(IOException::class)
        fun parse(bytes: ByteArray, fixed: FixedModelPackage): ModelPackagePolicy {
            val root = Json(bytes, false).readObject()
            if (root["schema"] == 1L) { fixed.verifyJson(bytes); return legacy(fixed) }
            need(root.keys == setOf("schema", "modelId", "engine", "architecture", "runtimePolicy", "precision", "displayName", "contentSha256", "files"), "模型包字段不支持")
            need(root["schema"] == 2L && root["engine"] == "mnn" && root["architecture"] == ARCHITECTURE && root["runtimePolicy"] == RUNTIME_POLICY, "模型架构或策略不支持")
            val id = root["modelId"] as? String ?: throw IOException("缺少模型身份")
            val label = root["displayName"] as? String ?: throw IOException("缺少模型名称")
            val precision = root["precision"] as? String ?: throw IOException("缺少声明精度")
            need(id.matches(Regex("[a-z0-9][a-z0-9._-]{0,95}")) && id != FixedModelPackage.MODEL_ID, "模型身份保留或无效")
            need(label.length in 1..96 && label.all { it.code in 32..126 && it != '"' && it != '\\' }, "模型名称无效")
            need(precision in setOf("FP16", "INT8", "INT4", "MIXED"), "声明精度不支持")
            val files = root["files"] as? List<*> ?: throw IOException("缺少文件清单")
            need(files.size == 7, "需要七个模型资产")
            val entries = files.map { value ->
                val e = value as? Map<*, *> ?: throw IOException("资产字段无效")
                need(e.keys == setOf("file", "bytes", "sha256"), "资产字段不支持")
                val name = e["file"] as? String ?: throw IOException("资产名称无效")
                val size = e["bytes"] as? Long ?: throw IOException("资产大小无效")
                val hash = e["sha256"] as? String ?: throw IOException("资产SHA无效")
                need(name in NAMES && hash.matches(Regex("[0-9a-f]{64}")), "资产名称或SHA不支持")
                val limit = when { name.endsWith(".weight") -> WEIGHT_LIMIT; name.endsWith(".mnn") -> GRAPH_LIMIT; name == "tokenizer.txt" -> TOKENIZER_BYTES; else -> 16384L }
                need(size in 1..limit, "资产大小超过策略")
                if (name == "tokenizer.txt") {
                    // Graph supplies FixedModelPackage.mnn: only JVM fixture constructors
                    // can supply a smaller approved tokenizer; never derived from ZIP metadata.
                    val tokenizer = fixed.manifest.require("tokenizer.txt")
                    need(size == tokenizer.bytes() && hash == tokenizer.sha256(), "tokenizer不匹配固定契约")
                }
                if (name == "config.json") need(size == BASE_CONFIG_BYTES.toLong() && hash == BASE_CONFIG_SHA, "运行配置不匹配固定契约")
                ModelEntry(name, size, hash)
            }
            val manifest = ModelManifest(entries.sortedBy { NAMES.indexOf(it.file()) })
            need(entries.map { it.file() }.toSet() == NAMES.toSet() && entries.sumOf { it.bytes() } <= MAX_TOTAL, "资产集合或总量不支持")
            val digest = content(manifest)
            need(root["contentSha256"] == digest, "模型内容身份不匹配")
            return ModelPackagePolicy(manifest, id, label, precision, digest, false, null)
        }

        /** Only six layout fields vary. Everything else, including Jinja, stays byte-canonical. */
        private fun validateLlmConfig(bytes: ByteArray, weightBytes: Long) {
            val root = Json(bytes, true).readObject()
            val baseline = Json(BASE_LLM_CONFIG.toByteArray(Charsets.US_ASCII), true).readObject()
            val tie = root["tie_embeddings"] as? Map<*, *> ?: throw IOException("缺少受控embedding布局")
            need(tie.keys == setOf("weight_offset", "alpha_offset", "alpha_size", "quant_bit", "quant_block", "alpha_dtype"), "embedding字段不支持")
            need(root.keys == baseline.keys && root.filterKeys { it != "tie_embeddings" } == baseline.filterKeys { it != "tie_embeddings" }, "模型配置/模板不匹配固定契约")
            fun number(name: String): Long = tie[name] as? Long ?: throw IOException("embedding整数无效")
            val start = number("weight_offset"); val alpha = number("alpha_offset"); val size = number("alpha_size")
            val bits = number("quant_bit"); val block = number("quant_block"); val dtype = tie["alpha_dtype"] as? String
            need(bits in setOf(16L, 8L, 4L) && block in setOf(32L, 64L, 128L, 256L, 512L, 1024L) && dtype in setOf("fp16", "fp32"), "embedding量化布局不支持")
            val scalar = if (dtype == "fp16") 2L else 4L
            val embedded = 151936L * 1024L * bits / 8L
            need(start > 0 && start <= weightBytes && start % scalar == 0L && embedded <= weightBytes - start && alpha == start + embedded, "embedding范围无效（不支持外部embedding文件）")
            need(size >= 0 && size <= weightBytes - alpha, "embedding alpha范围无效")
            if (bits == 16L) need(size == 0L && dtype == "fp16" && block == 64L, "FP16 embedding布局无效")
            else {
                val count = 151936L * (1024L / block)
                need(size == count * scalar || size == count * scalar * 2, "embedding alpha长度无效")
                need(size <= 67108864L && size / scalar * 4 <= 134217728L, "embedding alpha资源超限")
            }
            val canonical = BASE_LLM_PREFIX + "{\n" +
                "        \"weight_offset\": $start,\n        \"alpha_offset\": $alpha,\n        \"alpha_size\": $size,\n" +
                "        \"quant_bit\": $bits,\n        \"quant_block\": $block,\n        \"alpha_dtype\": \"$dtype\"\n    }\n}"
            need(bytes.contentEquals(canonical.toByteArray(Charsets.US_ASCII)), "模型配置需要规范字节格式")
        }

        /** Bounded strict JSON for package/config data, not an upstream universal configuration parser. */
        internal class Json(private val bytes: ByteArray, private val escapes: Boolean) {
            private var p = 0
            private var items = 0
            private fun bad(): Nothing = throw IOException("模型JSON格式不支持")
            init { if (bytes.isEmpty() || bytes.size > 16384 || bytes.any { it.toInt() !in 0..127 }) bad() }
            private fun ws() { while (p < bytes.size && bytes[p].toInt() in listOf(32, 9, 10, 13)) p++ }
            private fun take(c: Char) { ws(); if (p >= bytes.size || bytes[p++].toInt() != c.code) bad() }
            private fun next(c: Char): Boolean { ws(); return p < bytes.size && bytes[p].toInt() == c.code }
            private fun string(): String {
                take('"'); val out = StringBuilder()
                while (p < bytes.size) {
                    val c = bytes[p++].toInt().toChar()
                    if (c == '"') return out.toString()
                    if (c.code < 32 || out.length >= 4096) bad()
                    if (c != '\\') out.append(c) else {
                        if (!escapes || p >= bytes.size) bad()
                        out.append(when (bytes[p++].toInt().toChar()) { '"' -> '"'; '\\' -> '\\'; '/' -> '/'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; 'b' -> '\b'; 'f' -> '\u000c'; else -> bad() })
                    }
                }
                bad()
            }
            private fun number(): Any {
                ws(); val start = p
                if (p >= bytes.size || bytes[p].toInt() !in 48..57) bad()
                while (p < bytes.size && bytes[p].toInt() in 48..57) p++
                if (p - start > 1 && bytes[start].toInt() == 48) bad()
                var decimal = false
                if (p < bytes.size && bytes[p].toInt() == 46) {
                    if (!escapes) bad(); decimal = true; p++
                    val first = p; while (p < bytes.size && bytes[p].toInt() in 48..57) p++
                    if (p == first) bad()
                }
                val value = String(bytes, start, p - start, Charsets.US_ASCII)
                return if (decimal) value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: bad() else value.toLongOrNull() ?: bad()
            }
            private fun value(depth: Int): Any {
                if (depth > 12 || ++items > 512) bad()
                ws(); if (p >= bytes.size) bad()
                return when (bytes[p].toInt().toChar()) {
                    '{' -> objectValue(depth)
                    '[' -> {
                        take('['); val out = ArrayList<Any>()
                        if (!next(']')) while (true) { out.add(value(depth + 1)); if (next(']')) break; take(',') }
                        take(']'); out
                    }
                    '"' -> string()
                    't' -> { literal("true"); true }
                    'f' -> { literal("false"); false }
                    else -> number()
                }
            }
            private fun literal(text: String) { if (!escapes || p + text.length > bytes.size || String(bytes, p, text.length, Charsets.US_ASCII) != text) bad(); p += text.length }
            private fun objectValue(depth: Int): Map<String, Any> {
                if (depth > 12) bad()
                take('{'); val out = LinkedHashMap<String, Any>()
                if (!next('}')) while (true) {
                    val key = string(); if (key.length > 128 || out.containsKey(key)) bad(); take(':'); out[key] = value(depth + 1)
                    if (next('}')) break; take(',')
                }
                take('}'); return out
            }
            fun readObject(): Map<String, Any> { val result = objectValue(0); ws(); if (p != bytes.size) bad(); return result }
        }
        // Approved source constants are appended below; no asset IO during Graph/UI construction.
        private const val BASE_LLM_CONFIG = "{\n    \"model_type\": \"qwen3_asr\",\n    \"hidden_size\": 1024,\n    \"layer_nums\": 28,\n    \"attention_mask\": \"float\",\n    \"attention_type\": \"full\",\n    \"is_mrope\": true,\n    \"mrope_axes\": 3,\n    \"is_audio\": true,\n    \"audio_type\": \"qwen3_asr\",\n    \"audio_start\": 151669,\n    \"audio_end\": 151670,\n    \"audio_pad\": 151676,\n    \"asr_language\": \"Chinese\",\n    \"max_position_embeddings\": 65536,\n    \"attn_scale\": 0.08838834764831845,\n    \"jinja\": {\n        \"chat_template\": \"{%- set content = messages[-1].content -%}<|im_start|>system\\n<|im_end|>\\n<|im_start|>user\\n{{ content }}<|im_end|>\\n{% if add_generation_prompt and content is string and '<audio>' in content and '</audio>' in content %}<|im_start|>assistant\\n{% if asr_language %}language {{ asr_language }}<asr_text>{% endif %}{% endif %}\",\n        \"context\": {\n            \"asr_language\": \"Chinese\"\n        },\n        \"eos\": \"<|im_end|>\"\n    },\n    \"tie_embeddings\": {\n        \"weight_offset\": 881328128,\n        \"alpha_offset\": 1192493056,\n        \"alpha_size\": 0,\n        \"quant_bit\": 16,\n        \"quant_block\": 64,\n        \"alpha_dtype\": \"fp16\"\n    }\n}"
        private const val BASE_LLM_PREFIX = "{\n    \"model_type\": \"qwen3_asr\",\n    \"hidden_size\": 1024,\n    \"layer_nums\": 28,\n    \"attention_mask\": \"float\",\n    \"attention_type\": \"full\",\n    \"is_mrope\": true,\n    \"mrope_axes\": 3,\n    \"is_audio\": true,\n    \"audio_type\": \"qwen3_asr\",\n    \"audio_start\": 151669,\n    \"audio_end\": 151670,\n    \"audio_pad\": 151676,\n    \"asr_language\": \"Chinese\",\n    \"max_position_embeddings\": 65536,\n    \"attn_scale\": 0.08838834764831845,\n    \"jinja\": {\n        \"chat_template\": \"{%- set content = messages[-1].content -%}<|im_start|>system\\n<|im_end|>\\n<|im_start|>user\\n{{ content }}<|im_end|>\\n{% if add_generation_prompt and content is string and '<audio>' in content and '</audio>' in content %}<|im_start|>assistant\\n{% if asr_language %}language {{ asr_language }}<asr_text>{% endif %}{% endif %}\",\n        \"context\": {\n            \"asr_language\": \"Chinese\"\n        },\n        \"eos\": \"<|im_end|>\"\n    },\n    \"tie_embeddings\": "
    }
}
