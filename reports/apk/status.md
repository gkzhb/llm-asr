# Kotlin 批次2 APK：构建/直接自审/身份验证通过

任务 `llm-asr-ap5`；本批迁移task四类、AppState、ImeFieldPolicy，累计10/62 Kotlin，余52 Java，全量任务不关闭。

- 完整构建 `bb3c4f1e9` exit0/APK_READY，Kotlin2.0.21/JVM8/SDK35，真实JVM default保持Java接口实现。
- 完整host回归及43 Java ABI/语义checks、既有生产变异/源依赖/checker负例通过。
- 全源10 Kotlin+52 Java、真实JNI生成头/NDK/object/linked符号、DEX全部10生产Kotlin类定义、精确资产/许可/权限/组件、签名/zipalign/report-binding通过；`d8.txt`为空。
- 127构建/133冻结输入与current SHA一致；578MNN对象及JNI DSO与批次1逐字一致。Nix配置/锁文件/native源码本批无变更。
- APK `dist/qwen-asr-minimal-debug.apk`：0.6-debug/code6，3171318 bytes，SHA256 `5de230d55bc81b641682c6be38a13c01fe839ff39e4045475e1df02a2b0a8e26`。
- 同一本地debug签名证书 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`；不可覆盖签名不同的旧包。
- 直接自审 `reports/review/kotlin-batch2-self-review.md`；非独立review，日志 `.work/kotlin-batch2/build.log`。

本轮安全审计后创建本地提交，成功身份以Git日志及交付消息为准；提交失败不得宣称交付。未push，未访问设备/麦克风/私人数据；host不等同设备验收，App/IME/SAF/JNI运行仍pending。模型/音频/产物/缓存/密钥不入Git。后续继续模型/管理/诊断/Android入口批次。
