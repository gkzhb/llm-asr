# taw 当次正式 full：失败事实与边界

## 命令与真实终态

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

- BG `becdd8726`：自动终态 `failed` / exit1；约 2708.393s。
- Gradle：`BUILD FAILED in 45m 5s`；158 actionable tasks / 158 executed。
- 198 条唯一 task 行（含聚合/NO-SOURCE/SKIPPED/UP-TO-DATE，不冒充 198 actions）。
- pipeline Gradle1 / tee0 / DEX scan1（无匹配）；不含 D8/R8 警告。
- 完整日志 `.work/taw-full/full.log`，54034 bytes，SHA-256 `2109d2cd335cae0e4f2aa20e44564a33467cfd1eb97fd9dd81cb232b01c710ce`。
- Gradle 原日志 `.work/verify-local/full-LaJESV/build.log`；execution / pre / post / closure 保留在 `.work/taw-full/`。
- 真实终态后 `/proc` 一次相关 Gradle/host/JVM 进程检查无残留；未轮询等待。
- 宿主 BG 环境过滤 PI_PROVIDER/MODEL，execution 为 null；实际准入由本 leaf 环境与 live Herdr 独立核 newapi/gpt-6.1-sol/high，非从 null 推断 route。

## 唯一停止原因

```text
Execution failed for task ':asr-runtime:generateDebugUnitTestLintModel'.
Could not resolve all files for configuration ':asr-runtime:debugUnitTestCompileClasspath'.
Did not resolve 'org.hamcrest:hamcrest-core:1.3' which is part of the dependency lock state
Did not resolve 'junit:junit:4.13.2' which is part of the dependency lock state
No cached version of junit:junit:4.13.2 available for offline mode.
```

未调查/改动 cache、下载、锁或 metadata；不扩大权限、不盲重试。

## 实际覆盖

| 层 | 当次证据与结论 |
|---|---|
| 原 host | `:verifyHost` 完整返回0；默认/compat/IME return、readiness/first-use/boundary 普通与-O、原 runtime15-route、ABI/latch/真实 mutants/restores、owner/cleanup、provider/adapter/日志/格式源接线等按原脚本执行；host fake 非 Android |
| 新实际 start 边界 | ordinary/-O 各5 compile / 11 JVM（每模式16 attempted owned child），两 production mutants 可编译精确 AssertionError1 后恢复0；R1/R2 已接受 fresh 审不由本 leaf 重审；P3 per-condition gap仍 defer |
| SDK/JNI | app/runtime/core 真实 Java/Kotlin 编译；真实 NDK JNI object 与生成头/symbol check；非仅 grep |
| Native | 原578 MNN对象全部与 pre字节一致；JNI真实复编和增量最终link已执行，native hash仍匹配批准 baseline；非clean/hermetic/model运行 |
| 三包 | 新三 APK checker 独立 receipt 绑定240build/10module/工具逐SHA；签名v3/cert、zipalign、Manifest/权限、全DEX去重与owner/isolation及14仪器方法通过；product资产/native另只读逐payload核baseline一致 |
| JUnit | 新test-host7测试全零；core34/runtime1 task本次未运行，旧XML不作通过 |
| checksum | 正式full内唯一隔离AGP目标原0→mutant1→恢复0，expected全零/actual真实AGP SHA，主metadata保持；不是缺缓存作red |
| Production Lint | identity gate及app/runtime部分analysis运行；最终runtime model生成失败，report task未执行，磁盘XML均旧，不能报告当次0Error或warning数量 |
| 后续 fixture | `verifyLintEvidence` 与 `verifyAndroidTestFixtureEvidence` 未运行，不能继承旧0/1/0或五阶段证据 |

## 当次三 APK（非交付）

| APK | 版本/身份 | bytes | SHA-256 |
|---|---|---:|---|
| `android/app/build/outputs/apk/debug/app-debug.apk` | `org.llmasr.minimal` 0.6-debug/6 | 5372553 | `5574e0afb81e63932eeb4a1d334bf60cd4ed068dbeffcb8c3bfcbb3e443a11be` |
| `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | `org.llmasr.minimal.testhost` p1b-testhost/1 | 2701228 | `1f612142a37f94e7b25180d3857ac7d8c6db73c646b1ce0d1289b0599a223854` |
| `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | `org.llmasr.minimal.testhost.test` version attributes absent/not declared | 66382 | `4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8` |

三包证书 SHA-256 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`；均 v3。metadata路径/SHA/原version字段、工具raw hash、native/资产 payload SHA见 artifacts.json。仪器test未声明version，不能擅填与host相同。

## 未运行的正式必需后续任务

- `:core:test`
- `:asr-runtime:testDebugUnitTest`
- `:app:verifyDebugPackage`
- `:verifyBuildInputs`
- `:verifyNativeInputs`
- `:verifyApkChecker`
- `:verifyArchitecture`
- `:verifyFormat`
- `:verifyLocalGuards`
- `:verifyLint`
- `:app:lintReportDebug`
- `:asr-runtime:lintReportDebug`
- `:verifyLintEvidence`
- `:verifyAndroidTestFixtureEvidence`
- `:verifyDebug`

## 输入/保护域/权限

19候选及baseline、240build/10module/84native/3external、1986 tracked及752原excluded均pre/post零差异。84 native collector含app源，不能称相对旧31保护“全产品未变”。native二进制及578对象保持基线身份；新product APK字节变化为预期构建输出。旧reports不覆盖，无未知tracked生成报告变化。新4报告独立supplemental，保存后再次只读collector核等。

无 stage/commit/push；本轮 full 未通过，不能授权交付APK。runner/install/设备/mic/private/ASR/latency/PSS未运行。下一步需要父另授缓存解析调查/必要prepare范围；不自动重跑。
