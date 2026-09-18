# APK 状态：本机重编成功（新 debug 签名）

- APK：`dist/qwen-asr-minimal-debug.apk`
- 版本：`0.6-debug` / versionCode 6；包名 `org.llmasr.minimal`。
- 平台：Android 10+（minSdk29 / targetSdk35）、arm64-v8a。
- 大小：2,470,760 bytes。
- SHA-256：`233d134d343a636613fe6c762566ca881805cee387b185bd5a9640d8931c04eb`。
- 新签名证书 SHA-256：`e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`。
- 产品构建基线：`39a7923`，与此前 `cc4bec1` 产品源码相同。本轮没有修改产品源码、测试和构建脚本；仅恢复忽略的依赖并更新文本证据/说明。

## 本次证据

- 固定 MNN 源码、三份最终补丁、KleidiAI 1.16.0 已恢复；两个模型配置和公开音频通过原清单校验，未重新导出或下载模型权重。
- 重编的 578/578 MNN 对象与历史路径及 SHA 全部一致；`libMNN.so` 与原 P0 SHA 完全一致；最终 JNI DSO 与历史 APK 记录完全一致。
- 完整构建 exit0 / APK_READY，日志 `.work/local-repro/native-retry.log`。
- 完整 host 回归、fresh class 变异测试、全部 Java/JNI 编译、生成 JNI 头及 linked DSO ABI、资源/DEX/签名/zipalign/包检查通过。
- 119 个当前构建输入全部匹配；APK 与六份派生报告绑定通过，最终 standalone checker 再次通过。
- 父代理核对生成报告差异、当前输入及对象摘要；此次无代码改动，不声称新增独立代码审查。历史 R5/R6 源码审查仍是历史证据，不能充当本次设备验收。
- 复现步骤及首次 KleidiAI 下载降级失败见 [本机复现说明](../../docs/local-apk-reproduction.md)。此前包身份及被替换报告归档在 `pre-local-reproduction/`，不代表该旧 APK 仍在本机。

## 安装与验证限制

**使用新建 debug 密钥，不能直接覆盖旧签名 APK。** 不要盲目卸载，卸载可能删除已导入模型和本地结果；本次没有执行安装或卸载。

没有真实设备/SAF/Handler/IME/录音/JNI推理验收，也没有性能实测。APK 不嵌入模型，仍需七个与清单一致的部署模型文件。建议在独立测试设备或妥善处理旧签名及数据备份后，手动验证模型导入、示例/WAV、录音、输入法和导出。新 APK 的签名和时间戳不同，不宣称整个 APK 与历史字节相同。
