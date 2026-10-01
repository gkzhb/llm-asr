# M6 窗口与返回适配 — 最终验证

任务：`llm-asr-76d.7`。源码与验证完成；本地提交成功后才交付，提交身份以Git与最终消息为准。

## APK 身份

- 路径：`android/app/build/outputs/apk/debug/app-debug.apk`
- 包名：`org.llmasr.minimal`；版本 `0.6-debug` / code6；min29 / target35；arm64-v8a。
- 大小：**5,207,200 bytes**。
- SHA256：`0ca23d891ea0b797a805d3e06defa3d59700b02b86af4664efa2689d268dfc58`。
- 证书SHA256：`e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`，与M2相同。
- 单native DSO SHA256：`d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`，与M2相同，CPU数学与配置不变。
- 唯一权限仍为 `RECORD_AUDIO`；模型权重不打包。三Activity现代返回opt-in实际Manifest已查，IME策略不改。

版本不增加，以完整SHA辨识本轮包；`dist/qwen-asr-minimal-debug.apk`与历史`reports/apk/`不是本轮交付路径/证据，不混用。

## 最终验收

b8f9fc4e0 exit0：`verify-gradle.sh --offline --rerun-tasks`，43 actionable全执行。全host/原3production mutants、新adapter69fake-platform checks/3可编译mutants、3source suites、8staging/7实际APKidentity（含兄弟/嵌套属性拒绝）、9source普通/-O、4native普通/-O、10Kotlin53Java全编译/JNI头及实际符号、native config/ELF/签名权限组件资产DEX报告绑定全部通过。

**164 build-input freeze/current/report一致，74产品清单逐项匹配。** 报告显式绑定 `run-d80294252e524780a500c0b1fc566c13`，不按目录mtime或旧APK推断。完整日志保存在ignored `.work/window-m6/build-final.log`；非敏感文本见本目录和`validation-summary.json`。

直接源码自审（不是独立Agent审查）已收敛实际Manifest checker边界缺口，并经最后全构建/负例复验。证据：`.planning/llm-asr-76d.7/self-review.md`。

## 设备边界

代理未访问设备/麦克风/私人内容；API29、Android15+、大字体/横屏/分屏/导航/键盘/对话框/实际返回/IME均 **not run**。fake-platform通过不证明真实Android视觉或dispatcher行为，4KiB/16KiB运行仍未验收，不关闭通用设备/质量任务。

手动建议：三个页面顶部与底部无遮挡；键盘出现/隐藏边距正确；录音返回请求取消/转写忙返回提示/模型取消确认；输入法原流程不变。完整矩阵见[窗口验收指南](../../docs/activity-window-validation.md)。
