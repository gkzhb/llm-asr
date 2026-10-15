# B3a Findings

## 2026-10-15 lintfix-build 接管
- 用户三处scope已授权；Beads p1-steward/in_progress，Herdr wY:p17本leaf working/父idle，无其他同项目leaf或GradleLint。HEAD7eace17/index空，旧B2/B3只读保留。
- 诊断已有确定性真实统一Lint红基线：12 blocking/18warning；不重复145秒旧失败。窄source/helper、真实capture fakes及真实stage测试先旧红，最后统一Lint原反馈复验。假设排序：①已有guard但helper缺API契约→精确TargetApi关闭10NewApi及InlinedApi；②平台权限可在check后撤销且无SecurityException规范化→固定IOException及真实finally；③staged冗余debuggable→仅去生成属性，debugDSL保持。依赖升级/权限扩展不能解释这些具体fileline，排除为本轮路径。因根因由真实诊断精确确定，跳过无必要的二分/日志instrument与再红Lint，不增加调试框架。
- ForegroundRecorder真实try包含构造/start/read，finally始终captureReleased（即worker收尾，即使构造失败；真实RecordingControl.started仍false所以不可commit）。清理getRecordingState/stop/release亦可能SecurityException，应覆盖整个内部try/finally，且必须先完成release/captureReleased再固定IOException；不吞Error、不改gate。
- 保持legacyManifest及packagechecker；必要仅更新baseline中两份获准生产源码SHA（不是依赖hash/collector审批span），revision_scope解释。原193build将新增1测试等，94protected中的两个获准源码会变；不能再声称全部保持。
- 正确host seam无需改生产架构：真实ForegroundRecorder+KotlinRecordingControl/PcmWave/WaveInput，Android媒体/clock fake；不执行设备。ActivityWindow新增fake边界API调用拒绝与可编译guardmutants，API注解是lexical可核+最终真实Lint，不当作Android runtime。

- 继承 B2 当前193 build map SHA 85774ffaeb3a88f8a73c4d0bdcafcbb71c9d04d49d43742ceed53a3ea1414e31；最终 freeze ab480593aca691a7cc03202fae94af0a1e061cb66426c8d7629a174a0d97dd45，fresh 同模型上下文隔离 review NO BLOCKING，父已接受。不是跨家族独立，也不是生产 Lint/full 验收。
- 初核 HEAD 精确7eace17，index 空；既有6 tracked diff 与一组 untracked 属此前实现/研究，全部保留，不 stage/commit。本leaf仅被授 B3a。
- verify-local.sh lint 实际 task verifyLint、strict/no-daemon、入口参数 allowlist，日志 ignored .work/verify-local/lint-*；不更改 argv 绕 gate。
- nix-env.sh --cache-only 外层 offline/no-update/no-write locks、空 substituters/builders/maxjobs0，仅 tiny flake mirror，无自动补缓存授权。
- 可复用 freeze-a.py 已审 helper；先核 B2/A-v4 helper身份，只调用 collector_map/native_map 而非全 snapshot，可避免把旧外部生成输出误作 tested build input；不重造 sandbox。

## 本轮修复真实终态发现（blocked）
- 相关回归bb89d759e完成0；唯一统一Lint b8c883f16 failed1/126.473s/72action，App0blocking17warning/runtime1Error10warning，两份真实report齐。13gate8consumer66artifacts171edges全部pass；非cache/OOM。
- 原App12blocking全部关闭，ActivityWindow InlinedApi自然关闭。runtime首次report暴露另一MissingPermission文本：缺RECORD_AUDIO声明，空libraryManifest不能由AppManifest静态声明代替；原App“明确权限check或SecurityException”已关闭。不能把首次看见的library诊断伪成原异常修复无效或新权限需求。
- 当前授权禁止权限Manifest政策扩展，只准staging去debug属性，故不加library uses-permission/RequiresPermission/抑制，不再猜绿或full。父fresh同模型review及决定最小module契约授权。警告App17原18减InlinedApi1，runtime10同源重复，没有新warning类别。
- 新显式库存194build=原193+新capturetest，8changed/185unchanged/0removed；native80中两获准Java源变/78不变，94protected同两源变/92不变，collector/native数学/metadata/四lock/签名/源Manifest不变。历史436files逐SHA保护。

