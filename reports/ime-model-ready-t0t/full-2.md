# llm-asr-t0t 第二次正式 full — FAIL / Lint annotation 配置缺离线闭包

## 身份与命令

main/base `64d00d16e13577b9e6ad049511acd96b3e7d7d6b`，在已审七候选 dirty freeze 运行，无新 commit。原 full1/status/full-inputs 保失败历史未回写；本文仅第二批 supplemental。

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

实际 supervisor 仅固定项目 Gradle home/记录白名单环境后 exec 原入口，**没有 prepare init**。Gradle strict/verify-metadata=true/现锁、Nix cache-only/原SDKJDK/worker1/JVM1536/JUnit512/native2和原各内部预算均保持，无下载、skip、新 expected 或第三次 full。

宿主 `b4816f8af` **failed exit1**，owned root_reaped/owned_exited=true、primary=null；Gradle `BUILD FAILED in 50m54s`，**131 actions executed**，pipeline Gradle1/tee0/scan1。完整log `.work/verify-local/full-RwsT6e/build.log`，raw/argv/exit `.work/t0t-full-2/commands/full/`，身份见 [本次inputs](full-2-inputs.json)。

## 新具体 blocker

`:asr-runtime:extractDebugAnnotations` → `:asr-runtime:detachedConfiguration1` 离线解析 Lint31.6.1 闭包时缺：

- `org.jetbrains.kotlin:kotlin-stdlib:1.9.20`
- `org.jetbrains:annotations:23.0.0`
- `commons-codec:commons-codec:1.10`

这些来自现 Lint/common/layoutlib/sdk-common/httpclient 传递路径，不是本轮改版本。不能用“No cached version”推定jar物理不存在，不自行force到其他版本/补锁/降校验/联网prepare。首停，未自动 full3。

## 本次实际已执行（不等整个full成功）

- app/runtime/core真实SDK AGP编译，app D8/签名/package/assemble；测试host/instrumentation编译与两APK构建。
- JNI辅助SDK35全维护源码mixed编译、arm64 JNI实际符号、原native增量链接/provenance/ELF门通过；非全native clean/模型计算。
- `:test-host:testDebugUnitTest` 当前7JUnit，0failure/error/skip。
- `:verifyAndroidTestPackage` 当前三包静态checker/独立receipt通过，绑定239build/10module/5tool输入；全部DEX/Manifest/签名和runner库存是静态，不是Android runner执行。
- `:verifyDependencyEvidence` 真正既有checksum目标原0→精确AGP校验红1→恢复0通过。
- `:verifyHost` 完整运行，包括ABI/compat/新旧compiled mutants、readiness普通/-O、永久cleanup故障普通/-O、旧IME返回、worker生命周期等；不是只复用旧targeted。
- `:verifyJni` 单独执行通过。

未到达根core34/runtime1 JUnit任务、正式app/runtime Lint及其负例、独立产品`:app:verifyDebugPackage`、末尾Android fixture行为五阶段。不能用host直接调用相同测试体冒JUnit runner数量，也不能用本次三包静态receipt冒缺失的完整产品/Lint门。

## 当前三包（不是可交付包）

三包本批均有assemble/receipt证据；即使host/test字节与旧包相同，也按本次执行和receipt核对，不拿旧报告代替：

| 包 | bytes | SHA256 |
|---|---:|---|
| app-debug.apk | 5352953 | a16027cbd7764a9cf46d944c1623a51763aa82ab9ebe67135c90efdd8858a4f3 |
| test-host-debug.apk | 2685540 | ee978b23ad5993c2d2055f49ca424a4522800c73fb249941a0c792fdfadc98cd |
| test-host-debug-androidTest.apk | 66382 | 4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8 |

产品org.llmasr.minimal/0.6-debug/code6/min29/target35，证书SHA256三包同 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`。当前产品SHA不同旧553cf938…94cd；host/test字节相同。receipt `build/verification/android-test/run-d9fceb58ef6d4b47bf88a29d1221aa94/receipt.json` SHA `eaf3dc7b53129cb0be1d278099d02fb5187bfe6d7c514a4b119f961c7019a93a`；独立仅读审核 errors=[]、239build与full2freeze一致，APK/module/tool逐SHA当前吻合。此非完整full接受，不安装/交付。

## 保护与警告

前置2944已知generated文件/325498880B tar逐SHA验证，精确清单 `.work/t0t-full-2/before-backup.json`；full1archive/reports及七candidate保持，父supervision明确可变supplemental单列。最终保护audit真实exit0/errors=[]：历史41505输出原位字节保持、七candidate/index/full1reports/archive保持，正常generated32项更新有备份；known generated按本批授权正常更新，不清cache/nativeobjects/整个work。新增本两报告不进入产品库存，未stage凑239库存；current native84项及module10/tool5/build239分域逐SHA在inputs报告，所有本批后台已真实终态。

仅见原aapt2 override experimental、CMake旧minimum版本弃用提示和Java已过时API说明，未D8 metadata异常；不抑制或降gate。Lint尚未执行，不称本批0Error/Warning。

## 后续 / NOT RUN

父消费新具体Lint注释提取配置cache blocker及保护证据后决定最小准备scope；本leaf不自动prepare或full3。完整full通过后仍须fresh安全stage1及另授限定本地commit，**当前不能宣称APK交付或关闭任务**。

Android runner/系统IME/fcitx5/Binder/真实生命周期、设备/mic/private、模型/性能PSS、远端CI均 NOT RUN。
