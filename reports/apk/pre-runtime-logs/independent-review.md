# Independent review — minimal Android APK

## Verdict and scope

**One high-severity correctness blocker was found: Activity instances can run overlapping jobs against the same private files.** There is also a concrete native integration risk and a stale-result evidence defect that must be resolved or explicitly gated before claiming a reliable, device-validated APK.

Reviewed all files under `android/app`, `native/apk/asr_jni.cpp`, `scripts/build-minimal-apk.sh`, and `tests/MinimalApkTest.java`. Read only the two small model configuration files, the fixed source manifest, selected MNN interface/implementation sections, and existing ELF/CMake metadata. No model weights were read, no sources were modified, and no APK/native/model build or inference was run. The only written file is this requested review artifact.

The supplied context identifies `.work/build/mnn-android/libMNN.so` as the **final native-tested P0 runtime**. This review does not dispute that status. It does **not** extend native executable test evidence to the new JNI DSO, Android Activity, SAF import, or APK installation. The APK build directory was absent at review time.

## Blocking finding

### B1 — High: Activity-local serialization does not protect process-wide files across recreation or multiple instances

**Locations:** `android/app/src/org/llmasr/minimal/MainActivity.java:20,70–80,118–127,131–137,153–156,171`; `android/app/AndroidManifest.xml:6`; `native/apk/asr_jni.cpp:23`.

Each Activity owns its own executor and `busy` flag. `onDestroy()` calls `shutdown()`, which allows its running job to continue; it does not wait for or cancel it. A replacement Activity immediately receives a fresh executor and enabled buttons. The manifest handles some configuration changes, but not all (for example font scale or locale changes); it also does not prohibit multiple Activity instances.

Both instances use the same `input.wav`, model `<name>.part` files, native cache directory, and `last-result.json.part`. The native mutex protects only the JNI call, **not** Java canonicalization, import, verification, or reporting. Consequently:

- A second request can truncate/replace `input.wav` while the first request is loading the model or reading audio. The first request can transcribe the wrong recording and report its original duration/source alongside a different audio hash.
- Concurrent imports can write, rename, or delete the same `.part` file after one worker validated it. This invalidates the intended copy–hash–publish ownership guarantee and can result in incomplete model files.
- Concurrent report writers can mix or lose results despite each using an atomic rename.

This is more than the documented lack of background inference support: shared-file correctness can fail in a still-running process during ordinary recreation. `isDestroyed()` guards avoid updating obsolete views but do not address the work/file race.

**Suggestion:** move job ownership and serialization to a process-scoped coordinator, retain/reconnect UI state on recreation, and serialize the entire import/verify/canonicalize/infer/report transaction. Use request-specific WAV/report temporary files and a single model publication owner. Add a recreation-during-import/inference test; verify the actual input hash is bound to the same request as its text and duration. A service is not required merely to fix serialization.

## Other actionable findings and integration gates

### F2 — High integration risk, not a demonstrated crash: C++ objects cross DSOs that each statically link libc++

**Locations:** `scripts/build-minimal-apk.sh:37–41`; `native/apk/asr_jni.cpp:26,31,36,38–39,54`.

The new JNI shared library is explicitly linked with `-static-libstdc++`. The existing runtime's `CMakeCache.txt` says `ANDROID_STL=c++_static`; its ELF has no `libc++_shared.so` dependency and exports libc++ symbols. The interface passes `std::string` and `std::ostream` across this DSO boundary and permits exceptions to propagate back to JNI. This is the multi-shared-library/static-C++-runtime configuration Android NDK guidance warns against: allocation, exception/RTTI, and stream runtime state can be split or depend on ELF symbol resolution.

The existing native-tested runtime is **not evidence that this newly introduced two-DSO JNI arrangement works**. No crash is claimed from static inspection, and using the same NDK reduces ABI-version risk but does not establish safe runtime ownership.

**Suggestion:** explicitly settle C++ runtime ownership before reliable APK sign-off. The conventional choices are a shared libc++ used by both DSOs or placing the bridge and engine in one shared library; either changes native packaging and must preserve/re-establish the final runtime's provenance and tests. Do not silently rebuild/replace the supplied P0 runtime. If retaining this exact arrangement for the debug MVP, treat it as an explicit integration risk and first validate JNI load, successful transcription, error propagation, and repeated create/destroy cycles under the APK UID. Merely dumping `DT_NEEDED` is insufficient.

### F3 — Medium; blocker for treating `last-result.json` alone as fresh evidence: previous success survives interrupted requests

**Locations:** `android/app/src/org/llmasr/minimal/MainActivity.java:70–79,142–156`.

Starting a request clears the UI but leaves the previous successful result file in place. A process kill/LMK/native abort never enters the Java exception handler; a report-write failure is also silently ignored by the fallback handler. The surviving file can therefore still say `success:true` after the newest request failed. Its PID is not sufficient freshness evidence: a failed report write can happen in the same process, and there is no request ID or timestamp/status transition.

