# B formal full3 — FULL_ENGINEERING_PASS（未提交、未交付、非设备验收）

## 身份和真实终态
Task `llm-asr-zcm/B`；执行 writer `residentb-build-2`，父 `p1-steward` 唯一 Beads writer。依据 full-3-authorization 及完整继承授权，main/base `8b71b56b8927031be938d188bacafe0b530a4b8a`，index 空。前置 review-8 为 `newapi/gpt-6.1-sol/high/fork:false` 同模型新上下文隔离、**非跨家族独立审查**，父已接受 NO BLOCKING。

唯一正式 attempt3 原命令：

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

后台 `bd470b659` 自动 completed **exit0** 已消费；原 owned root reaped/owned_exited、无 timeout。耗时2308.89秒，Gradle `BUILD SUCCESSFUL in 38m 26s`，**184 actionable tasks: 184 executed**，`PIPELINE_STATUS Gradle=0 tee=0 scan=1`（scan1是无 DEX 告警匹配，不是失败）。230条 Task header 包含两次分段打印 core:test、无动作 lifecycle、预期 NO-SOURCE/KGP检查 SKIPPED，不冒230个 action。没有 retry/fourth full、产品修绿、跳任务或改门槛。

完整 stdout SHA `e44da1b6b4417adb0b6b950148769574b65860304e1f6e726511093b84b90fb4`；stderr为空/SHA `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`。Gradle tee body 与原入口 preamble/trailer 合并后逐字节等于 root stdout。完整 raw/owned账本在 ignored `.work/residentb-full-3/`，非敏感输入和产物身份见[输入报告](full-final-inputs.json)。full1/full2 原 FAILED/raw/seal/报告不改，不将此前 targeted 或 partial gate 替代本次 full。

## 实际执行门槛及分层证据
- AGP8.6.1/Gradle8.7/Kotlin2.0.21/JDK17.0.20.1 真实编译、D8、三 APK assemble/package，strict/offline/cache-only/--rerun-tasks。原1worker、Gradle1536m/metaspace512m、JUnit512m、Kotlin in-process、native parallel2和所有原子timeout不变。
- 原 verifyHost 全 required：Java ABI876、owner取消/cleanup、Next/compat/IME/runtime、T5普通及-O各9routes（3目标 mutant compile0/exact red1/restored0）、faultoracle1–4/early-null、proof mutants/actual AndroidModelVerificationIo host fake/corefault反向publication及普通既有required。T5 fault5/6继续精确NOTRUN，host fake不冒Android。实际 Adapter host/JUnit均完整14groups441checks，三finding回归保留。
- SDK javac-h/JNI实际四方法 descriptor/export、arm64对象真实编译；不是四CPU架构，也未loadDSO。实际 buildNativeArtifact 正常 prepare/SDK/CMake/Ninja/strict：当前 production源根正常增量，578对象保留且launch/current/provenance逐SHA相等；Ninja本次 **1个真实link action**，非重编578，未复制candidate对象/DSO冒任务。生成5,406,248B批准DSO SHA `4ee0b300caa099a7ef0358fdf42901b9f963d5e2be8fb8e330fb5cad55394481`。
- 原native数学/源锁/patch/tree/provenance/config、静态C++单DSO、四JNI、ELF AArch64/16KiB LOAD/W^X/RELRO/NX全部原strict gate实际通过；native输入84同launch。新增native合成probe未接full入口，继承已接受同源targeted，不声称本full重跑新probe或模型推理。
- 真实 strict checksum目标 expected全0/actual `9697733a215f2ed42e5b2fc4053484ec832eab29584ebced94d92c91090051c1`：原0→变异1→恢复0，非cache/编译失败。真实Lint默认green0→NewApi目标red→恢复green；Lint工具身份66artifacts/171edges。两合法fixture mutant实际编译后payload/cleanup各精确失败、两次恢复全绿，独立隔离输出不覆正式host XML。
- 模块唯一生产owner app8/runtime6/core52、实际模块compile正/负边界、真实全部DEX/Manifest/assets/权限/测试隔离/签名、APKchecker/architecture/buildinput/format130与负例、nativeinput普通/O等均按完整verifyDebug执行。

## Fresh JUnit：42测试，0failure/error/skipped
三 XML 在本次fresh目录生成，精确method/class库存由原checker和postaudit动态核，不用旧XML：

