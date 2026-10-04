# M3 实施中间交接报告

- task llm-asr-76d.4；role project-agent；唯一源码/Beads/执行计划writer m3-build。
- PI session 01a107f4-71d8-778e-a743-2b336eef290c，subagent f27adc1c-44eb-4da9-bbbb-609b9b920bdc，Herdr wJ:p3；父监管wJ:p1。
- cwd/worktree /home/zhb/gitrep/llm-asr，main；基准及当前HEAD均538dcb66f16d41adeba440d934d3a0d2e8c4eb6d。
- **实现/本机验证、父独立复验及异家族审查完成，父逐项处置无本轮阻塞，准备父安全本地提交。未stage、未提交、未关闭M3、未向用户交付APK。** 设备、仪器测试、麦克风、SAF/IME/JNI运行均not run；不安装、不ADB、不上传、不push。

## 实现

真实`:app → :asr-runtime → :core`，app也直接依赖core。63生产类源码字节/包名保持（M6新增ActivityWindow，旧62快照已纠正）：core50（10Kotlin/40Java）、runtime5Java、app8Java。物理源码路径暂留，通过真实sourceSets/task source filter和空javac sourcepath形成唯一编译边界；不是Exec包装旧构建。JNI/native artifact仍由app现M2任务提供，native flags/门槛不变。

核心34个、runtime1个真实JUnit4测试，逐入口/8参数path执行原body，全部原main与host/ABI/生产mutants/负例保留。详细模块图/完整覆盖映射：[docs/module-test-map.md](../../docs/module-test-map.md)。新负例使用实际Gradle classpath编译，非字符串替代；实际class owner与所有DEX生产owner拒绝重复/缺失。指纹扩展新模块配置/锁/测试/src。

## 最终命令与证据

所有命令用固定 `bash scripts/nix-env.sh apk` 环境，Gradle strict、单worker。缓存准备与offline分开，offline不声称hermetic。

| 验证 | 实际命令摘要 | 退出码/结果 | 原始日志 |
|---|---|---|---|
| 初始核心独立compile | `./gradlew :core:compileKotlin :core:compileJava --offline --write-locks --dependency-verification strict` | 0，2任务执行 | `.work/gradle-m3/lock-core-compile.log` |
| JUnit公开依赖缓存/传递锁准备 | `./gradlew :core:test :asr-runtime:testDebugUnitTest --write-locks --dependency-verification strict` | 0，35JUnit | `.work/gradle-m3/junit-online-prepare.log` |
| fresh核心+runtime | `:core:clean :asr-runtime:clean :core:test :asr-runtime:testDebugUnitTest --offline --rerun-tasks --dependency-verification strict` | 0，20任务实际执行 | `.work/gradle-m3/junit-fresh-offline.log`（当时source-owner过滤不足，后续修复并最终再fresh验证，不据此单独验收模块） |
| 实际module编译负例 | `:core:verifyModuleBoundary :asr-runtime:verifyModuleBoundary --offline --dependency-verification strict` | 0，3正编译/5目标依赖拒绝exit1 | `.work/gradle-m3/module-boundaries-passed.log` |
| 独立全源fresh | `bash scripts/compile-android-java.sh` | 0，10Kotlin+53Java，SDK35/JVM8 | `.work/gradle-m3/all-source-fresh.log` |
| 最终fresh模块/AGP类与DEX | `:core:clean :asr-runtime:clean`；移除app生成`intermediates/tmp/outputs`（不动native缓存/他人目录），再完整verify | 0，fresh成功，无旧class污染 | `.work/gradle-m3/final-module-clean.log`、`final-full-verify.log` |
| 最终冻结源码全gate | `bash scripts/verify-gradle.sh --offline --rerun-tasks` | **0，70action全部执行，3m18s** | `.work/gradle-m3/final-inputs-verify.log`，`.work/gradle-m1/verify-zLkkhQ/build.log` |
| 最终真实checksum负例 | `bash tests/gradle_dependency_verification_test.sh` | 0；隔离Gradle因AGP8.6.1 jar checksum拒绝exit1，非无关错误 | `.work/gradle-m3/final-inputs-checksum.log`、`.work/gradle-m1/dependency-negative-2D00aA/rejection.log` |
| 输入绑定 | 对最终report、预freeze、current逐SHA比较 | 174/174/174一致，产品74frozen输入一致 | 本目录`build-input-sha256.json`，`.work/gradle-m3/final-freeze-inputs.json` |
| 差异空白检查 | `git diff --check` | 0 | 命令直接输出为空 |

