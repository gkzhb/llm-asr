# 第二次正式full与当前产物绑定

状态：**full0 + binding0，待安全本地提交，尚非APK交付**。testedbase `326c9be5c367e4d33fe88fc21729cce4cb5ee9bd` +16approveddirtyfreeze，非未来commit对象。runUUID `97e15c20ec1246019d930000dfc4054f`，原cache-onlystrict/offline/rerun入口，正式attempt2，工程预算15000秒，实际 `6035.382` 秒。

原命令：`bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks`。原worker1/nonparallel/Gradle1536MiB/meta512MiB/Kotlininprocess/JUnit512MiB/native2及所有innergate预算保持；无clean/-x/依赖更新或checksum绕过。cache已准备不代表全新缓存/hermetic。

当前rawlog `.work/verify-local/full-kRicPP/build.log`，SHA `5d87da5ae25d24c869af0aadb8c5d05f84a1ec977c4da3ff0a9b9b6713338e78`；formalstdoutSHA `190c19c03ee090dc57bb3cb091d7b1a4234e99dfb171723302ad6d913b87745f`。唯一rawmarker选择产品receipt `android/app/build/verification/debug/run-e3e959d0f67841e0801417ded6e60d1f` 与三包receipt `build/verification/android-test/run-e491709ca12d4c72ab368aabc04b258e`，不按mtime或旧receipt选。动态build `265` /module `10` /native `95` /维护 `221` 输入绑定，详full-inputs.json。实际actionsummary：184 actionable tasks: 184 executed。

## 当前三包

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
|product|`android/app/build/outputs/apk/debug/app-debug.apk`|5581825|`fa506814786197f5e2efc91763f0a4f2f41712c5f50cf42ca4a8ac841260d035`|
|host|`android/test-host/build/outputs/apk/debug/test-host-debug.apk`|2866372|`c1698912edb4911e2c17827918b0ca2d4cbebb9e10ed917a6e4c96a877a9b57a`|
|test|`android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk`|66382|`4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8`|

产品 sourceManifest/DSL/AGPmetadata/actualbadging/result均10 /0.10-debug。独立host1/p1b-testhost和test包既有身份保持。三包固定证书e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c/v3/zipalign，完整DEX owner/testfixture隔离与14compiled-only仪器方法、仅产品RECORD_AUDIO/IME/assets/native门槛通过。产品嵌入DSO=当前baseline/strict/provenance SHA `053962bdf047a03dd01201250f8d026fe60e9c50ab479441e2a26ae52b963917`，578vendorobjects/schema/effectiveconfig保持；新UiText嵌套class实际当前SDK输出确认，无新增topowner。真实nativeASR未执行。

## 本次实际JUnit/全门槛

| XML | tests | fail/error/skip |
|---|---:|---|
|`core/build/test-results/test/TEST-CoreRegressionJUnitTest.xml`|34|0/0/0|
|`android/asr-runtime/build/test-results/testDebugUnitTest/TEST-RuntimeRegressionJUnitTest.xml`|1|0/0/0|
|`android/test-host/build/test-results/testDebugUnitTest/TEST-org.llmasr.minimal.testhost.FixtureContractTest.xml`|7|0/0/0|

合计42例，按当前源方法库存逐名匹配，未借旧XML。原host/T5/runtime/Return/NavIME/readyfirstuse/start/history/picker5/nav25/corefault普通-O四fault、ownerchecker+guard20/JavaKotlin/JNI/native/Lint/checksum/Androidfixture/三包签名全部本次执行；所需实际task列表与SHA在full-inputs.json。当前Lint：
- `android/app/build/reports/lint-results-debug.xml`: {"Warning": 19}
- `android/asr-runtime/build/reports/lint-results-debug.xml`: {"Warning": 12}

Lint Error/Fatal 均0。warning分类：App 的 UnusedAttribute3、GradleDependency1、ChromeOsAbiSupport1、DataExtractionRules1、UsableSpace2、SetTextI18n10；runtime 的 UsableSpace2、SetTextI18n10。均保留当前报告与原门槛，未用 suppression/baseline 消警；后续逐职责处理，不等于性能/存储或设备验收。外层还保留 aapt2 override experimental 与 CMake minimum-version deprecation 警告，不据此宣称无警告。D8/R8异常扫描无命中，PipelineGradle0/tee0/scan1。Androidfixture工程和instrumentation静态方法库存不等Androidrunner发现执行。

## history严格同批关联

只消费本次rawlog两exactmarker目录，controlledUUID `97e15c20ec1246019d930000dfc4054f`，独立launchKOTLIN_HOME/stdlibrealpathbytesSHA；原bind_markers/trusted_tools严格验证五summaryfileSHA、currentdriverinput/sourcefake、7compile7JVM精准redrestore、直属driverPID/start与真实terminal/rawSHA，无outer递归ancestry/mtimePID窗fallback。
- ordinary: `.work/build/ime-history-iF9CHW/ordinary`
- optimized: `.work/build/ime-history-iF9CHW/optimized`

## 保留首次失败与验证边界

第一次formal UUID581528d8a3a541e6ba2d4b56b951bb06，strict0/full1/5149.201秒，`:verifyHost` 独立corefaultcompile未带UiText源码导致internal跨模块不可见。原failure日志/部分APK/备份保持；另授单driverclosure修复/targeted/freshPASS后才重试。本次成功不回写旧失败，亦不借旧partialSDK/receipt。

所有旧保护/备份映射与无后台/indexempty事实见inputs.json及本地handoff。未stage/commit；尚无交付许可。设备/runner/SAFgrant/systemIME/Androidunixprovider/mic/private/modelweights/真实ASR质量性能均NOTRUN。
