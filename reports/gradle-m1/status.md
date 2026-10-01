# M1 Gradle/AGP debug 构建验证

任务 `llm-asr-76d.2`；基线 `449f01a` / 产品 `2fd7511`。实现者直接自审，非独立review。最终构建/必要自审修复/指纹核对完成；功能与证据已安全本地提交 **22918a4**，M1验收后关闭。用户已反馈本轮手动测试通过；未提供逐项设备证据的验收仍pending。

## 最终身份
- APK：`android/app/build/outputs/apk/debug/app-debug.apk`
- `org.llmasr.minimal` / `0.6-debug` / code6 / min29 / target35 / arm64-v8a
- 5202124 bytes，SHA256 `5e1a1e072937b9509bb326f617daefe18ed40214115b8413a601d341a2fb84c0`
- debug证书SHA256 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`，真实apksigner通过，未换key。
- 包内JNI SHA256 `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`，与旧包完全一致；native数学未改。
- Gradle8.7 / AGP8.6.1 / Kotlin2.0.21 / Nix JDK17 / SDK35 / NDK28.2；73项产品输入不变，152项最终构建输入与current SHA一致，外部小资产/native/config SHA另列result。

## 实际验证
- 在线准备并bootstrap依赖校验/锁后完整verifyDebug通过，非Exec旧APK脚本；AGP负责混合编译/资源/DEX/签名。
- 最终 `b7658194f` exit0：`scripts/verify-gradle.sh --offline --rerun-tasks` strict依赖校验，41任务全部执行；host races/43 Kotlin-Java ABI checks/原生产回归/3mutants/源码与包checker负例、全源Java-Kotlin、JNI头/NDK对象/实际AGP class与DSO symbols、真实apksigner/aapt2、ZIP/全部DEX definitions/资产/组件/权限/report绑定通过。无DEX metadata异常。
- AGP debug含9DEX，旧checker只读首DEX已修复；multidex正例/缺定义/重复定义实际checker负例通过，不以字符串引用替代定义。
- 8 staging输入测试、5实际AGP产物identity测试通过；native/source/新增inventory/符号链接/残留资产/签名/backup等fail-closed。
- 仓库 `tests/gradle_dependency_verification_test.sh`：隔离篡改AGP期望SHA，实际Gradle strict因Dependency verification failed拒绝exit1；负例脚本自身exit0。
- 407依赖artifact缓存SHA全匹配，AGP/stdlib官方sidecar交叉核对；其他受控HTTPS首次bootstrap信任固定，无校验豁免，不声称全部发布者独立签名认证。
- 最终APK/152当前输入/73产品输入/外部SHA/6派生报告绑定复核通过。日志hash见verification-evidence.json；自审 `.planning/llm-asr-76d.2/self-review.md`。

## 用户手动测试反馈
- 用户在收到本轮M1 APK后反馈：“没有问题，测试通过”。记录为本轮交付的用户报告手动测试通过。
- 未提供设备型号、Android版本、具体用例、运行日志或设备端APK哈希核对；不扩展为所有设备兼容、逐项生命周期/麦克风/SAF/IME/JNI自动验收。
- 代理未访问设备或私人数据；`result.json` 的 `device_verified=false` 继续表示未取得代理采集的设备验收证据，不抹去用户反馈。

## 限制与恢复
- 本轮M1 only：native现DSO hash过渡输入，M2干净native/模块/JUnit/CI/UI/release未实施。旧Kotlin迁移由用户暂缓，assignee保留，余52Java。
- 缓存后的offline与rerun不等于全新Gradle缓存hermetic/全新native重建。外部公开样例、小配置、native及原key必须显式准备，入口见docs/gradle-build.md；不下载模型。
- APK增大和9DEX是AGP debug打包/容器差异，不要求与旧包逐字相同、不承诺性能改善。
- 未安装APK、未访问设备/麦克风/私人文件。App/IME/SAF/JNI实际运行、录音、Android生命周期及多平台验收pending；旧包用户反馈不继承。
- 无push/历史重写，不提交APK、模型/音频、缓存、key或会话记录；只有文本证据/官方hash核验Wrapper JAR源码启动输入入Git。
