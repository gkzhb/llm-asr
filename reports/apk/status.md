# APK 状态：离线配置准备与路径修复验证通过

- APK：`dist/qwen-asr-minimal-debug.apk`
- 版本：`0.6-debug` / versionCode 6；包名 `org.llmasr.minimal`。
- 平台：Android 10+（minSdk29 / targetSdk35）、arm64-v8a。
- 大小：2,470,760 bytes。
- SHA-256：`66be525e2f69b12b23f5a76f5bc2d0815f97c7447a672000162d38b6d4e7dfed`。
- 签名证书 SHA-256：`e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`，复用上轮本机debug密钥，本轮未生成新密钥。
- 源码身份：基于 `d9ea37f` 的配置准备增量，精确输入见 `build-input-sha256.json`（122项），随本报告提交；不把基线提交当成本轮全部源码。

## 本次证据

- 离线准备脚本从可信历史日志恢复两份固定配置；大小/SHA、幂等、预检拒绝覆盖、逐级无符号链接、排他hardlink发布均有回归。
- 路径规范化缺陷已红绿验证：含 `..` 的output在规范化/目录创建前拒绝，覆盖symlink/普通/缺失/非目录路径及create/check；正常相对路径仍可用。
- 16项Python测试在普通与优化模式均通过；完整host回归、fresh-class变异测试、全部Java/JNI编译及ABI检查、资源/DEX/签名/zipalign/包检查通过。
- 完整构建任务 `b64ce963e` exit0 / APK_READY，日志 `.work/config-preparation-fix/build.log`。
- 122项构建输入与冻结及当前文件全部一致；APK与六份派生报告绑定通过，standalone checker再次通过。
- 578个复用MNN对象与上轮报告逐项一致；JNI DSO及APK所有ZIP条目内容与上轮逐字一致。未改Android逻辑或模型数学；包容器摘要变化不等于产品功能变化。
- 当前主代理源码自审与证据核对完成，详见 [修复报告](../review/config-preparation-fix.md)；未声称新独立审查或设备验收。
- 历史119项复现证据位于 `d9ea37f:reports/apk/`，不归因于本轮新输入；本机旧APK/报告备份 `.work/config-preparation-fix/baseline/`。

## 安装与验证限制

本轮签名与上轮本机复现APK一致，但仍不同于更早旧签名APK；不要盲目卸载以绕过签名冲突，以免删除模型和私人结果。未执行安装或卸载。

没有真实设备/SAF/Handler/IME/录音/JNI推理验收或性能实测。APK不嵌入模型，仍需七个与清单一致的部署文件。此次修复针对主机构建准备流程，不声称手机推理行为或性能提升。
