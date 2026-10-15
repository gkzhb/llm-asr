# 三处生产 Lint 窄修复：回归通过，统一 Lint 仍 blocked

Task `llm-asr-76d.5`；父 owner/唯一 Beads writer `p1-steward`；源码 writer `lintfix-build`，交接后停写。base/HEAD `7eace17a8ea636db6010f95d2229c28dc2f78467`，index空，未提交/交付APK。

## 终态结论

**BLOCKED_RUNTIME_PERMISSION_DECLARATION**：三处获准修复完成且相关回归通过；唯一真实统一 Lint exit1，不能进入full。原 B3a 11 Error+1 Fatal/18 Warning保存在 `.work/p1-b3/`，未改写。当前两报告：

| 模块 | Error/Fatal | Warning | 结果 |
|---|---:|---:|---|
| App | 0 | 17 | 原12blocking全部关闭，lintDebug完成 |
| asr-runtime | 1/0 | 10 | 首次standalone报告暴露空libraryManifest未声明RECORD_AUDIO，lintDebug失败 |

runtime新文本是 `Missing permissions required by AudioRecord.AudioRecord: android.permission.RECORD_AUDIO`，`ForegroundRecorder.java:20:22`；与原App“需checkPermission或显式处理SecurityException”不同。当前授权禁止权限/Manifest政策变更（仅准stage冗余debug），因此未改library Manifest/未加注解抑制/未第二次Lint猜绿。父需review并决定最小module权限契约授权，不应把此结果写成Lint pass。

## 实现与验证

- ActivityWindow仅新增API30/33精确TargetApi类契约，原SDK_INTguard全部保留。82fakechecks、7个可编译production mutants及source契约/负例通过；原10NewApi和InlinedApi自然关闭。
- ForegroundRecorder只在原try/finally外处理SecurityException为固定无cause IOException，原start/cancel/commitgate和finally/release/captureReleased不改。真实Java capture+真实Kotlin gate/PCM/WaveInput、Android媒体/clock fakes，12phase（8权限失败+cancel/fatal/success/illegalStop）通过。不是设备录音或完整Android运行。
- AGP stage副本去debuggable，源legacyManifest/debugDSL及packagechecker保持。9个真实stage/validate fixture通过，完整XML树除明确AGP-owned字段外精确相等及错误源debug拒绝。两项批准生产源码pin更新，native/signature/assets/源Manifest pins不变。最终APK debug=true/package/sdk/signature仍待后续full，不以fixture或Lint当包验收。
- 原host全部gate/mutants通过；Android10Kotlin+53Java、JNIheader/NDKobject/符号通过（非runtime）；fast27s/23action执行，core34+runtime1共35JUnit零fail/error/skip；format/diff0。
- 统一命令 `bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh lint --offline --rerun-tasks`，bg b8c883f16 failed1，126.473s/72action。13gate+8consumer各66artifacts/171edges，在action前身份通过；App原native prerequisite保持，无下载/依赖锁hash写入/版本变化。

## Warnings分类defer

App17：SetTextI18n9、UnusedAttribute3、GradleDependency1、ChromeOsAbiSupport1、DataExtractionRules1、UsableSpace1、MissingApplicationIcon1；原18仅InlinedApi1关闭。runtime10是同源SetTextI18n9+UsableSpace1重复报告，不是10个新增独立问题，无新warning类别。按原scope保留：不升级依赖/扩ABI/全UI资源化/扩backup与Manifest政策，不baseline/SuppressLint/关error。

## 输入与证据边界

新194build相对旧B2的193：新增capturetest1，8变化/185保持/0删；变化为两授权生产源、baseline两pin+scope、stage、host接线及三窄测试。旧94protected中仅两授权源变/92保持；native80库存因包含Java源仅此两项变/78保持，不是native数学变化。原collector审批span/helper、metadata/四locks/签名/源Manifest保持；436份旧B2/review/B3文件逐SHA保持。旧freeze仍历史，不宣称current相等。

详见 `.work/p1-lint-fix/handoff.md`、`command-index.md`、`diagnosis-runtime.md`、`lint-summary.json`（含每issue/fileline/message及XML/HTML/TXT SHA）、`input-freeze.json`、`evidence-index.json`。新freeze是blocked检查点输入绑定，不是成功Lint或APK身份。

## 未运行与下一步

未运行full/完整Lintfixture矩阵/APK assemble与包输入签名绑定、设备/麦克风/SAF/IME/JNI运行、权重/私人数据、远端CI、stage/commit/push。**下一步父fresh同模型上下文隔离review（非跨家族独立），再决定runtime权限契约scope；本leaf停写，不自行full。**
