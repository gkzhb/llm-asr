# B1 固定Lint工具依赖隔离准入

## Binding
Task llm-asr-76d.5；role project-agent leaf p1-test-2（前writer p1-test已terminated/provider失败，无final；本轮按父授权磁盘恢复接管，独占writer）；owner/唯一Beads writer p1-steward。cwd /home/zhb/gitrep/llm-asr main base7eace17a8ea636db6010f95d2229c28dc2f78467。唯一writer本目录三文件及.work/p1-lint-prep/、受控cache；不改主输入/metadata/locks/Gradle/report、不claim/commit/委派。

## Goal
正式授权B1：最小AGP8.6.1独立library compile35/min29，仅准备实际31.6.1 Lint工具固定闭包，来源许可/旁证/候选metadata-lockdiff与副本strict offline resolve；不执行Lint或主工程构建。主合并/接线由原writer经父候选审查后顺序执行。

## Current Phase
Phase2 blocked：source-audit真实exit0/168对象完成；license-summary真实exit2/8项UNKNOWN。provider失败不改这些结果。strict/offline resolve无成功证据且因许可硬停止不运行；仅补齐静态一致证明/冻结后核/blocked交接。

## Next Step
本leaf已完成blocked交接并停止writes；父派same-model candidate审查核本证据/13个分层许可缺口，再明确有限补证授权。不得先进入原writer B2。最终结果.work/p1-lint-prep/handoff.md。

### Phase 1 preflight/真实配置
- [x] HEAD/status/task/ps，v4本体SHA860fe28f…及已审freeze-a.py check exit0，183build/80native等所有域无漂移；无并行Gradle。
- [x] 独立fixture/真实AGP配置owner/name/attributes/root发现。discover-network b36e5874a exit0。
- **Status:** complete

### Phase 2 bounded bootstrap与来源准入
- [x] 仅目标bootstrap resolve，既有hash不改，候选副本metadata/locks；官方源URL/sidecar/固定图已保存并实算。
- [ ] 许可准入：8 UNKNOWN + 5 actual missing POM，license-summary.exit=2；明确blocked，未扩大调查。
- **Status:** blocked

### Phase 3 strict resolve/交接
- [ ] 副本strict offline resolve NOT RUN，因许可硬停止；静态恢复前后SHA不变不能冒充strict执行。
- [x] b8940d14c已completed/exit0：完整67nodes含reasons/171edges/66artifacts actual/named精确相等；66cache JAR与168官方对象SHA全部重核匹配；23新增/412旧保留/0改变缺失。
- [x] v4全域前后check exit0，candidate/两锁前后不变；候选语义diff/fixtureonly锁/主impact/blocked handoff已写，无遗留bg/Gradle/curl。
- **Status:** blocked

### Recovery handoff
- **Status:** complete
- 最终.work/p1-lint-prep/handoff.md。静态身份通过不等于B1准入/strict/Lint/M4/APK通过；父接棒，leaf停止writes。

## Boundaries
A p1-review-5已通过，其他leaves停写；Nix同tiny mirror真正cache-only无新工具下载。仅官方Google/MavenCentral HTTPS，单worker/保守串行；本机free实际15.5GiB，非旧8GiB，用户准许固定Lint依赖下载校验。无Lint green-red-green/productionLint/full/device/mic/private/model/key/history。来源许可/旧hash不符/未知版本repo/漂移/范围扩展立停；同blocker最多三不同有效尝试。不把bootstrap/同仓sidecar当独立可信审查或CVE扫描。

## Errors
- 接管读盘：source-audit.exit=0，license-summary.exit=2，8项许可UNKNOWN；不重跑audit，不将已下载/匹配hash当许可通过，不执行strict-resolve.sh。
- discover bed33ab27 exit1：本leafGroovy buildscript在plugins后语法失败，改独占顺序。
- discover-retry b48339a2c exit1：AGP-only选reflect1.9.20缺缓存（主Kotlin plugin选1.9.22），不force；父明确准许该固定前置，静态官方POM/JAR准入后candidate新增两SHA。
- reflect官方sha256/sha1侧文件返回404，asc200但无可信发布者key未验证；JAR无LICENSE/NOTICE条目，POM Apache2.0/JetBrains官方SCM许可来源明确，记录边界，不冒充独立签名证明。