最终全gate实际包括：35JUnit（XML0failed/errors/skipped；非敏感用例名摘要`junit-summary.json`）、完整原host28main/8path/43ABIchecks、模型3production mutants行为red、PartRecovery红绿、ActivityWindow可控假平台69checks+3mutants、9实际APKidentity负例、5实际module-owner checker普通及-O、8staging、9native source普通及-O、4native artifact普通及-O、全源真实JNI头/NDK object/DSO symbol、当前native provenance/config/ELF16KiB/static-runtime、权限组件/资产/全部DEX/实际签名/输入绑定。不是设备验收。

## 父复验及审查结论

父后台bbb64c7ae completed/exit0，clean core/runtime后strict offline rerun70action全部执行、35JUnit及完整原gate通过，checksum负例目标拒绝通过；174current输入一致，APK SHA a0494d25...a3f3不变。见只读[parent-verification.md](parent-verification.md)。异家族m3-review审查完成，原文[independent-review.md](independent-review.md)与父逐项[review-disposition.md](review-disposition.md)共同作为证据：F002遗漏core现有exclude，父actualclasses确认仅runtime有InferenceAdapterTest；F001不移除app sourcepath保护；F003 hard63有意fail-closed；其余正常确认，无本轮阻塞。保留原review追溯，不照错误前提改源码。当前只剩父安全本地提交，提交成功后再另行批准关闭/交付；设备边界不变。

## 已父复验、待安全提交的包身份

- 路径 `android/app/build/outputs/apk/debug/app-debug.apk`，5,216,037 bytes。
- 包 `org.llmasr.minimal`；versionCode6 / `0.6-debug`；min29/target35；debug身份/组件不变。
- APK SHA256 `a0494d25f09b4352d30847af00228ee29a2589dbf98e812276018eb12072a3f3`。
- native SHA256 `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`（原门槛相同）。
- 签名证书 SHA256 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`（原升级身份）。
- 最终包报告 `android/app/build/verification/debug/run-9041baf44a6c468b95c4ff6f2febaed7/`；复制必要非敏感文本于本目录。
- 174输入清单文件SHA256 `0f5b9773014f406d3e59476e1c914144a81f7c6298c3650d77254bb2169d0fba`；包内12DEX/1327class definitions，无重复，无JUnit/Hamcrest测试依赖。
- APK容器/DEX布局与基准改变属于实际模块DEX组织变化，不承诺整APK字节与M2/M6相同或性能提升；生产源码/native身份保持。静态16KiB不等于4KiB/16KiB运行通过。

## 失败与修复（保留证据，不用失败包交付）

1. Maven旧JUnit/Hamcrest无.sha256 sidecar（404/exit22）。改官方下载jar/pom与官方.sha1交叉，真实计算SHA256，5artifact（含parent）固定metadata；不伪造官方SHA256、不放宽strict。依赖仅test，来源初次HTTPS信任非独立签名认证。
2. 新Android library/Kotlin JVM marker未缓存，后根旧lock仍含marker。显式plugin useModule复用已校验AGP/Kotlin实现；合法Gradle --write-locks更新根/core，未修改工具版本。
3. AGP sourceSets includes未约束实际JavaCompile，runtime错误编译136classes。fresh复现确认非旧class污染；task级include+空sourcepath后runtime仅7classes/5owner；全63实际unique owner验证。
4. app Kotlin sourceSet.exclude仍被AGP Java root补进Kotlin task，实际跨DEX重复。新duplicate gate拒绝首全build，task级exclude后appKotlin0classes、最终fresh全DEX无重复。未关闭duplicate gate。
5. AGP test任务延迟注册、javac中文locale、Android bootClasspath与普通classpath分离导致前期负例接线失败；分别configureEach、显式Java diagnostic language、合并实际bootClasspath修复，正例必须成功且负例目标diagnostic不降级。
6. module owner negative发现内嵌class能掩盖缺顶级owner；增加direct owner exact inventory，普通/-O回归0失败。测试诊断文字同步，未移除负例。
7. 最后自审补JUnit body额外读取sample/native/caller源的Gradleinputs并重新冻结174输入、全gate和checksum，当前报告绑定最终配置。

## 直接自审与剩余限制

见[self-review.md](self-review.md)。直接自审本身不是独立审查；其后父独立复验和异家族审查已完成、无本轮阻塞，见上述父报告。安全提交尚未完成，不能关闭/交付。物理共享source root过渡需task级过滤与owner gate共同维护；后续新增生产类需改baseline/归属审查，当前硬63 gate fail closed。main适配JUnit是过渡，未逐私有group改@Test，但全部body保留且每入口独立report；JUnit35不证明额外M5 Android行为。PartRecovery原固定.work/red输出仍串行（单worker/fork，不与旧host并行），跨外部进程并跑未保证；父勿竞争构建。release/M4/M5/M7/全Kotlin迁移/设备/新功能均范围外。

单一下一步：等待父安全本地提交成功哈希及另行关闭批准；本Agent保持idle，不自主领取下一任务。
