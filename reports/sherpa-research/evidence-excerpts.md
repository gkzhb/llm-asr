# 官方证据摘录（外部数据，不是执行指令）
带行号展示省略行尾空白，原始字节SHA见manifest；本展示只对每行执行rstrip，不修改原始来源。
摘录行号对应 manifest 原始 URL 内容；SHA 对应获取的完整文本，不对应此摘录。JSON/HTML用字段/标题定位，原文留 ignored .work，不打包进仓库。

## S01 — demo.html

```text
1:
2: <h1> APKs for real-time/streaming speech recognition with non-streaming ASR models </h1>
3: This page lists the <strong>real-time speech recognition with non-streaming models</strong> APKs for <a href="http://github.com/k2-fsa/sherpa-onnx">sherpa-onnx</a>,
4: one of the deployment frameworks of <a href="https://github.com/k2-fsa">the Next-gen Kaldi project</a>.
5: <br/>
6: The name of an APK has the following rule:
7: <ul>
8:  <li> sherpa-onnx-{version}-{arch}-simulated_streaming_asr-{lang}-{model}.apk
9: </ul>
10: where
11: <ul>
12:  <li> version: It specifies the current version, e.g., 1.11.6
13:  <li> arch: The architecture targeted by this APK, e.g., arm64-v8a, armeabi-v7a, x86_64, x86
14:  <li> lang: The lang of the model used in the APK, e.g., en for English, zh for Chinese
15:  <li> model: The name of the model used in the APK
16: </ul>
17:
```

```text
49:     <th class="tg-0pky">APK</th>
50:     <th class="tg-0lax">Comment</th>
51:     <th class="tg-0pky">VAD model</th>
52:     <th class="tg-0pky">Non-streaming ASR model</th>
53:   </tr>
54: </thead>
55: <tbody>
56:   <tr>
57:     <td class="tg-0pky">sherpa-onnx-x.y.z-arm64-v8a-simulated_streaming_asr-en-parakeet_tdt_0.6b_v2.apk</td>
58:     <td class="tg-0lax">It supports only English. It is from <a href="https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2">https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2</a></td>
59:     <td class="tg-0pky"><a href="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx">silero_vad.onnx</a></td>
60:     <td class="tg-0pky"><a href="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2">sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2</a></td>
61:   </tr>
62:   <tr>
63:     <td class="tg-0pky">sherpa-onnx-x.y.z-arm64-v8a-simulated_streaming_asr-ja-zipformer_reazonspeech.apk</td>
64:     <td class="tg-0lax">It supports only Japanese. It is from <a href="https://github.com/reazon-research/ReazonSpeech">https://github.com/reazon-research/ReazonSpeech</a></td>
65:     <td class="tg-0pky"><a href="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx">silero_vad.onnx</a></td>
66:     <td class="tg-0pky"><a href="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01.tar.bz2">sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01.tar.bz2</a></td>
67:   </tr>
68:   <tr>
69:     <td class="tg-0pky">sherpa-onnx-x.y.z-arm64-v8a-simulated_streaming_asr-zh_en_ko_ja_yue-sense_voice.apk</td>
70:     <td class="tg-0lax">It supports Chinese, Cantonese, English, Korean, and Japanese (中、英、粤、日、韩5种语音). It is converted from <a href="https://github.com/FunAudioLLM/SenseVoice">https://github.com/FunAudioLLM/SenseVoice</a></td>
71:     <td class="tg-0pky"><a href="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx">silero_vad.onnx</a></td>
72:     <td class="tg-0pky"><a href="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2">sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2</a></td>
73:   </tr>
74:   <tr>
75:     <td class="tg-0pky">sherpa-onnx-x.y.z-arm64-v8a-simulated_streaming_asr-zh-telespeech.apk</td>
76:     <td class="tg-0lax">支持非常多种中文方言. It is converted from <a href="https://github.com/Tele-AI/TeleSpeech-ASR">https://github.com/Tele-AI/TeleSpeech-ASR</a></td>
77:     <td class="tg-0pky"><a href="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx">silero_vad.onnx</a></td>
78:     <td class="tg-0pky"><a href="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-telespeech-ctc-int8-zh-2024-06-04.tar.bz2">sherpa-onnx-telespeech-ctc-int8-zh-2024-06-04.tar.bz2</a></td>
```

```text
117:     <td class="tg-0pky">sherpa-onnx-x.y.z-arm64-v8a-simulated_streaming_asr-zh-zipformer.apk</td>
118:     <td class="tg-0lax">It supports only Chinese.</td>
119:     <td class="tg-0pky"><a href="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx">silero_vad.onnx</a></td>
120:     <td class="tg-0pky"><a href="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/icefall-asr-zipformer-wenetspeech-20230615.tar.bz2">icefall-asr-zipformer-wenetspeech-20230615</a></td>
121:   </tr>
122:   <tr>
123:     <td class="tg-0pky">sherpa-onnx-x.y.z-arm64-v8a-simulated_streaming_asr-zh-paraformer.apk</td>
124:     <td class="tg-0lax"><span style="font-weight:400;font-style:normal">It supports both Chinese and English.</span></td>
125:     <td class="tg-0pky"><a href="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx">silero_vad.onnx</a></td>
126:     <td class="tg-0pky"><a href="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-2023-03-28.tar.bz2">sherpa-onnx-paraformer-zh-2023-03-28</a></td>
127:   </tr>
128:   <tr>
```

## S02 — sherpa-onnx__android__SherpaOnnxSimulateStreamingAsr__app__src__main__java__com__k2fsa__sherpa__onnx__simulate__streaming__asr__screens__Home.kt

```text
65:     val lazyColumnListState = rememberLazyListState()
66:     val coroutineScope = rememberCoroutineScope()
67:
68:     var isInitialized by remember { mutableStateOf(false) }
69:
70:     // we change asrModelType in github actions
71:     val asrModelType = 15
72:
73:     LaunchedEffect(Unit) {
74:         if (asrModelType >= 9000) {
75:             resultList.add("Using QNN for Qualcomm NPU (HTP backend)")
76:             resultList.add("It takes about 10s for the first run to start")
77:             resultList.add("Later runs require less than 1 second")
78:         }
79:
80:         withContext(Dispatchers.Default) {
81:             // Call your heavy initialization off the main thread
82:             SimulateStreamingAsr.initOfflineRecognizer(activity, asrModelType)
83:             SimulateStreamingAsr.initVad(activity.assets)
```

```text
139:                     var buffer = arrayListOf<Float>()
140:                     var offset = 0
141:                     val windowSize = 512
142:                     var isSpeechStarted = false
143:                     var startTime = System.currentTimeMillis()
144:                     var lastText = ""
145:                     var added = false
146:                     var speechStartOffset = 0
147:
148:
149:                     while (isStarted) {
150:                         for (s in samplesChannel) {
151:                             if (s.isEmpty()) {
152:                                 break
153:                             }
154:
155:                             buffer.addAll(s.toList())
156:                             while (offset + windowSize < buffer.size) {
157:                                 SimulateStreamingAsr.vad.acceptWaveform(
158:                                     buffer.subList(
159:                                         offset,
160:                                         offset + windowSize
161:                                     ).toFloatArray()
162:                                 )
163:                                 offset += windowSize
164:                                 if (!isSpeechStarted && SimulateStreamingAsr.vad.isSpeechDetected()) {
165:                                     isSpeechStarted = true
166:                                     // offset 0.4s
167:                                     speechStartOffset = offset - 6400
168:                                     if(speechStartOffset < 0) {
169:                                         speechStartOffset = 0
170:                                     }
171:                                     startTime = System.currentTimeMillis()
172:                                 }
173:                             }
174:
175:                             val elapsed = System.currentTimeMillis() - startTime
176:                             if (isSpeechStarted && elapsed > 200) {
177:                                 // Run ASR every 0.2 seconds == 200 milliseconds
178:                                 // You can change it to some other value
179:                                 val stream = SimulateStreamingAsr.recognizer.createStream()
180:                                 stream.acceptWaveform(
181:                                     buffer.subList(speechStartOffset, offset).toFloatArray(),
182:                                     sampleRateInHz
183:                                 )
184:                                 SimulateStreamingAsr.recognizer.decode(stream)
185:                                 val result = SimulateStreamingAsr.recognizer.getResult(stream)
186:                                 stream.release()
```

```text
207:                             while (!SimulateStreamingAsr.vad.empty()) {
208:                                 val stream = SimulateStreamingAsr.recognizer.createStream()
209:                                 stream.acceptWaveform(
210:                                     SimulateStreamingAsr.vad.front().samples,
211:                                     sampleRateInHz
212:                                 )
213:                                 SimulateStreamingAsr.recognizer.decode(stream)
214:                                 val result = SimulateStreamingAsr.recognizer.getResult(stream)
215:                                 stream.release()
216:
217:                                 isSpeechStarted = false
218:                                 SimulateStreamingAsr.vad.pop()
219:
220:                                 buffer = arrayListOf()
221:                                 offset = 0
222:                                 if (lastText.isNotBlank()) {
223:                                     if (added && resultList.isNotEmpty()) {
224:                                         resultList[resultList.size - 1] = result.text
225:                                     } else {
226:                                         resultList.add(result.text)
227:                                     }
228:
229:                                     coroutineScope.launch {
230:                                         lazyColumnListState.animateScrollToItem(resultList.size - 1)
231:                                     }
232:                                     added = false
233:                                 }
234:                             }
235:                         }
236:                     }
237:                 }
238:             }
239:         } else {
240:             audioRecord?.stop()
241:             audioRecord?.release()
242:             audioRecord = null
243:         }
```

## S03 — sherpa-onnx__sherpa-onnx__kotlin-api__OfflineRecognizer.kt

```text
4:
5: data class OfflineRecognizerResult(
6:     val text: String,
7:     val tokens: Array<String>,
8:     val timestamps: FloatArray,
9:     val lang: String,
10:     val emotion: String,
11:     val event: String,
12:
13:     // valid only for TDT models
14:     val durations: FloatArray,
15:
16:     // The decoded word IDs. Empty for greedy search decoding, non-empty when
17:     // an HLG graph is used, i.e. when ctcFstDecoderConfig.graph is set.
18:     val words: IntArray = IntArray(0),
```

```text
78: data class OfflineQwen3AsrModelConfig(
79:     var convFrontend: String = "",
80:     var encoder: String = "",
81:     var decoder: String = "",
82:     var tokenizer: String = "",
83:     var maxTotalLen: Int = 512,
84:     var maxNewTokens: Int = 128,
85:     var temperature: Float = 1e-6f,
86:     var topP: Float = 0.8f,
87:     var seed: Int = 42,
88:     var hotwords: String = "",
89: )
```

```text
133:
134: data class OfflineSenseVoiceModelConfig(
135:     var model: String = "",
136:     var language: String = "",
137:     var useInverseTextNormalization: Boolean = true,
138:     var qnnConfig: QnnConfig = QnnConfig(),
139: )
140:
141: data class OfflineModelConfig(
142:     var transducer: OfflineTransducerModelConfig = OfflineTransducerModelConfig(),
143:     var paraformer: OfflineParaformerModelConfig = OfflineParaformerModelConfig(),
144:     var whisper: OfflineWhisperModelConfig = OfflineWhisperModelConfig(),
145:     var fireRedAsr: OfflineFireRedAsrModelConfig = OfflineFireRedAsrModelConfig(),
146:     var moonshine: OfflineMoonshineModelConfig = OfflineMoonshineModelConfig(),
147:     var nemo: OfflineNemoEncDecCtcModelConfig = OfflineNemoEncDecCtcModelConfig(),
148:     var senseVoice: OfflineSenseVoiceModelConfig = OfflineSenseVoiceModelConfig(),
149:     var dolphin: OfflineDolphinModelConfig = OfflineDolphinModelConfig(),
150:     var zipformerCtc: OfflineZipformerCtcModelConfig = OfflineZipformerCtcModelConfig(),
151:     var wenetCtc: OfflineWenetCtcModelConfig = OfflineWenetCtcModelConfig(),
152:     var omnilingual: OfflineOmnilingualAsrCtcModelConfig = OfflineOmnilingualAsrCtcModelConfig(),
153:     var medasr: OfflineMedAsrCtcModelConfig = OfflineMedAsrCtcModelConfig(),
154:     var funasrNano: OfflineFunAsrNanoModelConfig = OfflineFunAsrNanoModelConfig(),
155:     var qwen3Asr: OfflineQwen3AsrModelConfig = OfflineQwen3AsrModelConfig(),
156:     var fireRedAsrCtc: OfflineFireRedAsrCtcModelConfig = OfflineFireRedAsrCtcModelConfig(),
157:     var canary: OfflineCanaryModelConfig = OfflineCanaryModelConfig(),
158:     var cohereTranscribe: OfflineCohereTranscribeModelConfig =
159:         OfflineCohereTranscribeModelConfig(),
160:     var teleSpeech: String = "",
161:     var numThreads: Int = 1,
162:     var debug: Boolean = false,
```

