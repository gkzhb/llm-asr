# P1-A 同模型上下文隔离审查处置

Task llm-asr-76d.5；父协调/唯一Beads writer p1-steward。
Reviewer p1-review，newapi/gpt-6.1-sol/high，fresh standalone，与开发相同模型，非跨家族独立验证。审查回执明确仅只读，不执行测试/build。

## 证据身份
- base/current HEAD 7eace17a8ea636db6010f95d2229c28dc2f78467。
- parent-review-freeze.json自身SHA 7c5ba8debdd937afb55f03d50bfaea3c4a3dcc70e4b46d36a8f038aaab067caf：13/13开始结束匹配。
- input-freeze.json自身SHA 05330c6e203618928a071c4e904bfc25035121d83d598e882fc04b221db38025：179/179开始结束匹配。
- inputs map摘要799e3b0b3a3fb05310173461a936630e611f20dc4a61931d236f1ff8c25976c3；7日志SHA匹配。
- reviewer额外核native/source-lock.json SHA b3b4bc3a7c2947163d80deebc8686aecccaf38efd74b2025fe9557bfb0ccc497，与HEAD一致但冻结漏项。

## 结论
不接受P1-A验收。接受以下具体问题为修复/证据补齐范围；未发现旧full gate被删除，仅代码接线保留，不推断真实构建通过。

| ID | 分类/优先级 | 处置/验收 |
|---|---|---|
| R1 | 验证入口缺陷 HIGH | verify-local只取PIPESTATUS[0]漏tee失败，grep读取错误未fail closed。一次保存全部状态，保留Gradle失败/日志失败仲裁，检查错误非未命中；实际入口注入tee/grep失败负例及成功恢复。 |
| R2 | graph护栏缺陷 MEDIUM | 明确解析dry-run任务行与禁止task family，覆盖Lint/checksum/host/JNI/native/APK全部重型链；直接/间接接入负例。不据此说当前fast已误接native。 |
| R3 | 冻结清单缺口 MEDIUM | source-lock加入完整collector范围；保留历史179记录，新freeze覆盖全构建输入及外部证据。未发现当前漂移，不是APK collector回归。 |
| R4 | 验收负例缺口 MEDIUM | 实现隔离真实Lint error fixture/恢复正例并取得目标diagnostic；checksum断言唯一目标修改及具体checksum不符，不能用缺缓存/网络/配置失败作红。 |

## 调度约束
- p1-test仅现固定Nix闭包/版本/cache-only/input证据，工具准备仍活动，无源码writer。
- p1-build只读预备方案已交.work/p1-repair/repair-plan.md，原13/179零漂移，无测试/download；现停止写入。
- 父范围决定：分两batch。A只R1/R2入口与目标负例、R3冻结inventory补齐/collector集合实核、R4 checksum唯一变异及具体诊断逻辑；B独立Lint来源/锁metadata准备及真实fixture/生产Lint/full。工具交接后才启动A。R3缺口属于手工freeze，默认不重构已正确APK collector；如真正同源提取必要，先提供小diff及保持现收集语义的测试证据再决定。R2依据实际图维护窄护栏，不凭未执行图声明allowlist已兼容。
- 不一轮改入口+collector+新SDK/依赖+全构建。每batch交可验证checkpoint及冻结，review后再推进。
- p1-test终态交接后父解除源码冻结，再由原p1-build单writer集中修复；禁止父实施下载/测试/产品代码。
- 依赖/metadata写入在独立授权prepare阶段：核官方来源/许可及旁证，生成hash不自动信任。任何实际锁/metadata增量需重新冻结审查。
- 修复→同模型fresh窄复审→真实graph-fast/fast/Lint及checksum/full串行证据→源码APK身份→安全限定本地提交；不关闭未满足M4，不设备/模型/远端CI。

## 环境/证据限制
目前真实Gradle/Lint/full未跑，Nix cache-only失败不是目标负例。用户已授权必要固定下载校验，但未升级/模型/设备。只补Nix不保证Lint依赖齐，现metadata只见lint-model/typedef-remover，准备方案需实核。
Full中同级dependsOn不能保证顺序，APK子报告不替代整体exit；子Gradle额外JVM峰值需留意约8GiB主机。device/SAF/IME/JNI runtime/4K16K/quality/远端CI均not run。
