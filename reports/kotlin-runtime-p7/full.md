# AP5 runtime4 — 唯一正式 full 结果（未提交，非交付）

Task `llm-asr-ap5`；base `01dd907c3e1bfb539119553334f897b241f73496`。四runtime owner迁移后 **47 Kotlin / 16 Java / 63 owner**，实际app8/runtime5/core50。R1/R2及新增IMEguard已由同模型上下文隔离点审CLOSED/NO BLOCKING并获父接受；不是跨家族独立，不表示Java0或设备验收。

## 正式命令与当前输入

一次原始 `bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks`，exit0、1320.851秒，**178 actions全部executed**。无probeinit/-x/targetedhost替代/锁metadata补绿/成功full重跑。完整closedlog 50454bytes/SHA `3f8d73fb91ba2bf100ee02dc0975393a8014890772193ab18b10a6f68a5fd6d1`。

测试的是base上的**precommit dirty冻结内容，不是未来commit对象**。原27候选保持，只新增本页和full-inputs.json成29候选；当前build220/module10/native80逐SHA before=after=current并同正式receipt绑定，数目是当次库存非永久门槛。固定Nix cache-only/Gradle strict offline不是freshcache/hermetic或机器外复现。

## 正式实际门槛

维护host从头原样执行：runtime七生产mutants/106checks（15mixedroutes/30compilercommands/15Java，compile0→精准外red1→freshrestore0）、nextdefault5+compat4nullable/真实JavaTWR、enum/ports/phase/batch/model/Logs3/Window7/PartRecovery/mixeddefaultcleanup锁及所有旧gate，未引用targeted冻结printf。

本次JUnit **42**，core34/runtime1/testhost7，全零failure/error/skipped。InferenceAdapterTest main383checks/同checker10negative在**runtime1**真实调用，不能归入core34。Android instrumentation APK内14方法仅静态库存，Androidrunner未执行。

正式currentroot8app/5runtime/50core与runtime四Kt实际class、Recorder唯一普通Java可达Companion.capture精确准入/outer9byte同实现委托无stateIO、Saf附属check/access/private12lambda、Contextprivateidentity2bb0均核；Ime实参原序R1files11→requestcheck39→requestId42，R2savedinference150→model179→cache213→latecheck230→invoke245。正式classroot新Java访问正负通过，不用probeclass冒验收，也不称全反射API等价。

真实Java JNI声明javac-h→NDK object→显式DSO符号/descriptor/ELF16KiB/provenance通过；578MNN对象SHA/inode/mtime保持，DSO SHA `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416` 与原值同。不是设备4K/16K运行验收。实际LintNewApi/身份classpath负例、strictchecksum坏hash精准拒绝/恢复、Androidfixture载荷/cleanup目标red恢复、所有包检查均按正式raw核。

## 实际三APK及两正式receipt（仅身份记录，不提供交付声明）

|包|bytes|SHA256|
|---|---:|---|
|product `android/app/build/outputs/apk/debug/app-debug.apk`|5282161|`38b4e2fb51d72d33b15ab1f7e71dd3f00851f463de4bf48118f586d130f8f968`|
|host `android/test-host/build/outputs/apk/debug/test-host-debug.apk`|2633544|`8d592413584be860c3db6d8bfc47209e1d59b766f8f8fac78df205253a160ec0`|
|androidTest `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk`|52318|`950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41`|

逐archive entries/全部DEX definitions/permissions/components/assets/metadata/cert/v3真实核。生产身份仍org.llmasr.minimal/0.6-debug/code6，原cert e9365bf6…，未授权加版本；新代码身份靠SHA而非相同version。两正式receipt：`android/app/build/verification/debug/run-f603ebdc5fe440ce9ad594f3f4c90ac2` 与 `build/verification/android-test/run-576887b8ae1d45a289750e66352f5d9e`。对应源码/模块/native输入和当前实际包逐SHA绑定。

## Lint与保护：明确非全命令零、非583stat全同

app24/runtime17全Warning、0Error/Fatal；最新beforeXML与current以重复multiset(id,severity,message,file,line)完全同。唯UsableSpace在AppGraph.kt89列78→80随已准receiver!!移动；父精确接受本次非阻塞披露，性能建议未修、不suppress、不泛继承旧defer。后安全阶段1仍须独立接受，不能说零warning。

原保护post真实失败保历史。只读v2经父精确处置：**580严格mode/bytes/SHA/inode/mtime同（含578MNN）＋2artifact同mode/bytes/SHA但JNI重编重链接inode/mtime变＋1 appbuild JNI scratch同字节inode但mtime变**。主build/jni-check另generated域不混583。1746份最新before真实原字节（940精确SHA复用）、22reviewmutable映射，7024strictbyte身份同/96expectedgenerated变化有backup，unknown0；父协调progress独立supplemental，多个库存重合不硬相加。没有恢复旧stat伪未变、没有unbackedloss或新native字节豁免。

旧sourceguard普通0/优化1的已审非阻塞限制仍notpass，全scope复杂度compare2UNKNOWN/CCCC-Kt/DSLzero盲区保；局部compare0为review-required不是自动安全通过。旧targetedhost前缀1+修后suffix0历史不回写；本次正式host从头全0另有本次证据。

## 未授权/未验边界

当前**未本地提交、未交付APK**；父消费后先阶段1只读候选安全审，再另授阶段2逐path普通stage/cached全审/本地commit，提交成功且父核后才可交验证入口。未push/amend/reset。设备Androidrunner、mic、SAFBinder/grants、系统IME、真实JNI4K/16K、私人文件、模型推理、远端CI/releaseR8均NOT RUN。AP5仍16Java非Java0，不连带关闭M4/M5。
