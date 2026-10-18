# 复测与函数匹配

## 可复现范围

[runner](../scripts/baseline.py) 的 `maintained-tracked-v1` 从根 `git ls-files -z` 取 android/core/native/scripts/tests/model-tools 与根构建源码；仅选代码后缀和 wrapper，排 vendor/build/generated。`sources.json` 列每个文件 group/SHA/工具支持；core/runtime 部分生产实现仍由 app/src 的 module owner 分配，按物理文件去重，不按模块重复计数。生产=app/src+native/apk；测试=tests、test-host 与 src/test/androidTest；其余工具组包含 P0 harness/model-tools/构建源码，不等于产品运行路径。

仅显式文件 argv；cccc1.8.3 `--no-config --no-cache --jobs 2`，knots1.18.0 `--format json --jobs 2`（其 help 无 config/cache 开关，本批未使用过滤/基线/阈值/recursive/compile-commands）。stderr 必须保存，非空要求人工解释。取工具实际解析函数，非编译器 AST 验证；声明、嵌套函数/闭包、构造器计数随前端而异。cccc 保留 children 的 ancestry；knots 默认不独立计 anonymous closures。跨工具同名 cognitive 也不作数值等价。

新输出含 raw、execution、manifest、normalized。先核文件 SHA 和新旧 inventory；脚本 collect 前后再核 manifest 防分析中漂移。复现旧基线须旧 source bytes 与工具身份；在当前源码上跑只叫 current，不覆盖旧报告。无函数的空文件/声明/枚举与真正漏检必须人工区分；knots 没有逐文件 parse-error 字段，exit0 仍可能漏语法，不能宣称完整解析。

## 差异处置

- 同版本/二进制、选项、库存/group/tools 才比较。路径/语言/组改变时 comparator 返回2：列新增/删除/移动、来源SHA与影响，人工核 Git rename/diff 后，在另一个新目录建立经审的可比子集或新范围基线；记录与旧基线的关系，不能直接称退化0。
- 唯一 `(path,name)` 允许行号漂移；行号仅定位，不作跨版本主键。即使匹配也核源码签名/类归属，防同名替换。重复名称/重载保留每个 line 记录，输出 `ambiguous-manual-map`，不汇总覆盖；人工用类/签名/代码diff对应。同一行多个 lambda 可能连 `(path,name,line)` 也相同，附 `occurrence` 仅作本快照定位，不能据此跨版本自动匹配。
- 新函数与重命名输出 added-or-renamed；删除输出 removed-or-renamed。拆分/合并列一对多/多对一映射，分别检查新单函数最大值、路径/异常/owner变化，不能只比较总和；删除须确认实际路径移除而非工具漏记。参数/类名变化同样人工确认。
- 零记录在 coverage 中显式为 unknown；从有到无先查解析/移动/工具支持，不算降低。cccc parse_errors 为 partial-parse：受影响函数仅参考，需源码/另工具/必要编译证据。两工具都无记录仍不证明没有风险。
- 回归时以 raw 重新 normalize，校验 stdout/stderr SHA、argv 对库存；normalized 是便于索引的派生视图，不是独立原始证据。comparator 只列差异与 coverage，不实现阈值 gate，也不保证语义函数映射。

## 输出与失败

[fixtures 回归](../scripts/test_baseline.py) 用内联合成 JSON 覆盖增长、行移、重名、拆分/改名、范围/工具变化、raw漂移/非零、零记录、部分parse、schema/path/metric/同位置碰撞保留、无输出与坏JSON。脚本失败返回2并写 `FAIL-UNKNOWN`；保留输出后停该结论，修复/核版本和范围后用全新目录重跑。没有自动 baseline refresh、CI 接入或产品测试调用。
