# P1-B 2A 发现

## 2B 接线
- 父接受B1 closed，仅接线不改settings/module/fixture/lock。module live source/config collector共享给product receipt与format，包含新增未跟踪文件，排除build/.gradle/.cache；正式checker单variant metadata+当次SDK工具raw+全部class_def/class_data+7JUnit/12方法。
- 初轮targeted因status进格式库存暴露旧maintained_text_test重复mkdir兼容错误；父已追加唯一单行exist_ok=True授权，保留原失败日志和全部格式策略。不是目标red。
- tests/android_test_fixture_test.py仅将输出从历史b1-fix移至2b避免未来full污染旧证据，变异/断言/7case语义不改；本阶段不无理由重跑行为链，引用B1已审freeze。full-only任务以后执行一次新输出。
- 正式新入口不执行生产Lint，原fast精确35图及full旧依赖保持；新host仍受自动Lint identity gate，无绕过。最终full/生产Lint另验证leaf，不在本轮授权。

## B1 窄修
- fresh p1b-review定位旧request残留，父仅授权测试fixture窄修。修前ba0e89ea5 completed exit1、36actions全部executed；Java编译成功，精确7case中唯一reusedBackendQueuedCancellation报java.lang.AssertionError("same request cleanup")，XML SHA001363b9e17c40ee746d7a8ecae059bce2f7fdf9aee2cbb023516677ab88087f。FakePorts仍旧a2945734...830b时实测，不是工具red。
- 修复只在原同request校验/cleanup gate成功后加request=null，失败路径不清、owner断言不减；生产状态机不变。回归覆盖首次成功commit、排队取消cleanup无capture/transcribe/无迟到预览/owner释放及第三次成功。host7case，instrumentation新增共享wrapper后12方法，已实际新结果绑定：主树XML20c4a150...a29a；两APK重build81actions/静态检查exit0，新test40158bytes/37class/SHA770c1a72...2981，hostSHA856536f1...ee7d不变。behavior链bcd4b745a真实磁盘终态exit0，五阶段0/1/0/1/0 exact7case，两red各唯一目标AssertionError。无Android运行，仍待fresh回审。

- 依赖及父方案已完整读取。settings现仅app/core/runtime。app/src是core共享main/frozen inventory，测试必须独立目录。
- 真实现pin AGP8.6.1/Kotlin2.0.21/Gradle8.7、compile35/min29/target35。现签名PKCS12引用按app继承，无key读取/生成。
- SDK标准runner deprecated，仅测试工程useLibrary base/runner，JUnit4.13.2只testImplementation；无AndroidX新坐标。
- runtime主Manifest传递RECORD_AUDIO，宿主必须tools:node=remove；不改runtime。
- 本次Beads只读实核M5 in_progress/assignee p1-steward、M3closed。Beads显示lease expired（19min）但owner未变，显式父分派和唯一writer仍有效，不claim/心跳；提醒父处理。
- Git初态无tracked/index修改，原.be...gate锁/其他planning与docs未跟踪保留。leaf session 01a13f7f-d6a3-746d-bfba-a1f811faf8fe，Herdr wY:p1J。
- 旧root full/fast尚不接新模块，2A不运行完整旧链、不宣称接入。真实runner发现/设备SAF与InputConnection均not run。
- Herdr agent list实核仅父done/本leaf working，其他项目idle，无重叠writer；进程表无Gradle/Kotlin/Lint链。wrapper SHA实际匹配既有入口。
- 已将现195build/80native keys逐SHA及358份旧P1 evidence冻结在.work/p1b-build/before.json；只存hash，不读模型/音频/密钥内容。
- 生产接缝是ImeController Java + TaskCoordinator/RequestRunner/RecordingControl Kotlin，直接构造注入，无需AppGraph。SafModelSource provider call与cursor.close均固定安全IOException边界，取消检查在query返回后，因此本地阻塞query测试保持owner直到返回后取消。
- 一次读取不存在的ImeTranscriptionRunner.java/TaskCoordinator.java产生ENOENT，改按真实rg路径读取.kt；不是测试失败。
- 隔离真实prepare b427f8961 exit0；13 configs新host lock SHA d05a063c52689a10a5adc40fc0f64e5beec4f5e19c84fc3b6c87d5543dba976e与主树新锁逐bytes一致。old root/core/app/runtime lock与verification metadata前后SHA exact，详isolated-prepare-result.json。只4个现固定artifact：Kotlin2.0.21、annotations13.0、JUnit4.13.2、hamcrest1.3；JUnit只unitTest，androidTest runtime空（不复制target依赖）。
- 主树prepare中root锁意外删除/精确恢复事故不可抹去；Gradle deactivate后write-locks仍会持久化/删旧锁，因此后续prepare只能隔离副本并导出限定新锁。此为待交父长期知识候选，不写Beads。
- 实际2A工程结果：81actions executed；host exact6JUnit/0skip/error/fail，仪器编译与两APK成功。host2584412bytes/1235DEX定义、test37874bytes/34DEX定义，原v3证书；无权限/native/assets/测试实现泄漏。SDK runner/base required=true，deprecated与设备可用性未知。
- 行为driver真实5fresh阶段0/1/0/1/0，payload合法不同文本→enginePayloadAndOrder唯一AssertionError，cleanup提前完成标记→cleanupRetainsOwner唯一AssertionError；恢复green且主树FakePorts SHA a29457340e7b7a277e297e9ad53533df6a23f58e6e02d0e502b7e7d9841e830b始终原样。两个fixture行为mutant，不夸生产mutant或设备runner负例。
- 只做静态自审；2B正式checker/负例/collector/format/统一入口/full/fresh审查/提交尚未获准或运行。原35JUnit未重跑，本轮只针对新6case，不冒完整回归。
- 新长期知识候选由父决定持久化：共享src隔离（先前已有memory部分覆盖）；主树--write-locks停用旧locking仍会删旧锁，prepare应隔离导出。leaf只读Beads，未保存memory/改AGENTS。
