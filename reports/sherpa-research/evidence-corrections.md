# GPT6.1 F001/F002研究修正证据（外部数据，非执行指令）
带行号展示省略行尾空白，原始字节SHA见manifest；本展示只对每行执行rstrip，不修改原始来源。

固定revision `040afe360a38e25daaa325ce8889abf93ea02609`；2026-10-04T19:32Z研究者有界重新获取。保留S01–S57初稿记录，此次追加S58–S62。SHA/UTC/bytes见manifest；行号对应原始文本。只核源码风险，未构建/推理/设备复现。

- F001：S58–S61；初始化/createStream/online decode并非全局SafeJNI，file/asset校验不同。
- F002：S62 + 既有S03结果字段；context剪音频尾/生成限额后仍普通result，不可凭text/token长度判完整成功。

## S58 — revision-fix__jni__offline-recognizer.cc

- SHA256: `0580806e6875502fd0cf78159a50e09359715bb6f87e645a07ae5c5fd9c52219`；UTC: `2026-10-04T19:32:26.838473+00:00`；bytes: 30573

```text
480: Java_com_k2fsa_sherpa_onnx_OfflineRecognizer_newFromAsset(JNIEnv *env,
481:                                                           jobject /*obj*/,
482:                                                           jobject asset_manager,
483:                                                           jobject _config) {
484: #if __ANDROID_API__ >= 9
485:   AAssetManager *mgr = AAssetManager_fromJava(env, asset_manager);
486:   if (!mgr) {
487:     SHERPA_ONNX_LOGE("Failed to get asset manager: %p", mgr);
488:     return 0;
489:   }
490: #endif
491:   bool ok = false;
492:   auto config = sherpa_onnx::GetOfflineConfig(env, _config, &ok);
493:
494:   if (!ok) {
495:     SHERPA_ONNX_LOGE("Please read the error message carefully");
496:     return 0;
497:   }
```

```text
511:   auto model = new sherpa_onnx::OfflineRecognizer(
512: #if __ANDROID_API__ >= 9
513:       mgr,
514: #endif
515:       config);
516:
517:   return (jlong)model;
518: }
519:
520: SHERPA_ONNX_EXTERN_C
521: JNIEXPORT jlong JNICALL
522: Java_com_k2fsa_sherpa_onnx_OfflineRecognizer_newFromFile(JNIEnv *env,
523:                                                          jobject /*obj*/,
524:                                                          jobject _config) {
525:   bool ok = false;
526:   auto config = sherpa_onnx::GetOfflineConfig(env, _config, &ok);
527:
528:   if (!ok) {
529:     SHERPA_ONNX_LOGE("Please read the error message carefully");
530:     return 0;
531:   }
532:
533:   if (config.model_config.debug) {
534: #if __ANDROID_API__
535:     auto str_vec = sherpa_onnx::SplitString(config.ToString(), 128);
536:     for (const auto &s : str_vec) {
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
```

```text
582:                                                           jobject /*obj*/,
583:                                                           jlong ptr) {
584:   auto recognizer = reinterpret_cast<sherpa_onnx::OfflineRecognizer *>(ptr);
585:   std::unique_ptr<sherpa_onnx::OfflineStream> s = recognizer->CreateStream();
586:
587:   // The user is responsible to free the returned pointer.
588:   //
589:   // See Java_com_k2fsa_sherpa_onnx_OfflineStream_delete() from
590:   // ./offline-stream.cc
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
636: SHERPA_ONNX_EXTERN_C
637: JNIEXPORT void JNICALL
638: Java_com_k2fsa_sherpa_onnx_OfflineRecognizer_decodeStreams(
639:     JNIEnv *env, jobject /*obj*/, jlong ptr, jlongArray stream_ptrs) {
640:   SafeJNI(env, "OfflineRecognizer_decode_streams", [&] {
641:     if (!ValidatePointer(env, ptr, "OfflineRecognizer_decode_streams",
642:                          "OfflineRecognizer pointer is null.")) {
643:       return;
644:     }
645:
646:     auto recognizer = reinterpret_cast<sherpa_onnx::OfflineRecognizer *>(ptr);
647:
648:     jlong *p = env->GetLongArrayElements(stream_ptrs, nullptr);
649:     jsize n = env->GetArrayLength(stream_ptrs);
650:
651:     auto ss = reinterpret_cast<sherpa_onnx::OfflineStream **>(p);
652:     recognizer->DecodeStreams(ss, n);
653:
654:     env->ReleaseLongArrayElements(stream_ptrs, p, JNI_ABORT);
655:   });
```

