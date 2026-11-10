# BVA 首次正式 full 与当前三 APK 绑定

**工程验证通过，提交前快照；不是 APK 交付或设备验收。**

## 原命令、终态和资源

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

唯一 formal full actual exit0，elapsed **5650.336s**（工程上限15000；独立 teardown grace 不延长工程时间）。本次 Gradle log `.work/verify-local/full-qKbdRo/build.log`，SHA `609aeb08e184356fe7c16bba4fa8dffd70e8d2775f8d6c2909dde084cb76f917`；原始完整 stdout SHA `865b1365e707db48bcc710fdd7a353d54b858366f062557c3deb8edd27c364ac`，stderr 为空。

184 actionable tasks:184 executed。231 task header rows/229 unique task paths，其中 verifyDependencyEvidence/core:test 流式输出重复 active header，不当重复执行或额外action；NO-SOURCE/聚合/插件SKIPPED正常状态不冒全部task-action。全部 required host/JNI/native/module/Lint/checksum/fixture/APK gates实际执行。管道 Gradle0/tee0/DEXscan1（无目标D8/R8诊断匹配）。

Nix cache-only、Gradle strict/offline、worker1/nonparallel、heap1536m/metaspace512m、Kotlininprocess、JUnit512m/nativeparallel2与全部原inner timeout保持。未clean/-x/属性绕过/下载prepare/改锁metadata/nativeexpected；无第二full或并行heavy。缓存通过不声称空缓存/hermetic。

前置 admit/strict/freeze 各一次真实0，实际 toolreturn/terminal 与 fresh issuer/C27/manifest 联合绑定；strict耗时46.980s。固定red/同inode Java兼容POSIX recordlock持锁完整备份后释放，原测试自行取锁。176旧ledger/PIDstart确认，无真实lease/unconfirmed；不可读服务当批限制记录，不声称全UID可见。

## 当前三 APK（仅记录工程身份）

| 包 | path | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5541757 | `09f2c7544b5aee1b45c4f374ff7578ef1a8c9cc1cff3ba3991a05a6bb3a3bffe` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2831628 | `590c01f1e08a2b4bb0d76e3733457c549aeb87da243b4f7c3858a426979b711b` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 66382 | `4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8` |

产品 code8/name0.8-debug 与 sourceDSL/Manifest、AGP metadata、actual badging/result一致；testhost1/p1b-testhost保持，instrumentation是测试包独立metadata version0/空name，不冒产品版本。三包certificate `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`，actual v3/zipalign均0。完整所有 DEX class_def重解析与原receipt一致：product1505/host1366/test47是本次观测数量，不作为永久门槛；14 instrumentation methods仅compiled/static，不是runner发现或执行。

产品权限RECORD_AUDIO-only、现有IME组件/资产/owner/fixture隔离及Kotlin runtime由原actualchecker与当前rawreport绑定；测试包无assets/native/权限且无targetclass重包。包报告由本次log精确runUUID选择：`android/app/build/verification/debug/run-79b3a6faa8744c21994f445a1b0b3f4b`、`build/verification/android-test/run-cef13469df09418baf5e32b349492b23`，不按mtime找旧报告。

## Native与输入

DSO/embedded/nativebaseline/provenance/strictverification均 `053962bdf047a03dd01201250f8d026fe60e9c50ab479441e2a26ae52b963917`。原buildNativeArtifact正常重编JNI并增量最终链接一次，578 MNN对象SHA与DSO字节保持；不是人为强重链或全nativeclean。当前259build/10module/92native、1757tracked维护文本、9037prepared-source entries、578objects/18externaltools均动态绑定，本页数目只是当批库存。full-inputs.json列源/工具/外部与原输出备份映射。

## 行为与JUnit

core34/runtime1/testhost7，共42实际JUnit XML，failures/errors/skipped均0。新NAV12/IME7 ordinary/-O共四gate、80freshcompile/JVM routes、38编译成功的目标red/restored实例，合计1370.523s；详targeted.md。原全部host/mutants/ABI/PartRecovery/caller/latch/JavaJNI/native/包门槛未删。

独立fixture五phase actual0/1/0/1/0与精确七方法XML/合法payload和cleanup红；真实Lint fixture0/NewApi目标1/恢复0，加artifact/edge/files缺多替换拒绝及普通任务restore；checksum原0/唯一AGP jar目标1/恢复0。编译/缺cache/普通timeout不冒行为红。

## 告警、保护及限制

- App Lint22/runtime15 warnings (including duplicated source warnings), Error/Fatal0; categories in XML
- AGP aapt2 override experimental, CMake minimum compatibility deprecated, javac deprecated API notes; no D8/R8 diagnostic match
- Gradle reported daemon Metaspace exhaustion/expiry warning at configured512MiB; original full completed0 without changing caps; no resource peak measurement claimed
- Runtime/host Lint identities resolving is not host Lint execution; Android runner14 methods compiled/static only

App warning类别：SetTextI18n13、UnusedAttribute3、UsableSpace2、GradleDependency1、ChromeOsAbiSupport1、DataExtractionRules1、MissingApplicationIcon1；runtime SetTextI18n13/UsableSpace2重复源warning。均披露defer，未加baseline/关闭error或扩大本轮修复。无资源采样峰值声明。

953个相关ownedledger逐记录核PID-start现已消失。其中原fault suite故意保留1个synthetic unconfirmed ledger与3markers：原记录false/unknown原样保留，外部observed/emergencyfinally及独立PIDcheck证实无活worker；不把该故障记录改true，不当实际compiler成功，也未删marker。其他真实ownedledger均原确认。所有正式phase与后处理真实terminal消费后才封口noBG。

旧固定输出与三APK原字节按backup-map透明保留，正常测试/构建替换后旧路径binding不再current；旧receipt不改。历史reports/old random children和范围外保护保持，当前27维护源码未改。基点 `8165e26238c37e4ab68cb093f71c0e14406b5b57` + dirtyfreeze，**未测试未来commit对象**。

Android runner/设备任务栈/Back/Dialogqueue/系统权限SAFIME/Bindergrant/pixelsTalkBack、native4K16K、mic/private/真实ASR模型/质量性能均NOTRUN。本地安全审/commit/push/APK交付NOTRUN；父另两阶段提交授权。
