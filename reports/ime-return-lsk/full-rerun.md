# llm-asr-lsk 正式 full（提交前冻结）

基线 `d5b38ac7713dfb91dd1f261ff9f6a71984fee040`；父 lsk-parent 唯一 Beads writer，lsk-build 唯一 full writer。fresh 同 sol fork:false 上下文隔离 NO BLOCKING 已由父接受，非跨家族独立；LOW LSK-R1/R2 仅覆盖 gap 明确 defer，源码/测试未变。

## 修复后另授的一次正式执行

原 `full.md` / `full-inputs.json` 保持首次 prepareInputs 身份失败快照。之后 baseline 传播修复与窄15项回归获 fresh delta NO BLOCKING，父才另授本次 full。此前 ignored launcher 的 preparation.json 同名覆盖导致 prelaunch failed1，正式命令尚未执行；新 continuation 分离 owned/summary 命名，读取真实已退出准备账本与634备份/3已freshen路径作显式回溯消费，没有重跑准备或伪造原摘要。本节仅本次实际正式成功，不改写两个历史失败。

`bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks`

真实 exit0，2294.846秒；184 actionable tasks: 184 executed；PIPELINE_STATUS Gradle=0 tee=0 scan=1。全部原预算/单worker/JVM/native约束/strict/offline/cache-only保留，无下载补cache或gate降级。

当前238build/10module/84native输入与当次独立product/triple receipts/current源码绑定。输入及工具/产物SHA见 [full-inputs](full-rerun-inputs.json)。这是提交前dirty freeze下的真实执行，不冒测试新commit对象。

## APK与工程层

- product: `android/app/build/outputs/apk/debug/app-debug.apk`，5352113bytes，SHA256 `553cf93876668121d9af5873db332c8b2e4d4b8dce3078b2f235bba4eaa894cd`；原v3/cert身份由正式checker确认。
- host: `android/test-host/build/outputs/apk/debug/test-host-debug.apk`，2685540bytes，SHA256 `ee978b23ad5993c2d2055f49ca424a4522800c73fb249941a0c792fdfadc98cd`；原v3/cert身份由正式checker确认。
- test: `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk`，66382bytes，SHA256 `4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8`；原v3/cert身份由正式checker确认。

产品 org.llmasr.minimal 0.6-debug/code6/min29/target35/arm64，原证书/RECORD_AUDIO-only/组件/资产/无权重与JNI/native约束不变。旧APK不冒本批产物。

42JUnit（core34/runtime1/testhost7）零failure/error/skip；host原/新mutants、ABI、真实module边界、JNI头/实际符号/native provenance/ELF16KiB与三包parsed DEX/checker通过。真实checksum/Lintfixture/fixture行为mutants结果及restoration按当次正式log/退出证据，不以缓存或编译器失败冒红。

- Lint app: Error/Fatal0，{'Warning': 24}；类别 {'UnusedAttribute': 3, 'GradleDependency': 1, 'ChromeOsAbiSupport': 1, 'DataExtractionRules': 1, 'UsableSpace': 2, 'MissingApplicationIcon': 1, 'SetTextI18n': 15}。Warning保留defer，未baseline/抑制/关闭error。
- Lint asr-runtime: Error/Fatal0，{'Warning': 17}；类别 {'UsableSpace': 2, 'SetTextI18n': 15}。Warning保留defer，未baseline/抑制/关闭error。

578原MNN对象SHA保持，本次Ninja为1/1链接产物重生成，JNI真实复编/符号门槛另执行；不是全native clean、空cache/hermetic或native模型运算证明。正式日志实际来源18个根的132个owned记录/raw逐核、525个PID-start身份无活进程；另有Lint/checksum执行文件与fixture结果绑定。当前13候选/保护域与同源receipt最终封口见handoff，未凭agent完成代验收。Androidrunner仅编译/parsed库存14方法，不证明runner发现/执行。

## 交付边界

完整终态/owned退出/保护/当前身份封口见 ignored `.work/lsk-build/full-rerun/continuation/handoff.md`；本报告及docs/status为supplemental，不改变受审产品输入。尚未stage/本地提交；父消费后另授两阶段安全审与精确allowlist本地提交，提交前不得宣称APK已交付可验证。

Android系统IME/fcitx5往返、设备4K/16K、Androidrunner/Binder/窗口/SAF/JNI运行、麦克风/private/模型计算、下载/网络/远端CI均NOT RUN。host visibility模型仅锁定no-hide生产接线，不提升为已确认设备根因。