## S59 — revision-fix__jni__online-recognizer.cc

- SHA256: `102b882a83c2e2d1fb17a9656fd6b7806aa4497080bafd920d436f81d0ea67e1`；UTC: `2026-10-04T19:32:26.832803+00:00`；bytes: 17512

```text
245: JNIEXPORT jlong JNICALL
246: Java_com_k2fsa_sherpa_onnx_OnlineRecognizer_newFromAsset(JNIEnv *env,
247:                                                          jobject /*obj*/,
248:                                                          jobject asset_manager,
249:                                                          jobject _config) {
250: #if __ANDROID_API__ >= 9
251:   AAssetManager *mgr = AAssetManager_fromJava(env, asset_manager);
252:   if (!mgr) {
253:     SHERPA_ONNX_LOGE("Failed to get asset manager: %p", mgr);
254:     return 0;
255:   }
256: #endif
257:   bool ok = false;
258:   auto config = sherpa_onnx::GetConfig(env, _config, &ok);
259:
260:   if (!ok) {
261:     SHERPA_ONNX_LOGE("Please read the error message carefully");
262:     return 0;
```

```text
276:   auto recognizer = new sherpa_onnx::OnlineRecognizer(
277: #if __ANDROID_API__ >= 9
278:       mgr,
279: #endif
280:       config);
281:
282:   return (jlong)recognizer;
283: }
284:
285: SHERPA_ONNX_EXTERN_C
286: JNIEXPORT jlong JNICALL Java_com_k2fsa_sherpa_onnx_OnlineRecognizer_newFromFile(
287:     JNIEnv *env, jobject /*obj*/, jobject _config) {
288:   bool ok = false;
289:   auto config = sherpa_onnx::GetConfig(env, _config, &ok);
290:
291:   if (!ok) {
292:     SHERPA_ONNX_LOGE("Please read the error message carefully");
293:     return 0;
294:   }
295:
296:   if (config.model_config.debug) {
297: #if __ANDROID_API__
298:     auto str_vec = sherpa_onnx::SplitString(config.ToString(), 128);
299:     for (const auto &s : str_vec) {
300:       SHERPA_ONNX_LOGE("%s", s.c_str());
301:     }
302: #else
303:     SHERPA_ONNX_LOGE("%s", config.ToString().c_str());
304: #endif
305:   }
306:
307:   if (!config.Validate()) {
308:     SHERPA_ONNX_LOGE("Errors found in config!");
309:     return 0;
310:   }
311:
312:   auto recognizer = new sherpa_onnx::OnlineRecognizer(config);
313:
314:   return (jlong)recognizer;
315: }
```

```text
363: JNIEXPORT void JNICALL Java_com_k2fsa_sherpa_onnx_OnlineRecognizer_decode(
364:     JNIEnv * /*env*/, jobject /*obj*/, jlong ptr, jlong stream_ptr) {
365:   auto recognizer = reinterpret_cast<sherpa_onnx::OnlineRecognizer *>(ptr);
366:   auto stream = reinterpret_cast<sherpa_onnx::OnlineStream *>(stream_ptr);
367:
368:   recognizer->DecodeStream(stream);
369: }
370:
371: SHERPA_ONNX_EXTERN_C
372: JNIEXPORT void JNICALL
373: Java_com_k2fsa_sherpa_onnx_OnlineRecognizer_decodeStreams(
374:     JNIEnv *env, jobject /*obj*/, jlong ptr, jlongArray stream_ptrs) {
375:   auto recognizer = reinterpret_cast<sherpa_onnx::OnlineRecognizer *>(ptr);
376:
377:   jlong *p = env->GetLongArrayElements(stream_ptrs, nullptr);
378:   jsize n = env->GetArrayLength(stream_ptrs);
379:
380:   auto ss = reinterpret_cast<sherpa_onnx::OnlineStream **>(p);
381:
382:   recognizer->DecodeStreams(ss, n);
383:
384:   env->ReleaseLongArrayElements(stream_ptrs, p, JNI_ABORT);
385: }
```

