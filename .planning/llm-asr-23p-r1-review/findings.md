# Findings / llm-asr-23p R1 / ime-review-2

- 初审 R1 是静态发现：新 suite 的 finally cleanup 在 unknown/corrupt ledger 时覆盖正在传播的 BaseException；不是产品缺陷、不证明删活输入，也不归因旧 outer UNKNOWN。
- 父精确授权仅两 delta（driver/status），其余八冻结；新 candidate b67968a43d0fdf19c9c1bae9f14fd1becfd1a74ffba6de1ee16be39a67d4fac4，R1 handoff ab3a239653dec2a4af6c23fea3b1e15a850653334476303ebfa172c2c1ef9e03。
- 已完整阅读 actual suite、fault-harness、normal-suite、validation runner/log、共享 cleanup、修复三 plan、status 和复杂度处置；harness exec 实际旧/新 driver，只替换 prepare/owned.execute，用实际共享 cleanup；no-primary case 另在真实 cleanup 后抛 sentinel。外部 require 为显式 raise，不受 -O 去断言影响。
- R1 diff 仅 primary=None、except BaseException 保存同对象并裸 raise、finally cleanup 失败有 primary 则 add_note/无 primary 裸 raise。未动 quiescent/attempted/remove=False、mutation/预算或旧 default/compat。静态语义符合最小修复；实际独立核验已完成（evidence-consumption.json），R1 具备 CLOSED 依据。
- 合成 case 的 unknown/missing/corrupt ledger 明确为已完成 fake execute 的合成数据，不是未退出 compiler。每模式旧6个具体替换异常、新10case通过；confirmed恢复以及无primary cleanup sentinel身份由外部require检查。未将静态初审冒动态旧红。
- genuine validation43账本（7外层+36正常子命令）和另复杂度2账本均严格owned/root与closed流SHA核同；正常两模式各132输入SHA、恢复生产/当前caller绑定一致，四mutants为compile0/exact AssertionError1/restored0。
- 223 current_build同原库存，唯一build delta为driver；10module全同；修复manifest只列1变化supplemental(status)，旧docs supplemental由10候选中的冻结身份保留，不能误称本批supplemental原总数永久变成1。27旧产物哈希核同，只为保护不为当前APK验收。
- cccc10/7→14/10，knots14/11→20/16，nesting2/SLOC42→51；显著增长源自主异常/清理异常归属，已逐路径语义+故障证据审查接受，不以分数放宽安全检查；七源未漂移、21旧ambiguous组before/after完全相同，旧产品热点不重审。
- 读文件结构时两条可选探针路径并不存在（complexity/evidence/compare.json、complexity/current/execution.json）；无执行失败、未创建或改证据，已通过实际目录定位 commands/compare.stdout 和 evidence/execution.json。
