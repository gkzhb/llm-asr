# Kotlin 测试切片 P8：本次正式 full

任务 `llm-asr-ap5`，基点 `70ecd599a987b2abc68e8f4987446d7dd87e28ff`；父接受 fresh 同模型上下文隔离审查 NO BLOCKING（非跨家族）。本次只验证已冻结10类测试迁Kt及接线28候选，另加本文与 [输入/身份报告](full-inputs.json) 为30候选。**正式 full 已通过，但未安全本地提交，尚未授权交付 APK；不关闭 AP5/M4/M5。**

## 实际执行

唯一命令：

```sh
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

真实自动终态exit0，owned session退出/reap确认，耗时 1425.057s；Gradle181actions全部executed，Gradle0/tee0/DEXscan1。O_EXCL no-second-launch、实际PID/start/argv/输入身份、完整闭流日志在独占 `.work/alljava-full/`；没有poll或重跑成功full。工程在base上的precommit dirty冻结内容运行，不冒未来commit对象或全新缓存/hermetic。

## 分层结果

- **42个JVM JUnit实际运行**：core34/runtime1/testhost7，逐method/class/XML无fail/error/skip；两个Kt wrapper task实际编译，正式18个top/Companion class结构/完整methodcode/annotation与接受审查一致，45000timeout/publicctor/main6Throws2无Throws/ResultFiles open/nullableargs无earlycheck；八main原Java freshbaseline marker/check数出现在本次正式runner输出。
- host29main/8reviewmode/809 JavaABI与switch/SAM/default/null、原enum/ports/phase/batch/next5+compat4/runtime7/Logs3/Window7/model3及owned lifecycle/cleanup/跨JVMlock等全部从头执行。新三生产mutants普通/-O各9route，compile0→准确外部AssertionError1→原SHA恢复green0，closed owned确认，不用编译失败或targeted日志替代。
- 正式生产owner app8/runtime5/core50=63，47Kt16Java；真实module编译正负/重复owner/平台污染/全部DEX等gate保持，JUnit/fixture不入产品。
- 真javac-h原Java JNI声明→NDK include/object/link→实际DSO符号/descriptor/provenance/ELF16KiB通过；nativeSHA `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416` 不变。578MNN对象当次bytes/mode/inode/mtime全保持；本次native生成199项stat/inode变化逐条解释（CMake/JNI/headers/artifact等），唯一native生成内容变化为`.ninja_log`内容重写：前后均81984bytes/587行，非前缀追加；移去四条更旧link记录并新增四条当轮记录，前轮四条及其余内容保持。具体工具内部原因UNKNOWN，不推定必然轮换行为。不是套用前代580+2+1豁免。
- App/runtime **Lint Error/Fatal0**；当前warnings24/17与本次before逐id/message/path/line/column/重复multiset完全相同，新增/消失0。原warning类别沿已接受基线保留，未新defer/抑制；host Lint本入口未执行不冒通过。实际NewApi默认AGP0/1/0、工件/边/共享validator files拒绝恢复、真实checksum0/1/0和七JUnit fixture五阶段0/1/0/1/0均执行。
- 三包签名v3/原cert、Manifest权限组件/资产native/全部DEX/无重复泄漏通过；**14仪器方法仅编译/parsedDEX库存，不是runner发现或设备执行**。

## 本次重建的三APK身份（非交付声明）

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5282161 | `38b4e2fb51d72d33b15ab1f7e71dd3f00851f463de4bf48118f586d130f8f968` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2633544 | `8d592413584be860c3db6d8bfc47209e1d59b766f8f8fac78df205253a160ec0` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 52318 | `950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41` |

产品仍org.llmasr.minimal / 0.6-debug / versionCode6，原签名e9365bf6…c16c，无版本提高。三包与前代bytes相同不表示复用旧产物：本次精确清理已备份APK/DEX/class域后正式重建，并由两份新formalreceipt动态关联当前221build/10module/80native、当前三包/metadata与输入无漂移。receipt路径及hash见full-inputs.json。

## 保护与边界

当前2037generated真实原件83,897,840bytes备份，NOFOLLOW仅27已授class/JUnit/DEX/APK目录清理；无全build/native/cacheclean，不删锁inode，不读取/复制key内容。2277strictsource/旧plan/build/review证据保持，父监督必要协调文本仅supplemental单列；native578strict保持、全部生成变化有原bytes映射、unknown0。未自动修产品/测试/DSL/锁metadata或降低gate。

工具复杂度 UNKNOWN（cccc Kotlin/kts、knots kts零记录、跨Java/Kt量纲）及非阻塞热点沿fresh审结论，不刷旧baseline。设备/Androidrunner/mic/SAF/IME/Binder/JNI4K16K/private/model/远端CI/releaseR8均NOT RUN。维护Java46、生产16、历史7与必要Java编译fixture策略仍未全目标完成。

下一步：父消费full/receipt/30候选后，另授reviewer两阶段安全清单审和精确stage/cached审/普通本地提交；提交核验成功前不宣称用户可验证APK。
