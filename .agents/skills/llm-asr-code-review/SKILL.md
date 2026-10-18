---
name: llm-asr-code-review
description: 审查本项目源代码或检查复杂度回归时使用；以 cccc/knots 同版本同范围基线定位单函数热点与相对退化，关联行为测试和人工处置。
---

# llm-asr 源码审查基准

## 1. 固定审查对象

先按 [delivery](../llm-asr-delivery/SKILL.md) 恢复授权、owner、Git/base/diff、唯一 writer 和证据边界；该页是交付流程 SSOT。本 skill 只提供源码/复杂度审查方法，不授权 CI gate、调门槛、大重构、下载或设备访问。

读 [基线说明](../../../reports/complexity-baseline/README.md)，核本批源码范围和语言盲区。逐项列出产品行为、安全契约与测试目标，再看分数；复杂度不是缺陷、性能或安全证明，低分不免审。**完成：审查 base、当前候选、范围变化、工具身份及未测边界均明确。**

## 2. 同口径复测

从仓库根执行（已安装双工具；Python 标准库，不执行源码）：

```sh
python3 -B .agents/skills/llm-asr-code-review/scripts/test_baseline.py
python3 -B .agents/skills/llm-asr-code-review/scripts/baseline.py collect --out .work/review-complexity-current
python3 -B .agents/skills/llm-asr-code-review/scripts/baseline.py compare reports/complexity-baseline .work/review-complexity-current > .work/review-complexity-delta.json
```

输出目录须全新；长命令用宿主后台终态通知，保留 exit/log。脚本选 **Git tracked** 维护源码；待审新文件尚未 tracked 时另列其精确路径/SHA，在授权临时副本或审核后的库存中分析，不为工具擅动主 index，也不能漏掉新代码后称全覆盖。详见 [复测与匹配规则](references/comparison.md)。

比较两端 raw SHA、精确 argv/版本/二进制、源码 manifest 和覆盖。`FAIL-UNKNOWN`/非零退出/无 stdout/JSON 错误/范围或工具漂移不是 pass；先解释并补齐。`[]`、无函数和解析告警均须逐文件人工核查，不能填 0。cccc 对 Kotlin 无覆盖；knots 的 `.kts` 零记录不验证 Gradle DSL。**完成：所有变更文件有同口径证据或明确 unknown；不通过更新旧基线消警。**

## 3. 单函数信号 → 行为风险

以下仅本项目 review 信号（保守定位，不是行业定理/自动拒绝条件）：

| 信号 | 人工审查动作 |
|---|---|
| 任一工具 cognitive ≥15；cccc cyclomatic 或 knots mccabe ≥10 | 新函数/越线函数优先读；历史未改热点不要求本轮重写 |
| 同一匹配函数 cognitive/分支指标增长 ≥3 且 ≥20%，或从0增长≥3 | 解释新增路径、嵌套与异常；不得用全仓平均抵消 |
| knots nesting ≥4 或 SLOC ≥80；AIRD ≥85 / AICP ≥80 | 增加载入上下文/协作者审查；AIRD/AICP仅该工具启发式，非模型能力保证 |
| 解析部分缺失、记录骤减、函数拆分/重命名或范围变化 | 先人工映射和查漏，再讨论是否改善 |

两工具数值分别报告，不互换或合成评分。优先读**变更命中的生产热点**及其调用者/边界，其次测试和构建工具热点；测试主函数的大量断言与生产事务不能混排定结论。

按改动契约选择：owner admission/release 与真实 IO 收尾；cancel/commit/publication 原子仲裁、epoch/迟到回调；SAF/provider 路径/异常脱敏、日志与转写隐私；JNI ABI/待处理异常/RAII 与真实 native 编译。要求针对生产路径的可失败测试，回调外断言、有界并发收尾；host/fake、编译/包静态与设备证据按 delivery 分层。**完成：每个新超阈/显著增长项有人工解释、测试证据、必要最小处置或明确获准 deferred；降分不能替代正确性。**

## 4. 只读报告与处置

先报告给父协调，不边审边改；接受项由原 writer 最小修复后定点 fresh 回审，提交走 delivery。基线更新是另行解释范围/版本/映射的审查结果，不是清除告警的操作。

```text
审查 base/current candidate；范围/工具/源码身份；pass/fail/unknown/not run：
位置 path:function:line；生产/测试/工具；工具名与指标 before→after：
新增/增长/重命名/拆分/删除/零记录及人工映射依据：
契约与具体风险；证据（源码、真实测试命令/exit、覆盖边界）：
严重性；建议最小处置/获准defer；fresh复核结论：
```

**完成：每项发现可定位、有依据且不夸大证据；无阻塞也列覆盖 unknown 与未测项。**
