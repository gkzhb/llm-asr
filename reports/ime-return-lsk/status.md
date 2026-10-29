# llm-asr-lsk：返回上一输入法不预先隐藏

基线 main `d5b38ac7713dfb91dd1f261ff9f6a71984fee040`；父 `lsk-parent` 唯一 Beads writer，`lsk-build` 唯一产品 writer。本页为实施/targeted 阶段记录，不是 full、提交或 APK 交付。

## 合同

返回按钮先使本地会话失效、清除私有预览并取消未提交录音，再请求上一输入法；不调用 `requestHideSelf`，包括 false/异常的单次选择器降级。返回 boolean 仅按请求接受处理，不宣称上一键盘实际显示。保留原外部操作 RuntimeException 边界（含不可用选择器的 Toast 失败），Error 不吞。直接选择器、设置/授权、模型管理原 invalidate → best-effort hide → external 路径不变；Controller/worker/native owner 的真实退出与取消仲裁不变。无自动录音/提交/重试。

## 证据边界

诊断旧源码 fresh compile0、两次精确可见性断言1，13组降级/通用按钮控制通过。显式 host fake 将 hide 建模为清除 showRequested，previous=true 只改 selection，不自行新建 show 请求；真实返回按钮暴露 accepted 但不可见，不能据此冒称 Android/OEM 系统复现。当前 SDK35 本地 stub 只能证明签名，不能证明系统实现。

增强维护测试在同一生产按钮路径要求成功不 hide/保显示意图、平台调用前旧会话/预览已失效、false/异常单次降级、null controller、Toast RuntimeException/致命 Error 策略。capture/native/cleanup 三阶段真实 worker 阻塞时通过返回按钮检查 owner 到真实退出才释放、迟到预览拒绝，无自动编辑写入。原 ImeSessionTest owner/取消矩阵及四 IME mutant、旧默认五 mutant/ABI/compat 预算保持。

## 当前状态

增强 caller/fake 已冻结：driver SHA256 `51046ed9a693345f4880a566e6296dc240fdc5eb53ce569ab179e1179e9f53f7`、Java caller `d1658a179a636df6689777ea389bccbdd0e9ab7ad478cd8fb2015da1c4939724`。旧 HEAD Service 独立 fresh compile0/精确 visibility AssertionError1，后台 `b256fcb0b` completed0（正确识别目标红，不是产品通过）。Service 最小 no-hide 修复及唯一 source pin 已应用，原 generic helper/Controller 字节保持；同 caller fixed fresh compile0/run0、普通/-O IME 各四独立 mutants compile0/精确 assertion1/restore0、旧默认五 mutants/ABI 与 compat、SDK35全源48Kt+18Java、fast/graph/format均已有真实终态0。fast27actions全部executed/core34+runtime1 JUnit零failure/error/skip；graph仅38项精确dry-run，format133维护文本通过。四路线68inner owned账本/root确认退出/raw核同/全部生产副本恢复当前字节。三文件复杂度 baseline selftest/两collect/compare均真实exit0，`bf2b34eab` completed0自动终态已消费。cccc1.8.3/knots1.18.0同版本/二进制/参数/精确三文件；stderr空，cccc不覆盖Kotlin，嵌入Python字符串的fake另人工审。Service return knots C2/M3→C3/M4/nesting1，原render C29/M17/nesting5未改。新activeReturn测试 C10/Y18（cccc）与C27/M25/nesting4（knots）、hideFailure矩阵 C32/Y21与C25/M18增长、dispatcher+3及全部重复命名组均已按真实按钮/有界worker/错误外传/四mutants逐项人工处置；未据指标重构或刷新旧基线。此为writer审，不替代fresh审。无需重跑既有成功链。

旧后台 `b2667039e` 自动通知在续接中未收到；父指出磁盘已终态后一次恢复：targeted.exit0/result pass/九top owned0且raw核同，原wrapper PID已消失、exact task registry返回Unknown。保存通知缺口，不伪造后台通知或将unknown视为另一场失败。旧源码红与fixed绿同caller SHA保持；没有fixture修正或放宽预算。