## B3a真实终态发现
- bg b49d5db96 exit1，145.254s；统一生产Lint真实分析/报告已执行。App aggregate XML11Error+1Fatal/18Warning，MissingPermission1 ForegroundRecorder:19、NewApi10 ActivityWindow:78/81/82/90/95/96、HardcodedDebugMode1 generatedManifest:5。全部warning/fileline/message见.work/p1-b3/diagnostics.md。
- runtime analyze/debugAndroidTest/debugUnitTest已执行，但runtime report/debug及root verifyLint未到；不声称runtime standalone通过/零warning。13gate+7实际consumer在action前身份通过66artifact/171edge，未运行baseline/fix。
- ActivityWindow外层实际SDKguard存在，嵌套Api30/33未声明API契约；建议父另授精确API契约而非抑制。ForegroundRecorder无局部SecurityException处理，需保留RecordingControl/finally/release；AGPstageManifest冗余debuggable可考虑只从生成副本移除交DSL，源冻结不动。只是建议，未改。
- 193build/80native before/after/current保持批准身份，134B2历史文件不变；138生成文件已备份。实际必需native链仅mixed/JNI检查重编+既有578对象增量重链，DSO d5bacffc...a416保持；非clean/full验收。
- host16293168KiB，0.5秒282次，最多3相关JVM（wrapper/daemon/短Kotlincompiler），同时采样JVM RSS最大1959516KiB；未观察独立LintworkerJVM，非证明无workeraction；RSS非全机峰值。
- full/APK/package/currentAPK签名输入绑定/设备/模型/远端CI/提交均notrun。唯一下一步父决定修复scope，当前不满足full准入。

## permfix-build scope / seam
Runtime Manifest已在APK collector递归android/asr-runtime/src库存，但不在baseline.files静态产品pin中；无需变更baseline或任何依赖hash。现gradle_inputs_test.py是生产stage seam，另可用小型unittest验证两源Manifest契约，实际merge后用同一测试模块函数检查XML。保App原RECORD_AUDIO、microphone required=false，runtime仅一无属性补充声明，无设备运行时授权含义。既有check-minimal-apk.py精确权限白名单保持。

## PF1 final observed result
唯一productionLint已成功0；静态声明补全足以关闭runtime MissingPermission，不需RequiresPermission/依赖/suppression。Manifest不授予录音运行时授权，已审异常路径与gate仍必要且保持。App17/runtime10无新类别、runtime逐file/message是App同源重复，warning政策在新work。六fresh生成XML完整树精确兑现权限不增预测，但没有当前signed APK验收；full与签名输入绑定必须后置。RuntimeManifest在递归build库存而非baseline.files，不改pin是依据实际清单而非漏审。旧528历史文件保持；195build与测试前/后/current身份相同，新freeze不是旧blocked957051。

保护域补正：protected94包含runtimeManifest，故相对blocked957051仅此授权XML改变、93保持；native80无此XML因而全保持。不可把build/native/protected集合混称完全相同。首次finalize因错误预期而拒绝，历史失败保留，精确断言已按实清单纠正。

## B3b preflight
实际verifyDebug完整依赖原host/JNI/native/35JUnit/module/package plus Lint、入口格式source、verifyDependencyEvidence与verifyLintEvidence，未删gate。full的fixture会在父Gradle驻留时串行启动各single-use JVM；采样仅命令后代RSS/hostMemAvailable非PSS或全机峰值。permission195freeze c84a2641...5599及fresh74项只读review无阻塞已接受；旧B2/Lint失败均历史，不重写。

## B3b 完整验证最终事实
唯一完整入口真实pass而非若干局部拼接。Gradle111action全executed；140logtask行中的119无状态含8无action聚合，另12NO-SOURCE/6无actionUP-TO-DATE/3SKIPPED，所以不能报140tests或119action。35JUnit与其内部main checks分层。真实fixtureNewApi红仅隔离目标不是生产Lint失败，App17/runtime10defer并保留精确warning同源证据。
本次fixture/driver启动全SHA在full-launch，不再继承旧B2launchSHA缺失局限但不改历史。currentAPK195map与权限freeze/前后全部exact、native80同；product_baseline82a5563为历史标签，不冒HEAD7eace17/提交身份。源码freeze未动，后置docs/plan格式域独立。native578对象逐SHA相同证明增量范围而非全nativeclean；静态ELF16KiB与设备notrun分开。5秒RSS采样最多4JVM具体兑现父子Gradle并驻风险，RSS和约2.980GiB非PSS全机峰值。
无需新memory写入：本轮长期候选（full父子JVM与计数口径）交父决定是否纳入已有gradle-nix-verification；本leaf无Beads/memory授权，不改AGENTS。
