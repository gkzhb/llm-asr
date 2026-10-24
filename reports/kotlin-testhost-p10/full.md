# P10 / T5 正式 full 验证（未提交、未交付 APK）

任务 `llm-asr-ap5`，owner `p1-steward`。基点 `843dc146e1a38db6a184980fe46f60a254081e10`；
测试的是该基点上的冻结 dirty 候选内容，不冒称已测试未来 commit 对象。R1 定点审查 CLOSED/NO BLOCKING 已由父接受；
本报告及 [输入与产物指纹](full-inputs.json) 是唯一获准的两份新公开摘要，24 路径为 5D/9A/10M。
**尚未安全阶段1/阶段2提交，不宣称 APK 已交付，不关闭 AP5/M4/M5 或全部 Java 目标。**

## 当批唯一正式执行

`bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks`

O_EXCL 一次启动，真实 exit **0**，1802.676711 秒，**184 actionable tasks 全部 executed**。
固定 Kotlin2.0.21/AGP8.6.1/Gradle8.7/JDK17/SDK35、worker1/JVM1536MiB/Meta512MiB/JUnit512MiB、
Kotlin in-process/native jobs2，strict/offline/cache-only；未 prepare、下载、补 metadata、改 gate 预算或第二 full。
full stdout SHA256 `1b1e918c8576de54a3c8fbcf5d1b0ec0a9b6ab4fab6a3c56d9d37c96267ebe83`，stderr 空；owned 后代退出并 wait/reap。

正式 host 从头实际执行全部旧 gates 与新增 T5 source/self 普通/-O、三生产 mutant 普通/-O各9路由、
原 Java/Kotlin/可编译 early-null bad 三故障路由；不是复用 targeted 中断前缀组合。真实目标红均 compile0、
精确外部 AssertionError1 并恢复0，原 Part/model/enum/ports/phase/batch/next/runtime/Logs/Window/owned lifecycle 全保。
fixture 两个合法 mutant 的五阶段0/1/0/1/0，每阶段真实 XML 精确7，无非目标错误冒红。

实际 JUnit **core34＋runtime1＋host7＝42**，failure/error/skipped全0。main/unit/androidTest Kotlin 源归属1/3/3，
Java 仅 Provider；fresh 当批16个 Activity/shared/suite/Provider class 字节全等已接受 probe，因此原14方法、fields/Throws、
public Activity、private erased helper/精确桥无新增差异，不以旧 class 替本次编译。真实 D8 三包/14 compiled inventory、
精确8个 private lambda 名单检查通过，无 Kotlin metadata/rewriting 异常；不是 runner 发现/执行证明。

JNI 当批真实 javac-h/NDK object/DSO符号、ELF16KiB/provenance门槛通过，578 MNN对象 stat/bytes/SHA全同，
未 clean native/cache。Lint app24/runtime17 Warning、Error/Fatal0，本次 before/current XML 字节和完整重复 issue multiset
（含全部 location 属性）相同，无新增，不套旧 count 豁免。真实 NewApi green0→targetred1→restore0、checksum原0→
精准SHA拒绝1→恢复0、共享validator坏图/工件/classpath拒绝与恢复均实际执行。host Lint未接任务，NOTRUN。

## 真实三包及输入绑定（不等于交付）

| 角色 | bytes | SHA256 |
|---|---:|---|
| product | 5282161 | `38b4e2fb51d72d33b15ab1f7e71dd3f00851f463de4bf48118f586d130f8f968` |
| host | 2634048 | `c44831b734b9c42be0ea61f0cf38a81a65685d2d75c9e7905ddfebc181cca3bc` |
| instrumentation | 63942 | `ea180917c0910c79bd8a1ffa7f32544aea82d39789ab580600e666bb50a1bda3` |

产品 `org.llmasr.minimal` / `0.6-debug` / code6 / 原证书v3，Manifest权限/组件/资产/native/唯一owner原门槛保留。
host合法既有core stdlib（实际1001 Kotlin类），instrumentation实际0 Kotlin类且不重复目标host class；实际类计数
是本次事实不是新硬编码。产品同旧SHA仍有本次真实重建与双formal/current输入绑定，不用旧包代本次执行。
动态 build222/module10/native80 共312输入 before=after=current，与产品及三包两个formal receipts一致；
工具 entry/resolved target SHA复核，外部sample/model校验只引用既有formal工具记录，不扩大代理独立私文/音频读取权限。

## 保护差异与父明确裁决

当代2009份真实备份约84MB/6962严格保护核同；只清26个已备份class/JUnit/DEX/APK生成根，未删锁/lease。
原 post attempt1 **exit1 metadata drift 永久保留**，不是把旧断言改成绿。父完整核实后只新增 post-v2，真实 exit0：
9 metadata 分为4严格输入不变＋3生成sample允许本次stat变化（内容UNKNOWN，未读copy/hash）＋2 JNI object/DSO重建
stat但mode/bytes/SHA同本次备份。Native生成差异是199同字节生成stat＋1 `.ninja_log` 精确重排更新，**非append-only**：
前579行同，当前580–583等原584–587，尾4本轮timing保持4个output与commandhash；6个正常lock/journal变化均保inode。
上述分类相互交叉（native2、artifactlock亦在199内），不能叠加冒更多独立差异或套历史豁免。新post核双formal/3APK/
312输入/原22候选/备份/Lint/native闭合，208 owned ledger退出/reap、无marker、无相关后台，旧所有proof/index/receipt未改。

## 未覆盖与后置

fault5 actual TWR self/distinct close/Error/interrupt动态注入NOTRUN，父接受实际源码/Code/exceptiontable成员suppression
足本窄迁移；fault6真实SAF/provider blocked IO/stop-before-delete、Android runner/Binder/grant/系统IME/mic/JNI4K16K/设备/
真实模型/私文/远端CI/release全NOTRUN。Aactual任意旧owner binary drop-in Companion失败永久保留；A′source非AGP与Bactual
各分层，Companion/static-final/privateowner/反射/lambdaMetadata附属不宣称全集相等。两个额外POM未checksum授信，NOTICE/source/
legal/CVE/PGP/SBOM UNKNOWN不补绿；工具复杂度原compare2scopeUNKNOWN/Kt/KTS/shell/embeddedJava盲区保持。
维护22Java/物理29Java/生产16Java仍在，不是全部Java迁完。父消费本轮封口后另授reviewer只读安全阶段1，再精确stage/cached
审和正常本地commit阶段2；成功提交并父核actualcommit/current/full/APK前不交付可验证APK。