```text
198:             newFromAsset(assetManager, config)
199:         } else {
200:             newFromFile(config)
201:         }
202:         require(ptr != 0L) {
203:             "Invalid OfflineRecognizerConfig: failed to create native OfflineRecognizer"
204:         }
205:     }
206:
207:     protected fun finalize() {
208:         if (ptr != 0L) {
209:             delete(ptr)
210:             ptr = 0
211:         }
212:     }
213:
214:     fun release() = finalize()
215:
216:     fun createStream(): OfflineStream {
217:         val p = createStream(ptr)
218:         return OfflineStream(p)
219:     }
220:
221:     fun createStream(hotwords: String): OfflineStream {
222:         val p = createStreamWithHotwords(ptr, hotwords)
223:         return OfflineStream(p)
224:     }
225:
226:     fun getResult(stream: OfflineStream): OfflineRecognizerResult {
227:         return getResult(stream.ptr)
228:     }
229:
230:     fun decode(stream: OfflineStream) = decode(ptr, stream.ptr)
```

```text
470:         15 -> {
471:             val modelDir = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17"
472:             return OfflineModelConfig(
473:                 senseVoice = OfflineSenseVoiceModelConfig(
474:                     model = "$modelDir/model.int8.onnx",
475:                 ),
476:                 tokens = "$modelDir/tokens.txt",
477:             )
478:         }
479:
480:         16 -> {
481:             val modelDir = "sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01"
```

```text
996:         61 -> {
997:             val modelDir = "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25"
998:             return OfflineModelConfig(
999:                 qwen3Asr = OfflineQwen3AsrModelConfig(
1000:                     convFrontend = "$modelDir/conv_frontend.onnx",
1001:                     encoder = "$modelDir/encoder.int8.onnx",
1002:                     decoder = "$modelDir/decoder.int8.onnx",
1003:                     tokenizer = "$modelDir/tokenizer",
1004:                 ),
1005:                 tokens = "",
1006:                 numThreads=3,
1007:             )
1008:         }
1009:
1010:         62 -> {
1011:             val modelDir = "sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming"
1012:             return OfflineModelConfig(
1013:                 transducer = OfflineTransducerModelConfig(
1014:                     encoder = "$modelDir/encoder.int8.onnx",
```

## S04 — sherpa-onnx__sherpa-onnx__kotlin-api__OnlineRecognizer.kt

```text
5: data class EndpointRule(
6:     var mustContainNonSilence: Boolean,
7:     var minTrailingSilence: Float,
8:     var minUtteranceLength: Float,
9: )
10:
11: data class EndpointConfig(
12:     var rule1: EndpointRule = EndpointRule(false, 2.4f, 0.0f),
13:     var rule2: EndpointRule = EndpointRule(true, 1.4f, 0.0f),
14:     var rule3: EndpointRule = EndpointRule(false, 0.0f, 20.0f)
15: )
16:
17: data class OnlineTransducerModelConfig(
```

```text
42:     var transducer: OnlineTransducerModelConfig = OnlineTransducerModelConfig(),
43:     var paraformer: OnlineParaformerModelConfig = OnlineParaformerModelConfig(),
44:     var zipformer2Ctc: OnlineZipformer2CtcModelConfig = OnlineZipformer2CtcModelConfig(),
45:     var neMoCtc: OnlineNeMoCtcModelConfig = OnlineNeMoCtcModelConfig(),
46:     var toneCtc: OnlineToneCtcModelConfig = OnlineToneCtcModelConfig(),
47:     var tokens: String = "",
48:     var numThreads: Int = 1,
49:     var debug: Boolean = false,
50:     var provider: String = "cpu",
51:     var modelType: String = "",
52:     var modelingUnit: String = "",
53:     var bpeVocab: String = "",
54: )
55:
56: data class OnlineLMConfig(
57:     var model: String = "",
58:     var scale: Float = 0.5f,
59: )
60:
61: data class OnlineCtcFstDecoderConfig(
62:     var graph: String = "",
63:     var maxActive: Int = 3000,
64: )
65:
66: data class OnlineRecognizerConfig(
67:     var featConfig: FeatureConfig = FeatureConfig(),
68:     var modelConfig: OnlineModelConfig = OnlineModelConfig(),
69:     var lmConfig: OnlineLMConfig = OnlineLMConfig(),
70:     var ctcFstDecoderConfig: OnlineCtcFstDecoderConfig = OnlineCtcFstDecoderConfig(),
71:     var hr: HomophoneReplacerConfig = HomophoneReplacerConfig(),
72:     var endpointConfig: EndpointConfig = EndpointConfig(),
73:     var enableEndpoint: Boolean = true,
74:     var decodingMethod: String = "greedy_search",
75:     var maxActivePaths: Int = 4,
76:     var hotwordsFile: String = "",
77:     var hotwordsScore: Float = 1.5f,
78:     var ruleFsts: String = "",
79:     var ruleFars: String = "",
80:     var blankPenalty: Float = 0.0f,
81: )
```

```text
90:
91: class OnlineRecognizer(
92:     assetManager: AssetManager? = null,
93:     val config: OnlineRecognizerConfig,
94: ) {
95:     private var ptr: Long
96:
97:     init {
98:         ptr = if (assetManager != null) {
99:             newFromAsset(assetManager, config)
100:         } else {
101:             newFromFile(config)
102:         }
103:         require(ptr != 0L) {
104:             "Invalid OnlineRecognizerConfig: failed to create native OnlineRecognizer"
105:         }
106:     }
107:
108:     protected fun finalize() {
109:         if (ptr != 0L) {
110:             delete(ptr)
111:             ptr = 0
112:         }
113:     }
114:
115:     fun release() = finalize()
116:
117:     fun createStream(hotwords: String = ""): OnlineStream {
118:         val p = createStream(ptr, hotwords)
119:         return OnlineStream(p)
120:     }
121:
122:     fun reset(stream: OnlineStream) = reset(ptr, stream.ptr)
123:     fun decode(stream: OnlineStream) = decode(ptr, stream.ptr)
124:     fun isEndpoint(stream: OnlineStream) = isEndpoint(ptr, stream.ptr)
125:     fun isReady(stream: OnlineStream) = isReady(ptr, stream.ptr)
126:     fun getResult(stream: OnlineStream): OnlineRecognizerResult {
127:         return getResult(ptr, stream.ptr)
128:     }
129:
130:     private external fun delete(ptr: Long)
131:
132:     private external fun newFromAsset(
133:         assetManager: AssetManager,
134:         config: OnlineRecognizerConfig,
135:     ): Long
136:
137:     private external fun newFromFile(
138:         config: OnlineRecognizerConfig,
139:     ): Long
140:
141:     private external fun createStream(ptr: Long, hotwords: String): Long
142:     private external fun reset(ptr: Long, streamPtr: Long)
143:     private external fun decode(ptr: Long, streamPtr: Long)
144:     private external fun isEndpoint(ptr: Long, streamPtr: Long): Boolean
145:     private external fun isReady(ptr: Long, streamPtr: Long): Boolean
146:     private external fun getResult(ptr: Long, streamPtr: Long): OnlineRecognizerResult
147:
148:     companion object {
149:         init {
150:             System.loadLibrary("sherpa-onnx-jni")
151:         }
152:
153:         @JvmStatic
```

## S05 — sherpa-onnx__sherpa-onnx__kotlin-api__OnlineStream.kt

```text
3: class OnlineStream(var ptr: Long = 0) {
4:     init {
5:         require(ptr != 0L) { "Failed to create native OnlineStream" }
6:     }
7:
8:     fun acceptWaveform(samples: FloatArray, sampleRate: Int) =
9:         acceptWaveform(ptr, samples, sampleRate)
10:
11:     fun inputFinished() = inputFinished(ptr)
12:
13:     fun setOption(key: String, value: String) = setOption(ptr, key, value)
14:
15:     fun getOption(key: String): String = getOption(ptr, key)
16:
17:     protected fun finalize() {
18:         if (ptr != 0L) {
19:             delete(ptr)
20:             ptr = 0
21:         }
22:     }
23:
24:     fun release() = finalize()
25:
26:     fun use(block: (OnlineStream) -> Unit) {
27:         try {
28:             block(this)
29:         } finally {
30:             release()
31:         }
32:     }
33:
34:     private external fun acceptWaveform(ptr: Long, samples: FloatArray, sampleRate: Int)
35:     private external fun inputFinished(ptr: Long)
36:     private external fun setOption(ptr: Long, key: String, value: String)
```

## S06 — sherpa-onnx__sherpa-onnx__kotlin-api__Vad.kt

```text
6: data class SileroVadModelConfig(
7:     var model: String = "",
8:     var threshold: Float = 0.5F,
9:     var minSilenceDuration: Float = 0.25F,
10:     var minSpeechDuration: Float = 0.25F,
11:     var windowSize: Int = 512,
12:     var maxSpeechDuration: Float = 5.0F,
13: )
14:
15: data class TenVadModelConfig(
16:     var model: String = "",
17:     var threshold: Float = 0.5F,
18:     var minSilenceDuration: Float = 0.25F,
19:     var minSpeechDuration: Float = 0.25F,
20:     var windowSize: Int = 256,
21:     var maxSpeechDuration: Float = 5.0F,
22: )
23:
24: data class VadModelConfig(
25:     var sileroVadModelConfig: SileroVadModelConfig = SileroVadModelConfig(),
26:     var tenVadModelConfig: TenVadModelConfig = TenVadModelConfig(),
27:     var sampleRate: Int = 16000,
28:     var numThreads: Int = 1,
29:     var provider: String = "cpu",
30:     var debug: Boolean = false,
31: )
32:
33: class SpeechSegment(val start: Int, val samples: FloatArray)
34:
35: class Vad(
36:     assetManager: AssetManager? = null,
37:     var config: VadModelConfig,
38: ) {
39:     private var ptr: Long
```

```text
63:
64:     fun acceptWaveform(samples: FloatArray) = acceptWaveform(ptr, samples)
65:
66:     fun empty(): Boolean = empty(ptr)
67:     fun pop() = pop(ptr)
68:
69:     fun front(): SpeechSegment {
70:         return front(ptr)
71:     }
72:
73:     fun clear() = clear(ptr)
74:
75:     fun isSpeechDetected(): Boolean = isSpeechDetected(ptr)
76:
77:     fun reset() = reset(ptr)
78:
79:     fun flush() = flush(ptr)
80:
81:     private external fun delete(ptr: Long)
82:
83:     private external fun newFromAsset(
84:         assetManager: AssetManager,
85:         config: VadModelConfig,
86:     ): Long
87:
88:     private external fun newFromFile(
89:         config: VadModelConfig,
90:     ): Long
91:
```

## S07 — sherpa-onnx__sherpa-onnx__csrc__online-recognizer-transducer-impl.h

```text
210:       const std::string &hotwords) const override {
211:     auto hws = std::regex_replace(hotwords, std::regex("/"), "\n");
212:     std::istringstream is(hws);
213:     std::vector<std::vector<int32_t>> current;
214:     std::vector<float> current_scores;
215:     if (!EncodeHotwords(is, config_.model_config.modeling_unit, sym_,
216:                         bpe_encoder_.get(), &current, &current_scores)) {
217:       SHERPA_ONNX_LOGE("Encode hotwords failed, skipping, hotwords are : %s",
218:                        hotwords.c_str());
219:     }
220:
```

```text
249:   bool IsReady(OnlineStream *s) const override {
250:     return s->GetNumProcessedFrames() + model_->ChunkSize() <
251:            s->NumFramesReady();
252:   }
```

```text
341:     auto states = model_->StackStates(states_vec);
342:
343:     auto pair = model_->RunEncoder(std::move(x), std::move(states),
344:                                    std::move(processed_frames));
345:
346:     if (has_context_graph) {
347:       decoder_->Decode(std::move(pair.first), ss, &results);
348:     } else {
349:       decoder_->Decode(std::move(pair.first), &results);
350:     }
351:
352:     std::vector<std::vector<Ort::Value>> next_states =
353:         model_->UnStackStates(pair.second);
354:
355:     for (int32_t i = 0; i != n; ++i) {
356:       ss[i]->SetResult(results[i]);
357:       ss[i]->SetStates(std::move(next_states[i]));
```

