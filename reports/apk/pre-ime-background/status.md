# 当前 APK 状态：IME 构建与聚焦源码复核通过，可供手动设备测试

- 版本 `0.4-ime-debug` / versionCode 4，API29–35，arm64-v8a。
- APK：`dist/qwen-asr-minimal-debug.apk`，2,413,416 bytes。
- SHA-256：`c433d1b82f30b5b1ef3ab37fbd4b349199196bd8507386d97e0463ca5c451a2c`。
- 构建 `b4ea5c08d` exit0，日志 `.pi/tasks/session-525127-525127/b4ea5c08d.output`，APK_READY。
- 245既有 + 160 IME生产controller检查 = **405 host checks**；Java/资源/DEX/native链接/签名/包检查通过，RECORD_AUDIO唯一申请权限，IME服务有BIND_INPUT_METHOD绑定保护。
- 55项build-input SHA与当前输入一致；native DSO与`dist/pre-ime-0.3`旧重构包逐字相同。
- 原独立审查 `reports/review/ime-independent.md` 提出B1选择器恢复问题；最终复核 `reports/review/ime-fix-independent.md` 已关闭B1源码缺陷，未发现范围内新阻塞，独立405项host checks通过。父核对31项合并审查指纹、55项build-input与APK身份一致。
- **可供用户手动安装/测试，不是已实机验收或商店发布包**。两项P3提示/测试强度改进保留，详见`reports/review/ime-disposition.md`。
- 原构建bff361037 exit1历史保留：末尾manifest解析格式断言错误已修；不改写其退出码。
- 未安装/运行新IME、麦克风、系统选择器或InputConnection设备测试。用户旧0.3验收不归因新IME。
- 未commit/push；用户用法与验收清单见`docs/voice-ime.md`。
