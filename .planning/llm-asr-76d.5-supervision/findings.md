# P1-A 监督发现

- 最新产品路线是未跟踪.planning/llm-asr-mqn三文件，规划任务closed仅表示规划验收；P1-P10均pending。mtime更晚g53为依赖skill文档，不是下一开发路线。
- HEAD7eace17为依赖skill收尾；产品M3功能9ec98d3已提交。真实63源/10Kotlin53Java与core50/runtime5/app8；最新产品证据reports/modules-m3，不用旧reports/apk替代。
- 现verifyDebug已连host/35JUnit/JNI/native/APK+module gates；scripts/verify-gradle.sh固定strict且拒写metadata/锁，未有完整快速/Lint分层入口。需保留原fail-closed机制而非重造测试。
- M4/M5 assignee原为空，blocks仅M3closed。bd ready/blocked汇总会把parent-child epic误报前置，以show的dependency_type为准。
- ap5 deferred且原owner llm-asr-kotlin，不接管本批。sherpa native/layout/许可、驻留预算、UI框架、设备授权均待后续决策。
- Herdr首次实核仅父本项目working、另项目idle，无重叠writer。父claim M4成功，子不写Beads。
- 初始tracked diff空；未跟踪保留.beads.gate.lock、两分析目录、mqn/v07目录及docs/multi-engine-adaptation-plan.md；不夹带旧研究提交。
- 现约8GiB主机，重型Gradle/native测试串行；bg_run使用fish默认，复杂命令显式bash入口，不轮询。

## 新父恢复实核
- /tmp/llm-asr-handoff-2026-10-15-p1-b2.md已读，当前父session01a13d7f-ed59-777f-8029-01440851d699，沿用p1-steward职责，不更换assignee。
- Herdr agent/pane仅本项目父working，另一nix-config项目idle；ps无相关Gradle/Lint/B2后台，允许新唯一writer接管，不等待已结束任务。
- M4 in_progress/p1-steward，blocks仅M3 closed，epic parent-child不阻塞；ap5 deferred/llm-asr-kotlin保持。HEAD7eace17/index空。
- fixture-shared-validator.exit=0；run-ukwens2p/result.json记录默认AGP Lint0/1/0、green源SHA相同、identity artifact/edge及files三负例exit1/restore0。父只读消费记录，完整源码/日志身份验收委派leaf，非父重跑。
- 当前6 tracked差异及多份新文本与交接范围一致；旧分析目录/mqn/v07/多引擎规划及.beads.gate.lock保留，不纳入自动提交。

## 提交后当前真相（2026-10-15）
- P1-A已完成功能提交2d869e5，完整证据reports/local-verify-p1/full.md；提交后195build/80native绑定receipt在.work/p1-ship-review/commit-receipt.json。此处旧6tracked描述仅恢复时事实，不是当前Git状态。
- M4剩余CI provider/workflow/静态验证未完成；M5(llm-asr-76d.6)仍open/未分派/assignee空，唯一blocks前置M3closed，epic只是parent-child；ap5仍deferred/原owner保持。
- 本次成功full自然保存driver启动SHA并重新实际执行fixture/checksum，旧B2缺launchSHA局限不追溯抹掉；shared validator负例仍不等默认AGP可变性测试。
- 规划完成记录属于提交后协调文本，不改原测试报告/freeze；旧supplemental域与已更新规划有预期差异，应核195产品域或用Git2d869e5还原旧文档字节，不能重跑已完成开发弥补状态文字。
- 后续遵守父只协调、所有开发/下载/校验/构建由leaf执行，同sol模型fresh审查；普通final的started/yield会被视为completed，不把它当实际bg终态。通知丢失时有明确异常才一次性核runtime/exit，磁盘已有终态则收尾而非重复计算。
