# llm-asr-zcm formal full — FAILED / 首失败停

唯一正式 full `b14f54fea` 已真实退出 **1**，owned root reaped/descendants exited，noBG。基点 `f7d1d795f591299a3aa70194537652ad33691275`，index空；未提交、无可交付 APK。命令仅执行一次：

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

## 首阻塞

`:test-host:compileDebugAndroidTestKotlin`：`android/test-host/fixtures/java/org/llmasr/minimal/testhost/FakePorts.kt:85:5` 的实际 `FakePorts.Backend : ImeController.Backend` 未实现本轮新增必需 `requireModelReady`。这是实际仪器测试 fixture 编译遗漏，不是 production mutant 目标红；该文件未在本批允许修改域。**未自行修改、下载、削 gate 或重跑 full**。父须另定精确 caller 修复/审查/验证与 full 授权。

日志：`.work/resident-full/formal-full.stdout` SHA256 `d61d365b9f4d7f5790748d5180e92d0f361e768567bbe6bed57e22867d1b34ef`；stderr空。`formal-full.json` exit1、owned_exited=true、root_reaped=true；Gradle 101 actions executed，`PIPELINE_STATUS Gradle=1 tee=0 scan=1`，`full-0P5v7a/build.log`。前序 App/runtime/test-host main SDK/AGP/D8、App和host debug assemble 已执行，但**不等于完整 full/三包验收**；后续 host/签名包 gate/JUnit/Lint/checksum等未全部执行，不以旧结果补齐。

## 输入与产物保护

准备时正式 collector freeze227 build/10 module/82 native/9 external-tool 输入；45review候选身份与正式失败后源码均同。1168个精确 generated/报告/旧包文件已字节与mode备份核验，再清22 known generated roots；缓存/native objects/vendor未删。旧28对象保留于 `.work/resident-full/backup-generated/`，新输出正常覆盖部分APK路径，旧历史 receipts/报告不冒当前产物。

当前仅部分未经完整验收 APK（不得交付）：

| 路径 | bytes | SHA256 |
|---|---:|---|
| android/app/build/outputs/apk/debug/app-debug.apk | 5317469 | 09651a661b94b0448ff680b988a411640555a1286ceb9077f8eee98aca97fa0b |
| android/test-host/build/outputs/apk/debug/test-host-debug.apk | 2665500 | 09db677924b9b0ac138ad6a5247ff37f8cf3ba58c1b451b771994658938731e8 |

新 instrumentation APK及本轮三包正式 receipt **未生成**。原始输入/备份/失败审计：`.work/resident-full/{source-freeze.json,generated-before.json,failure-audit.json}`；非敏感分域摘要见 `full-inputs.json`。

## 未执行/下一步

full NOT PASSED。不补做后续 gate、不复用旧APK宣布通过；无stage/commit/push。Android runner/设备/mic/private/模型计算/质量PSS热测/真实noBackup故障/驻留B仍NOT RUN。本轮A仍每请求 native load/free，无驻留性能承诺。唯一下一步父接受首失败并决定精确 fixture caller 补域与新验证授权；当前 writer 停写 noBG。
