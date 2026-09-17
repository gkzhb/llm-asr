# 当前 APK：0.5 模型管理构建通过，独立审查待完成

- `dist/qwen-asr-minimal-debug.apk`，`0.5-debug` / code5，2,442,088 bytes。
- SHA256 `f4ad0e57b4d3da661a310df94510affbb42bad95bd71b2533310974b7caf37ab`。
- 完整构建 `b6b11ff8b` exit0 / APK_READY，日志`.pi/tasks/session-1194494-1194494/b6b11ff8b.output`。
- 947项host检查（847核心/旧回归 + 100 Android纯Java策略helper，16组）、source-policy fixture、全部资源/Java/DEX/native/签名/权限及组件检查通过。真实Android框架与设备行为不在host覆盖内。
- 父核对72项build-input SHA及61项冻结审查输入全部一致；native DSO与旧0.4背景修复包逐字相同。独立页面为同进程非exported Activity；权限仍仅RECORD_AUDIO。
- **独立双审 `e6cfb984` 仍pending，本包尚不标记为最终验收包**。不以构建通过代替源码审查，更不替代设备验收。
- 已验证旧包保留`dist/pre-model-management-0.4/`，旧报告`reports/apk/pre-model-management-0.4/`。
- 已知展示缺口：精确聚合预检空间需求未进入快照、删除失败数只在终态汇总、逐文件复用未保存终态标记；审查后统一处置。
- 真实SAF、导航/旋转/Home/锁屏、IME和视觉/insets/TalkBack尚未实测。无设备/麦克风/私人数据访问，无commit/push。
