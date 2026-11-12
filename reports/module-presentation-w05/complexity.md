# 模型页面呈现：复杂度与职责审查

任务 llm-asr-w05；基点 `326c9be5c367e4d33fe88fc21729cce4cb5ee9bd`。本报告是静态复杂度信号、人工职责处置与当批验证的摘要，不把分数当缺陷/安全/性能证明。

## 全仓来源与覆盖边界

当批基点全仓维护库存由实际 cccc1.8.3 / knots1.18.0 CLI 采集，来源 `.work/w05-metrics/metrics-report.md`（SHA256 `c991a500a984ba343679da53b7130b50d25f8ede697d8e7698318a2b9d887ce5`）、`current/{sources,execution,cccc,knots,normalized}.json` 与 supplemental4源码。原始大JSON不纳提交。显式文件参数、cccc `--no-config --no-cache --jobs 2`，knots `--format json --jobs 2`；实际exit0。工具二进制SHA分别 `aca4b804b8ebe9cd151f4fb949ff6081247bca5dc8e04d74e94e678c62ebb4ee`、`c20482d92eb145d45307c0ccb0ec8629229b7a0b13c6a05cf1232e6e3a7d0de0`。

主库存266文件分组：生产77、测试112、工具77。生产 cccc22输入/466函数、knots77/1008；测试71/1172、108/1290；工具54/280、59/255。另4supplemental不混总排名。旧209基线范围已变，原compare exit2/FAIL-UNKNOWN保留，不称零退化；仅139共同路径同版本同范围复采，近期8共同IME路径单列。历史基线未覆盖刷新。

cccc不支持Kotlin；knots Gradle DSL零记录不证明任务逻辑无复杂度，JNI宏/解析partial、声明零函数和匿名closure覆盖均需人工说明。生产/测试/工具热点分表，不用全仓平均抵消局部事务风险。补充指标/原rawSHA见本批inputs.json证据索引。

## A 三生产文件同版本同范围前后

局部精确三生产+三测试，before Git326c9be原字节、after当前冻结源码；`.work/w05-a-implement/before-metrics` 与最终`after-2-metrics`，raw/argv/source/toolSHA齐全，两个CLI0。不与全仓不同库存求总分。

| 位置 | cccc cognitive/cyclomatic | knots cognitive/mccabe | 解释 |
|---|---|---|---|
| ModelManagementActivity.render |84/78→31/40|33/78→18/40，SLOC72→42|仍保生命周期/inspect后busy复核/View/owner，非机械拆setter |
| ModelManagementController.pagePresentation（新增） |不支持Kotlin，UNKNOWN|5/5，nesting3/SLOC17|短锁仅采内存绑定，锁外纯投影，不承诺全局原子 |
| ModelUiText.detail/fileDetails/card（新增） |不支持Kotlin，UNKNOWN|11/8、9/7、7/5|模型证据/展示资格与文本同处，返回嵌套不可变值 |
| Activity.expectedHash→UiText.expectedHash |Java记录移除，Kotlin不可见|新纯manifest查找1/2|不是整体复杂度消失 |
| 原Controller terminal/run/finish |不支持Kotlin|181/40、88/45、43/12均不变|submit至EOF受保护body逐字节不改，保内聚事务 |
| 原UiText.task |不支持Kotlin|29/18不变|既有文本热点，未扩事务 |

收益是调用方Interface知识减少：Activity不再知道安装identity归属、row/readiness epoch支持证明、bound操作与cached库存降级、空custom删除资格或manifest期望SHA。一个pagePresentation(key,residentSnapshot)隐藏上述策略，不暴露可变Deployment/Repository/Readiness，不复制巨Snapshot，不新增topowner或十几布尔参数。页面资格不是操作授权，实际delete key/installation/epoch/confirmation与最终命令复核保持。

### 新测试热点和盲点

picker.main cccc14/11→27/21、knots13/12→22/20，来自第二合法identity mutant/actual-exit安全恢复；旧预算不增。corefault修复driver core_fault_suite cccc9/9→15/15、knots7/7→9/10，nesting2，新增有限源码闭包/SHA检查触阈经人工处置，原四fault断言与budget/lease不变。修复单文件raw在 `.work/w05-corefault-repair/{before,after}-metrics`，不重采产品。

knots对tests/ModelAndroidHelpersTest.kt虽保34记录，静默遗漏scopedMutation/listeners/workerRetention/newpresentation及metadata/native stub/pages/main等；逐函数**UNKNOWN**及行号在 `.work/w05-a-implement/knots-helper-unknown.json`，人工读并实际canonicalhelper执行，不填0。全部9新ModelUiText命名投影函数已记录；嵌入Python字符串的JavaActivitycaller也不由Python指标覆盖，靠人工+实际编译执行。

## 选择与保留

隔离职责审查结合全仓raw与实际caller选择A，不仅因为render高分。B（IME纯呈现，knots74/48）独立后置；Controller终态、ZIPprepare91/53、Repository/Residency/capture/history/JNI等高分但内聚的owner/IO/commit事务本轮不拆。fresh同模型上下文隔离Standards/Spec及C/corefault窄审由父接受，非跨家族审查；无伪造diskreceipt。设备/真实ASR/质量性能均未验证。
