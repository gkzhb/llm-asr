# llm-asr-zcm formal full — 第三 attempt 工程通过，未提交交付

**`b1503c412` 正式 exit0，184 actions 全部 executed；完整 post-audit/noBG 收尾完成。尚无安全候选审/本地提交，不能告知可验证 APK。** 基点 main `f7d1d795f591299a3aa70194537652ad33691275`，52review候选冻结下运行，index空；此前两次真实 failed1 历史不改写。

## 执行与真实终态

唯一授权命令：

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

固定 offline/strict、单worker1536MiB/JUnit512MiB/Kotlin inprocess/native低并发；outer72000仅包络，原host/四proofmutant/420actualIO/600corefault/T5各3200/fault2800等原子预算不变。实际耗时2326.14秒（Gradle38m43s），`PIPELINE_STATUS Gradle=0 tee=0 scan=1`，`full-fqCyNa/build.log`。无下载/写锁metadata/-x/门槛放宽/源码修改，标准debug签名仅原任务，无读导出key。

原始 `formal-full.stdout` SHA256 `4117bc6bb42cf46893914038a611446a270b8405eac3140c987cce5932eb1235`，stderr空；`formal-full.json` exit0/owned_exited=true/root_reaped=true。额外335条本次真实嵌套owned记录（274×0、61×1，含各driver预期拒绝/目标负例，不将所有异类exit1合称同一行为红）及对应stream SHA/退出/root逐条核验；模拟unknown/cleanup策略负例不冒真实存活进程。完整父owned session终态确认不以agentfinal代替验收。

## 全required工程门槛

| 层 | 本次实际结果与边界 |
|---|---|
| 当前App/runtime/testhost | AGP8.6.1/KGP2.0.21/SDK35/JDK17/Gradle8.7真实编译/D8/三包assemble；无malformed metadata/D8/R8诊断 |
| 原host | ABI/null/identity/取消/latch/lease、K2J/testslice/jvmnext、原next/compat/IME/runtime及所有原正式门槛通过；host/fakes不等Android系统 |
| T5同步 | source/self ordinary/-O通过，3owner各green/red/restored共9+9路由、faults3路由；旧Git原bytes与显式adapted historical interface bridge provenance保留，非原Java已支持proof；fault5/6 precise NOTRUN |
| 模型proof | ordinary/-O各9compile/11JVM，四实际生产mutants compile0→精确AssertionError1→restored compile/run0；跨JVM恢复/撤销/取消/请求0hash |
| 实际AndroidIO host方法 | 普通/-O各420入口实际source rawFD单owner/close-before-rename/partialwrite/限额/异常与suppressed验证；不是实际Android Os probe |
| 核心fault | 普通/-O各600入口1compile/4JVM；真实Controller3反向publication场景+8COMMITTED失败/rollback/lateepoch与registeredworkers；combined仅实观marker-first，restore-first仅合法允许非穷举实测 |
| JNI/native | 当前Java JNI headers/descriptors+实际object与DSO exports核同；明确native源码/578objects/provenance/数学config/arm64单DSO/staticC++/ELF W^X RELRO nonexecstack 16KiB全部原gate通过；无模型运算 |
| current module | 实际Gradle输出唯一owner App8/runtime6/core51、全部guard正负例普通/-O，非仅前序finiteSDK替代 |
| JUnit | 真实XML core34+runtime1+testhost7=42，全fail/error/skip0；14仪器方法只compiled/DEX解析库存，不等runner运行 |
| checksum/Lint负例 | strict checksum original0/具体坏hash1/restored0；实际Lint身份错误拒绝+defaultAGP合法green/NewApi目标red/恢复green及fixture两合法行为mutants恢复通过 |
| 三包 | 当前signature/v3/Manifest/permissions/allDEX class_def唯一owner/隔离/assets无权重/标准runner/14methods/zipalign与原product identity gate通过 |

