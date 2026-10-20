# llm-asr-mfe 模型维护日志闭环（targeted 实施中）

任务 llm-asr-mfe；父/唯一 Beads writer p1-steward；唯一实施 leaf mfe-research，newapi/gpt-6.1-sol/high。base `d48951dda51427f60a57dc622e26c069e892d79d`，父已批准模型维护日志窄切片。**尚未 fresh 审查、full 或安全本地提交，不交付新 APK。** near-MAX 历史序号耗尽与 picker 原因覆盖仍未实施，不关闭 mfe。

## 范围与契约

只改 Controller、既有 Controller 测试、单源 baseline pin 及本报告/两份文档。pending/terminal 的已决定事件在状态安装后、controller lock 外先 best-effort emit，再通知 presentation；finish 仅在合成 FAILED 分支发既有对应 MODEL_*_FAILED，正常终态不重复，INSPECT不产维护日志。无新字段/API/事件/依赖/worker框架；不改模型IO、ready/epoch/取消仲裁、control.complete finally或shared owner释放位置。现有 observer/worker Error传播与safeLog Throwable隔离保持。健康Store每操作日志路径一次尝试，不是坏Store/磁盘/进程fatal存活的持久保证。

## 真实生产 red 与实现

隔离副本以 exact d48951d Controller SHA256 `e6cc19db65e44f5e6f50bd85a43b2b100b47a123e6917d7a5d2cd58535d1285d`、canonical host mixed source inventory fresh编译 exit0，六selector分别在运行时命中目标AssertionError/exit1：IMPORT/VERIFY/DELETE admitted拒绝（缺FAILED），queued worker InternalError早退（缺FAILED），terminal observer InternalError（跳过已决定终态日志），pending observer InternalError（跳过STARTED）。不是编译/cache/timeout失败作red。首次red后台bfb1e0e71真实completed/exit0；原始证据`.work/mfe-research/implementation/old-red/`。

产品最小修复 Controller SHA256 `1485ea152e0dbdfbacbb6306e5a77ced61c92aedcc2869a3f4eda4eedb0c5da3`。新增测试在现有main默认运行，既有JUnit/host入口无需新增或修改。所有新断言在callback外，正常操作/异常传播/释放先于目录清理；只读3字节合成模型，不访问私人内容。

## 历史失败（保留）

首轮 fixed-green（后台bcb0c02bb exit1）六缺陷selector已green，保护组在成功deleteAll已移除空模型根后，测试cleanup误调用Files.walk导致NoSuchFileException。此为新测试清理缺陷，不是产品目标red。修复仅在确认queue/owner/control已结束后，对NOFOLLOW确认不存在的根直接返回；原失败log/compiled inputs保留，新测试身份重新执行完整红绿链。

## Targeted 行为闭环

后台 `b8e4a56f1` completed/exit0。清理修复后的同一测试 SHA256 `68243c45c5b778352c10983d447fb82dfa87f210eda119aaa9462750be5279d7` 在四个全新隔离源码/class目录执行：

| 阶段 | 真实结果 |
|---|---|
| old-red-testfix | exact old Controller fresh编译0；六R1–R4 selector各目标AssertionError1 |
| fixed-green-testfix | fixed Controller fresh编译0；六selector0、logging-protection0、默认Controller main0、ModelNotificationTest0、ModelReviewFixTest全部8参数各0 |
| old-rollback-red | exact old Controller字节回退，fresh编译0；同一六目标AssertionError1 |
| fixed-restored-green | fixed Controller恢复，fresh编译0；六selector0、logging-protection0 |

四阶段均由驱动核主树源码未变、每个subprocess实际退出，无pending；manifest/executions/result精确绑定每次源码与log SHA。原始证据`.work/mfe-research/implementation/{old-red-testfix,fixed-green-testfix,old-rollback-red,fixed-restored-green}/`。没有在main产品源码取red或用旧class替代fresh混编。

保护组覆盖三kind普通success/failure、IMPORT/VERIFY取消，late cancel不覆盖终态、INSPECT/busy/stale不产日志、拒绝后重试新UUID/旧新各exact pair；RuntimeException与InternalError sink分别组合正常worker/同一InternalError worker，不改变业务结果/原Error/释放；真实RuntimeLogCodec导出无合成URI/path/正文/异常类/stack sentinel。只检查best-effort已接受内存事件，不冒持久落盘保证。

