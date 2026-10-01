# Findings — llm-asr-76d.7

- 当前 main/82a5563，无产品/index改动；两个未跟踪分析目录属于既有他人工作，保留且不提交。
- M6 P1 open且M0依赖closed；已成功claim assignee llm-asr-m6/in_progress。
- Herdr本session wJ:p1 working，同项目另一session wJ:p2 idle工具分析，无其他working产品writer；ap5 deferred，不恢复迁移。
- 当前源码扫描：MainActivity正常与初始化失败路径均无insets；模型/日志页正常ScrollView已有API30 systemBars|cutout listener，API29走legacy systemWindowInsets，失败页未处理。这里只是静态风险，未复现设备遮挡。
- MainActivity/ModelManagementActivity仍override onBackPressed；后续需读完整录音取消/忙拒绝与Manifest，不能绕过旧行为直接finish。
- 当前Gradle8.7/AGP8.6.1/Kotlin2.0.21，M2显式native与strict离线门槛已验证；保留native SHA与签名，不放宽包检查。
- 设备/麦克风/私有内容无本轮授权，Android15/API29/字体/横屏/分屏及真实返回一律待验证。
- 完整源码确认：首页返回先尝试cancel录音，再busy拒绝，空闲平台默认；模型页cancellable弹确认，否则默认；日志页默认离开，provider独立slot不提前释放。
- 三页面Manifest adjustResize，未enableOnBackInvokedCallback；拟用平台API（不新增AndroidX）：统一edge-to-edge/insets及API33返回callback仅在resume登记/pause解除，默认返回保持root-launcher/API31行为。API29使用legacy flags+cutout安全边距，API30+decorFits=false及visible IME/systemBars/cutout逐边max，不累加重复边距。初始化失败页纳入。
- Gradle当前baseline-inputs严格冻结整个产品源，M6必须显式刷新变更清单且保留未修改资产/native/签名门槛；新增源还需架构map及产品输入collector。
- 路径探索app/build.gradle.kts及tests/android_architecture_test.py不存在；实际android/app/build.gradle.kts及tests/architecture_boundary_test.py。仅定位错误，无测试通过/失败证据。
