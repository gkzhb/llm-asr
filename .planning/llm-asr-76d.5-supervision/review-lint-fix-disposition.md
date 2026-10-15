# 三处生产Lint修复审查处置

Task llm-asr-76d.5；父/唯一Beads writer p1-steward。main HEAD7eace17a8ea636db6010f95d2229c28dc2f78467。

## 接受的审查结论

已完整消费 `.work/p1-lint-fix-review/final-review.md` 及 independent-checks.json。lintfix-review为newapi/gpt-6.1-sol/high fresh standalone，同模型上下文隔离，非跨家族独立。三处授权修复源码及现有回归无阻塞；30项独立只读核对通过，194build/delta/436历史/两模块XML/guard次序/35JUnit/首尾输入及HEAD-index一致。审查不运行Gradle/Nix/Lint或产品测试。

helper精确API契约与真实SDK guard保持；录音异常固定安全IOException及原gate/finally保持；staged Manifest删除冗余debug属性由既有debugDSL接管，最终签名包检查仍pending。baseline-inputs仅授权两源pin及scope更新，不是Lintbaseline。

freeze957051a9977f1bda2a2458b66041fb279ca6fa3275965e80fcad8ed9743bbf98是blocked检查点，不能当Lint/full通过或APK绑定。

## 剩余阻塞及精确待授权scope

runtime空Manifest缺RECORD_AUDIO静态声明，App已存在相同声明；catch权限撤销异常不替代module Manifest能力声明。原B3a未执行runtime report，当前是首次暴露而非旧已通过报告回归。

推荐仅给android/asr-runtime/src/main/AndroidManifest.xml补一条无附加属性uses-permission RECORD_AUDIO，保留App原声明，不增加权限种类/flags/maxSdk/uses-permission-sdk-23；library对未来消费者贡献改变，需用户精确授权后才实施。

授权后需窄缺失/额外权限负例、实际runtime/App merged/packaged Manifest精确permission集合与microphone策略、统一strict/offline Lint两模块零Error/Fatal。随后full/当前签名APK现有精确RECORD_AUDIO-only/debugSDK组件签名gate及新输入绑定。无需新依赖或抑制，声明不授予运行时权限、不授权设备采音。

App17warning/runtime10为同源重复，按原类别defer，不扩大UI/ABI/依赖/backup范围。当前保持blocked，无writer运行，不full/commit/交付，等待用户批准上述单一module声明补全scope。
