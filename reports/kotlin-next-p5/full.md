# Kotlin next P5 / 正式 full 验证

## 状态与范围

六类1082原Java行行为保持迁移，当前43 Kotlin /20 Java /63生产owner。fresh及R1–R3定点回审已由父接受全部CLOSED/NO BLOCKING（同模型新上下文隔离，非跨家族独立）。本轮唯一正式full成功并完成当前类/三包/输入/保护绑定，**待父独立消费与另阶段安全审、提交；尚未提交，不宣称本轮APK可交验证**。不连带关闭AP5/M4/M5或设备事项。

基于main `364c85cf77b11cba9b4a3f231c59f36da4bb7457` 的precommit dirty freeze内容测试，不是执行未来commit对象。原30候选严格冻结，status及旧报告不回写；本阶段仅新增本页与 `full-inputs.json`，总32候选。

## 唯一正式命令与实际结果

```sh
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

- exit0；1179.206秒；176 actionable tasks，176 executed。闭合log48934bytes，SHA256 `181e8d1fba31c73f6514be0617c201b412596b667e31f07fdee31d6d6a69889a`；pipeline Gradle0/tee0/scan1。没有probe init/-x、cache/锁放宽或第二full。
- 固定Gradle8.7/AGP8.6.1/Kotlin2.0.21/JVM8/JDK17；保单Gradleworker、1536MiB heap、JUnit512MiB、Kotlin in-process及nativejobs2。cache-only/offline不等于全新缓存hermetic证明。
- 实际core34/runtime1/test-host7共42项JUnit，failure/error/skip全部0。14个Android仪器方法仅编译/DEX库存，不冒runner发现或执行。
- 原host809 ABI与enum/ports/phase/batch10、cleanup13、PartRecovery、原五生产mutants、Logs3/Window7、mixed default/cleanup/lock、owner8普通/-O及真实模块classpath正负全部执行。新增compat-only正式host单路由执行：R1 self/distinct/close-only/close-once/owner语义，四nullable点各编译成功坏副本→独立精确外部AssertionError1→SHA恢复freshgreen0；owned退出/rawstreams核过。后验sourcebinding由确定性driver/baseline/emittedclass重建，不冒compiler启动前per-mutant SHA采样。
- 实际currentroot app五Kt/coreController非probe输出，exact8app/5runtime/50core；六owner及必要nested原public/protected子集delta0，五privateowner附属例外、三个privateSDK identity helper和唯一Graphsynthetic桥符合精确准入。R1实际5处JavaThrowable.invokevirtual、0 Kotlinselfguard/closeFinally；九private lambda编号仅精确映射、无新增helper/bridge；不称反射全集相同。
- 真JNI javac-h/NDKobject/linked symbol及descriptor核验通过；578 MNN对象SHA/inode/mtime均保持，DSO `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416` 与原身份一致；ELF16KiB是静态检查，非4K/16K设备运行。
- actual NewApi唯一目标0/1/0、Lint工具/真实classpath共享validator负例恢复、strict checksum0/1/0、Androidfixture五阶段0/1/0/1/0及三包签名/Manifest权限/assets/allDEX/receipt绑定全部通过，metadata/DEX warning fail-closed策略保持。

## 当次 Lint（不继承旧计数）

生产app/runtime Error/Fatal均0；Warning分别24/17（前代17/10）。各增加7项：AppGraph.kt:89 `UsableSpace` 1项，LogsActivity.kt:110–111 `SetTextI18n` 6项；两module来源相同，保持原Java空间provider及summary拼接行为，Kotlin报告粒度可见增加。没有新API gate错误，未添加Suppress/baseline或修改冻结源码。新增warning分类显式交父/安全审接受，不把旧defer自动推广；逐项XML/location及前代真实backup见ignored `lint-warning-delta.json`、`lint-disposition.md`。

## 产物与动态输入绑定（非交付许可）

| 包 | bytes | SHA256 |
|---|---:|---|
| product | 5276549 | `959a8b4f1525dcec275e95630144d6f10f4451680ac51bfa644ee326ada16e83` |
| host | 2628036 | `31c4ac3a3a8b7265c84c7a97016218c10c65b76207cdb47cfd9e1829197579b4` |
| androidTest | 52318 | `950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41` |

product路径 `android/app/build/outputs/apk/debug/app-debug.apk`；org.llmasr.minimal /0.6-debug /versionCode6；原证书SHA256 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`，v3签名。min29/target35/arm64与原权限资产策略由正式checker通过；actual class_def而非字符串存在判断，product1348/host1251/test43类。

动态build218/module10/native80为本次collector实际库存，不是永久数量门槛；before=after=current逐SHA并映射product result/native-verification与三包receipt，external工具entry/resolvedtarget另域核。两正式报告目录与详细身份在 `full-inputs.json`。

## 保护与历史限制

finite6533 protected：6425 unchanged，108 expectedgenerated变化均有本代真实原bytes/modeSHA。1691当前代backup（600精确身份复用），20项review生成件通过backup映射保可重验；未以旧799/34948替代最新代，未递归复制全部旧work。当前full mutable ledger/streams/classes/JUnit等247项另存immutable新域，防下阶段同路径覆盖。父协调新授权/进度为excluded supplemental，不伪称原路径全部未变；原历史多代ledger缺失仍保历史限制，本次不补造旧bytes。

post一次纯检查误要求非verbose unittest列出case名，后改按实际task路由与两次8tests summary核、未重跑测试；旧失败脚本保持，不影响唯一full0。源码/30候选/HEAD/index不变，原commandPID已退出，无相关活worker/未完后台。

## 未验证边界与下一步

设备、Android runner发现/执行、真实麦克风、SAF/Binder grant、系统IME/InputConnection、JNI4KiB/16KiB运行、模型真实推理/私人文件、远端CI与releaseR8全部not run。主机fake、class/DEX静态与签名包检查不能等同设备验收。

父独立消费本轮32candidate/输入/ABI/三包/保护/完整证据后，另授只读安全审及逐文件正常本地提交。当前无stage/commit/push权限，未提交成功父核前不告知可验证APK。
