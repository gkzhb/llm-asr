# P1-A 修复 A 子批（本地证据完成，等待父窄审）

Task llm-asr-76d.5；owner/唯一Beads writer p1-steward，源码/执行plan writer p1-build；main/base7eace17a8ea636db6010f95d2229c28dc2f78467。工具p1-test已交cache-only通过并停止写入，父明确解除冻结。此批仅R1入口fail-closed、R2完整图护栏、R3ignored新freeze生成器、R4checksum部分；不Lint/download/locksmetadata/full/APK/production/native数学/权限签名/设备/commit。

## 当前实现及目标证据

- R1：实际旧入口tee23/grep2均假成功0，目标red保存 `.work/p1-repair/a-pre-fix-red.log`；修复一次保存全部PIPESTATUS，保留Gradle非零优先/否则tee码、两码diagnostic；完整grep读取，无-q，区分0/1/error。目标tee23、Gradle7+tee23→7、grep执行2/真实丢日志读2，恢复0；实际shell入口mock Gradle，普通/-O，不能冒称真实Gradle执行。
- R2：真实parser完整dry-run行、一次根verifyFast、重复/畸形/空图拒绝；heavy任务families先检查全图，再module/root范围。只runtime library两个JAR及实际main图证明的runtime packageDebugResources资源stage精确例外，非APK。主真实图初次资源stage被保守拒绝，记录失败后窄修，不删gate。
- 真实无plugin Gradle direct Lint/checksum/host/JNI/APK及indirect native图6目标拒绝、恢复图通过；Gradle自身均BUILD SUCCESSFUL，入口6次exit1+恢复0；`.work/p1-repair/a-real-graph-retry.log`及`graph-real-vt9g1iit/`。此前fixture wrapper权限丢失exit126仅fixture失败，不算目标红，改copy2恢复权限后重跑。
- R3：不改已正确APK collector，ignored freeze-a.py从actual collector AST中只执行已审inventory片段，得完整keys/SHA并纳入source-lock；同样读取native input_hashes纯AST函数证明子集。缺lock/同长度摘要漂移/多未知输入/少script目标红绿；独立build_inputs/supplemental_review_inputs/external_evidence域，原13/179freeze文件本体不覆盖。现external native只是旧产物观测摘要，不宣称本轮APK。
- R4 checksum部分：唯一component/artifact/SHA各1，只改目标SHA、XML回读恢复证明其余语义不变。实际HTML必须唯一tbody目标row、expected零actual原可信SHA，还实算当前cache jar SHA；不把帮助文字/缺checksum/网络失败当红。纯负例普通/-O通过；真实original0→mutant1→restored0 strict offline help完成exit0，fresh report `dependency-negative-l74w78`，actual可信SHA `9697733a215f2ed42e5b2fc4053484ec832eab29584ebced94d92c91090051c1`，cache jar实算一致。

## 未做

B批Lint来源/闭包/锁metadata准备、真实Lint error/恢复及production Lint、full/JNI/native/APK未运行，不称R4全部解决或M4完成。原full gate代码依赖保留，A批只在verifyLocalGuards补入口/图/checksum parser tests；真实35JUnit fast strict offline rerun exit0，23action全执行/27s，core34+runtime1 XML零failure/error/skipped，3实际正编译/5目标module依赖拒绝；main graph-fast35完整任务exit0（9s），无native/Lint/full链，不以旧M3包替代。设备/麦克风/private/model/远端CI全部not run。父fresh同模型窄review待分派，非跨家族验证。

最终commands/exit/log、freeze SHA与检查点结论以 `.work/p1-repair/batch-a-handoff.md` 为准。后台bbb895fd3 terminal completed/exit0：graph/pure/checksum/fast各0。新freeze分build/supplemental/external/tool身份并保存旧freeze本体SHA，不产生新artifact身份；真实APK绑定待B full后核验。父同模型p1-review-3已消费：原R1/R3漏/R4checksum关闭；A1–A4收尾需差异复核，非跨家族验证。

## A1–A4 限定收尾（本地完成，父fresh差异复核待做）

精确任务path以已执行35主图登记，core/runtime未知任务亦拒绝；leading space/tab task候选拒为畸形。新tests旧程序先目标red，修复后真实入口/parser固定3.12.14与PATH3.14.7普通/-O全绿。真实无plugincore:downloadArtifact间接图Gradle success但entry1具体未知path拒绝，恢复0；主35任务graph0；fast strict offline rerun23s/23action全部执行，35JUnit零失败/错误/跳过。证据polish-{pure,real-graph,main-graph,fast}.log/.exit，后台b97f6699f完成exit0。

ignored generator执行前核完整已审collector span SHA c3eb247847a358d14bb9b753dd1cf227355904f9196c1615f0914a16dd20a25a、native函数span SHA85701edbd4a3b4f1ac6832a19b470485ebf2087ed41f5783d5d94c3538519799；unknown段在compile/exec前拒绝，变更必须重审批准，不自动更新。临时目录新增script/native-cmake/native-apk/src/缺source-lock及段变更/importalias/重绑定有目标红绿；未知片段只有无害observer，未执行危险payload。辅助工具只证明这两个已审片段身份，不是通用安全AST或未来sandbox。

旧179/13/A-v2及首次freeze均留历史；新v3独立build/review/external/tool及generator/tests身份，Phase2旧notrun已标历史。无新APK、B/full未做，R1/checksum闭合逻辑未改；本轮3个graph构建scripts/tests文件变化，不扩inventory框架。最终交接 `.work/p1-repair/batch-a-polish-handoff.md`，父接受前不进入B。
