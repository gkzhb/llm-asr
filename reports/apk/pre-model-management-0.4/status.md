# 当前 APK：输入法不透明背景修复，可供视觉验收

- `dist/qwen-asr-minimal-debug.apk`，`0.4-ime-debug` / code4，2,413,416 bytes。
- SHA256 `2070cd7f1d1eb7a51be971c16a328d5db1a1950b99e0506b657a9337fbd4044a`。
- 构建 `b82a77e99` exit0 / APK_READY；405 host checks、Java/资源/DEX/native/签名/权限/组件检查通过。
- 55项构建输入SHA核对通过；相较`reports/apk/pre-ime-background`仅`AsrImeService.java`变化：不透明浅灰面板、白色预览区/深色文字、统一浅色控件主题。录音/推理/会话逻辑未变，native DSO逐字相同。
- 版本号保持不变，以新SHA区分；可覆盖安装。旧包保留`dist/pre-ime-background/`。
- 先前Phase13独立源码审查证据保留；本轮视觉差异由父检查/构建，未独立复审，不能宣称旧Service指纹与新版相同。
- 真实视觉效果仍待用户确认；405项host checks不执行Android绘制或硬件。IME其他设备验收限制见`docs/voice-ime.md`与`reports/review/ime-disposition.md`。
- 未访问设备、麦克风，未commit/push。