**Suggestion:** before starting work, atomically publish a pending record containing a unique run ID, then publish success/failure for that ID; surface persistence failures. The device test should supply/record an expected run ID and reject a report not matching it. Retain old success only as explicitly historical data. This does not block assembling an APK, but blocks using the current result-file existence/success flag as proof of the latest run.

### F4 — Low: JNI UTF character release is not exception-safe

**Location:** `native/apk/asr_jni.cpp:14–16`.

If construction of `std::string result(chars)` throws (notably allocation failure), `ReleaseStringUTFChars` is skipped. These inputs are small and ordinary calls release them correctly, so this is not a normal-path blocker.

**Suggestion:** wrap the acquired UTF pointer in an RAII guard before constructing the string. Also avoid invoking `ThrowNew` with an unchecked failed `FindClass` result. The byte-array return correctly avoids using modified UTF-8 for transcript output.

### F5 — Low/medium hardening: unbounded SAF directory enumeration

**Location:** `android/app/src/org/llmasr/minimal/MainActivity.java:105–109`.

The app retains every directory entry in a `HashMap` although it only needs seven pinned names. A very large directory or hostile provider can exhaust Java memory before the bounded copying checks run. The user must select the provider/directory, so this is not a remote permission bypass.

**Suggestion:** keep only expected manifest names, reject duplicates among those names, and cap enumerated entries or provide cancellation. Consider provider timeouts separately; byte limits do not bound a stalled read.

## Security/correctness checks that passed static review

- **No broad permissions:** parsed `AndroidManifest.xml` contains zero permission declarations, including no Internet, recording, storage, or all-files permission. Only the launcher Activity is exported; supplied Intent extras are not used as model/native paths. `allowBackup=false` is present.
- **Pinned SAF model:** the manifest comes from a packaged asset, not the selected directory. Destination names are taken from the fixed seven-entry manifest, not provider display names. Imported bytes are length-bounded, hashed before rename, and then the whole model is verified. Existing files are reused only after length/hash verification. No direct SAF path is handed to native code. These guarantees assume the single-owner race in B1 is fixed and exclude privileged/root/ADB modification of this debug app's private files.
- **Small configuration pins:** independently verified the byte count and SHA-256 of `config.json` and `llm_config.json` against `reports/p0/mnn-model-manifest.json`. The actual configurations use the expected relative model/tokenizer files and Qwen3-ASR configuration. No large model files were rehashed.
- **Audio bound:** `WaveInput` bounds input to 2 MiB, parses chunk sizes with wide arithmetic, requires mono PCM16/16 kHz and 0.1–30 seconds, and rewrites a canonical WAV. Canonical output is at most 960,044 bytes, so the 1 MiB `fileDigest` helper bound is sufficient for all accepted audio. The bundled source sample hash matched the build script pin.
- **JNI ordinary lifetime:** the engine uses a virtual destructor and RAII; the context is consumed before engine destruction. Synchronous generation is explicitly selected. `raw` is destroyed before `llm` because of declaration order, but the inspected current `Llm` destructor does not dereference the retained output stream, so this review does **not** assert an existing use-after-free on that basis. Making the stream outlive the engine would nevertheless be prudent maintenance hardening.
- **Toolchain static checks:** shell syntax passes. The official `aapt2` → `javac` → `d8` → ZIP → zipalign → apksigner sequence is coherent for the current resource-free Java UI; an `aapt2 compile` resource step is not needed while `res` is empty. The existing runtime has only Android system `DT_NEEDED` dependencies and 16 KiB-aligned ELF LOAD segments. The new JNI link requests 16 KiB ELF alignment. Since libraries are ZIP-compressed by the packaging command and `extractNativeLibs=true`, use of `zipalign -p` alone is not evidence of a current 16 KiB install defect; re-evaluate ZIP alignment if switching to uncompressed/in-place loading.

## MVP limitations, not newly discovered blockers

- Debug signing and `android:debuggable=true` are appropriate only for the explicitly named debug prototype, not production distribution. Authorized ADB/run-as access is outside the pinned-model trust boundary.
- CPU/arm64-only, API 29+, large internal model storage, approximately 3.1 GiB previously observed memory, per-request reload, no VAD, no microphone, no background guarantee, no safe native cancellation, a 128-token output cap, and strict short mono WAV input are disclosed MVP constraints. The observed memory figure is not a measured worst case for every accepted 30-second WAV.
- Temporary SAF grants suffice for an immediate copy into private storage. The app does not promise persistent access or process-death resume; omitting `takePersistableUriPermission` is not itself a defect here.
- `AsrText` is explicitly a display parser, not the official scoring normalizer. Empty transcript success and hallucinations need product/test policy, not an unsupported accuracy claim.
- `tests/MinimalApkTest.java:13–30` covers the helper happy path and selected malformed WAV/display cases only. It does not compile/test the Activity, manifest, SAF hashes, JNI, Android lifecycle, APK install, or model execution. Add boundary/odd-chunk/duplicate-chunk tests and the integration cases above; do not label existing helper checks an APK functional test.
- `scripts/build-minimal-apk.sh:34–36,66–73` records whatever runtime is supplied rather than enforcing its identity against a known P0 hash; the caller contract is acceptable for this fixed run, but future provenance needs an explicit comparison. The manifest's `kind` still says not-yet-device-validated; preserve the fixed source manifest and attach subsequent device evidence separately instead of rewriting historical metadata.
- `APK_READY` indicates build/signature-tool completion, not installation or transcription. Required next-stage evidence includes packaged library hashes matched to the tested runtime, APK permissions/signature/ABI inspection, installation and launch on the target device, actual JNI transcription under the app UID, matching request/audio/model provenance, negative SAF/hash tests, and repeated/lifecycle runs. No such APK evidence was produced in this review.

