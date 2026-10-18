# Findings — llm-asr-jrq / complexity-build

- 准入成功：embedded llm_asr；llm-asr-jrq in_progress/complexity-steward，无显示blocks；父为唯一Beads writer。Herdr仅父idle、本leafworking在本项目。
- cccc为Rust多语言CLI，不是老同名C/C++ Counter；版本1.8.3，支持no-config/no-cache/jobs；knots1.18.0支持JSON/jobs，help无配置/缓存开关，不可虚构参数。
- 小样本10命令全exit0/stderr空：cccc对RequestRunner.kt/core/build.gradle.kts静默files[]（不支持）；knots正确报告RequestRunner.kt，core/build.gradle.kts输出[]（DSL无命名函数覆盖）。shell/Nix/CMake无所列前端。cccc JNI有3条parse_errors却exit0；保留部分覆盖，不冒解析完整。
- 额外空/坏Java真实CLI探针：cccc空文件functions[]，坏语法返回部分broken函数+parse_error仍exit0；knots空文件[]，坏语法仍broken函数且无诊断/exit0。工具成功不证明语法有效。
- cccc存在file aggregate与summary function sum不等（apk_report_binding.py样本3 vs2），基线单独保存raw file值与展开function值，不混解释总量。knots AIRD/AICP为该工具启发式，其--explain宣传验证对象不转述为本项目有效保证。
- 原75未跟踪文件含父三plan，剔除该目录恰72原路径。protection-before保存SHA/lstat；主index未动。
- .work/p2enum-full/final-input-freeze.json含current build/module/native/source_owners、3APK和2receipt精确SHA；实际212/10/80/63全核一致，最终receipt入口.work/p2enum-ship/commit-final/handoff.md已读。仅只读核，不重跑产品测试。
- b792ba772正式exit0：209库存production64/test76/tooling69，cccc170文件1771记录(681/968/122)，knots188文件1347记录(631/601/115)。两stderr空。cccc11零记录Python/knots21零记录（11Python+6KTS+4KT）均显式unknown；11Python用stdlib AST证实名函数0但顶层执行仍有分支。4KT源码确认为3enum+1字段类，无命名函数。未运行这些产品脚本。
- 生产terminal216 cccc93/33 vs knots37/33，importFrom342=53/35 vs55/41；不能合并cognitive口径。生产file原值Repository178/184、Controller164/125。测试/工具热点与契约阅读顺序已写README。
- self-compare exit0，仅171 ambiguous-manual-map，0自动metric-change，符合保留同名/重载而非覆盖。13组fixture含bad/empty JSON、stderr、命令/范围、碰撞、漂移/非零等分支，普通/-O均绿；Pi真实加载notrun。
- 独立重复bb86b9a14 exit0，manifest、双raw/空stderr、execution、normalized七项逐bytes同，compare0仅171人工重名映射，无自动指标差异。当前既有freeze与3APK/2receipt逐SHA保持，不产生新APK；所有候选均普通check-ignore准入，无forceadd，shadowindex check0且主index未动。
