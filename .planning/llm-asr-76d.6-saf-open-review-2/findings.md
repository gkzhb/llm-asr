# Findings — m5saf-review-2

已完整读取 AGENTS、原 final-review/handoff、父 review-r1-disposition、新 r1-fix handoff/report。旧 R1 是 open 前已有 model.bin PENDING 而误断言 map 空；其他旧范围已审 NO ADDITIONAL BLOCKING，不重复。

初始 HEAD add5d2c854ac8d2980b2bf13d92ad5bc373ad477，index SHA c8047347a7e587cfb2501f71d51db94a77537a782cf0da85d944f5a14c24600a，cached diff 空。已保存 64 个显式 scope/evidence 文件 SHA/bytes。

suite-r1.diff 只增加 ModelReports import、纠正注释、保留 failedFiles 空并精确断言 singleton model.bin/PENDING。已读实际生产 repository :382–384/:418/:441、controller :104/:196–202/:223–253 及 provider boundary，路径与修复一致；inspect map 相等、同 controller retry 新 op/READY/实际字节 SHA 和取消外部断言全部仍在。

原 findings 错误句保留，追加段明确纠错；新报告反复区分静态确定路径与 Android notrun。已读 build 完整 290 行/118 actions/1m23s/exit 记录、format 15 行/1 action/6s 及四 checker 普通/-O 日志。未重跑。

新六候选与旧 .work/m5saf-build/final-seal.json 的 11 seal 是不同历史阶段；suite+三 plan 获准变更，其余历史候选应保持。原20 excluded 只核 before/after 记录，不直接打开内容。

两次只读定位错误：误猜 scripts/android_test_inputs.py 不存在（实际 module_inputs 在 verify-android-test-apk.py）；误猜旧 seal.json（实际 final-seal.json）。均已按实际文件定位，不是产品检查失败、未改证据。

独立只读 parse/hash 最终通过：当前动态枚举 209 build/10 module/80 native = after/freeze/receipt（native另与before同）；R1 build delta 唯一 suite。实际三包所有 DEX/class_def/method、ZIP/AGP metadata、15 raw exit0/SHA/副本、5 tools SHA、新 receipt SHA/副本、7 JVM精确XML/零failure/error/skip吻合。14方法只静态。

893 before/after SHA/bytes/mode/mtime/inode记录精确相等；本leaf另外实核873已知保护文件当前内容metadata，其余20未知excluded未直接读取，仅记录对比。新六seal current；旧11seal中suite与三plan历史副本SHA匹配，其余7 current保持。旧报告/freeze/审查证据仍旧历史，不用旧test包冒新current。

独立审计脚本自身两处假设错误已修：AST namespace漏json；日志记录receipt目录而非receipt.json文件。最终通过，未改受审材料。ps两原PID无输出exit1是不存在，不是测试失败；未拉registry或重启任何链。

结论 R1 CLOSED / 本定点scope NO BLOCKING；只是源码路径和现有证据绑定复核，不是Android行为redgreen。