Targeted与有限复杂度完成，最终候选/当前输入/退出/保护证据将封口于 `.work/lsk-build/implementation-handoff.md`；没有相关活构建命令。已知旧三APK/正式receipt/无关原未跟踪及Controller/工具链/依赖保持，主HEAD不变/index空；源码尚未提交，不能宣称APK可验证。

上述fresh审/full未执行为targeted时点。父其后已接受fresh同sol fork:false NO BLOCKING（非跨家族），LSK-R1通用测试catch可掩逃逸RuntimeException、R2Toast.show缺独立注入两LOW覆盖gap明确defer，冻结源码/测试不改。

唯一获准正式full `b5a076152` 真实exit1/362.145秒，`:app:prepareInputs` 拒绝 `patches/mnn-asr-request-cleanup.patch` frozen SHA。current/admission/HEAD实际466ab7d5…f62，HEAD/current baseline期望05952106…392，证实本批前已有身份不一致；未擅改期望或patch/降gate/重试。失败前native/JNI/ELF通过且578对象最终SHA保持；其余full host/42JUnit/Lint/checksum/fixture/三APK包检查未完成，不冒通过。三APK原输出已按授权备份freshen，现无本批可交付APK/新receipt。真实owned/raw/protection退出已封口，无活命令；见[full失败报告](full.md)与[输入证据](full-inputs.json)。父须另定身份阻塞处置及验证授权。未stage/commit，不可宣称APK可验证。

父其后已独立接受原/现patch仅八context空行表示差异与历史六A/B输出/tree/source-lock/provenance来源证明，另授窄metadata传播修复；baseline仅cleanup期望0595→466a，保IMEpin，patch/source-lock/native不变。增强现有stage测试先冻结，同caller旧baseline命名consistency断言实际红；修复后同caller普通/-O完整15tests均0（含旧13、新一致性及真实stage三拒绝）；单测试文件selftest/两复杂度collect/compare0、仅新增2函数且旧指标不变。78文件/四目录身份与最终currentinput/owned/protection封口见ignored identity-repair/handoff，不提升为full成功。原full失败报告/输入保持字节，该targeted时点尚无二次full/APK/提交。见[窄身份修复](identity-repair.md)。

## 修复后正式 full 最新终态（未提交）

fresh identity delta NO BLOCKING经父接受后另授必要full；prelaunch监督同名文件碰撞失败保留，未执行formal；机械续接不重prepare，新owned/summary分离并验证真实准备账本/634备份/3fresh路径。`ba7b6690e` 自动completed0已消费：正式exit0/2294.846秒，184actions全executed/Gradle0tee0DEXscan1，42JUnit（34core+1runtime+7host）零failure/error/skip。原host/ABI/所有requiredmutants/真实模块边界、15stage tests、JNI/native/provenance/ELF、真实checksum0→1→0、Lint默认NewApi0→1→0/共享validator负例恢复、fixture五阶段0/1/0/1/0及三包签名parsed checks通过。app/runtime Lint Error/Fatal0，24/17warnings分类defer不抑制；host Lint未执行，runner仅编译。

当前产品APK `android/app/build/outputs/apk/debug/app-debug.apk`，5352113bytes，SHA256 `553cf93876668121d9af5873db332c8b2e4d4b8dce3078b2f235bba4eaa894cd`，0.6-debug/code6/min29target35/arm64/RECORD_AUDIO-only/原v3证书。238build/10module/84native与独立product/triple receipts/currentfreeze绑定，578MNN对象保持。详[成功full](full-rerun.md)与[当批输入](full-rerun-inputs.json)。完整退出/保护域/13候选SHA在ignored continuation/handoff，132owned/raw与525PID-start退出核验；没有相关活命令。原失败full/prelaunch/targeted历史不重标成功。

当前仍未stage/本地提交，**不是APK交付指令**。父消费后另授两阶段安全本地提交；未提交前不宣称可验证APK交付。系统IME/fcitx5/device/runner/Binder/mic/private/nativeASR/质量PSS仍NOTRUN，host模型不提升设备验收。Android runner、系统 IME/fcitx5 往返、Binder/窗口生命周期、设备/麦克风/private/模型计算/下载均 NOT RUN。后续需父消费 targeted，再 fresh 同 sol fork:false 上下文隔离审查（非跨家族），另授完整验证及两阶段安全本地提交；旧 APK 不代表本批修复。
