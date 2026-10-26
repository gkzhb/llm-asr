# 修后正式 full（第二 attempt）— FAILED / 首失败停

`bde7c0561` 正式 failed exit1，owned_exited/root_reaped=true，noBG；仅执行本次授权一次，未自动第三次 full。首次 `b14f54fea` 编译遗漏失败历史/原1168备份完整保留，fixture修复及 fresh接受不被改写为全链成功。

## 当前首阻塞

`:verifyHost` 内 `tests/kotlin_testhost_migration_test.py:76` 的 `OWNERS['FakePorts']` 仍固定旧fixtureSHA，拒绝已获授权且实际 instrumentation编译/7JUnit通过的新fixture。`FixtureChecks`亦独立固定旧SHA，尚未走到其拒绝。具体报错 `ValueError: T5 source/body/fields/Throws/null/TWR identity: FakePorts`。这是既有精确源码身份 gate拒绝，不是生产mutant目标红。该checker不在本次允许写域，**未更新pin、未改语义断言/门槛、未重跑 full**。需父独立处置精确T5 guard与相关验证/后续full授权，不自行更新hash凑green。

真实日志 `.work/resident-full-2/formal-full.stdout`、`formal-full.json` 和 `failure-audit.json` 保留完整 argv/exit/root/streams；128actions executed，Gradle1/tee0/scan1，`full-liN5s3/build.log`。前序actualAGP/D8/三个APK、testhost7JUnit、三包parsedDEX/签名/隔离子gate、strictchecksum原0/坏1/恢复0，以及部分原host ABI/mutants通过；**后续required host/JNI/Lint/最终product gate未全部完成，不等全full验收**。

## 本轮输入与备份

49review候选原冻结、227build/10module/82native/9tool新freeze；失败后current build/native及候选SHA全同。777个本次已知generated/报告/partial文件备份核验后13精确fresh roots清理；原nativeobjects/cache/vendor不清。旧首次失败13seal引用及1168backup仍字节完全同。详 `full-repaired-inputs.json` 与 ignored `.work/resident-full-2/`。

本轮三包子gate receipt `build/verification/android-test/run-5fa24a59e69b4232957b92d01b1a853c/receipt.json`，SHA `ad6ae9c1705bd5719b6c683b1c1fabc7b85045642d2de63aa639cb9b05b18f3a`，其中build/module maps匹配本次freeze；该receipt只证明已执行子gate，不能替代失败的完整full。

| 未整体验收APK | bytes | SHA256 |
|---|---:|---|
| android/app/build/outputs/apk/debug/app-debug.apk | 5317469 | 09651a661b94b0448ff680b988a411640555a1286ceb9077f8eee98aca97fa0b |
| android/test-host/build/outputs/apk/debug/test-host-debug.apk | 2665500 | 09db677924b9b0ac138ad6a5247ff37f8cf3ba58c1b451b771994658938731e8 |
| android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk | 66382 | 4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8 |

## 停止与边界

无生产/测试/checker源码修改，无下载、无第三次full、无stagecommit；HEAD仍f7d1d79/index空，writer正式停写noBG。APK不可交付。Queue仅deferred taskbody非设备offmain；14编译方法非runner；Android设备/mic/private/model质量PSS热测/noBackup真实故障/驻留B均未测。原A每请求load/free不变。父需决定T5精确身份/语义同步授权，安全提交仍后置。