```text
375:   bool IsEndpoint(OnlineStream *s) const override {
376:     if (!config_.enable_endpoint) {
377:       return false;
378:     }
379:
380:     int32_t num_processed_frames = s->GetNumProcessedFrames();
381:
382:     // frame shift is 10 milliseconds
383:     float frame_shift_in_seconds = 0.01;
384:
385:     // subsampling factor is 4
386:     int32_t trailing_silence_frames = s->GetResult().num_trailing_blanks * 4;
387:
388:     return endpoint_.IsEndpoint(num_processed_frames, trailing_silence_frames,
389:                                 frame_shift_in_seconds);
390:   }
391:
392:   void Reset(OnlineStream *s) const override {
393:     int32_t context_size = model_->ContextSize();
394:
395:     {
396:       // segment is incremented only when the last
397:       // result is not empty, contains non-blanks and longer than context_size)
398:       const auto &r = s->GetResult();
399:       if (!r.tokens.empty() && r.tokens.back() != 0 &&
400:           r.tokens.size() > context_size) {
401:         s->GetCurrentSegment() += 1;
402:       }
403:     }
404:
405:     auto r = decoder_->GetEmptyResult();
406:     auto last_result = s->GetResult();
407:
408:     if (static_cast<int32_t>(last_result.tokens.size()) > context_size) {
409:       // if last result is not empty, then
410:       // truncate all last hyps and save as the 'ys' context for next result
411:       // (the encoder state buffers are kept)
412:       for (const auto &it : last_result.hyps) {
413:         auto h = it.second;
414:         r.hyps.Add({std::vector<int64_t>(h.ys.end() - context_size, h.ys.end()),
415:                     h.log_prob});
416:       }
417:
418:       r.tokens = std::vector<int64_t>(last_result.tokens.end() - context_size,
419:                                       last_result.tokens.end());
420:     } else {
421:       if (config_.reset_encoder) {
422:         // reset encoder states, use blanks as 'ys' context
423:         s->SetStates(model_->GetEncoderInitStates());
424:       }
425:     }
426:
427:     // but reset all contextual biasing graph states to root
428:     if (config_.decoding_method == "modified_beam_search" &&
429:         nullptr != s->GetContextGraph()) {
430:       for (auto it = r.hyps.begin(); it != r.hyps.end(); ++it) {
431:         it->second.context_state = s->GetContextGraph()->Root();
432:       }
433:     }
434:
435:     s->SetResult(r);
436:
437:     // Note: We only update counters. The underlying audio samples
438:     // are not discarded.
439:     s->Reset();
440:   }
```

## S08 — sherpa__docs__source__onnx__pretrained_models__online-transducer__zipformer-transducer-models.rst