```text
389: Java_com_k2fsa_sherpa_onnx_OnlineRecognizer_createStream(JNIEnv *env,
390:                                                          jobject /*obj*/,
391:                                                          jlong ptr,
392:                                                          jstring hotwords) {
393:   auto recognizer = reinterpret_cast<sherpa_onnx::OnlineRecognizer *>(ptr);
394:
395:   const char *p = env->GetStringUTFChars(hotwords, nullptr);
396:   std::unique_ptr<sherpa_onnx::OnlineStream> stream;
397:
398:   if (strlen(p) == 0) {
399:     stream = recognizer->CreateStream();
400:   } else {
401:     stream = recognizer->CreateStream(p);
402:   }
```

## S60 — revision-fix__jni__common.h

- SHA256: `8ad5c17177f78866ae2609b9fd6721aba2a5f3e5de343941d793c08338c38f1a`；UTC: `2026-10-04T19:32:26.822176+00:00`；bytes: 11254

```text
159: // Template function for non-void return types
160: template <typename Func, typename ReturnType>
161: ReturnType SafeJNI(JNIEnv *env, const char *functionName, Func func,
162:                    ReturnType defaultValue) {
163:   try {
164:     return func();
165:   } catch (const std::exception &e) {
166:     jclass exClass = env->FindClass("java/lang/RuntimeException");
167:     if (exClass != nullptr) {
168:       std::string errorMessage = std::string(functionName) + ": " + e.what();
169:       env->ThrowNew(exClass, errorMessage.c_str());
170:       env->DeleteLocalRef(exClass);
171:     }
172:   } catch (...) {
173:     jclass exClass = env->FindClass("java/lang/RuntimeException");
174:     if (exClass != nullptr) {
175:       std::string errorMessage = std::string(functionName) +
176:                                  ": Native exception: caught unknown exception";
177:       env->ThrowNew(exClass, errorMessage.c_str());
178:       env->DeleteLocalRef(exClass);
179:     }
180:   }
181:   return defaultValue;
182: }
183:
184: // Specialization for void return type
185: template <typename Func>
186: void SafeJNI(JNIEnv *env, const char *functionName, Func func) {
187:   try {
188:     func();
189:   } catch (const std::exception &e) {
190:     jclass exClass = env->FindClass("java/lang/RuntimeException");
191:     if (exClass != nullptr) {
192:       std::string errorMessage = std::string(functionName) + ": " + e.what();
193:       env->ThrowNew(exClass, errorMessage.c_str());
194:       env->DeleteLocalRef(exClass);
195:     }
196:   } catch (...) {
197:     jclass exClass = env->FindClass("java/lang/RuntimeException");
198:     if (exClass != nullptr) {
199:       std::string errorMessage = std::string(functionName) +
200:                                  ": Native exception: caught unknown exception";
201:       env->ThrowNew(exClass, errorMessage.c_str());
202:       env->DeleteLocalRef(exClass);
203:     }
204:   }
205: }
```

## S61 — revision-fix__csrc__offline-paraformer-model.cc

- SHA256: `2401fb302d8116e3a52e8b91ad9fd8dd3cc810b24c2a30658a61461a1703aa63`；UTC: `2026-10-04T19:32:26.840110+00:00`；bytes: 5417

