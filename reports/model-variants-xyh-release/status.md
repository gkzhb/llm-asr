# llm-asr-xyh：修复发布验证状态

**工程验证通过，尚未安全提交，不构成 APK 交付声明。** 任务owner/唯一Beads writer为 model-variants-coordinator。

- tested base：`d9db46a7eb8363bb76e1878a4a80ce29f6472aa8`；本次测试在提交前 dirty freeze 下执行，不把未来commit或历史baseline.source_commit当作testedcommit。
- App版本：**7 / 0.7-debug**；test-host独立版本1/p1b-testhost不变。
- 当前工程APK：`android/app/build/outputs/apk/debug/app-debug.apk`，5513993 bytes，SHA256 `ba46228ab4608a5e1dccbc533013008d2437e2ce33e330be35c0c95f902820ec`。
- native：`053962bdf047a03dd01201250f8d026fe60e9c50ab479441e2a26ae52b963917`；固定证书 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`，v3/zipalign及当批三APK身份通过。
- 原formal full实际任务摘要：`184 actionable tasks: 184 executed`；当批JUnit合计42，failure/error/skip均0。实际build/module/native输入257/10/92，不是永久计数门槛。
- 原strict native已在身份采纳后实际0，formal full实际0；本次runUUID/rawreport/receipt/当前输入/embeddednative已绑定。

有界受管隔离审查已接受原residual、版本A/B、C语义；只读reviewer使用inline返回，父保存真实记录，没有虚构reviewer磁盘receipt。最终文本安全审/本地新commit仍待父另授；无push。

NOT RUN：Android runner发现/执行、设备4KiB/16KiB、SAF/Binder/grant/IME系统生命周期、mic/private、当前App实机推理/质量性能。历史模型包/hostEOS证据不被本full冒称重测。

详细工程结果见[full](full.md)、[身份](native-identity.md)、[review](review.md)、[输入](inputs.json)。
