# M5 SAF open 只读审查

## Binding
- task llm-asr-76d.6；leaf m5saf-review；父 p1-steward 为唯一 Beads writer。
- main/base add5d2c854ac8d2980b2bf13d92ad5bc373ad477；fresh standalone 同 newapi/gpt-6.1-sol 上下文隔离审，非跨家族独立。
- 唯一可写：本目录三文件及 .work/m5saf-review/；不二级委派、Beads 写、stage/commit、修改产品或旧证据。

## Goal
只读核四工程 delta 的 SAF 调用、失败/重试/阻塞取消与断言有效性、checker 14/7 库存与真实负例，独立绑定当次输入/receipt/三包和保护域；交付分级发现与 notrun 边界。禁止执行测试/编译/Gradle/full/下载/设备/模型运算。发现未知范围漂移或 owner 冲突即停报证据。

## Current Phase
Phase 2 complete：只读审查和独立身份核对完成，BLOCKING R1；报告交父，本leaf停写。

## Next Step
父消费 .work/m5saf-review/handoff.md 并另授权唯一writer集中修复R1；本leaf不自行修复/full/提交。

### Phase 0: 恢复与冻结范围
- [x] 读授权资料、三执行文件与父监督；保存首部快照。
- **Status:** complete

### Phase 1: Spec / 标准源码审查
- [x] 四工程 delta、生产 API、旧行为保持与断言/线程清理。
- [x] checker 原门槛/14 方法/7 JVM 与目标负例实际连接。
- **Status:** complete

### Phase 2: 证据及身份审计
- [x] seal/209build/10module/80native/保护域、receipt/raw/XML/log/三包当前身份。
- [x] 尾部范围/Git/index不变核对，完成 final-review/handoff 后停写。
- **Status:** complete

## Errors / Constraints
- 初次合并根历史及多个 plan 输出超过 50KB；后续用 read 分文件补全，不视截断输出为已读。
- 不读取 cache/密钥/private/session 正文；原 20 excluded 不直接打开。只核既有记录及许可保护清单的摘要。
- 本轮没有 full/提交/Android 行为运行；不得将静态和编译判作行为 red-green 或 M5 完成。
- 独立只读核对脚本初稿三处审计假设错误（xmltree raw 名实际 manifest、zipalign 非 verbose 可空输出、原 Gradle log 是 wrapper capture 子串而非整份相等），分别校正后 identity 核对通过；不是产品测试失败或重跑构建。
