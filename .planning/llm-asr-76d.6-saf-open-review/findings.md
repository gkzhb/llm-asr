# Findings

## 恢复与首部
- 已读 AGENTS、临时 P1-B 交接、调查及 build handoff、调查/父监督/build 三文件和根历史（分文件补读）。旧 P1-B/add5d2c 已验收，不重做 full。
- HEAD add5d2c，index 空；tracked delta 5 文件（4 工程及追加 docs）；首部 .work/m5saf-review/start-snapshot.json 记录 32 个 scope 文本哈希及 Git/index，11 候选全部匹配 final-seal。
- 新 SAF 测试未在 Android runtime 运行；同进程 ContentProvider.wrap 不代表 DocumentsProvider/Binder/grant；cursor/PFD 计数不代表返回流 close 验证。

## 最终阻塞 R1（静态确定序列，未执行行为）
- FixtureInstrumentationTest 失败方法断言 failed.fileResults.isEmpty()，但真实 ModelRepository.importFrom 在 open 前对每个 manifest entry 调 progress.fileResult(PENDING)，controller.publishFileResult 装入 map。open SecurityException 被边界变为裸安全 IOException，terminal 非 ModelFileException 不清 map；所以失败 snapshot 必有 model.bin=PENDING。该断言未来实际 runner 必失败；failedFiles 为空合理，fileResults 为空不合理。精确位置 suite:195–196，repository:382–384→controller:104/196–202/223–253。只建议测试精确单项PENDING与注释更正，不改生产凑测试。
- 当前 checker delta 仅追加两方法/静态提示，无原门槛变动；目标负例实际调用共享 dex_inventory/check_packages，非仅方法字符串。

## 独立核对完成
- independent-checks.json：209build/10module/80native与当前/freeze/receipt一致；before209=Git base，四delta精确覆盖。11 seal一致，804保护tracked内容/metadata一致；20excluded只核既有before/after记录相等，未直接开原未知材料。
- 实际三包SHA/metadata/全DEX/Manifest policy与receipt一致；15tool记录exit0/raw SHA/副本一致；7JVM XML精确全零；build118executed/format1action磁盘exit0与原Gradle日志包含关系通过。
- 除R1外，取消阻塞与有界清理/外部断言未发现新阻塞；旧12方法/query字节保持。新SAF实际运行全部notrun。
- 完整final-review/handoff已写，结论BLOCKING R1，不启动full/提交；尾部范围核验后停写。