```text
32:   explicit Impl(const OfflineModelConfig &config)
33:       : config_(config),
34:         env_(ORT_LOGGING_LEVEL_ERROR),
35:         sess_opts_(GetSessionOptions(config)),
36:         allocator_{} {
37:     sess_ = std::make_unique<Ort::Session>(
38:         env_, SHERPA_ONNX_TO_ORT_PATH(config_.paraformer.model), sess_opts_);
39:     Init(nullptr, 0);
40:   }
41:
42:   template <typename Manager>
43:   Impl(Manager *mgr, const OfflineModelConfig &config)
44:       : config_(config),
45:         env_(ORT_LOGGING_LEVEL_ERROR),
46:         sess_opts_(GetSessionOptions(config)),
47:         allocator_{} {
48:     auto buf = ReadFile(mgr, config_.paraformer.model);
49:     Init(buf.data(), buf.size());
50:   }
```

```text
75:     if (model_data) {
76:       sess_ = std::make_unique<Ort::Session>(
77:           env_, model_data, model_data_length, sess_opts_);
78:     } else if (!sess_) {
79:       SHERPA_ONNX_LOGE(
80:           "Please pass model data or initialize the session outside of "
81:           "this function");
82:       SHERPA_ONNX_EXIT(-1);
83:     }
84:
```

## S62 — revision-fix__csrc__offline-recognizer-qwen3-asr-impl.cc

- SHA256: `d9daad0eafcd066fa088eb06d858ecfd394fef35ce1e6f595ab934e6e5525824`；UTC: `2026-10-04T19:32:27.131792+00:00`；bytes: 41307

```text
211: Ort::Value TruncateAudioFeatures(Ort::Value audio_features, int32_t keep_frames,
212:                                  OrtAllocator *allocator) {
213:   if (keep_frames <= 0) {
214:     return audio_features;
215:   }
216:
217:   auto info = audio_features.GetTensorTypeAndShapeInfo();
218:   auto shape = info.GetShape();
219:   if (shape.size() != 3 || shape[0] != 1 || shape[1] <= 0 || shape[2] <= 0) {
220:     return audio_features;
221:   }
222:
223:   int32_t A = static_cast<int32_t>(shape[1]);
224:   int32_t H = static_cast<int32_t>(shape[2]);
225:   if (keep_frames >= A) {
226:     return audio_features;
227:   }
228:
229:   auto elem_type =
230:       static_cast<ONNXTensorElementDataType>(info.GetElementType());
231:   if (elem_type != ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT &&
232:       elem_type != ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16 &&
233:       elem_type != ONNX_TENSOR_ELEMENT_DATA_TYPE_UINT16) {
234:     return audio_features;
235:   }
236:
237:   std::array<int64_t, 3> new_shape{1, static_cast<int64_t>(keep_frames), H};
238:   Ort::Value truncated = Ort::Value::CreateTensor(
239:       allocator, new_shape.data(), new_shape.size(),
240:       static_cast<ONNXTensorElementDataType>(elem_type));
241:
242:   if (elem_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT) {
243:     const float *src = audio_features.GetTensorData<float>();
244:     float *dst = truncated.GetTensorMutableData<float>();
245:     std::memcpy(dst, src,
246:                 static_cast<size_t>(keep_frames) * static_cast<size_t>(H) *
247:                     sizeof(float));
248:   } else {
249:     const uint16_t *src = audio_features.GetTensorData<uint16_t>();
250:     uint16_t *dst = truncated.GetTensorMutableData<uint16_t>();
251:     std::memcpy(dst, src,
252:                 static_cast<size_t>(keep_frames) * static_cast<size_t>(H) *
253:                     sizeof(uint16_t));
254:   }
255:
256:   return truncated;
257: }
```

```text
873:   if (context_len > max_seq_len) {
874:     const int32_t one_audio_len = static_cast<int32_t>(audio_pad_ids_.size());
875:     if (one_audio_len <= 0) {
876:       result.text = "";
877:       return result;
878:     }
879:
880:     int32_t after_len =
881:         context_len - before_len - fake_audio_token_len * one_audio_len;
882:     if (after_len < 0) {
883:       after_len = 0;
884:     }
885:
886:     int32_t keep_audio = (max_seq_len - before_len - after_len) / one_audio_len;
887:     if (keep_audio < 0) {
888:       SHERPA_ONNX_LOGE(
889:           "qwen3-asr prompt scaffold exceeds max_total_len: before=%d after=%d "
890:           "max_total_len=%d",
891:           before_len, after_len, max_seq_len);
892:       Qwen3LogMaxTotalLenSuggestions(max_seq_len, model_max_len);
893:       result.text = "";
894:       return result;
895:     }
896:
897:     if (keep_audio == 0) {
898:       SHERPA_ONNX_LOGE(
899:           "qwen3-asr max_total_len=%d leaves no room for audio placeholders "
900:           "(before=%d after=%d)",
901:           max_seq_len, before_len, after_len);
902:       Qwen3LogMaxTotalLenSuggestions(max_seq_len, model_max_len);
903:       result.text = "";
904:       return result;
905:     }
906:
```

