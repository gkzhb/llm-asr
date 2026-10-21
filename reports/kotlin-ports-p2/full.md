# Kotlin 日志 ports：当批正式 full

任务 `llm-asr-ap5`；owner `p1-steward`；唯一验证 leaf `ap5ports-plan`。fresh `newapi/gpt-6.1-sol` fork:false 上下文隔离审查 **NO BLOCKING** 已由父接受，非跨家族独立；未需源修复。原[status](status.md)保留 targeted 历史，不回写旧freeze。

## 实际命令与输入

```sh
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

本轮唯一执行一次，exit **0**，601.012秒；**174 actionable tasks全部executed**，pipeline Gradle0/tee0/DEXscan1。闭合日志46068bytes，SHA256 `d6c4dba57e5b850f0b6a6f5ff8bdab19439037f93482e7d89ca77bcdc3200b62`。外层Nix禁substituters/builders/宿主构建、Gradle strict/offline；缓存通过非新机器/hermetic。

基点/当前HEAD `9a2a0239febc936e769820a098e8d588ecfaba5b`，在已审**precommit dirty freeze**上执行；未来提交内容绑定不冒重跑新commit对象。动态库存build 214 / module 10 / native 80，前后/current/独立产品与三包正式receipt精确相同；路径/mode/bytes/SHA、entry symlink与resolved tool目标及产物身份见[full-inputs.json](full-inputs.json)。本摘要两文件属于supplemental，不改受审10候选与build输入。

## 实际验证层级

- 正式host真实接线：400 Java ABI/日志persistence checks，新Clock default no-op和None null-reject生产副本各compile0→精确AssertionError1→原源SHA恢复freshgreen0；原双enum同闭环，cleanup13case，以及Store/Worker/Inference/模型/owner/privacy/picker全部原门槛保留。
- 全生产14Kt+49Java混编，Gradle core/runtime/app真实owner唯一50/5/8，无重复/平台边界负例通过。JNI header、NDK arm64 object、实际linked symbols与ELF静态16KiB检查通过，不冒设备JNI运行。
- 当前JUnit按源库存核core 34、runtime 1、test-host 7，全部零failure/error/skipped。14仪器public concrete ()V方法仅编译及parsed DEX库存，不代表runner发现执行。
- app/runtime生产Lint Error/Fatal均0；警告实际：android/app {'Warning': 17}；android/asr-runtime {'Warning': 10}。沿既有分类defer，不新增baseline/关闭error/宽Suppress；test-host Lint未实际执行，不以identity gate resolve冒执行。8个Kotlin non-final method兼容Suppress属于已审源码局部契约，与Lint警告不同。
- full内唯一隔离fixture五阶段0/1/0/1/0、精确payload/cleanup AssertionError；实际默认AGP Lint green0→唯一NewApi(API33/min29) red1→原源green1；工件/边/共享validator files缺多替换均目标拒绝恢复；实际checksum原0→唯一AGP jar expected全零/actual cache SHA目标1→恢复0。未用工具/cache失败冒红，未单独重跑五阶段。
- 正式产品与测试三APK：ZIP/全部DEX class_def/无重复生产owner、组件权限/资产/native/原证书v3/metadata签名与两个当前formal receipts一致；见下表。产品保持0.6-debug/code6/min29/target35/arm64/仅RECORD_AUDIO。

## 当前产物（未提交，不宣称交付）

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5219177 | `9a66ee95d71f1e5917fd551ecafc32476215d9c4f0a31677d5c21368c23ccc5b` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2587256 | `44ae838f5dd0f2b7c8d08f465e9882bd8180e8266e9be94c3205bec98e705888` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 52318 | `950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41` |

独立产品receipt：`android/app/build/verification/debug/run-aa46771cd41748599fbbcbd36a2a87e1/result.json` SHA `20d80cedd3f66aec56cb71ab5df31dc6355ee0aa9b43cc1aec7f24793c45e09f`；三包receipt：`build/verification/android-test/run-60b0ac4e8b9843ed83f1039732e887f0/receipt.json` SHA `ecc5275532de3f12d4ed83365e2bd2f66a956bd72abd9d2587e89ff72866b4b4`。这些对应本批current freeze，旧包receipt仅历史保护，不冒当前。

## 保护与未测边界

- 前置逐字节备份1233已知generated/旧包/receipt记录，仅清13列明生成目录；6719旧源码/targeted/review/历史/随机receipt记录保持，33事前允许生成overwrite逐项记录，未清全build/cache/native对象。
- 578 MNN对象SHA/inode/mtime全部保持，native DSO `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`保持；JNI复编与增量final link，不冒clean native rebuild。两ports Java→Kt rename仅使native输入路径相对base变化，native代码/数学不改，不称所有native输入与base同。
- 未stage/commit/push；两阶段安全候选审及父另授精确本地提交仍待完成，提交成功前不向用户宣称已交付可验证APK。ap5生产Java非0不关闭，也不关闭M4/M5/其他任务。
- Android runner发现/执行、设备/ADB/模拟器、麦克风/私人文件、真实SAF/Binder/grant/IME/JNI及4KiB/16KiB运行、模型运算/新下载prepare、远端CI、release/R8全部 **not run**。主机/DEX/签名静态通过不等设备验收。

ignored `.work/ap5ports-full/`保存闭合full/post日志、动态清单、各真实退出码/负例诊断、三包/两receipt交叉核、保护delta与最终12候选manifest；仅本页与full-inputs.json作为简洁非敏感公开摘要，不发表raw巨量日志/产物/key/cache。
