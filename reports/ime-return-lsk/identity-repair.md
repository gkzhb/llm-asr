# llm-asr-lsk：已接受表示的 baseline 传播修复

本阶段为另授窄 metadata 修复，不更改 native patch/C++/source-lock/tree/算法/DSO。父 lsk-parent 唯一 Beads writer、原 lsk-build 唯一 writer；main基线 d5b38ac，未提交。正式 full 首次失败仍为真实 exit1，不重标通过；原 [full失败报告](full.md) 与 [失败输入](full-inputs.json)保持字节。

## 来源证明而非盲采 SHA

历史原 cleanup patch 10115B/SHA `059521069394f08ac0a89bdd2d82aa605dd39fc537a0cc6c1b5de1d0450c8392`；当前已接受 patch 10107B/SHA `466ab7d52007e4d26b9791f60f13087fdde12793edcf5641694e2f8c1ac51f62`。236行仅14/121/135/176/194/218/228/234八context空行 `20 0a → 0a`，其余字节、hunk标头及C++增删行完全相同。保留的历史 A/B 实际应用输出六文件逐SHA一致且对应旧/新source-report；本调查没有重新apply或执行native代码。

source-lock与合法prepared报告已锁新466a，tree不变；full3→表示采纳→HEAD的baseline文件却保持旧期望0595。产品checker的live输入重绑不执行prepareInputs全部frozen文件校验，因此既有APK补验未发现漏同步。只读78baseline文件审计仅此一处不符。父独立接受上述证据后，明确授权同步baseline cleanup单字段，保留本轮IME Service pin；不改patch/source-lock/native期望或gate。

## 窄回归合同及当前阶段

现有 `tests/gradle_inputs_test.py` 增加baseline/source-lock/实际patch一致性断言，以及真实生产 `module.stage` 对cleanup patch一字节漂移/缺失/意外重复的精确拒绝与无输出。digest/inventory门槛不mock，仅保留既有model-config subprocess mock；原正常stage正例及全部旧测试不削弱。增强caller先冻结，旧baseline必须在命名consistency断言红，再同caller修复后普通/-O全测试绿。使用明确校验的现有 native4ee…产物和既有公开sample fixture，无模型或私人音频访问。

增强caller SHA256 `ac9a987c7fec5f70d24d76191ca4fd2d80f4d788505e323fe1af177b2f76915d` 已在baseline修改前冻结；旧目标实际exit1且精确命名consistency断言0595≠466a。单字段修复后同caller普通/-O完整15tests均0（0.422/0.428秒），包括全部原13tests/正常staging与新三故障拒绝。原有测试/setup AST逐项保持；digest/inventory未mock或放宽。78baseline文件当前全部match，四目录库存后置封口。测试单调用180秒，原IME/host/JNI/full预算不变；同一测试单文件baseline selftest/两collect/compare均真实0，cccc1.8.3/knots1.18.0版本/二进制/参数/范围一致、stderr空，函数记录14→16；仅新增两测试，无旧函数指标变化/重复名映射。新consistency C0/Y1与C0/M1，新三故障测试 C9/Y6与C7/M6/nesting2，人工核有限循环/外部拒绝断言/finally恢复，非生产复杂度增加。最终保护/退出/输入证据在 ignored identity-repair/handoff封口。不独立重跑成功IME链，不full/Lint/APK/nativeprepare或提交。

这些证明源身份/host gate，不证明native行为、ASR质量或Android设备。原IME fresh NO BLOCKING及两LOW gap defer保持。后续仍需父消费targeted，fresh delta审后另授必要正式full，安全两阶段本地提交后才能交APK。当前无新APK/receipt/commit；设备/runner/Binder/系统IME/mic/private/model/PSS均NOT RUN。