## Targeted Gradle 与复杂度

后台 `b6431cf35` completed/exit0，串行固定Nix cache-only执行：

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh fast --offline --rerun-tasks
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh format --offline
```

fast真实exit0/23 actionable全部executed、`PIPELINE_STATUS Gradle=0 tee=0 scan=1`；真实core/runtime编译、JUnit及实际module classpath正负例执行。format exit0，既有轻量入口/架构/格式保护负例保持。fast没有full/host复编/JNI/native/APK/Lint设备链；不能把其通过冒完整工程或新APK。终态审计实际解析fresh XML：core34/runtime1，全部failure/error/skip0，方法名单与当前源码精确一致；core XML stdout包含默认Controller main（含新logging组）执行。未新增JUnit方法或改host接线。

双工具before/after均真实exit0，maintained-tracked-v1同一210源码库存、cccc1.8.3 `--no-config --no-cache --jobs 2`、knots1.18.0 `--format json --jobs 2`；工具binary SHA/完整argv/source/raw/stderr留ignored证据，原reports/complexity-baseline不改。

| 生产函数 | cccc cognitive/cyclomatic before→after | knots cognitive/mccabe before→after | 人工处置 |
|---|---|---|---|
| finish | 11/7→19/10 | 11/7→12/10 | 新增的三kind失败映射及lock外条件emit命中相对增长/分支阈值；嵌套不增（knots2），30 SLOC，合成FAILED分支有真实三kind拒绝/异常/observer redgreen及正常不重复保护。暂保最小实现，不为降分重构terminal或只移入浅wrapper；fresh审查须明确接受/指出阻塞。 |
| terminal | 93/33→93/33 | 37/33→37/33 | 历史热点/nesting5保持，仅lock外emit通知排序；真实决定后observer Error锁定行为，不重写主体。 |
| pending | 8/5→8/5 | 3/5→3/5 | 控制流不增，仅lock外emit通知排序，准入observer Error锁定。 |
| safeLog | 2/3→2/3 | 2/2→2/2 | 现有隔离策略不改。 |

新增测试信号单列：logPair cccc C11/Y13、knots C8/M13（循环选UUID、顺序/字段/隐私断言与detail选择）；loggingObserver knots C11/M12（admission/terminal共享确定性场景、try/finally移除observer、外部断言）；main knots M10/SLOC128/AICP86主要是既有长main另加focused dispatch/default新组。测试信号不与生产混排，不以均分抵消；新case分方法，无新线程或无界清理。cccc Kotlin盲区、旧JNI partial-parse/GradleDSL零记录仍保留，不称全语言完整语义覆盖。

## 保护与终态

后台 `b82a36cee` 的首次证据审计exit1，是辅助脚本误在Gradle控制台找main stdout（Gradle默认不打印）；实际stdout在JUnit XML。修正后 `b402ea1e7` completed/exit0，仅重做审计与保护，不重跑已成功工程链；原失败log保留。

after保护核验2688明确文件：2683字节/mode/size保持，仅5个授权既有候选改变，新报告另列candidate manifest；原诊断handoff/sources、三旧APK/正式两域receipt与历史reports、578native objects/DSO、所有锁/metadata/未授权源保持。动态213build/10module/80native库存与before一致：build仅Controller/Test/baseline三SHA变，module零变，native输入域仅Controller SHA变（该collector含所有生产JVM源，不表示native架构/DSO变）。原APK不对应本轮新产品源，不冒本轮可验证APK。保护是显式列出的非私人文件，不宣称扫描全部ignored/cache/private。

完整行为/工具/复杂度/实际JUnit/输入delta/候选mode-bytes-SHA和所有后台terminal证据仅在ignored `.work/mfe-research/implementation/`，交接`handoff.md`与`closure.json`。旧证据不改写。当前fresh审查、唯一full及两阶段安全本地提交仍未执行；本leaf停写等待父fresh审查。

## 未执行

full、生产Android/JNI/native重编与新包检查、设备/runner发现执行/ADB/模拟器/麦克风/私文、模型计算/下载、新依赖prepare、远端CI、stage/commit/push/amend均未执行。原三包/receipt/578native及旧证据逐SHA保护；host/fake不能代设备验收。
