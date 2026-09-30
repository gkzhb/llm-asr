# Findings — llm-asr-ap5

- 当前生产 Android 62 Java 文件，功能子包约4623行，根入口另计；不是简单单文件改造。现有无 Gradle/Maven，直接 javac → d8 → aapt2 / NDK / apksigner。
- flake.lock 已锁定 nixpkgs；apk shell 当前无 kotlinc。host测试指定纯生产 subset，Java tests 仍有 source/mutation 检查，必须保持这些门槛不变。
- JNI 强制 javac -h 头及实际符号检查；第一批不迁移 JNI 声明类，混合编译需保证 javac 读 Kotlin 已编译类。
- Kotlin 技术上适合 Android/JVM，但需控制 nullable、@JvmField/@JvmStatic、异常/Error传播和 synchronized；不在语言迁移中引入 coroutine。
- Beads 新建 llm-asr-ap5，owner/assignee llm-asr-kotlin；无旧 Kotlin 任务/memory。主机未安装 kotlinc，Nix 包版本待查。
- Herdr agent list 与 snapshot 核对：本项目唯一 working session 为当前 01a0f290...；其他 working agent 位于不同项目，未发现重叠 writer。未委派。
- 锁定 nixpkgs 的 Kotlin 包为2.4.10，源归档带Nix SHA；apk shell已接 pkgs.kotlin/KOTLIN_HOME，b9de278a7 exit0，真实kotlinc版本2.4.10/JRE17验证成功。
- 实际包许可在 license/ 而非 share/kotlin/，已从锁定分发复制Apache与stdlib第三方适用许可到assets；只打stdlib/annotations，不打compiler/reflect。DEX checker新增runtime/4 owner descriptor与许可精确检查，synthetic fixture补missing-runtime负例。
- 首轮host未进入编译：Bash `${2:?}`会同时拒绝missing/empty参数；空classpath为合法host输入，改`${2?}`仅拒绝missing，修后回归任务b48f7db3f。
- 完整build日志暴露D8/R8 8.6.2 metadata2.4不兼容，不能仅凭exit0交付。改固定Kotlin2.0.21官方归档；官方sha256旁文件真实读取为0352c0a45bd22f80f6b26e485cd04da8047baa5de54865281fb9f89a4a7bcf2a，已写入Nix override（尚待归档下载比对/编译）。nix-prefetch超时后curl HEAD官方302→200可达，采用有界curl替代，不复跑原无限等待。
- DEX最初字符串检索不足以证明runtime类有定义，自审改为解析class_def/type/string表。synthetic负例保留Intrinsics字符串和type引用、只移除class_def，实际checker正确exit1；绑定与其他旧负例全部通过。编译/runtime jar SHA与nixpkgs revision将由构建自动记录。
- 第一批选择 AsrText/PcmWave/RecordingControl/NativeResponse：现成 host 覆盖高，JNI声明与模型/诊断变异源仍保留Java。NativeResponse 必须保留 Java String.split(" ") 的末尾空字段规则；字符串 trim 必须仍按 <=U+0020 而非 Kotlin Unicode whitespace；@JvmField DTO和@JvmStatic工厂保持Java互操作。
- 首批实际Kotlin2.0.21+127输入APK已提交a23277d。2026-09-30继续批次2时git干净，bd owner仍llm-asr-kotlin，Herdr仅当前同项目working。
- 用户询问Nix hack：实际仅标准overrideAttrs固定官方2.0.21/hash和同hash镜像fallback；无compiler patch/metadata剥离/校验旁路/系统代理写入，SDK/NDK未改变。批次2不再改Nix。
- 批次2 task.Lifecycle原为Java default方法，Kotlin必须-jvm-default=all生成真正JVM defaults，Java匿名实现才能继续只覆写需要的方法；Task/TaskBody/Preflight/Cleanup/Listener为fun interface SAM。RequestContext.inputWav为可空可变字段，不把原允许null构造器改为非空前置检查。TaskCoordinator fatal观察异常/owner释放次序与RequestRunner catches必须保持。
