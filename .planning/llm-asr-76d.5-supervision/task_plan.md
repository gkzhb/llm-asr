# P1-A 验证护栏监督计划

## Goal
按最新综合规划P1-A交付同源快速/完整本地验证入口、Lint与限定格式gate和真实失败负例；保持35JUnit、host/ABI/mutants、模块/native/JNI/APK/strict checksum原门槛。开发→冻结同模型上下文隔离审查→集中修复→委派验证agent独立复验→指纹/安全审计→授权review agent本地提交。未满足完整M4或设备验收不标完成。

## Binding
- Beads: llm-asr-76d.5；owner/唯一Beads writer p1-steward（父）。role steward。
- 当前父session 01a13d7f-ed59-777f-8029-01440851d699；Herdr wY:p1；沿用协调身份p1-steward。前父session 01a13b5d-d4ba-777f-8029-0142a2c4aeaf为历史。
- cwd /home/zhb/gitrep/llm-asr；main；base 7eace17a8ea636db6010f95d2229c28dc2f78467。
- 父唯一监督plan writer；开发p1-build独占源码及.planning/llm-asr-76d.5/，审查只读。
- 本用户已授权委派执行和持续协调。本批只P1-A；P1-B后续顺序分派，P2/P5/P6决策不擅定。

## Current Phase
B2实现/有界回归交接已消费（2026-10-15）：p1b2-build正式完成停写，无遗留后台；b6763c0ee completed0，主13consumer只resolve、graph35、fast23action/35JUnit、checksum0/1/0和3.12/3.14普通-O通过，193build输入前后/current一致。finalfreeze SHA ab480593aca691a7cc03202fae94af0a1e061cb66426c8d7629a174a0d97dd45。旧成功fixture未重跑，历史launchdriverSHA缺失限制明确；默认AGP Lint与普通共享validator负例证据分层。B2同模型fresh只读审查NO BLOCKING/0阻塞，父完整消费报告。B3a生产Lint已于11:35真实failed/exit1，11Error+1Fatal/18Warning；最终handoff磁盘11:37落盘但父未收到回执，15m advisory后一次runtime/文件核查恢复消费，无相关构建进程。193build/80native及B2历史输入未变，非缓存或资源失败。用户授权三处修复已实现并回归通过：App原12blocking全关闭、17warnings；runtime首次standalone报告1 MissingPermission Error/10同源warnings（空library Manifest缺既有RECORD_AUDIO声明），超原Manifest权限scope，writer已停。新194build冻结SHA957051a9977f1bda2a2458b66041fb279ca6fa3275965e80fcad8ed9743bbf98，原host/35JUnit/JavaJNI及窄回归通过，统一Lint仍exit1。lintfix-review同模型fresh审查已完成：三修复无阻塞，30项独立核对通过；确认runtime静态声明缺失，建议仅补App已有RECORD_AUDIO一条无附加属性声明并验证最终集合不变。用户已明确要求自行执行、无需逐项人工授权；当前解除scope阻塞，自主完成本轮必要窄修复与验证/审查/full/安全本地提交。permfix-build已完成停写，唯一统一Lint真实exit0/72actions；两模块零Error/Fatal，App17/runtime10同源warnings保留defer，六份真实Manifest检查App全树不变/runtime仅加既有声明。新195buildfreeze c84a2641c291ffa037b2145bfb3fb8c91473e73e91b49443d9bebd10f71f5599，旧528证据保持。permfix-review已完成无阻塞，74项独立核对与首尾195输入身份一致；父接受后B3b已completed0：唯一full428.052s/111actions全执行、35JUnit全零、Lint/真实fixturechecksum/JNI-native/APK包签名输入绑定通过。195build/80native/current/APKreport exact，最终freeze a401c3e9180194e6c28ae9fd991c11a6a299f4e6e455e089c346236f1824f698；工程验证通过；p1ship-review最后只读审查NO BLOCKING，101独立核对通过，72逐文件候选/全部35构建输入delta覆盖，0敏感模式命中。父接受，冻结监督目录写入，另阶段授权该reviewer复核动态8文件后限定stage/安全审计/新本地commit。是否提交成功及身份以Git日志和提交receipt为准，不push。其他设备/私人数据/push等边界不扩大。

## Next Step
p1ship-review执行已批准72逐文件清单安全stage/commit并核committed195输入/APK绑定；父停止仓库文档写入，消费提交receipt后更新Beads并交付。

### Phase 0: 恢复/范围/ownership
- [x] 完整恢复根三文件（截断补读）及最新mqn三文件。
- [x] 显式核M4/M5/ap5与dependency_type、Git/runtime；claim M4成功。
- [x] 保留已有未知/他人未跟踪目录和研究文档。
- **Status:** complete

### Phase 1: 窄开发批次
- [x] p1-build生成任务绑定plan、基线及实现检查点。
- [x] 快速/完整同源入口、Lint/格式/strict参数防绕过、目标负例代码实现。
- [x] shell/Python针对性及source回归通过，文档/清单交接；真实Gradle/Lint/full仍阻塞。
- **Status:** complete

