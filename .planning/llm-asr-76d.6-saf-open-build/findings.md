# Findings

- 已接受scope仅无设备编译工程；现provider实际ContentProvider，必须ContentResolver.wrap，不注册DocumentsProvider或改manifest。
- P1-B旧full已提交add5d2c；12仪器/7JVM历史不重写。当前新增两方法只编译/DEX，行为not run。
- 初始tracked/index无差异；26 untracked含原20excluded与父/调查6plan文件。
- 本leafruntime/授权见task_plan。所有合成IO限定target cache UUID，禁止私人目录/权重。
- before快照：实际build collector209、native collector80、protected tracked804、原excluded20；三包已备份自有work。大cache未递归。
- 生产open错误是裸安全IOException，并非ModelFileException，因此失败历史failedFiles/fileResults应为空；不能断言虚构文件失败项。RequestRunner异常处理/finish可能转换终态，测试需外部精确FAILED/FILE_FAILURE与CANCELLED断言，不以owner释放或flag冒完成。
- 新SafWorker独占有界join与Throwable捕获；provider latches 5s，join6s/interrupt1s。活worker无法join时失败且不删活IO目录。
- DEX fixture实际14方法经同dex_inventory/check_packages通过；两新增逐名missing/duplicate与flags public/static/native/abstract、return、argc、code非法拒绝。普通/-O全部exit0，非SAF行为red。
- 当次统一build真实exit0/118actions118executed，7JVM XML全零、14方法实际DEX静态；format真实exit0/1action。当前动态collector209build/10module/80native全部与receipt/current一致，四delta精确覆盖；不是沿旧数量冒当前。
- 原804非允许tracked与20excluded内容metadata精确保持，产品/宿主APK字节不变，仪器新52110bytes/SHA46edf78e8510f0ffc4bc686fee9840459ef1f20a01a74733c780b374bf2a8a4c。原12方法体/provider query逐字保持。
- 调查三plan与父task/findings内容相同；父监督progress在父自身协调恢复中追加，本leaf未共写、不声称其metadata冻结不变。当前报告为新域三文本，旧P1-B/full原样。
- 新增SAF行为/runner发现执行/设备全部notrun；没有full/prepare/download/设备/提交。完成targeted后停写交父fresh同sol上下文隔离审，不冒跨家族。

## R1修复阶段：明确纠正旧结论（保留上文历史）
- fresh m5saf-review R1/P2及父disposition已接受。上文“failedFiles/fileResults应为空”中fileResults部分是错误推断，不可作为当前结论。
- 实际ModelRepository.java:382–384在open前发model.bin→PENDING；controller publishFileResult复制并保留，普通FILE_FAILURE裸IOException不进入ModelFileException分支，也不清map。故failedFiles仍空，fileResults精确单项PENDING；inspect必须保留该历史。静态确定路径，无Android行为red。
- 最小修仅suite ModelReports import、注释与两精确断言，保留inspect map相等/same controller retryREADY/实际字节SHA及取消路径。旧work根seal/freeze/receipt/log/handoff和三旧reports/review均保护不覆盖。
- 新证据ONLY .work/m5saf-build/r1-fix/，新增reports限r1-fix.md/r1-input-sha256.json。runtime同session、新pane wY:p23；无生产/工具/锁metadata/设备/full/提交范围扩大。
- R1修后checker四命令/build118actions/format1action均exit0；实际三包全DEX重新只读解析与新receipt一致，209build/10module80native与新freeze/current绑定，仅suite为本阶段build delta。893保护记录逐SHA/bytes/mode/mtime/inode不变，包括旧work根历史、旧三report/freshreview与原20excluded。
- 修后test52318bytes/SHA950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41；产品/host字节保持。编译证据不证明PENDING断言已在Android运行；该修复仍交父fresh定点复核，R1不由writer自行关闭。
