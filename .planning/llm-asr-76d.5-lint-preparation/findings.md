# B1 findings

- 正式B1授权已到，A3通过、其他leaves停写。v4所有域已审helper实际check exit0，183build SHA3b9a90f5…；HEAD7eace17不变。
- AGP8.6.1既存JAR含LintFromMaven/LintTool/LintTaskManager，先反汇编固定可信class读取真实工具配置，不猜名字。
- 本轮仅隔离候选准备，旧plan.md为只读预备历史，正式状态以后续三文件与handoff为准；不改旧toolchain计划。

## p1-test-2 接管实证
- 前writer已terminated/provider失败；无final/handoff。仅磁盘恢复，不读history，不Beads/stage/commit/委派；PI_PROVIDER=newapi、PI_MODEL=gpt-6.1-sol。
- source-audit.exit=0/log末尾SOURCE_AUDIT_COMPLETE 168；license-summary.exit=2，97 POM许可汇总中8未知，不能把来源下载成功当准入成功。
- 8未知：javax.inject:javax.inject:1；net.sf.kxml:kxml2:2.3.0；org.bouncycastle:{bcpkix-jdk18on,bcprov-jdk18on,bcutil-jdk18on}:1.77；metadata-only net.java:jvnet-parent:1、org.sonatype.oss:oss-parent:7/9。
- 另5个actual节点只有.module/无POM许可证据：aapt2-proto8.6.1-11315950、builder-model8.6.1、manifest-merger31.6.1、checker-qual3.33.0、kotlin-stdlib1.9.20。未按其他对象许可推断它们；不扩大调查/下载。
- 真实strict/offline resolve未运行，无strict-resolve exit/log。strict-resolve.sh已读审但许可硬停止，不执行；历史discover-network的strict只配置枚举/AGP前置，不是Lint完整闭包strict offline验收。
- discovery网络日志的project字段旧误取task.path，后来fixture源码/graph project.path已修；config-discovery.json不可冒充后续重新执行的准确owner快照。AGP实际detached/named audit graph应以resolved-graph.json完整比较为准。
- 已读审freeze-a.py及fixture/static审计/metadata脚本；不改APPROVED_FRAGMENTS或freeze。串行本地静态重核b8940d14c已completed/exit0，仅自身目录证据，无Gradle/Nix/download/Lint。
- 重核结果：actual/named完整67 nodes含reasons、171 edges、66 artifacts含paths精确相等；每edge两端存在/固定版本；66 cache JAR与168官方对象SHA全部重核匹配candidate。candidate23新增/412旧条目不变、0 missing/changed，所有新条目有official object记录。
- 新对象sidecars：sha256 200=18/404=5，sha1 200=21/404=2，asc200=23；可用checksum匹配，所有asc未验key/signature。来源/licensing/CVE边界不变。
- candidate SHA5c72c2f2…、fixture两锁SHA56da4d9f…/94c98ed8…恢复前后不变；这不是strict resolve验收。named lock66模块集合等于actual，但不证明detached实际消费锁。
- v4本体SHA860fe28f…；183build/80native/7supplemental/9external/6tools/3aux全域前后check=0，主源码/metadata/锁/审批SHA无漂移；index空，相关Gradle/Lint/curl/audit进程NONE。
- 最终handoff.md已写，包含实际commands/逐exit log SHA、complete graph+artifact hash索引、许可blockers、fixture/main impact、v4域后核与B2前置。恢复任务完成，B1准入仍blocked；停止writes交父same-model审核。