### Phase 2: 冻结与审查修复
- [x] 检查真实diff/日志，179输入与13审查文本SHA核MATCH。
- [x] 同模型p1-review完成：发现R1 HIGH/R2护栏及R3冻结/R4负例缺口，不通过。
- [x] p1-build只读方案、p1-test工具准备最终handoff均消费；工具cache-only exit0。
- [x] 原p1-build A实际交接/停止writes，父完整读取handoff/plan/progress；183build80native新freeze，旧13/179文件历史保留。
- [x] p1-review-2 provider408无报告；p1-review-3完成：R1/R3漏项/R4checksum关闭，285指纹及35JUnit实证匹配；A1/A2graph及A3freeze辅助工具三MEDIUM/A4LOW文档待修。
- [x] 原p1-build限定收尾交接已完整消费，停止writes，v3/真实unknown图负例/主graph与fast再次通过。
- [x] p1-review-4关闭A1/A2/A4，A3仅decorator审批范围缺口待修。
- [x] 原writer单点decorator修复交接停止writes，v4/183build不变；最小目标红绿完成。
- [x] p1-review-5 A3单点close/v4核一致，A阻塞全部解决，可进入B但非M4验收。
- [x] p1-test B只读方案已消费，正式B1独占隔离准备授权启动。
- [x] B1 source/candidate/hostlicense/strictcomparator实际验收及review6/7/8闭环。
- [x] p1b2-build完成B2最终回归/freeze/handoff，父完整消费，writer停止写入。
- [x] p1b2-review同模型fresh只读审查NO BLOCKING，193库存及全部分域/保护/目标证据核齐，限制准确。
- [x] B3a生产Lint实际执行并保存失败证据，父已消费lint-handoff/diagnostics；非验收通过。
- [x] 用户明确授权三处最小修复，核runtime无其他执行者、owner保持。
- [x] lintfix-build三修复及回归完成，fresh lintfix-review无阻塞；runtime声明缺口需补。
- [x] permfix-build权限契约/merge/Lint通过，fresh permfix-review无阻塞，74项独立核对通过。
- [x] p1full-test B3b full/JNI/native/APK输入签名绑定及报告通过。
- [x] p1ship-review最后只读证据/安全清单审NO BLOCKING，101项核对/72候选安全通过。
- [ ] p1ship-review已获限定stage/commit授权；成功身份以Git及receipt为准，不以本计划勾选替代。
- **Status:** in_progress

### Phase 3: 父复验/提交
- [x] 已委派p1full-test独立完整验证，当前APK/195inputs/80native绑定通过；父只读消费handoff/report，不亲做产品验收。
- [ ] 用户明确要求由review agent执行提交：审查先只读；独立批次全部开发/验证/必要审查及产物绑定满足后，父另授权review agent限定暂存/安全审计/本地提交，再核committed输入后交付。不等整个P1-P10完成，不将未验证检查点当已提交交付。
- [ ] Beads及plan真实状态更新，未满足完整M4/设备保持待验收。
- **Status:** pending

## Boundaries
不改变模型数学、production行为、录音gate/IME隐私/owner/权限/包名/签名；用户已进一步授权本轮必要窄修复自主推进，包含runtime既有RECORD_AUDIO静态契约补全（最终APK权限集合不增）；原三处生产Lint修复：精确helper API契约（真实版本分支保留）、权限撤销SecurityException安全处理、AGP staged Manifest冗余debuggable治理，不扩展权限/依赖/ABI；不恢复ap5或更改其owner。无设备/麦克风/私人数据/模型权重访问、远端CI/push、历史改写。用户后续已明确授权本批固定工具闭包及Lint必要依赖的受控下载校验，不升级版本、不自动接受新hash；首次未经该新增授权的Nix下载仍保留为边界错误。CI provider未决只文档不自动选。签名复用现构建流程，不读取密钥内容。父不并写worker文件，开发child不提交/关闭任务/二级委派。用户后续明确要求review agent执行本地提交；须先完成只读审查，再由父另行授权限定提交阶段，安全审计/验证/产物绑定通过后才commit，不push/改写历史。

## Errors Encountered
| 错误 | 处置 |
|---|---|
| 恢复progress/findings超50KB截断 | 按offset补齐，不以截断当完成 |
| 查询尚不存在M4 plan ls exit2 | 尚未实施，无旧plan，创建新任务绑定目录，不重复读取缺失文件 |
| 子外层Nix自动补cache违背cache-only边界 | 父核PID发送TERM，resume同child确认终止；改真正Nix offline并在缺缓存时停止准备，Gradle offline不替代Nix离线 |
| p1-review-2 provider408 stream disconnected | raw错误仅连接中断非权限拒绝/审查结论；同模型新fresh p1-review-3 bounded重审，若再发生则暂停连接分支报告 |
| 新父监督findings首次edit原文不匹配 | 原子拒绝且未改变文件；改用真实末行追加恢复事实，不重复空替换 |

## Blocked Stop Condition
owner/runtime冲突、必须新依赖/下载/远端平台/产品策略决定、无法保留门槛或Lint出现大量既有问题需生产修复时停止相应分支并上报；三种有效不同尝试仍阻塞则报告证据与下一输入，不baseline/抑制/删检查/更新hash凑green。