| 模块 | tests | SHA256 |
|---|---:|---|
| core | 34 | `70207ae602817e8b9ab13e27e966dab500b8dbc5028e9081dc6c9ac29085e25d` |
| asr-runtime | 1 | `caa1d76fdb65f04fb659bd02432bfa45b4ede0ff320f5319c4746e83018afdd9` |
| test-host JVM | 7 | `8d037f1cf132ab7bd5d0dacdb3b01f27e48333de2badd75592c266a8e4f9fc51` |

runtime一个45000ms wrapper调用完整main，system-out **14groups441checks**，private fixture闭包真实AGP compile成功，exact2testowner/Werror与断言无改动。androidTest **14个compiled-only方法**是包内解析库存，**不是Android runner执行或发现通过**。

## Lint真实分类，未抑制
app24 Warning：SetTextI18n15、UnusedAttribute3、UsableSpace2、GradleDependency1、ChromeOsAbiSupport1、DataExtractionRules1、MissingApplicationIcon1。asr-runtime17 Warning：SetTextI18n15、UsableSpace2。两模块0Error/Fatal。均沿原defer/工程限制保留，不新增baseline、不关警告、不宣称零warning；CMake旧minimum版本deprecated、Java deprecated API及aapt2 override实验提示亦原样保留，不是D8告警。

## 正式两receipt和三APK（身份未更名，尚非交付）
- 产品正式报告：`android/app/build/verification/debug/run-5b523637fdbf4d5a886e9de2b9d881a0/`。
- 三包正式receipt：`build/verification/android-test/run-33bf3185f4c242518306b7177042abe8/receipt.json`。
- 两者build238==launch==current；三包module10==launch==current，native84==launch==current。standalone tool11逐SHA同；三包实际tool5==launch已保护旧receipt5==current5，重叠3同，合并13真实external工具（两optional测试jar不假塞原11map）。native compiler/readelf/CMake/Ninja同时与批准身份同。不回写maps或receipt凑数。

| APK路径 | bytes | SHA256 |
|---|---:|---|
| `android/app/build/outputs/apk/debug/app-debug.apk` | 5352541 | `cdf9ed7d86a1e52c11ce9d998f3d47b73265bdd0ba9773f6eebb830ecac0f49f` |
| `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2685540 | `ee978b23ad5993c2d2055f49ca424a4522800c73fb249941a0c792fdfadc98cd` |
| `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 66382 | `4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8` |

三包均原v3证书公开SHA `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`。产品 `org.llmasr.minimal / 0.6-debug / code6 / min29 target35 / arm64-v8a / RECORD_AUDIO-only`不改。与此前partial三包字节相同是确定性重建结果；本次实际fresh assemble和**新正式receipt绑定当前已修测试输入**，不拿旧包/旧receipt冒成功。不读/hash/复制签名秘密key。

## 冻结、备份、监督和未测边界
正式launch67候选=已审65+本次2新报告，仅formatter两库存行prelaunch机械变更；产品/fixture/测试/Gradle/Manifest/nativeSHA冻结。928明确known generated文件逐mode/bytes/SHA新备份（17,744,791B），25必要class/JUnit/Lint/APK路径fresh，无gradleclean/广泛cache拷贝/删除；578native对象留原current根。721有限保护记录逐同，旧2136+955备份及大历史继承父已核证据，不重扫整树。旧seal/review/nativeidentity/workbuild不动。

337本次owned记录/1011完整record+stdout+stderr身份，1100 distinct PID-start均无同身份活进程，无lease/unknownexit，noBG。276子exit0/61子exit1为required负例记录，正式root0；不以子red误报全链失败。Ignored额外postaudit两次机械断言错误（tool5非11子集、stdout含wrapper前后缀）保明文history，窄纠正静态审计后通过，无工程重跑/输入或原map修改。

仅本status与两新报告在终态后写supplemental结果，launch字节保 `.work/residentb-full-3/launch-supplemental/`，source-freeze/candidate-full/supplemental-delta分域；这些报告不是full在其终态文案或新commit对象上重跑过。**本writer无stage/commit权限**；父消费本次证据后还须安全候选审及另授精确stage/cached审/正常本地commit。当前不向用户宣称APK可验。

实际loadedASR/模型请求隔离和质量、Android runner/设备/麦克风/IME系统/SAF系统运行、4KiB/16KiB设备、PSS/性能/ASan均 **NOTRUN**。本次只required固定公开样本/两config读取打包，不模型graph/weight/tokenizer/私音频、DSO推理、ADB/模拟器、下载或远端操作。
