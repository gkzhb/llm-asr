# Findings

- HEAD 64d00d16e13577b9e6ad049511acd96b3e7d7d6b，main，tracked/index diff 均空。现有未跟踪历史 planning、两研究 doc 和 .beads.gate.lock excluded，不读其私文、不修改。
- 显式 msj show：现 owner p1-steward；blocks 仍约束 VAD/长音频/性能/API，不解锁。zcm/lsk closed，已交 proof/startup 快恢复、显式 prepare/release、单 resident、IME no-hide 返回，不重做。
- 本轮只给有界提案，全部测试/编译/模型/设备 not run。初次两次批量读取输出偏大，后续只过滤必要 Beads 字段与有限源码。
- 已定位唯一推荐：IME UI 把文件 proof READY 当作可录音。AsrImeService.startRecording/render 只看 readiness.ready，未看已有 residentSnapshot；startup/显式释放后 proof 可 READY 而 resident EMPTY，录音按钮仍亮且提示普通会话；真正 ImeBackend.requireModelReady→ModelAccess.requireResidentReady→ModelResidency.requireReady 会拒绝，用户点后才失败。只修 Service 展示/按钮/点击快照守卫，保 worker 权威准入，不重做驻留或返回逻辑。
- 现有 ImeFixture 仅 markVerified，resident 实际 EMPTY，注释称 synthetic prepared；新 gate 必须同步用既有 preparedAccess 的真实小文件 proof+resident 受控 native fixture 明确准备，不删除旧恢复/返回断言。
- 已形成 proposal/allowlist/verification：仅 Service 的 proof/resident 提示、录音按钮与 stale-action 快照 guard，精确六文件候选、三个 compiled mutant 目标及同 caller 旧源精确红；每模式7 compile/7 JVM，2400s 独立预算。保 own-busy 进度、preview 手动确认、no-hide 与 worker 最终准入。无需新权限/依赖/架构；产品写权待父另授。
- 只读检索有两次命令错误：不存在 scripts/host-test.sh 令 rg exit2；一条 Bash 正则引号不配对 exit2。后续正确定位 scripts/test-minimal-apk.sh，无测试或产品失败。

## Next Step
父 p1-steward 消费 .work/msj-next-plan/handoff.md 及提案，决定是否接受六文件窄 scope 并另授唯一产品 writer；本 leaf 停写，不自行实施。