```text
908:       SHERPA_ONNX_LOGE(
909:           "qwen3-asr: context_len (%d) exceeds max_total_len (%d). Truncating "
910:           "audio placeholders: audio_token_len=%d -> keep_audio=%d (before=%d "
911:           "after=%d).",
912:           context_len, max_seq_len, fake_audio_token_len, keep_audio,
913:           before_len, after_len);
914:       Qwen3LogMaxTotalLenSuggestions(max_seq_len, model_max_len);
915:       std::vector<int64_t> ids_before(source_ids.begin(),
916:                                       source_ids.begin() + before_len);
917:       std::vector<int64_t> ids_after(source_ids.end() - after_len,
```

```text
928:
929:       source_ids.insert(source_ids.end(), ids_after.begin(), ids_after.end());
930:
931:       fake_audio_token_len = keep_audio;
932:       audio_token_len = keep_audio;
933:       context_len = static_cast<int32_t>(source_ids.size());
934:
935:       trimmed_audio_features = TruncateAudioFeatures(
936:           std::move(trimmed_audio_features), keep_audio, model_->Allocator());
937:     }
```

```text
1026:   int32_t cur_len = context_len;
1027:
1028:   for (int32_t step = 1; step < max_new_tokens; ++step) {
1029:     if (cur_len >= max_seq_len) {
1030:       break;
1031:     }
1032:
1033:     if (step + 1 == max_new_tokens) {
1034:       SHERPA_ONNX_LOGE(
1035:           "Result is truncated. max_new_tokens %d is too small for "
1036:           "this audio input. Please either use a shorter audio or use a "
1037:           "larger max_new_tokens",
1038:           max_new_tokens);
1039:     }
```

```text
1078:     }
1079:
1080:     next_id = SampleTokenFromLogits(logits, time_dim2 - 1, temperature, top_p);
1081:
1082:     if (next_id == eos_id) {
1083:       break;
1084:     }
1085:
1086:     generated_ids.push_back(next_id);
```

```text
1125:   result.text = tokenizer_->Decode(cleaned_ids);
1126:   RemoveUtf8ReplacementChars(&result.text);
1127:
1128:   if (!cleaned_ids.empty()) {
1129:     std::vector<std::string> all_tokens;
1130:     all_tokens.reserve(cleaned_ids.size());
1131:     std::string pending_bytes;
1132:
1133:     for (int64_t token_id : cleaned_ids) {
1134:       std::string s =
1135:           tokenizer_->GetTokenStringStreaming(token_id, &pending_bytes);
1136:       all_tokens.push_back(std::move(s));
1137:     }
1138:
1139:     if (!pending_bytes.empty() && !all_tokens.empty()) {
1140:       all_tokens.back().append("\xEF\xBF\xBD");
1141:     }
1142:
1143:     result.tokens = std::move(all_tokens);
1144:   }
1145:
1146:   return result;
1147: }
1148:
1149: void OfflineRecognizerQwen3ASRImpl::DecodeStreams(OfflineStream **ss,
1150:                                                   int32_t n) const {
1151:   for (int32_t i = 0; i != n; ++i) {
1152:     Decode(ss[i]);
1153:   }
```

```text
1231:       GenerateText(std::move(audio_features), valid_frames, stream);
1232:
1233:   r.text = ApplyHomophoneReplacer(std::move(r.text));
1234:
1235:   stream->SetResult(r);
```