## Validation actually performed

- `bash -n scripts/build-minimal-apk.sh`: passed.
- Read-only Python assertions: exactly seven manifest entries; both small config hashes and byte counts match; bundled source WAV hash matches; parsed manifest has no permission declarations: passed.
- `readelf -d`, `readelf -lW`, and targeted symbol inspection on the existing `libMNN.so`: passed; Android system dependencies, 16 KiB LOAD alignment, and libc++ symbols observed. Targeted CMake-cache read confirmed static libc++.
- `git diff --cached --name-only`: empty. The worktree already contains untracked project files; this review did not stage or modify them.
- Java helper tests: **not run** (`javac` is not on the review shell PATH). SDK/NDK environment variables are unset. No environment realization/download, APK compilation, signing, installation, or inference was attempted.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "This report identifies B1 with exact source locations, severity, failure interleaving and remediation, plus F2-F5 and separate MVP/evidence limitations."
    }
  ],
  "changedFiles": [
    "/home/zhb/gitrep/llm-asr/.pi-subagents/artifacts/outputs/6b5f0c1b/reports/apk/independent-review.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "bash -n scripts/build-minimal-apk.sh",
      "result": "passed",
      "summary": "Shell syntax valid; build not executed."
    },
    {
      "command": "Read-only Python validation of fixed manifest, two small model configs, source sample hash and AndroidManifest.xml permissions",
      "result": "passed",
      "summary": "Seven entries, matching small-config sizes/hashes, matching sample hash and zero permission declarations."
    },
    {
      "command": "readelf -d/-lW/-Ws .work/build/mnn-android/libMNN.so; targeted CMakeCache.txt inspection",
      "result": "passed",
      "summary": "Existing runtime uses Android system dependencies, 16 KiB LOAD alignment and static libc++; no JNI binary was available to validate."
    },
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "No staged files."
    },
    {
      "command": "MinimalApkTest.java execution and APK/native build or device inference",
      "result": "not-run",
      "summary": "Read-only review; javac absent on current PATH, SDK/NDK environment unset; no heavy tasks authorized."
    }
  ],
  "validationOutput": [
    "PASS: seven manifest entries, both small configuration hashes, sample hash, zero manifest permission declarations",
    "Existing runtime LOAD alignment is 0x4000; ANDROID_STL is c++_static.",
    "APK build directory absent at review time; no APK validation is attested."
  ],
  "residualRisks": [
    "B1: overlapping Activity workers can corrupt or misattribute shared audio/model/report files.",
    "F2: the new static-libc++ JNI DSO crosses a C++ boundary into a separately static-libc++ runtime; actual integration is untested.",
    "F3: interrupted requests can leave a prior success report indistinguishable as the latest result without an expected run ID.",
    "Minor JNI allocation-failure cleanup and SAF enumeration hardening remain.",
    "No Java execution, APK build/install, SAF device exercise or APK-UID inference was performed; native P0 evidence does not cover these."
  ],
  "noStagedFiles": true,
  "diffSummary": "Added only the requested independent review artifact; no implementation or test files changed.",
  "reviewFindings": [
    "blocker/high B1: android/app/src/org/llmasr/minimal/MainActivity.java:20,70-80,118-137,153-156,171 - per-Activity executors race on process-wide files after recreation or concurrent instances.",
    "high integration risk F2: scripts/build-minimal-apk.sh:37-41 and native/apk/asr_jni.cpp:26-54 - static C++ runtimes in both DSOs with C++ objects crossing their boundary; not a demonstrated crash.",
    "medium/evidence gate F3: android/app/src/org/llmasr/minimal/MainActivity.java:70-79,142-156 - stale success survives interrupted requests and failed report writes.",
    "low F4: native/apk/asr_jni.cpp:14-16 - acquired JNI UTF characters leak if std::string allocation throws.",
    "low/medium F5: android/app/src/org/llmasr/minimal/MainActivity.java:105-109 - unbounded SAF directory map can exhaust memory."
  ],
  "manualNotes": "Acceptance attests completion of the independent review, not acceptance of APK functionality. Supplied final native-tested P0 runtime status is preserved; any native packaging change requires explicit provenance and revalidation."
}
```