## 当前正式产物身份（工程通过，未提交不交付）

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | android/app/build/outputs/apk/debug/app-debug.apk | 5317469 | 09651a661b94b0448ff680b988a411640555a1286ceb9077f8eee98aca97fa0b |
| host | android/test-host/build/outputs/apk/debug/test-host-debug.apk | 2665500 | 09db677924b9b0ac138ad6a5247ff37f8cf3ba58c1b451b771994658938731e8 |
| instrumentation | android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk | 66382 | 4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8 |

Product仍 `org.llmasr.minimal`/`0.6-debug`/code6/min29/target35/arm64，RECORD_AUDIO唯一App权限。三包同原证书 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`、v3=true。native SHA `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416` 保持；APK与前次partial bytes相同不代表复用旧验收，本次fresh真实assemble及全部gate/input receipts已执行。

## 两正式receipt与分域输入

- product report：`android/app/build/verification/debug/run-5c4d75c3bc8d437abf36effc293e0708/`。
  - result.json SHA `65fb60287f828e52d2ed7af4e29bfab66cd12f415d3d89e8fc42e06256a7c214`
  - build-input-sha256.json SHA `cf68c747d3904169706f8af45246ca8a74181504bcb99e9add140c57a30c594d`
  - native-verification.json SHA `efd36835e239b2122a335e367d7ae3aa5cdcb85f843e8aab3052f89c93b9b0eb`
- 三包receipt：`build/verification/android-test/run-f2989fa32dc54e3993ec15194299c56d/receipt.json` SHA `18321e4d4d1a7764f07fcc1319453c2382281dcfb2c6ce2fefcb2da596c415af`。

本次prelaunch freeze动态227 build/10 module/82 native/9 external工具；build/module两receipt及当前collector精确相等，native82与本次正式verification/current SHA相等；三包5工具map对照原immutable Nix工具身份核同，包含optional runner/base。工具/provenance/资产摘要分域见 `full-final-inputs.json`，无模型权重/音频/private/key字节交付。status+两新报告只supplemental，不进入APK collector；当前source/test仍52freeze同。

## Lint与其他warnings人工分类

实际App24 Warning：SetTextI18n15、UnusedAttribute3、UsableSpace2、GradleDependency1、ChromeOsAbiSupport1、DataExtractionRules1、MissingApplicationIcon1；runtime报告17 Warning（链接源SetTextI18n15+UsableSpace2），两者 Error/Fatal0。App与prelaunch旧报告按id/severity/message/file去行列比较完全同，无新增类别/消息；runtime链接位置同这些历史App源，新proof/rawFD源无diagnostic location。i18n资源、旧Manifest/API兼容属性、存储API建议、固定工具注解版本升级、仅arm64/图标/backup声明建议继续明确defer；未抑制/写baseline/擅加依赖权限。原experimental aapt2 override、vendor CMake minimum deprecation、Java deprecated API说明保留，不写“零warning”。

## 保护、历史与未测

本次797个已知generated/三APK/classes/JUnit/receipt备份字节核验后15精确fresh roots清理；不清native缓存/objects/vendor，不宣称全clean/空缓存hermetic。首次失败1168、第二次777备份及两失败seal/raw/报告全同；旧历史receipts保留，新正式输出仅正常生成。原excluded保留，新增仅授权新plan/报告，无未知源码/index变更。

设备/Android runner/真实noBackup Os故障与断电/4KiB或16KiB运行/SAF系统生命周期/fcitx5往返/mic/private/模型推理质量/PSS热测均NOTRUN。Queue仅deferred task-body非设备offmain；历史oracle仅interface适配。A持久proof/启动轻恢复/输入前准入已工程验证，**native仍每请求load/free，不是驻留B、warm性能承诺或质量验收**。

唯一下一步父消费full原始证据→fresh只读候选安全审→另授逐路径stage/cached完整diff/blob审→正常本地commit。当前writer无提交权，未commit成功不得交APK。
