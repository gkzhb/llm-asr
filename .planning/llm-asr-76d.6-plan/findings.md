# 发现

Task llm-asr-76d.6；writer p1b-plan；Beads owner p1-steward；cwd /home/zhb/gitrep/llm-asr；base a73f7f3。

- 初始HEAD a73f7f339a6d3a5e4f6a7faace7cdd262aade3c4/main；tracked diff/index空。
- 旧未跟踪 .beads.gate.lock、父supervision、ap5-toolchain-analysis、mqn、msj-modern-android-analysis、v07、多引擎文稿保留。
- Beads显式任务in_progress/assignee p1-steward，M3依赖closed；本leaf只调查，不改任务。
- P1-A 2d869e5不重复验证。M5目标允许先本地test APK工程；无设备运行必须not run，M4余CI、ap5不接管。
- 依赖地图是4ef49875旧导航，事实须以当前Gradle配置为准；禁止生成锁/metadata凑通过。
- 当前app main是物理src共享根+显式8源include；runtime5源、core排除13平台源。新增app/src/androidTest等默认路径会落入core共享源树，须显式隔离或改用app独立根（例如android/app/androidTest/java、test/java）。app全部KotlinCompile排除.kt，首轮Java fixtures更窄，不动迁移owner。
- 当前无tracked androidTest/debug源码；app无test runner或测试依赖，JUnit4.13.2仅core/runtime testImplementation。verifyFast/verifyDebug都未包含Android测试APK，verifyDebugPackage仅产品APK。新测试入口须独立，不把连接设备任务塞进full。
- AppGraph私有构造/静态单例，install硬装配JNI+仓库+进程startup；不能声称已支持测试替换。优先单测现公开构造接缝，不首轮改生产全局注入。
- 现Nix cache-only和Gradle strict/offline入口已存在；执行禁止，本轮仅读声明。
- 新增测试根必须在android/app/src外：prepare-gradle-inputs.py对整个src做frozen inventory精确匹配，core也会收src内任意新Java/Kotlin；默认src/androidTest放文件将同时破坏两道gate。推荐android/app/androidTest/java与android/app/test/java独立根。
- 可直接构造InferenceAdapter(Readiness/Library/NativeTranscription)、ImeController(Backend/TaskCoordinator)、SafModelSource(ContentResolver/Uri)。真实AsrImeService final并在onCreate固定取AppGraph，首轮不测试替换服务实例、不反射改全局。
- 现host ImeSessionTest与InferenceAdapterTest覆盖纯控制器/adapter，ModelAndroidHelpersTest明确不是Android运行；首轮新增Android组件/Resolver/InputConnection表面而不复制全M5。
- 有限缓存观察：受控Gradle目录下JUnit4.13.2、hamcrest-core1.3、AGP8.6.1 jar/pom存在；androidx.test、androidx.test.ext group目录未发现。只存在性，未运行checksum/解析，不能称cache ready。app旧lock已有空debugAndroidTestRuntimeClasspath，未来测试scope可能要求定向锁变更，不能以无新坐标等同无锁工作。
- 从已知P1-A非敏感构建证据定位唯一SDK路径（不扫store）：API35 revision2 optional base/runner/mock JAR实际存在，zip成员含InstrumentationTestRunner/InstrumentationTestCase/JUnit3 TestCase/Assert。ContentResolver.wrap(ContentProvider) descriptor存在；仅静态API表面，不证明可启动。
- 推荐先独立:test-host application模块，target自己、implementation core/runtime但不依赖app，SDK平台InstrumentationTestRunner+JUnit3（不自写runner），host unit复用JUnit4.13.2；生产app不添加测试组件、不改AppGraph。独立模块避免共享src/frozen基线风险，test-only useLibrary不污染产品；需父批准settings/new模块/限定lock准备，不顺手实施。
- 平台runner为deprecated、Android端可用性待实测，JUnit3方法发现不同于JUnit4 @Test。若无法建立精确测试清单/真断言负例或兼容失败，则停此分支申请AndroidX准入，不为离线改写简陋runner。
- 最终方案与21项精确拟允许路径、标准入口/候选新任务、包/隔离/输入指纹、依赖来源hash/license与准入缺口已写.work/p1b-plan/handoff.md。新host plugin会自动接现Lint identity gate，不绕过；full/fast原gate保持。
- 封口Git：HEAD a73f7f3/main未变，tracked/index空，旧未跟踪保留，只增加本leaf计划；.work输出ignored。产品/锁/metadata无改动、执行型验证全部not run。长期知识候选交父，未写Beads memory。
