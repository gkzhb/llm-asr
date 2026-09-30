# Kotlin 首批 APK：构建/自审/身份验证通过

任务 `llm-asr-ap5`；本批4/62生产类迁移（AsrText/PcmWave/RecordingControl/NativeResponse），余58 Java未迁移，全量任务不关闭。

- 最终兼容构建 `b5a7aff69` exit0/APK_READY；真实Kotlin2.0.21、JVM8、SDK35/NDK28.2。
- 完整host回归、23新Java ABI/语义checks、既有生产变异/源依赖及APK checker负例通过。
- 全源4 Kotlin+58 Java、真实javac-h/NDK object及linked JNI描述/符号、DEX runtime class_def、精确许可/权限/组件/签名/zipalign/report-binding通过。`d8.txt`为空，无metadata警告。
- 127 build-input与132 frozen/current SHA一致；578 MNN对象/最终JNI DSO和基线不变。
- APK：`dist/qwen-asr-minimal-debug.apk`，0.6-debug/code6，3167222 bytes，SHA256 `f24b4da1f254f17a324b756990408e3f4474c2b09afce94290d06d608562a163`。
- 同一本地debug签名证书 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`。
- 直接自审 `reports/review/kotlin-batch1-self-review.md`，非独立review；完整日志 `.work/kotlin-migration/build-compatible.log`。

Kotlin2.4中间构建虽exit0但D8 metadata重写异常，自审拒绝交付；版本固定2.0.21并新增warnings fail-closed后重新全构建，不继承旧证据。

本轮按项目规则在安全审计后创建本地提交，成功身份以Git日志及交付消息为准；若提交失败则不得交付。无设备/麦克风/私人数据访问；主机通过不等于设备验收。Kotlin运行时使APK增加约0.7MB，不声明性能提升。模型不在APK或Git，不push。