```text
1140: .. _sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20:
1141:
1142: csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20 (Bilingual, Chinese + English)
1143: ----------------------------------------------------------------------------------------------------
1144:
1145: This model is converted from
1146:
1147: `<https://huggingface.co/csukuangfj/k2fsa-zipformer-chinese-english-mixed>`_
1148:
1149: which supports both Chinese and English. The model is contributed by the community
1150: and is trained on tens of thousands hours of some internal dataset.
1151:
1152: In the following, we describe how to download it and use it with `sherpa-onnx`_.
1153:
1154: Download the model
1155: ~~~~~~~~~~~~~~~~~~
1156:
1157: Please use the following commands to download it.
1158:
1159: .. code-block:: bash
1160:
1161:     cd /path/to/sherpa-onnx
1162:
1163:     wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
1164:
1165:     tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
1166:     rm sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
1167:
1168: Please check that the file sizes of the pre-trained models are correct. See
1169: the file sizes of ``*.onnx`` files below.
1170:
1171: .. code-block:: bash
1172:
1173:   sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20$ ls -lh *.onnx
1174:   -rw-r--r-- 1 kuangfangjun root  13M Mar 31 21:11 decoder-epoch-99-avg-1.int8.onnx
1175:   -rw-r--r-- 1 kuangfangjun root  14M Feb 20 20:13 decoder-epoch-99-avg-1.onnx
1176:   -rw-r--r-- 1 kuangfangjun root 174M Mar 31 21:11 encoder-epoch-99-avg-1.int8.onnx
1177:   -rw-r--r-- 1 kuangfangjun root 315M Feb 20 20:13 encoder-epoch-99-avg-1.onnx
1178:   -rw-r--r-- 1 kuangfangjun root 3.1M Mar 31 21:11 joiner-epoch-99-avg-1.int8.onnx
1179:   -rw-r--r-- 1 kuangfangjun root  13M Feb 20 20:13 joiner-epoch-99-avg-1.onnx
1180:
1181:
```

## S09 — sherpa__docs__source__onnx__pretrained_models__offline-paraformer__paraformer-models.rst

```text
1143: csukuangfj/sherpa-onnx-paraformer-zh-2023-03-28 (Chinese + English)
1144: -------------------------------------------------------------------
1145:
1146: .. note::
1147:
1148:    This model does not support timestamps. It is a bilingual model, supporting
1149:    both Chinese and English. (支持普通话、河南话、天津话、四川话等方言)
1150:
1151:
1152: This model is converted from
1153:
1154: `<https://www.modelscope.cn/models/damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-pytorch>`_
1155:
1156: The code for converting can be found at
1157:
1158: `<https://huggingface.co/csukuangfj/paraformer-onnxruntime-python-example/tree/main>`_
1159:
1160:
1161: In the following, we describe how to download it and use it with `sherpa-onnx`_.
1162:
1163: Download the model
1164: ~~~~~~~~~~~~~~~~~~
1165:
1166: Please use the following commands to download it.
1167:
1168: .. code-block:: bash
1169:
1170:   cd /path/to/sherpa-onnx
1171:   wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-paraformer-zh-2023-03-28.tar.bz2
1172:
1173:   tar xvf sherpa-onnx-paraformer-zh-2023-03-28.tar.bz2
1174:
1175: Please check that the file sizes of the pre-trained models are correct. See
1176: the file sizes of ``*.onnx`` files below.
1177:
1178: .. code-block:: bash
1179:
1180:   sherpa-onnx-paraformer-zh-2023-03-28$ ls -lh *.onnx
1181:   -rw-r--r-- 1 kuangfangjun root 214M Apr  1 07:28 model.int8.onnx
1182:   -rw-r--r-- 1 kuangfangjun root 824M Apr  1 07:28 model.onnx
1183:
```

## S10 — sherpa__docs__source__onnx__pretrained_models__online-paraformer__paraformer-models.rst

```text
1: Paraformer models
2: =================
3:
4: .. hint::
5:
6:    See :ref:`install_sherpa_onnx` to install `sherpa-onnx`_
7:    before you read this section.
8:
9: .. _sherpa_onnx_online_paraformer_bilingual_zh_en:
10:
11: csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en (Chinese + English)
12: -------------------------------------------------------------------------------
13:
14: .. note::
15:
16:    This model does not support timestamps. It is a bilingual model, supporting
17:    both Chinese and English. (支持普通话、河南话、天津话、四川话等方言)
18:
19: This model is converted from
20:
21: `<https://www.modelscope.cn/models/damo/speech_paraformer_asr_nat-zh-cn-16k-common-vocab8404-online/summary>`_
22:
23: The code for converting can be found at
24:
25: `<https://huggingface.co/csukuangfj/streaming-paraformer-zh>`_
26:
27:
28: In the following, we describe how to download it and use it with `sherpa-onnx`_.
29:
30: Download the model
31: ~~~~~~~~~~~~~~~~~~
32:
33: Please use the following commands to download it.
34:
35: .. code-block:: bash
36:
37:   cd /path/to/sherpa-onnx
38:
39:   wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-paraformer-bilingual-zh-en.tar.bz2
40:
41:   tar xvf sherpa-onnx-streaming-paraformer-bilingual-zh-en.tar.bz2
42:
43: Please check that the file sizes of the pre-trained models are correct. See
44: the file sizes of ``*.onnx`` files below.
```

## S11 — sherpa__docs__source__onnx__sense-voice__pretrained.rst

```text
1: Pre-trained Models
2: ==================
3:
4: This page describes how to download pre-trained `SenseVoice`_ models.
5:
6:
7: .. _sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17:
8:
9: sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17-int8 (Chinese, English, Japanese, Korean, Cantonese, 中英日韩粤语)
10: ------------------------------------------------------------------------------------------------------------------------
11:
12: .. hint::
13:
14:    This model supports punctuations if you set ``use_itn`` to 1.
15:
16: This model is converted from `<https://www.modelscope.cn/models/iic/SenseVoiceSmall>`_
17: using the script `export-onnx.py <https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/sense-voice/export-onnx.py>`_.
18:
19: It supports the following 5 languages:
20:
21:   - Chinese (Mandarin, 普通话)
22:   - Cantonese (粤语, 广东话)
23:   - English
24:   - Japanese
25:   - Korean
26:
27: The sections below show how to use it.
28:
29: .. hint::
30:
```

```text
100:   -rw-r--r--@ 1 fangjun  staff   175K 18 Jul  2024 zh.wav
101:
102: .. hint::
103:
104:    If you need the ``float32`` model file, please use::
105:
106:      wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2
107:      tar xvf sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2
108:      rm sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2
109:
110:      ls -lh sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17
111:
112:   It prints::
113:
114:     ls -lh sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/
115:     total 2299200
116:     -rwxr-xr-x@ 1 fangjun  staff   5.8K 18 Jul  2024 export-onnx.py
117:     -rw-r--r--@ 1 fangjun  staff    71B 18 Jul  2024 LICENSE
118:     -rw-r--r--@ 1 fangjun  staff   228M 18 Jul  2024 model.int8.onnx
119:     -rw-r--r--@ 1 fangjun  staff   894M 18 Jul  2024 model.onnx
120:     -rw-r--r--@ 1 fangjun  staff   104B 18 Jul  2024 README.md
121:     drwxr-xr-x@ 7 fangjun  staff   224B 18 Jul  2024 test_wavs
122:     -rw-r--r--@ 1 fangjun  staff   308K 18 Jul  2024 tokens.txt
```

## S12 — sherpa__docs__source__onnx__qwen3-asr__pretrained.rst

```text
1: Pre-trained Models
2: ==================
3:
4: This page describes how to download pre-trained `Qwen3-ASR`_ models.
5:
6: .. _sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25:
7:
8: sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25
9: --------------------------------------------------------------------
10:
11: This model is converted from `Qwen3-ASR`_
12: using scripts from `<https://github.com/Wasser1462/Qwen3-ASR-onnx>`_.
13:
14: It supports the following languages:
15:
16:   - Chinese (zh), English (en), Cantonese (yue), Arabic (ar), German (de)
17:   - French (fr), Spanish (es), Portuguese (pt), Indonesian (id)
18:   - Italian (it), Korean (ko), Russian (ru), Thai (th)
19:   - Vietnamese (vi), Japanese (ja), Turkish (tr), Hindi (hi)
20:   - Malay (ms), Dutch (nl), Swedish (sv), Danish (da), Finnish (fi)
21:   - Polish (pl), Czech (cs), Filipino (fil), Persian (fa), Greek (el)
22:   - Hungarian (hu), Macedonian (mk), Romanian (ro)
23:
24: It also supports the following Chinese dialects:
25:
26:   - Anhui, Dongbei, Fujian, Gansu, Guizhou, Hebei, Henan, Hubei, Hunan
27:   - Jiangxi, Ningxia, Shandong, Shaanxi, Shanxi, Sichuan, Tianjin, Yunnan
28:   - Zhejiang, Cantonese (Hong Kong accent), Cantonese (Guangdong accent)
29:   - Wu language, Minnan language.
30:
31: .. hint::
32:
33:    支持的语言有:
34:
35:     - 中文， 英语，粤语，阿拉伯语，德语，法语，西班牙语
36:     - 葡萄牙语，印尼语，意大利语，韩语，俄语，泰语，越南语
37:     - 日语，土耳其语，印地语，马来语，荷兰语，瑞典语，丹麦语
38:     - 芬兰语，波兰语，捷克语，菲律宾语，波斯语，希腊语，匈牙利语
39:     - 马其顿语，罗马尼亚语
40:
41:    支持的中文方言有:
42:
43:     - 安徽，东北，福建，甘肃，贵州，河北，河南，湖北，湖南，江西
44:     - 宁夏，山东，山西，陕西，四川，天津，云南，浙江，粤语（香港口音）
45:     - 粤语（广东口音）, 吴语, 闽南语
46:
47:
48:    此外还支持歌词识别和说唱语音识别。
49:
50:
51: The sections below show how to use it.
52:
53: Download
54: ^^^^^^^^
55:
56: Please use the following commands to download it::
57:
58:   cd /path/to/sherpa-onnx
59:
60:   wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2
61:   tar xvf sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2
62:   rm sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2
63:
64: After downloading, you should find the following files::
65:
66:   ls -lh sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25
67:
68:   total 1954552
69:   -rw-r--r--@  1 fangjun  staff    42M  7 Apr 17:45 conv_frontend.onnx
70:   -rw-r--r--@  1 fangjun  staff   721M  7 Apr 17:50 decoder.int8.onnx
71:   -rw-r--r--@  1 fangjun  staff   174M  7 Apr 17:50 encoder.int8.onnx
72:   -rw-r--r--@  1 fangjun  staff   328B  7 Apr 17:51 README.md
73:   drwxr-xr-x@ 19 fangjun  staff   608B  7 Apr 17:45 test_wavs
74:   drwxr-xr-x@  5 fangjun  staff   160B  7 Apr 17:51 tokenizer
75:
76: .. code-block::
77:
78:   ls -lh sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/tokenizer
79:
80:   total 8728
81:   -rw-r--r--@ 1 fangjun  staff   1.6M  7 Apr 17:50 merges.txt
82:   -rw-r--r--@ 1 fangjun  staff    12K  7 Apr 17:51 tokenizer_config.json
83:   -rw-r--r--@ 1 fangjun  staff   2.6M  7 Apr 17:51 vocab.json
84:
```

## S13 — sherpa-onnx__sherpa-onnx__csrc__offline-qwen3-asr-model.cc

```text
121: class OfflineQwen3ASRModel::Impl {
122:  public:
123:   explicit Impl(const OfflineModelConfig &config)
124:       : config_(config),
125:         env_(ORT_LOGGING_LEVEL_ERROR, "qwen3-asr"),
126:         sess_opts_conv_(GetSessionOptions(config)),
127:         sess_opts_encoder_(GetSessionOptions(config)),
128:         sess_opts_decoder_(GetSessionOptions(config)),
129:         allocator_(),
130:         cpu_mem_info_(
131:             Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeDefault)),
132:         is_cpu_provider_(config.provider == "cpu" || config.provider.empty()) {
133:     const auto &c = config_.qwen3_asr;
134:
135:     conv_sess_ = std::make_unique<Ort::Session>(
136:         env_, SHERPA_ONNX_TO_ORT_PATH(c.conv_frontend), sess_opts_conv_);
137:
138:     InitConvFrontend(nullptr, 0);
139:
140:     encoder_sess_ = std::make_unique<Ort::Session>(
141:         env_, SHERPA_ONNX_TO_ORT_PATH(c.encoder), sess_opts_encoder_);
142:
143:     InitEncoder(nullptr, 0);
144:
145:     decoder_sess_ = std::make_unique<Ort::Session>(
146:         env_, SHERPA_ONNX_TO_ORT_PATH(c.decoder), sess_opts_decoder_);
```

```text
349:       Ort::IoBinding binding(*conv_sess_);
350:       binding.BindInput(conv_input_names_ptr_[0], input_features);
351:       binding.BindOutput(conv_output_names_ptr_[0], *cuda_mem_info_);
352:       binding.SynchronizeInputs();
353:       conv_sess_->Run(Ort::RunOptions{nullptr}, binding);
354:       binding.SynchronizeOutputs();
355:       auto outs = binding.GetOutputValues();
356:       if (outs.empty()) {
```

```text
389:       binding.BindInput(encoder_input_names_ptr_[0], conv_output);
390:       binding.BindInput(encoder_input_names_ptr_[1], feature_attention_mask);
391:       binding.BindOutput(encoder_output_names_ptr_[0], cpu_mem_info_);
392:       binding.SynchronizeInputs();
393:       encoder_sess_->Run(Ort::RunOptions{nullptr}, binding);
394:       binding.SynchronizeOutputs();
395:       auto outs = binding.GetOutputValues();
396:       if (outs.empty()) {
```

```text
472:       for (size_t i = 1; i < decoder_output_names_ptr_.size(); ++i) {
473:         binding.BindOutput(decoder_output_names_ptr_[i], cpu_mem_info_);
474:       }
475:       binding.SynchronizeInputs();
476:       decoder_sess_->Run(Ort::RunOptions{nullptr}, binding);
477:       binding.SynchronizeOutputs();
478:       outputs = binding.GetOutputValues();
479:     } else {
```

## S14 — sherpa-onnx__LICENSE

```text
1:
2:                                  Apache License
3:                            Version 2.0, January 2004
4:                         http://www.apache.org/licenses/
5:
6:    TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION
7:
8:    1. Definitions.
9:
10:       "License" shall mean the terms and conditions for use, reproduction,
```

## S15 — sensevoice-github-README.md

```text
502: ## License
503:
504: - Source code in this repository is licensed under the [MIT License](./LICENSE).
505: - Model weights are distributed separately and follow the terms on each model card. The official [SenseVoiceSmall model card](https://huggingface.co/FunAudioLLM/SenseVoiceSmall) links to the [FunASR Model Open Source License Agreement](https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE); other artifacts and conversions may list different terms, so check their model cards before use.
506: - The maintainers have provided an [official SenseVoiceSmall license clarification](https://github.com/QwenAudio/SenseVoice/issues/334#issuecomment-5083546605) for the [FunASR Model Open Source License Agreement v1.1](https://github.com/modelscope/FunASR/blob/58830eca4012644aac0c3218c3ccc7d98f003fda/MODEL_LICENSE): Commercial use of the official SenseVoiceSmall weights is permitted when the model license is followed; Section 3 is a responsibility and risk disclaimer rather than an additional non-commercial restriction; and fine-tuned derivative weights may remain private. The Section 2.2 attribution and model-name requirements still apply. This clarification covers the official weights only, so check the terms for third-party conversions and bundled artifacts separately.
507:
```

## S16 — funasr-model-license

```text
1: FunASR Model Open Source License Agreement
2:
3: Version: 1.1
4:
5: Copyright (C) [2023-2028] [Alibaba Group]. All rights reserved.
6:
7: Thank you for choosing the FunASR open-source model. The FunASR open-source model includes a range of free and open industrial models for you to use, modify, share, and learn from.
8:
9: To ensure better community collaboration, we have established the following agreement, and we hope you will read and comply with its terms.
10: Definitions
11:
12: In this agreement, [FunASR Software] refers to FunASR open-source model weights and their derivatives, including finetuned models; [You] refers to individuals or organizations using, modifying, sharing, and learning from [FunASR Software].
13:
14: 2 License and Restrictions
15:
16: 2.1 License
17:
18: You are free to use, copy, modify, and share [FunASR Software] under the terms of this agreement.
19:
20: 2.2 Restrictions
21:
22: When using, copying, modifying, and sharing [FunASR Software], you must attribute the source and author information and retain relevant model names in [FunASR Software].
23:
24: 3 Responsibility and Risk
25:
26: [FunASR Software] is provided for reference and learning purposes only, and Alibaba Group assumes no responsibility for any direct or indirect losses resulting from your use or modification of [FunASR Software]. You should assume all risks associated with using and modifying [FunASR Software].
27:
28: 4 Community Conduct Guidelines
29:
```

## S17
- state: comment；updated: 2026-09-22T06:08:37Z

> **Status update (2026-09-22):** This is an earlier response, not final license-owner confirmation. As explained in the [reopening follow-up](https://github.com/QwenAudio/SenseVoice/issues/334#issuecomment-5463240506), this issue remains open pending license-owner/core-maintainer confirmation and an opportunity for the reporter to respond. The original response below is retained as discussion history and should not substitute for that confirmation.
>
> **状态更新（2026-09-22）：** 以下为早期回复，不是许可方的最终确认。按[重新打开 issue 时的说明](https://github.com/QwenAudio/SenseVoice/issues/334#issuecomment-5463240506)，本问题仍在等待许可方/核心维护者确认，并保留报告者反馈的机会。下方原回复作为讨论历史保留，不应替代该确认。

<!-- funasr-ops:license334-pending-confirmation-status-20260922 -->

---


Thanks for raising this precise question. The repository source code and the model weights use different licenses:

- Source c

## S18
- state: comment；updated: 2026-08-29T15:25:42Z

Reopening this because I answered and closed it immediately. A licensing question with production impact should remain open for core-maintainer confirmation and reporter acknowledgement; my earlier answer should be treated as context, not as a substitute for that confirmation. We will keep this issue open until the license owner/core maintainer confirms the interpretation and the reporter has had a chance to respond.

## S19
- state: open；updated: 2026-09-22T06:08:37Z

Hi SenseVoice team — thank you for the excellent work. SenseVoiceSmall's multilingual quality (zh/yue/ja/ko) and built-in LID are outstanding in our on-device benchmarks.

We are evaluating SenseVoiceSmall for a **commercial desktop dictation application** (fully on-device inference, with attribution as required by §2.2 of the MODEL_LICENSE).

The MODEL_LICENSE is clear in **§2.1** ("You are free to use, copy, modify, and share") and **§2.2** (attribution required). However, **§3 (Disclaimer)** contains the sentence:

> "...provided for reference and learning purposes only" （仅作为参考和学习使用）

Could

## S20
```json
{
  "Name": "speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-pytorch",
  "Organization": {
    "ApplyFailureReason": "",
    "ApplyReason": "",
    "Avatar": "https://resouces.modelscope.cn/avatar/86fc4b9c-4548-463c-8cbb-f37f68b87141.png",
    "CreateCompetition": false,
    "CreatedBy": "damoadmin",
    "Description": "[\"root\",{},[\"p\",{},[\"span\",{\"data-type\":\"text\"},[\"span\",{\"data-type\":\"leaf\"},\"通义实验室(Institute for Intelligent Computing, aka Tongyi Lab) 专注于各领域大模型技术研发与创新应用。实验室研究方向涵盖LLM、多模态理解与生成、视觉AIGC、语音等多个领域。我们并积极推进研究成果的产业化落地。实验室同时积极参与开源社区建设，全方位拥抱开源社区，共同探索AI模型的开源开放。\"]]]]",
    "DisplayUrl": "",
    "Email": "",
    "FromSite": "",
    "FullName": "通义实验室",
    "GithubAddress": "",
    "GmtCreated": "2022-08-03T17:01:55Z",
    "GmtModified": "2025-11-21T08:30:42Z",
    "Id": 1,
    "InitAdminMembers": "",
    "IsApply": false,
    "IsCertification": "",
    "Mobile": "",
    "Name": "iic",
    "Path": "",
    "Roles": null,
    "StarCnt": 0,
    "Status": 0,
    "SubscribeVo": null,
    "Type": 2
  },
  "License": "Apache License 2.0",
  "Revision": "master",
  "ModelRevisions": null
}
```

## S21
```json
{
  "Name": "SenseVoiceSmall",
  "Organization": {
    "ApplyFailureReason": "",
    "ApplyReason": "",
    "Avatar": "https://resouces.modelscope.cn/avatar/86fc4b9c-4548-463c-8cbb-f37f68b87141.png",
    "CreateCompetition": false,
    "CreatedBy": "damoadmin",
    "Description": "[\"root\",{},[\"p\",{},[\"span\",{\"data-type\":\"text\"},[\"span\",{\"data-type\":\"leaf\"},\"通义实验室(Institute for Intelligent Computing, aka Tongyi Lab) 专注于各领域大模型技术研发与创新应用。实验室研究方向涵盖LLM、多模态理解与生成、视觉AIGC、语音等多个领域。我们并积极推进研究成果的产业化落地。实验室同时积极参与开源社区建设，全方位拥抱开源社区，共同探索AI模型的开源开放。\"]]]]",
    "DisplayUrl": "",
    "Email": "",
    "FromSite": "",
    "FullName": "通义实验室",
    "GithubAddress": "",
    "GmtCreated": "2022-08-03T17:01:55Z",
    "GmtModified": "2025-11-21T08:30:42Z",
    "Id": 1,
    "InitAdminMembers": "",
    "IsApply": false,
    "IsCertification": "",
    "Mobile": "",
    "Name": "iic",
    "Path": "",
    "Roles": null,
    "StarCnt": 0,
    "Status": 0,
    "SubscribeVo": null,
    "Type": 2
  },
  "License": "Apache License 2.0",
  "Revision": "master",
  "ModelRevisions": null
}
```

## S22
```json
{
  "Name": "Qwen3-ASR-0.6B",
  "Organization": {
    "ApplyFailureReason": "",
    "ApplyReason": "",
    "Avatar": "https://resources.modelscope.cn/avatar/4c40e6ce-1348-43b5-a3bc-6bafec6ac805.jpg",
    "CreateCompetition": false,
    "CreatedBy": "Cherrytest",
    "Description": "[\"root\",{},[\"p\",{},[\"span\",{\"data-type\":\"text\"},[\"span\",{\"data-type\":\"leaf\"},\"欢迎来到 Qwen 👋\"]]],[\"p\",{},[\"span\",{\"data-type\":\"text\"},[\"span\",{\"data-type\":\"leaf\"},\"这是 Qwen 的组织，来自阿里的大模型家族。在这个组织中，我们不断发布大型语言模型 (LLM)、大型多模态模型 (LMM) 和其他 AGI 相关项目。快来查看并享受吧！\"]]]]",
    "DisplayUrl": "",
    "Email": "",
    "FromSite": "",
    "FullName": "千问",
    "GithubAddress": "https://github.com/QwenLM",
    "GmtCreated": "2023-08-02T03:48:45Z",
    "GmtModified": "2026-09-01T09:09:19Z",
    "Id": 209,
    "InitAdminMembers": "",
    "IsApply": false,
    "IsCertification": "",
    "Mobile": "",
    "Name": "Qwen",
    "Path": "",
    "Roles": null,
    "StarCnt": 0,
    "Status": 0,
    "SubscribeVo": null,
    "Type": 2
  },
  "License": "apache-2.0",
  "Revision": "master",
  "ModelRevisions": null
}
```

## S23 — qwen-github-LICENSE

```text
1:                                  Apache License
2:                            Version 2.0, January 2004
3:                         http://www.apache.org/licenses/
4:
5:    TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION
6:
7:    1. Definitions.
8:
9:       "License" shall mean the terms and conditions for use, reproduction,
```

## S24 — silero-github-LICENSE

```text
1: MIT License
2:
3: Copyright (c) 2020-present Silero Team
4:
5: Permission is hereby granted, free of charge, to any person obtaining a copy
6: of this software and associated documentation files (the "Software"), to deal
7: in the Software without restriction, including without limitation the rights
8: to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
9: copies of the Software, and to permit persons to whom the Software is
10: furnished to do so, subject to the following conditions:
11:
12: The above copyright notice and this permission notice shall be included in all
```

## S25 — sherpa-onnx__build-android-arm64-v8a.sh

```text
1: #!/usr/bin/env bash
2: set -ex
3:
4: # If BUILD_SHARED_LIBS is ON, we use libonnxruntime.so
5: # If BUILD_SHARED_LIBS is OFF, we use libonnxruntime.a
6: #
7: # In any case, we will have libsherpa-onnx-jni.so
8: #
9: # If BUILD_SHARED_LIBS is OFF, then libonnxruntime.a is linked into libsherpa-onnx-jni.so
10: # and you only need to copy libsherpa-onnx-jni.so to your Android projects.
11: #
12: # If BUILD_SHARED_LIBS is ON, then you need to copy both libsherpa-onnx-jni.so
13: # and libonnxruntime.so to your Android projects
```

```text
93: onnxruntime_version=${SHERPA_ONNX_ONNXRUNTIME_VERSION:-1.28.2}
94:
95: if [ -n "${SHERPA_ONNXRUNTIME_LIB_DIR:-}" ] && [ -n "${SHERPA_ONNXRUNTIME_INCLUDE_DIR:-}" ]; then
96:   echo "Using externally provided ONNX Runtime"
97: elif [ -n "${SHERPA_ONNX_ONNXRUNTIME_ROOT:-}" ] && [ "$BUILD_SHARED_LIBS" == ON ]; then
98:   export SHERPA_ONNXRUNTIME_LIB_DIR="$SHERPA_ONNX_ONNXRUNTIME_ROOT/jni/arm64-v8a/"
99:   export SHERPA_ONNXRUNTIME_INCLUDE_DIR="$SHERPA_ONNX_ONNXRUNTIME_ROOT/headers/"
100: elif [ "$BUILD_SHARED_LIBS" == ON ]; then
101:   if [ ! -f $onnxruntime_version/jni/arm64-v8a/libonnxruntime.so ]; then
102:     mkdir -p $onnxruntime_version
103:     pushd $onnxruntime_version
104:     wget -c -q https://github.com/csukuangfj/onnxruntime-libs/releases/download/v${onnxruntime_version}/onnxruntime-android-${onnxruntime_version}.zip
105:     unzip onnxruntime-android-${onnxruntime_version}.zip
106:     rm onnxruntime-android-${onnxruntime_version}.zip
107:     popd
108:   fi
109:
110:   export SHERPA_ONNXRUNTIME_LIB_DIR=$dir/$onnxruntime_version/jni/arm64-v8a/
111:   export SHERPA_ONNXRUNTIME_INCLUDE_DIR=$dir/$onnxruntime_version/headers/
112: else
113:   if [ ! -f ${onnxruntime_version}-static/lib/libonnxruntime.a ]; then
114:     wget -c -q https://github.com/csukuangfj/onnxruntime-libs/releases/download/v${onnxruntime_version}/onnxruntime-android-arm64-v8a-static_lib-${onnxruntime_version}.zip
115:     unzip onnxruntime-android-arm64-v8a-static_lib-${onnxruntime_version}.zip
116:     rm onnxruntime-android-arm64-v8a-static_lib-${onnxruntime_version}.zip
117:     mv onnxruntime-android-arm64-v8a-static_lib-${onnxruntime_version} ${onnxruntime_version}-static
118:   fi
119:
120:   export SHERPA_ONNXRUNTIME_LIB_DIR=$dir/$onnxruntime_version-static/lib/
```

```text
127: if [ -z $SHERPA_ONNX_ENABLE_RKNN ]; then
128:   SHERPA_ONNX_ENABLE_RKNN=OFF
129: fi
130:
131: if [ -z $SHERPA_ONNX_ENABLE_QNN ]; then
132:   SHERPA_ONNX_ENABLE_QNN=OFF
133: fi
```

```text
164: if [ -z $SHERPA_ONNX_ANDROID_PLATFORM ]; then
165:   SHERPA_ONNX_ANDROID_PLATFORM=android-21
166: fi
167:
168: if [ -z $SHERPA_ONNX_ENABLE_JNI ]; then
169:   SHERPA_ONNX_ENABLE_JNI=ON
170: fi
171:
172: cmake -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
173:     -DSHERPA_ONNX_ENABLE_TTS=$SHERPA_ONNX_ENABLE_TTS \
174:     -DSHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=$SHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION \
175:     -DSHERPA_ONNX_ENABLE_BINARY=$SHERPA_ONNX_ENABLE_BINARY \
176:     -DBUILD_PIPER_PHONMIZE_EXE=OFF \
177:     -DBUILD_PIPER_PHONMIZE_TESTS=OFF \
178:     -DBUILD_ESPEAK_NG_EXE=OFF \
179:     -DBUILD_ESPEAK_NG_TESTS=OFF \
180:     -DCMAKE_BUILD_TYPE=Release \
181:     -DBUILD_SHARED_LIBS=$BUILD_SHARED_LIBS \
182:     -DSHERPA_ONNX_ENABLE_PYTHON=OFF \
183:     -DSHERPA_ONNX_ENABLE_TESTS=OFF \
184:     -DSHERPA_ONNX_ENABLE_CHECK=OFF \
185:     -DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF \
186:     -DSHERPA_ONNX_ENABLE_JNI=$SHERPA_ONNX_ENABLE_JNI \
187:     -DSHERPA_ONNX_LINK_LIBSTDCPP_STATICALLY=OFF \
188:     -DSHERPA_ONNX_ENABLE_C_API=$SHERPA_ONNX_ENABLE_C_API \
189:     -DCMAKE_INSTALL_PREFIX=./install \
190:     -DSHERPA_ONNX_ENABLE_RKNN=$SHERPA_ONNX_ENABLE_RKNN \
191:     -DSHERPA_ONNX_ENABLE_QNN=$SHERPA_ONNX_ENABLE_QNN \
192:     -DANDROID_ABI="arm64-v8a" \
193:     -DANDROID_PLATFORM=$SHERPA_ONNX_ANDROID_PLATFORM ..
194:
195:     # By default, it links to libc++_static.a
196:     # -DANDROID_STL=c++_shared \
197:
198: # Please use -DANDROID_PLATFORM=android-27 if you want to use Android NNAPI
```

## S26 — sherpa-onnx__android__SherpaOnnxAar__sherpa_onnx__build.gradle.kts

```text
1: plugins {
2:     alias(libs.plugins.android.library)
3:     alias(libs.plugins.jetbrains.kotlin.android)
4: }
5:
6: android {
7:     namespace = "com.k2fsa.sherpa.onnx"
8:     compileSdk = 34
9:
10:     defaultConfig {
11:         minSdk = 21
12:
13:         testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
14:         consumerProguardFiles("consumer-rules.pro")
15:     }
16:
17:     buildTypes {
18:         release {
19:             isMinifyEnabled = false
20:             proguardFiles(
21:                 getDefaultProguardFile("proguard-android-optimize.txt"),
22:                 "proguard-rules.pro"
23:             )
24:         }
25:     }
26:     compileOptions {
27:         sourceCompatibility = JavaVersion.VERSION_1_8
28:         targetCompatibility = JavaVersion.VERSION_1_8
29:     }
30:     kotlinOptions {
31:         jvmTarget = "1.8"
32:     }
33: }
```

## S27 — sherpa-onnx__android__SherpaOnnxAar__gradle__libs.versions.toml

```text
1: [versions]
2: agp = "8.4.0"
3: kotlin = "1.7.20"
4: coreKtx = "1.15.0"
5: junit = "4.13.2"
6: junitVersion = "1.2.1"
7: espressoCore = "3.6.1"
8: appcompat = "1.7.0"
9: material = "1.12.0"
10:
11: [libraries]
12: androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
13: junit = { group = "junit", name = "junit", version.ref = "junit" }
```

## S28 — sherpa-onnx__.github__workflows__android.yaml

```text
60:           export SHERPA_ONNX_ENABLE_C_API=ON
61:           ./build-android-arm64-v8a.sh
62:
63:           readelf -l ./build-android-arm64-v8a/install/lib/*.so
64:
65:           mkdir -p jniLibs/arm64-v8a/
66:           cp -v ./build-android-arm64-v8a/install/lib/*.so ./jniLibs/arm64-v8a/
67:           cp -v ./build-android-arm64-v8a/install/lib/README.md ./jniLibs/arm64-v8a/
68:           rm -rf  ./build-android-arm64-v8a/
```

```text
230:         run: |
231:           ls -lh /tmp/jniLibs
232:
233:           # drwxr-xr-x 2 runner docker 4.0K Dec 12 06:56 arm64-v8a
234:           # drwxr-xr-x 2 runner docker 4.0K Dec 12 06:56 armeabi-v7a
235:           # drwxr-xr-x 2 runner docker 4.0K Dec 12 06:56 x86
236:           # drwxr-xr-x 2 runner docker 4.0K Dec 12 06:56 x86_64
237:           #
238:       - name: Copy libs
239:         shell: bash
240:         run: |
241:           for arch in arm64-v8a armeabi-v7a x86 x86_64; do
242:             cp -v /tmp/jniLibs/$arch/* android/SherpaOnnxAar/sherpa_onnx/src/main/jniLibs/$arch/
243:           done
244:
245:       - name: Check libs
246:         shell: bash
```

```text
314:       - name: Release android aar
315:         if: github.repository_owner == 'csukuangfj' && github.event_name == 'push' && contains(github.ref, 'refs/tags/')
316:         uses: svenstaro/upload-release-action@v2
317:         with:
318:           file_glob: true
319:           overwrite: true
320:           file: ./*.aar
321:           # repo_name: k2-fsa/sherpa-onnx
322:           # repo_token: ${{ secrets.UPLOAD_GH_SHERPA_ONNX_TOKEN }}
```

## S29
```json
{
  "tag": "v1.13.8",
  "published_at": "2026-09-10T14:00:23Z",
  "assets_metadata_not_downloaded": [
    {
      "name": "sherpa-onnx-1.13.8-rknn.aar",
      "size": 26598297,
      "digest": "sha256:33489f8d039e8f29907a0abeeacd3ddc006a78335799928855ce5f98280fc876",
      "browser_download_url": "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8-rknn.aar"
    },
    {
      "name": "sherpa-onnx-1.13.8.aar",
      "size": 50129134,
      "digest": "sha256:633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96",
      "browser_download_url": "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar"
    },
    {
      "name": "sherpa-onnx-static-link-onnxruntime-1.13.8.aar",
      "size": 38691998,
      "digest": "sha256:b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471",
      "browser_download_url": "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar"
    }
  ]
}
```

## S30 — sherpa-onnx__CMakeLists.txt

```text
74: option(SHERPA_ONNX_USE_PRE_INSTALLED_ONNXRUNTIME_IF_AVAILABLE "True to use pre-installed onnxruntime if available" ON)
75: option(SHERPA_ONNX_ENABLE_SANITIZER "Whether to enable ubsan and asan" OFF)
76: option(SHERPA_ONNX_BUILD_C_API_EXAMPLES "Whether to enable C API examples" ${SUGGEST_BUILD_BINARIES})
77: option(SHERPA_ONNX_ENABLE_RKNN "Whether to build for RKNN NPU " OFF)
78: option(SHERPA_ONNX_ENABLE_AXERA "Whether to build for Axera NPU " OFF)
79: option(SHERPA_ONNX_ENABLE_AXCL "Whether to build for Axcl NPU " OFF)
80: option(SHERPA_ONNX_ENABLE_ASCEND_NPU "Whether to build for Ascend NPU " OFF)
81: option(SHERPA_ONNX_ENABLE_QNN "Whether to build for Qualcomm NPU" OFF)
82: option(SHERPA_ONNX_ENABLE_SPACEMIT "Whether to build for SpacemiT CPUs " OFF)
83: set(SHERPA_ONNX_LINUX_ARM64_GPU_ONNXRUNTIME_VERSION "1.11.0" CACHE STRING "Used only for Linux ARM64 GPU. Set to 1.11.0 if you use CUDA 10.2 and cudnn8. Set it to 1.16.0 if you use CUDA 11.4 and cudnn8. Set it to 1.18.0 if you use CUDA 12.2 and cudnn8. Set it to 1.18.1 if you use CUDA 12.6 and cudnn9")
```

```text
204: if(ANDROID)
205:   # see https://github.com/microsoft/onnxruntime/pull/22076
206:   # https://github.com/k2-fsa/sherpa-onnx/issues/2413
207:   set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
208: endif()
```

```text
390: if(SHERPA_ONNX_ENABLE_QNN)
391:   add_definitions(-DSHERPA_ONNX_ENABLE_QNN=1)
392: endif()
393:
394: if(SHERPA_ONNX_ENABLE_SPACEMIT)
395:   add_definitions(-DSHERPA_ONNX_ENABLE_SPACEMIT=1)
396: endif()
397:
```

## S31 — sherpa-onnx__sherpa-onnx__csrc__provider.cc

```text
15: Provider StringToProvider(std::string s) {
16:   std::transform(s.cbegin(), s.cend(), s.begin(),
17:                  [](unsigned char c) { return std::tolower(c); });
18:   if (s == "cpu") {
19:     return Provider::kCPU;
20:   } else if (s == "cuda") {
21:     return Provider::kCUDA;
22:   } else if (s == "coreml") {
23:     return Provider::kCoreML;
24:   } else if (s == "xnnpack") {
25:     return Provider::kXnnpack;
26:   } else if (s == "nnapi") {
27:     return Provider::kNNAPI;
28:   } else if (s == "trt") {
29:     return Provider::kTRT;
30:   } else if (s == "directml") {
31:     return Provider::kDirectML;
32:   } else if (s == "spacemit") {
33:     return Provider::kSpacemiT;
34:   } else if (s == "openvino") {
35:     return Provider::kOpenVINO;
36:   } else {
37:     SHERPA_ONNX_LOGE("Unsupported string: %s. Fallback to cpu", s.c_str());
38:     return Provider::kCPU;
39:   }
40: }
```

## S32 — sherpa-onnx__sherpa-onnx__csrc__session.cc

```text
140:     int32_t num_threads, const std::string &provider_str,
141:     const ProviderConfig *provider_config /*= nullptr*/) {
142:   std::unordered_map<std::string, std::string> config;
143:   std::string new_provider_str;
144:
145:   SplitProviderAndConfig(provider_str, new_provider_str, config);
146:
147:   Provider p = StringToProvider(new_provider_str);
148:
149:   Ort::SessionOptions sess_opts;
150:   sess_opts.SetIntraOpNumThreads(num_threads);
151:
152:   sess_opts.SetInterOpNumThreads(num_threads);
153:
154:   std::vector<std::string> available_providers = Ort::GetAvailableProviders();
155:   std::ostringstream os;
156:   for (const auto &ep : available_providers) {
157:     os << ep << ", ";
158:   }
```

```text
241:     case Provider::kXnnpack: {
242: #if ORT_API_VERSION >= 12
243:       if (std::find(available_providers.begin(), available_providers.end(),
244:                     "XnnpackExecutionProvider") != available_providers.end()) {
245:         sess_opts.AppendExecutionProvider("XNNPACK");
246:       } else {
247:         SHERPA_ONNX_LOGE("Available providers: %s. Fallback to cpu!",
248:                          os.str().c_str());
249:       }
250: #else
251:       SHERPA_ONNX_LOGE(
252:           "Does not support xnnpack for onnxruntime: %d. Fallback to cpu!",
253:           static_cast<int32_t>(ORT_API_VERSION));
254: #endif
255:       break;
256:     }
```

```text
389:     case Provider::kNNAPI: {
390: #if __ANDROID_API__ >= 27
391:       SHERPA_ONNX_LOGE("Current API level %d ", (int32_t)__ANDROID_API__);
392:
393:       // Please see
394:       // https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html#usage
395:       // to enable different flags
396:       uint32_t nnapi_flags = 0;
397:       // nnapi_flags |= NNAPI_FLAG_USE_FP16;
398:       // nnapi_flags |= NNAPI_FLAG_CPU_DISABLED;
399:       OrtStatus *status = OrtSessionOptionsAppendExecutionProvider_Nnapi(
400:           sess_opts, nnapi_flags);
401:
402:       if (status) {
403:         const auto &api = Ort::GetApi();
404:         const char *msg = api.GetErrorMessage(status);
405:         SHERPA_ONNX_LOGE(
406:             "Failed to enable NNAPI: %s. Available providers: %s. Fallback to "
407:             "cpu",
408:             msg, os.str().c_str());
409:         api.ReleaseStatus(status);
410:       } else {
411:         SHERPA_ONNX_LOGE("Use nnapi");
412:       }
413: #elif defined(__ANDROID_API__)
414:       SHERPA_ONNX_LOGE(
415:           "Android NNAPI requires API level >= 27. Current API level %d "
416:           "Fallback to cpu!",
417:           (int32_t)__ANDROID_API__);
418: #else
419:       SHERPA_ONNX_LOGE("NNAPI is for Android only. Fallback to cpu");
420: #endif
421:       break;
422:     }
```

## S33 — 官方页面标题/文本定位
- 文本行 178: The NNAPI Execution Provider (EP) requires Android devices with Android 8.1 or higher. It is recommended to use Android devices with Android 9 or higher to achieve optimal performance.
- 文本行 180: Pre-built packages of ONNX Runtime with NNAPI EP for Android are published on Maven.
- 文本行 205: NNAPI_FLAG_CPU_DISABLED
- 文本行 227: ai.onnx:DequantizeLinear All quantization scales and zero points should be constant.
- 文本行 250: ai.onnx:QLinearConv Only 2D Conv is supported.Weights and bias should be constant.All quantization scales and zero points should be constant.
- 文本行 251: ai.onnx:QLinearMatMul All quantization scales and zero points should be constant.
- 文本行 252: ai.onnx:QuantizeLinear All quantization scales and zero points should be constant.
- 文本行 268: com.microsoft:QLinearAdd All quantization scales and zero points should be constant.
- 文本行 269: com.microsoft:QLinearAveragePool Only 2D Pool is supported.All quantization scales and zero points should be constant.
- 文本行 270: com.microsoft:QLinearSigmoid All quantization scales and zero points should be constant.

## S34 — 官方页面标题/文本定位
- 文本行 188: so.AppendExecutionProvider("XNNPACK", {"intra_op_num_threads", std::to_string(intra_op_num_threads)});
- 文本行 192: XNNPACK has a separate internal threadpool which can lead to contention with the ORT intra-op threadpool. To minimize this, we recommend setting the following options:
- 文本行 193: Disable the ORT intra-op thread-pool spinning by adding the following to the session options:
- 文本行 195: Set the XNNPACK intra-op thread-pool size when registering the XNNPACK EP. The suggested value would be the number of physical cores on the device.
- 文本行 196: so.AppendExecutionProvider("XNNPACK", {"intra_op_num_threads", std::to_string(intra_op_num_threads)});
- 文本行 197: Set the ORT intra-op thread-pool size to 1:
- 文本行 199: This configuration will work well if your model is using XNNPACK for the nodes performing the compute-intensive work, as these operators are likely to use the intra-op threadpool. e.g. Conv, Gemm, MatMul operators.
- 文本行 200: If your model contains compute-intensive nodes using operators that are not currently supported by the XNNPACK EP these will be handled by the CPU EP. In that case better performance may be achieved by increasing the size of the ORT intra-op threadpool and potentially re-enabling spinning. Performance testing is the best way to determine the optimal configuration for your model.
- 文本行 202: intra_op_num_threads
- 文本行 203: The number of threads to use for the XNNPACK EP’s internal intra-op thread-pool. This is the number of threads used to parallelize the execution within a node. The default value is 1. The value should be >= 1.
- 文本行 207: ai.onnx:AveragePool Only 2D Pool is supported.
- 文本行 208: ai.onnx:Conv Only 2D Conv is supported.Weights and bias should be constant.

## S35 — 官方页面标题/文本定位
- 文本行 169: The QNN Execution Provider for ONNX Runtime enables hardware accelerated execution on Qualcomm chipsets. It uses the Qualcomm AI Engine Direct SDK (QNN SDK) to construct a QNN graph from an ONNX model which can be executed by a supported accelerator backend library. OnnxRuntime QNN Execution Provider can be used on Android and Windows devices with Qualcomm Snapdragon SOC’s.
- 文本行 209: ONNX Runtime QNN Execution Provider has been built and tested with QNN 2.22.x and Qualcomm SC8280, SM8350, Snapdragon X SOC’s on Android and Windows
- 文本行 230: ‘cpu’ Enable CPU backend. Useful for integration testing. The CPU backend is a reference implementation of QNN operators.
- 文本行 231: ‘gpu’ Enable GPU backend.
- 文本行 232: ‘htp’ Enable HTP backend. Offloads compute to NPU. Default.
- 文本行 235: ‘libQnnCpu.so’ or ‘QnnCpu.dll’ Enable CPU backend. See backend_type ‘cpu’.
- 文本行 236: ‘libQnnHtp.so’ or ‘QnnHtp.dll’ Enable HTP backend. See backend_type ‘htp’.
- 文本行 237: ‘libQnnGpu.so’ or ‘QnnGpu.dll’ Enable GPU backend. See backend_type ‘gpu’.
- 文本行 375: The QNN HTP backend only supports quantized models. Models with 32-bit floating-point activations and weights must first be quantized to use a lower integer precision (e.g., 8-bit or 16-bit integers).
- 文本行 378: QNN EP does not support models with dynamic shapes (e.g., a dynamic batch size). Dynamic shapes must be fixed to a specific value. Refer to the documentation for making dynamic input shapes fixed for more information.
- 文本行 384: Quantization for QNN EP requires the use of calibration input data. Using a calibration dataset that is representative of typical model inputs is crucial in generating an accurate quantized model.

## S36 — sherpa-onnx__sherpa-onnx__csrc__offline-recognizer-impl.cc

```text
189:   if (config.model_config.provider == "qnn") {
190: #if SHERPA_ONNX_ENABLE_QNN
191:     if (!config.model_config.sense_voice.model.empty() ||
192:         !config.model_config.sense_voice.qnn_config.context_binary.empty()) {
193:       return std::make_unique<
194:           OfflineRecognizerSenseVoiceTplImpl<OfflineSenseVoiceModelQnn>>(
195:           config);
196:     } else if (IsQnnTransducerArtifact(config.model_config.transducer) &&
197:                config.model_config.model_type == "nemo_transducer") {
198:       return std::make_unique<OfflineRecognizerParakeetTdtQnnImpl>(config);
199:     } else if (IsQnnTransducerArtifact(config.model_config.transducer)) {
200:       return std::make_unique<OfflineRecognizerTransducerQnnImpl>(config);
201:     } else if (!config.model_config.zipformer_ctc.model.empty() ||
202:                !config.model_config.zipformer_ctc.qnn_config.context_binary
203:                     .empty()) {
204:       return std::make_unique<OfflineRecognizerZipformerCtcQnnImpl>(config);
205:     } else if (!config.model_config.paraformer.model.empty() ||
206:                !config.model_config.paraformer.qnn_config.context_binary
207:                     .empty()) {
208:       return std::make_unique<
209:           OfflineRecognizerParaformerTplImpl<OfflineParaformerModelQnn>>(
210:           config);
211:     } else if (!config.model_config.nemo_ctc.model.empty() ||
212:                !config.model_config.nemo_ctc.qnn_config.context_binary
213:                     .empty()) {
214:       return std::make_unique<OfflineRecognizerParakeetCtcQnnImpl>(config);
215:     } else if (!config.model_config.whisper.encoder.empty() ||
216:                !config.model_config.whisper.qnn_config.context_binary.empty()) {
217:       return std::make_unique<
218:           OfflineRecognizerWhisperTplImpl<OfflineWhisperModelQnn>>(config);
219:     } else if (!config.model_config.moonshine.encoder.empty() ||
220:                !config.model_config.moonshine.decoder.empty() ||
221:                !config.model_config.moonshine.qnn_config.context_binary.empty()) {
222:       return std::make_unique<OfflineRecognizerMoonshineQnnImpl>(config);
223:     } else {
224:       SHERPA_ONNX_LOGE(
225:           "Only SenseVoice, Paraformer, Zipformer transducer, Zipformer CTC, "
226:           "NeMo CTC (Parakeet), Parakeet TDT, Whisper, and Moonshine models "
227:           "are currently supported by QNN for non-streaming ASR.");
228:       SHERPA_ONNX_EXIT(-1);
229:       return nullptr;
230:     }
231: #else
232:     SHERPA_ONNX_LOGE(
233:         "Please rebuild sherpa-onnx with -DSHERPA_ONNX_ENABLE_QNN=ON if "
234:         "you want to use qnn. See also "
235:         "https://k2-fsa.github.io/sherpa/onnx/qnn/build.html");
236:     SHERPA_ONNX_EXIT(-1);
237:     return nullptr;
238: #endif
239:   }
240:
```

## S37 — sherpa-onnx__sherpa-onnx__csrc__online-recognizer-impl.cc

```text
84:   if (config.model_config.provider_config.provider == "qnn") {
85: #ifdef SHERPA_ONNX_ENABLE_QNN
86:     if (HasQnnOnlineTransducerModel(config.model_config)) {
87:       if (config.model_config.model_type == "nemo_transducer") {
88:         return std::make_unique<OnlineRecognizerNemoTransducerQnnImpl>(config);
89:       }
90:       return std::make_unique<OnlineRecognizerZipformerTransducerQnnImpl>(
91:           config);
92:     }
93:
94:     SHERPA_ONNX_LOGE(
95:         "Only Zipformer transducers and nemo_transducer are currently "
96:         "supported by qnn for online recognition.");
97:     SHERPA_ONNX_EXIT(-1);
98: #else
99:     SHERPA_ONNX_LOGE(
100:         "Please rebuild sherpa-onnx with -DSHERPA_ONNX_ENABLE_QNN=ON if you "
101:         "want to use qnn.");
102:     SHERPA_ONNX_EXIT(-1);
103:     return nullptr;
104: #endif
105:   }
```

## S38 — sherpa-onnx__sherpa-onnx__csrc__qnn__qnn-model.cc

```text
473:   bool Run() {
474:     std::vector<Qnn_Tensor_t> input_tensors_raw;
475:     std::vector<Qnn_Tensor_t> output_tensors_raw;
476:
477:     input_tensors_raw.reserve(input_tensors_.size());
478:     output_tensors_raw.reserve(output_tensors_.size());
479:
480:     for (const auto &p : input_tensors_) {
481:       input_tensors_raw.push_back(*p);
482:     }
483:
484:     for (const auto &p : output_tensors_) {
485:       output_tensors_raw.push_back(*p);
486:     }
487:
488:     auto ret = backend_->QnnInterface().graphExecute(
489:         graph_handle_, input_tensors_raw.data(), input_tensors_raw.size(),
490:         output_tensors_raw.data(), output_tensors_raw.size(), nullptr, nullptr);
491:     SHERPA_ONNX_QNN_CHECK(ret, "Failed to run graphExecute");
492:
493:     return true;
```

## S39 — sherpa__docs__source__onnx__qnn__build-android-demo.rst

```text
40: 2. Copy QNN libs
41: ::::::::::::::::
42:
43: .. code-block::
44:
45:   cd android/SherpaOnnxSimulateStreamingAsr/app/src/main/jniLibs/arm64-v8a
46:
47:   cp -v $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtp.so ./
48:   cp -v $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtpPrepare.so ./
49:   cp -v $QNN_SDK_ROOT/lib/aarch64-android/libQnnSystem.so ./
50:
51:   cp -v $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtpV68Stub.so ./
52:   cp -v $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtpV69Stub.so ./
53:   cp -v $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtpV73Stub.so ./
54:   cp -v $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtpV75Stub.so ./
55:   cp -v $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtpV79Stub.so ./
56:   cp -v $QNN_SDK_ROOT/lib/aarch64-android/libQnnHtpV81Stub.so ./
57:
58:   cp -v $QNN_SDK_ROOT/lib/hexagon-v68/unsigned/libQnnHtpV68Skel.so ./
59:   cp -v $QNN_SDK_ROOT/lib/hexagon-v69/unsigned/libQnnHtpV69Skel.so ./
60:   cp -v $QNN_SDK_ROOT/lib/hexagon-v73/unsigned/libQnnHtpV73Skel.so ./
61:   cp -v $QNN_SDK_ROOT/lib/hexagon-v75/unsigned/libQnnHtpV75Skel.so ./
62:   cp -v $QNN_SDK_ROOT/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so ./
63:   cp -v $QNN_SDK_ROOT/lib/hexagon-v81/unsigned/libQnnHtpV81Skel.so ./
64:
65: .. hint::
66:
67:    To make things easier, we have copied many unused Stub and Skel libraries. For a given
68:    device, you only need one Stub and one Skel library.
69:
70:    For instance, if you are using Xiaomi 17 Pro, you only need to copy ``libQnnHtpV81Stub.so``
71:    and ``libQnnHtpV81Skel.so`` to your phone.
72:
73: In the end, you should have the following files inside
```

```text
139: Since we have selected ``sherpa-onnx-qnn-10-seconds-sense-voice-zh-en-ja-ko-yue-2024-07-17-int8-android-aarch64``, which corresponds to
140: the model type 9002 in the file
141:
142:   `<https://github.com/k2-fsa/sherpa-onnx/blob/16d62b6a08f617c2bd6d21d411911c6462607f08/sherpa-onnx/kotlin-api/OfflineRecognizer.kt#L790>`_
143:
144: .. code-block:: kotlin
145:
146:         9002 -> {
147:             val modelDir = "sherpa-onnx-qnn-10-seconds-sense-voice-zh-en-ja-ko-yue-2024-07-17-int8-android-aarch64"
148:             return OfflineModelConfig(
149:                 provider = "qnn",
150:                 senseVoice = OfflineSenseVoiceModelConfig(
151:                     model = "$modelDir/libmodel.so",
152:                     qnnConfig = QnnConfig(
153:                         backendLib = "libQnnHtp.so",
154:                         systemLib = "libQnnSystem.so",
155:                         contextBinary = "$modelDir/model.bin",
156:                     ),
157:                 ),
158:                 tokens = "$modelDir/tokens.txt",
159:             )
```

## S40 — sherpa__docs__source__onnx__qnn__download-qnn.rst

```text
17:    :width: 600
18:
19: We’re using version 2.40.0.251030, but you can choose whichever version you prefer.
20:
21: After selecting the version to download, just click the button ``Download``, or use
22: the following URL to download it::
23:
24:   https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.40.0.251030/v2.40.0.251030.zip
```

```text
62: QNN_SDK_ROOT
63: ------------
64:
65: Now you can run::
66:
67:   export QNN_SDK_ROOT=$HOME/qnn/qairt/2.40.0.251030
```

## S41 — sherpa-onnx__sherpa-onnx__csrc__qnn-config.cc

```text
17: void QnnConfig::Register(ParseOptions *po) {
18:   po->Register("qnn-backend-lib", &backend_lib,
19:                "Path to libQnnHtp.so "
20:                "Used only when provider is qnn."
21:                "Leave it empty if you don't use qnn");
22:
23:   po->Register(
24:       "qnn-context-binary", &context_binary,
25:       "Path to model.bin. Used only when provider is qnn. "
26:       "If it exists, libmodel.so is ignored. "
27:       "For models with multiple QNN components, you can provide multiple "
28:       "context binaries separated by commas. "
29:       "If a specified file does not exist, a context binary is saved there so "
30:       "that it is loaded the next time you run it. You can leave it empty if "
31:       "you don't use qnn");
32:
33:   po->Register("qnn-system-lib", &system_lib,
34:                "Required and used only when --qnn-context-binary is not empty "
35:                "and exists. You can leave it empty if you don't use qnn.");
36: }
37:
38: bool QnnConfig::Validate() const {
```

## S42 — sherpa-onnx__sherpa-onnx__csrc__macros.h

```text
22: #if __ANDROID_API__ >= 8
23: #include "android/log.h"
24: #define SHERPA_ONNX_LOGE(...)                                                  \
25:   do {                                                                         \
26:     fprintf(stderr, "%s:%s:%d ", __FILE__, __func__,                           \
27:             static_cast<int32_t>(__LINE__));                                   \
28:     fprintf(stderr, ##__VA_ARGS__);                                            \
29:     fprintf(stderr, "\n");                                                     \
30:     __android_log_print(ANDROID_LOG_WARN, "sherpa-onnx", "%s:%s:%d", __FILE__, \
31:                         __func__, static_cast<int32_t>(__LINE__));             \
32:     __android_log_print(ANDROID_LOG_WARN, "sherpa-onnx", ##__VA_ARGS__);       \
33:   } while (0)
34: #elif defined(__OHOS__)
35: #define SHERPA_ONNX_LOGE(...) OH_LOG_INFO(LOG_APP, ##__VA_ARGS__)
```

```text
54: #define SHERPA_ONNX_EXIT(code) \
55:   do {                         \
56:     fflush(stdout);            \
57:     fflush(stderr);            \
58:     _Exit(code);               \
59:   } while (0)
```

## S43 — sherpa-onnx__sherpa-onnx__csrc__qnn__macros.h

```text
7:
8: #include "sherpa-onnx/csrc/macros.h"
9:
10: #define SHERPA_ONNX_QNN_CHECK(ret, msg, ...)                             \
11:   do {                                                                   \
12:     if (ret != QNN_SUCCESS) {                                            \
13:       SHERPA_ONNX_LOGE("Return code is: %d", static_cast<int32_t>(ret)); \
14:       SHERPA_ONNX_LOGE(msg, ##__VA_ARGS__);                              \
15:       SHERPA_ONNX_EXIT(-1);                                              \
16:     }                                                                    \
17:   } while (0)
18:
19: #endif  // SHERPA_ONNX_CSRC_QNN_MACROS_H_
```

## S44 — sherpa__docs__source__onnx__rknn__install.rst

```text
53:
54:   strings /lib/librknnrt.so | grep "librknnrt version"
55:
56: It should print something like below::
57:
58:   librknnrt version: 2.2.0 (c195366594@2024-09-14T12:18:56)
59:
60: ``librknnrt.so`` ``2.2.0`` is known to work on rk3588.
61:
62: You can download ``librknnrt.so`` ``2.2.0`` from:
63:
64:   `<https://github.com/airockchip/rknn-toolkit2/blob/v2.2.0/rknpu2/runtime/Linux/librknn_api/aarch64/librknnrt.so>`_
```

## S45 — sherpa__docs__source__onnx__ascend__install.rst

```text
1: Install
2: =======
3:
4: You need to build `sherpa-onnx`_ from source for Ascend NPU.
5:
6: Install CANN
7: -------------
8:
9: Before you continue, make sure you have installed ``CANN``. The following
10: is an example to check that.
11:
12: .. image:: ./pic/cann-env.png
13:    :align: center
14:    :alt: screenshot of checking CANN
15:    :width: 600
16:
17: Build sherpa-onnx
18: ------------------
19:
20: .. code-block:: bash
21:
22:   source /usr/local/Ascend/ascend-toolkit/set_env.sh
23:
24:   git clone https://github.com/k2-fsa/sherpa-onnx
25:   cd sherpa-onnx
26:   mkdir build
27:   cd build
28:
29:   cmake \
30:     -DSHERPA_ONNX_ENABLE_ASCEND_NPU=ON \
31:     -DBUILD_SHARED_LIBS=ON \
32:     ..
33:
34:   make
35:
```

## S46 — 标题定位
- 页面分别有 Check the alignment of ELF segments、Check ZIP alignment、Build your app with support for 16 KB devices、Test your app in a 16 KB environment；不能以一个链接参数代替全依赖/运行验收。

## S47 — sherpa-onnx__sherpa-onnx__jni__offline-recognizer.cc

```text
537:       SHERPA_ONNX_LOGE("%s", s.c_str());
538:     }
539: #else
540:     SHERPA_ONNX_LOGE("%s", config.ToString().c_str());
541: #endif
542:   }
543:
544:   if (!config.Validate()) {
545:     SHERPA_ONNX_LOGE("Errors found in config!");
546:     return 0;
547:   }
548:
549:   auto model = new sherpa_onnx::OfflineRecognizer(config);
550:
551:   return (jlong)model;
552: }
```

```text
574: JNIEXPORT void JNICALL Java_com_k2fsa_sherpa_onnx_OfflineRecognizer_delete(
575:     JNIEnv * /*env*/, jobject /*obj*/, jlong ptr) {
576:   delete reinterpret_cast<sherpa_onnx::OfflineRecognizer *>(ptr);
577: }
```

```text
620: JNIEXPORT void JNICALL Java_com_k2fsa_sherpa_onnx_OfflineRecognizer_decode(
621:     JNIEnv *env, jobject /*obj*/, jlong ptr, jlong stream_ptr) {
622:   SafeJNI(env, "OfflineRecognizer_decode", [&] {
623:     if (!ValidatePointer(env, ptr, "OfflineRecognizer_decode",
624:                          "OfflineRecognizer pointer is null.") ||
625:         !ValidatePointer(env, stream_ptr, "OfflineRecognizer_decode",
626:                          "OfflineStream pointer is null.")) {
627:       return;
628:     }
629:
630:     auto recognizer = reinterpret_cast<sherpa_onnx::OfflineRecognizer *>(ptr);
631:     auto stream = reinterpret_cast<sherpa_onnx::OfflineStream *>(stream_ptr);
632:     recognizer->DecodeStream(stream);
633:   });
634: }
635:
```

## S48 — sherpa-onnx__android__SherpaOnnx__app__src__main__java__com__k2fsa__sherpa__onnx__MainActivity.kt

```text
174:             val ret = audioRecord?.read(buffer, 0, buffer.size)
175:             if (ret != null && ret > 0) {
176:                 val samples = FloatArray(ret) { buffer[it] / 32768.0f }
177:                 stream.acceptWaveform(samples, sampleRate = sampleRateInHz)
178:                 while (recognizer.isReady(stream)) {
179:                     recognizer.decode(stream)
180:                 }
181:
182:                 val isEndpoint = recognizer.isEndpoint(stream)
183:                 var text = recognizer.getResult(stream).text
184:
185:                 // For streaming parformer, we need to manually add some
186:                 // paddings so that it has enough right context to
187:                 // recognize the last word of this segment
188:                 if (isEndpoint && recognizer.config.modelConfig.paraformer.encoder.isNotBlank()) {
189:                     val tailPaddings = FloatArray((0.8 * sampleRateInHz).toInt())
190:                     stream.acceptWaveform(tailPaddings, sampleRate = sampleRateInHz)
191:                     while (recognizer.isReady(stream)) {
192:                         recognizer.decode(stream)
193:                     }
194:                     text = recognizer.getResult(stream).text
195:                 }
196:
197:                 var textToDisplay = lastText
198:
199:                 if (text.isNotBlank()) {
200:                     textToDisplay = if (lastText.isBlank()) {
201:                         "${idx}: $text"
202:                     } else {
203:                         "${lastText}\n${idx}: $text"
204:                     }
205:                 }
206:
207:                 if (isEndpoint) {
208:                     recognizer.reset(stream)
209:                     if (text.isNotBlank()) {
210:                         lastText = "${lastText}\n${idx}: $text"
```

## S49 — sherpa-onnx__scripts__apk__generate-qnn-asr-apk-script.py

```text
37: def get_models():
38:     return [
39:         Model(
40:             model_name="sherpa-onnx-qnn-streaming-zipformer-transducer-zh-en-2023-03-20-chunk-size-32-android-aarch64",
41:             idx=9025,
42:             lang="zh_en",
43:             short_name="streaming_zipformer_transducer_2023_03_20_chunk_32",
44:             cmd="""
45:             pushd $model_name
46:
47:             rm -rfv test_wavs
48:
49:             ls -lh
50:
51:             popd
52:             """,
```

```text
81:             popd
82:             """,
83:         ),
84:         Model(
85:             model_name="sherpa-onnx-qnn-SM8850-binary-x-asr-streaming-zipformer-transducer-zh-en-2026-06-05-chunk-size-480ms",
86:             idx=9029,
87:             lang="zh_en",
88:             short_name="SM8850_x_asr_streaming_zipformer_transducer_2026_06_05_chunk_480ms",
89:             release_tag="asr-models-qnn-binary",
90:             cmd="""
91:             pushd $model_name
92:
93:             rm -rfv test_wavs
94:
95:             ls -lh
96:
97:             popd
98:             """,
99:         ),
100:         Model(
101:             model_name="sherpa-onnx-qnn-SM8850-binary-x-asr-streaming-zipformer-transducer-zh-en-punct-2026-06-05-chunk-size-480ms",
102:             idx=9030,
103:             lang="zh_en",
104:             short_name="SM8850_x_asr_streaming_zipformer_transducer_punct_2026_06_05_chunk_480ms",
105:             release_tag="asr-models-qnn-binary",
106:             cmd="""
107:             pushd $model_name
108:
```

## S50 — sherpa-onnx__sherpa-onnx__csrc__qnn__qnn-backend.cc

```text
87:   bool IsInitialized() const { return is_initialized_; }
88:
89:  private:
90:   bool InitQnnInterface(const std::string &backend_lib) {
91:     backend_lib_handle_ = std::unique_ptr<void, decltype(&dlclose)>(
92:         dlopen(backend_lib.c_str(), RTLD_NOW | RTLD_LOCAL), &dlclose);
93:     if (!backend_lib_handle_) {
94:       SHERPA_ONNX_LOGE("Failed to dlopen '%s'. Error is: '%s'",
95:                        backend_lib.c_str(), dlerror());
96:       return false;
97:     }
```

```text
192:                                             &backend_handle_);
193:     SHERPA_ONNX_QNN_CHECK(ret, "Failed to call backendCreate");
194:   }
195:
196:   void InitDevice() {
197:     auto ret =
198:         qnn_interface_.deviceCreate(log_handle_, nullptr, &device_handle_);
199:     SHERPA_ONNX_QNN_CHECK(ret, "Failed to call deviceCreate");
200:   }
201:
202:  private:
203:   bool debug_ = true;
```

## S51 — sherpa-onnx__sherpa-onnx__csrc__CMakeLists.txt

```text
259: if(SHERPA_ONNX_ENABLE_QNN)
260:   list(APPEND sources
261:     ./qnn/offline-parakeet-ctc-model-qnn.cc
262:     ./qnn/offline-parakeet-tdt-model-qnn.cc
263:     ./qnn/offline-sense-voice-model-qnn.cc
264:     ./qnn/offline-paraformer-model-qnn.cc
265:     ./qnn/offline-whisper-model-qnn.cc
266:     ./qnn/offline-moonshine-model-qnn.cc
267:     ./qnn/offline-zipformer-transducer-model-qnn.cc
268:     ./qnn/offline-zipformer-ctc-model-qnn.cc
269:     ./qnn/online-nemo-transducer-model-qnn.cc
270:     ./qnn/online-zipformer-transducer-model-qnn.cc
271:     ./qnn/qnn-backend.cc
272:     ./qnn/qnn-model.cc
273:     ./qnn/utils.cc
274:   )
```

```text
452: if(SHERPA_ONNX_ENABLE_QNN)
453:   target_include_directories(sherpa-onnx-core PRIVATE ${QNN_SDK_ROOT}/include/QNN)
454: endif()
455:
```

## S52 — sherpa-onnx__sherpa-onnx__java-api__src__main__java__com__k2fsa__sherpa__onnx__OfflineRecognizer.java

```text
1: // Copyright 2024 Xiaomi Corporation
2:
3: package com.k2fsa.sherpa.onnx;
4:
5: public class OfflineRecognizer {
6:     private long ptr = 0;
7:     private final OfflineRecognizerConfig config;
8:
9:     public OfflineRecognizer(OfflineRecognizerConfig config) {
10:         LibraryLoader.maybeLoad();
11:         ptr = newFromFile(config);
12:         if (ptr == 0) {
13:             throw new IllegalArgumentException("Invalid OfflineRecognizerConfig: failed to create native OfflineRecognizer");
14:         }
15:
16:         this.config = config;
17:     }
18:
19:     public void setConfig(OfflineRecognizerConfig config) {
20:         setConfig(ptr, config);
21:         // we don't update this.config
22:     }
23:
24:     public OfflineRecognizerConfig getConfig() {
25:         return config;
26:     }
27:
28:     public void decode(OfflineStream s) {
29:         decode(ptr, s.getPtr());
30:     }
31:
32:     public void decode(OfflineStream[] ss) {
33:         long[] streamPtrs = new long[ss.length];
34:         for (int i = 0; i < ss.length; ++i) {
35:             streamPtrs[i] = ss[i].getPtr();
36:         }
37:         decodeStreams(ptr, streamPtrs);
38:     }
39:
40:     public OfflineStream createStream() {
41:         long p = createStream(ptr);
42:         return new OfflineStream(p);
43:     }
44:
45:     public OfflineStream createStream(String hotwords) {
46:         long p = createStreamWithHotwords(ptr, hotwords);
47:         return new OfflineStream(p);
48:     }
49:
50:     @Override
51:     protected void finalize() throws Throwable {
52:         release();
53:     }
54:
55:     // You'd better call it manually if it is not used anymore
```

## S53 — sherpa-onnx__android__SherpaOnnxSimulateStreamingAsr__app__src__main__java__com__k2fsa__sherpa__onnx__simulate__streaming__asr__SimulateStreamingAsr.kt

```text
146:                 config.hr = hr
147:             }
148:
149:             var assetManager: AssetManager? = context.assets
150:
151:             if (config.modelConfig.provider == "qnn") {
152:                 // We assume you have copied files like libQnnHtpV81Skel.so to jniLibs/arm64-v8a
153:                 Log.i(TAG, "nativelibdir: ${context.applicationInfo.nativeLibraryDir}")
154:
155:                 // If we don't set the environment variable for ADSP_LIBRARY_PATH, we will see
156:                 // the error code 1008 from qnn_interface.deviceCreate()
157:                 // See also
158:                 // https://workbench.aihub.qualcomm.com/docs/hub/faq.html#why-am-i-seeing-error-1008-when-trying-to-use-htp
159:                 OfflineRecognizer.prependAdspLibraryPath(context.applicationInfo.nativeLibraryDir)
160:
161:                 // for qnn, we need to copy *.so files from assets folder to sd card
162:                 if (config.modelConfig.senseVoice.qnnConfig.backendLib.isEmpty()
163:                     && config.modelConfig.zipformerCtc.qnnConfig.backendLib.isEmpty()
164:                     && config.modelConfig.paraformer.qnnConfig.backendLib.isEmpty()
165:                     && config.modelConfig.transducer.qnnConfig.backendLib.isEmpty()
166:                 ) {
```

```text
62:     }
63:
64:     outFile.parentFile?.mkdirs()
65:
66:     context.assets.open(path).use { input: InputStream ->
67:         FileOutputStream(outFile).use { output: OutputStream ->
68:             input.copyTo(output)
69:         }
70:     }
71:     Log.i(TAG, "Copied $path to $targetRoot/$path")
72:
73:     return outFile.absolutePath
74: }
75:
76: fun copyAssetListToInternalStorage(
77:     paths: String,
78:     context: Context
79: ): String {
80:     if (paths.isBlank()) return paths
81:
82:     val pathList = paths.split(",")
83:         .map { it.trim() }
```

## S54 — sherpa-onnx__cmake__onnxruntime-android-aarch64.cmake

```text
15: endif()
16:
17: set(onnxruntime_URL  "https://github.com/csukuangfj/onnxruntime-libs/releases/download/v1.28.2/onnxruntime-android-1.28.2.zip")
18: set(onnxruntime_HASH "SHA256=01518867f78241138b6aa25925802e843a4fa9085af8d303d49e35bbb52aff4d")
19:
20: # If you don't have access to the Internet,
```

## S55 — sherpa-onnx__cmake__onnxruntime-android-aarch64-static.cmake

```text
15: endif()
16:
17: set(onnxruntime_URL  "https://github.com/csukuangfj/onnxruntime-libs/releases/download/v1.28.2/onnxruntime-android-arm64-v8a-static_lib-1.28.2.zip")
18: set(onnxruntime_HASH "SHA256=bc68410f35f13df82c260830bf476ee5565f5efd0ec7622297829a929613c6c0")
19:
20: # If you don't have access to the Internet,
```

## S56 — sherpa-onnx__sherpa-onnx__csrc__offline-recognizer-sense-voice-impl.h

```text
29:     int32_t frame_shift_ms, int32_t subsampling_factor,
30:     bool is_funasr_nano = false) {
31:   OfflineRecognitionResult r;
32:   r.tokens.reserve(src.tokens.size());
33:   r.timestamps.reserve(src.timestamps.size());
34:
35:   std::string text;
36:
37:   // Funasr NanO does not support emotion, event, language, etc.
38:   int32_t start = is_funasr_nano ? 0 : 4;
39:
40:   for (int32_t i = start; i < src.tokens.size(); ++i) {
41:     auto sym = sym_table[src.tokens[i]];
42:     text.append(sym);
43:
44:     r.tokens.push_back(std::move(sym));
45:   }
46:   r.text = std::move(text);
47:
48:   float frame_shift_s = frame_shift_ms / 1000. * subsampling_factor;
49:
50:   for (int32_t i = start; i < src.timestamps.size(); ++i) {
51:     float time = frame_shift_s * (src.timestamps[i] - start);
52:     r.timestamps.push_back(time);
53:   }
54:
55:   r.words = std::move(src.words);
56:
```

## S57 — sherpa-onnx__sherpa-onnx__csrc__qnn__offline-sense-voice-model-qnn.cc

```text
198:     }
199:
200:     if (!model_->HasTensor("logits")) {
201:       SHERPA_ONNX_LOGE("Model does not have output node 'logits'");
202:       SHERPA_ONNX_EXIT(-1);
203:     }
204:
205:     expected_num_frames_ = x_shape[1];
206:   }
207:
208:   std::vector<float> ApplyLFR(const std::vector<float> &in) const {
209:     return ApplyLfrForFixedShape(
210:         in, /*input_dim=*/80, meta_data_.window_size, meta_data_.window_shift,
211:         expected_num_frames_);
212:   }
213:
```
