# Kotlin 推理 phase/ports：当批唯一正式 full

任务 `llm-asr-ap5`，owner `p1-steward`，唯一验证 leaf `ap5phase-plan`。fresh `newapi/gpt-6.1-sol/high`、fork:false **同模型上下文隔离**审查 NO BLOCKING 已由父接受，非跨家族；无必要源修复。审查receipt SHA `d852a7041f0a919b166f477ad1306ba4d87f8a254c0c22b17f90e3d1c70e6d14`。原[status](status.md)保 targeted 冻结历史，不回写旧文案。

## 唯一实际命令与来源

```sh
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

只执行一次，真实exit **0**，637.587秒，**174 actionable tasks全部executed**，pipeline Gradle0/tee0/DEXscan1。闭合日志46415bytes，SHA256 `5eea0d44812a9cd89827d188e88e072494a7deef572ccc79556605e4aa60cf64`。固定外层Nixcacheonly/Gradle strict/offline及原资源限制，无下载prepare/锁metadata写入；离线缓存成功不是全新机器/hermetic。

HEAD/base `809c4a986651592bb7e9d855a78880828178582b`，在已审 **precommit dirty freeze**上执行，未来commit不是重跑该commit对象。实际动态build 214/module 10/native 80，前后/current/独立产品和三包formalreceipts精确绑定；逐path/mode/bytes/SHA、tool entry lstat与resolvedtarget分别见[full-inputs.json](full-inputs.json)。本批17Kt46Java/63topowner，非Java0不关闭ap5。

## 本full实际验证

- 正式host真实跑725 Java ABI/行为checks；新phase显式code1→0与NONE MIN_VALUE抛错生产副本各freshcompile0→精确外部AssertionError1→原SHA恢复freshgreen0，defaultenum/ports/cleanup13及Store/Worker/Adapter/模型owner/privacy/picker全部原gate保留。Native纯abstract不虚构第三行为body。
- 原公开ABI子集与新编译器附属分列：当前真实core class javap核JVM52、publicfinalintcode、NONE原interfacepublicstaticfinalfield、onPhase(I)V、Native IOException/单SAM/六Nullable；不宣称class字节或全API成员相同，也不冒包checker检查字段/注解。
- 当前产品全部DEX实际class_def另核三phase topowner和Listener$Companion；不是strings/type引用。原包/模块checker核唯一productionowner与隔离无泄漏。全生产Java/Kotlin混编、真实moduleclasspath正/负例、JNI生成头/NDKarm64object/linked符号、nativeprovenance/ELF静态16KiB均实跑，不冒native/device运行。
- 动态JUnit：core34、runtime1、test-host7，全零failure/error/skipped。14仪器publicconcrete()V仅编译和DEX库存，非runner发现执行。
- 生产app/runtime Lint Error/Fatal0；实际警告：android/app {'Warning': 17}；android/asr-runtime {'Warning': 10}。既有分类defer，不新增baseline/宽Suppress/关闭error；hostLint未实际执行，identityresolve不冒执行。
- full内唯一fixture五阶段0/1/0/1/0及精确payload/cleanup AssertionError；实际AGPLint0→唯一NewApi(API33/min29/精确fileline)1→原bytes0，工件/边及共享validatorfiles缺/多/替换目标拒绝恢复；真实checksum原0→唯一AGPjar expected全零/actualcacheSHA目标1→恢复0。不用工具/cache错误冒red，不另重跑成功链。

## 当批三APK（未提交，不宣称交付）

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5220413 | `69e775be05ed69604100a3a14833b3fde125f73d9aa4f41aa6b4f67254640720` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2588492 | `c9afa9021bd23f2349eb2b5ec60e7e55ee56a774f8120625b80efb92e6dbbd43` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 52318 | `950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41` |

独立产品receipt `android/app/build/verification/debug/run-7f320e222bcd491ab858cff1e7f4cac9/result.json`，SHA `2a1e834e3839458856488a0e109e7a3924224e8373edc421b4c4733e9ef200e6`；三包receipt `build/verification/android-test/run-f405eb84dd4e4a2b8dbc8ecdf62a6a23/receipt.json`，SHA `b782484f3281c27a365f19aafd50269751d52ecd830b1fb4e079355274693de9`。产品保持0.6-debug/code6/min29/target35/arm64-v8a/唯一RECORD_AUDIO/原证书v3；签名/Manifest/assets/native/ZIP/全DEX与AGPmetadata/当前来源交叉核。

## 保护与边界

- 前置1266生成/旧APK/formalreceipt文件逐字备份，仅清13列明生成目录；9404旧源码/targeted/review/历史/随机receipts记录保持，29事前允许generatedoverwrite逐项记录，unknown0。不清全build/cache/nativeobjects/key/model/audio，原11候选/status与旧freeze全同；父协调plan后续文字独立supplemental。
- 578 MNN对象SHA/inode/mtime全保持，DSO `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`保持；只JNI复编和增量final link，非clean MNN/hermetic。三Java→Kt相对base的native来源路径变化如实保留，不声称base所有nativeinputs同。
- 尚未stage/commit/push；当前共13候选待父消费full后两阶段安全清单审与另授精确本地commit。提交成功前不宣称可验证APK交付，不关闭ap5/M4/M5/其他任务。
- Androidrunner发现/执行、设备ADB安装/模拟器、mic/private、真实SAF/Binder/grant/IME/JNI与4K/16K运行、模型计算/下载prepare、远端CI/release/R8全部 **not run**。host/JVM/DEX/签名静态与ELF对齐不等设备验收；ccccKt/Phase零记录/initializer及phase未独立fault注入等已审unknown/notrun不因full而改为通过。

ignored `.work/ap5phase-full/`保存闭合full/post日志、actualgates/负例/三包/receipts/ABI与保护delta及13候选manifest，仅本页和full-inputs.json作为规范非敏感摘要，不发布海量raw/编译产物/缓存/密钥。
