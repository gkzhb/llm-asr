# 0.5 内部模型根路径兼容修复：构建及独立复核通过，待手机验证

- APK：`dist/qwen-asr-minimal-debug.apk`，0.5-debug / code5，2,446,184 bytes。
- SHA256：`1c8de63959cb8d8b99aa7706e8239bb2349fb49eea2ccb4b3b7d1d245652f7b3`。
- 完整构建 `bc04a521f` exit0/APK_READY，日志 `.pi/tasks/session-1194494-1194494/bc04a521f.output`。
- 945项数值断言（16组）+新增private-root alias真实主机文件系统回归+8 review groups/provider/3可编译mutants/source-policy/checker绑定fixtures通过；资源/Java/DEX/native/签名/权限/组件包检查通过。不同证据单位不相加。
- 79构建输入指纹全部匹配；standalone APK checker验证六份报告绑定；native DSO与修复前逐字一致。
- 用户任务 c8dbb9fb-04e3-41ad-9183-bd044be604e9 反馈导入失败：非法 model 根路径/符号链接，同时概况刷新与清理状态未知。
- 旧逻辑在主机真实父目录symlink场景产生相同异常 `.work/model-root-fix/red.log`。新专用 `forAppFiles` 入口仅规范化 Context 提供的 filesDir 父目录（worker延迟解析），仍拒绝 model/final/part 符号链接；一般repository严格策略不变。
- **独立静态窄复核 4e96a5f8 / 35d4908c 已完成，无阻塞问题。** 完整报告 `reports/review/model-private-root-review.md`；两项LOW测试加强建议（首次worker/一次解析、别名场景ModelAccess/native continuation直接覆盖）明确后续补强，不当作已测。审查者未运行测试/构建，父执行全量验证。真实Android路径与SAF尚未实测，不宣称用户设备已修好。
- 旧包与报告：`dist/pre-model-root-fix/`、`reports/apk/pre-model-root-fix/`。无清数据、设备访问、commit/push。
