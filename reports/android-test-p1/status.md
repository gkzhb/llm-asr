# P1-B / M5 本地完整工程验证通过（未提交/未交付）

Task `llm-asr-76d.6`；Beads owner/唯一writer `p1-steward`，实施leaf `p1b-build`。base `a73f7f339a6d3a5e4f6a7faace7cdd262aade3c4`，独占计划 `.planning/llm-asr-76d.6-build/`，原始证据与交接 `.work/p1b-build/`。

原2A实现范围（历史，当前完整验证结果见下）：独立 `:test-host` application，仅依赖core/runtime，不改生产AppGraph/IME Service。host `org.llmasr.minimal.testhost`、test包 `.test`，versionCode1/versionName `p1b-testhost`；SDK标准deprecated `android.test.InstrumentationTestRunner` / JUnit3，host复用既有JUnit4.13.2及hamcrest1.3，无新第三方版本。

## 当前完整验证状态

2B fresh上下文隔离审查NO BLOCKING已由父接受，B1 CLOSED不重开；非跨家族独立审查。唯一完整cache-only strict offline full实际exit0，494.275s、174actionable/174executed；core34/runtime1原35与host7精确XML全零，production app/runtime Lint Error/Fatal0（warnings分类defer），host Lint未运行。fixture行为一次7case五阶段0/1/0/1/0，隔离Lint真实NewApi及工件/边/同validator文件拒绝恢复，checksum唯一AGP目标HTML/cacheSHA拒绝恢复均通过。三AGP metadata/当前三包正式及原产品独立receipt均绑定当前209build/80native；578MNN对象SHA保持，仅JNI复编/增量链接，不冒全clean。

当前完整证据见[full.md](full.md)、[输入](full-inputs.json)、[精确JUnit](full-junit.json)、[执行与gate摘要](full-evidence.json)及`.work/p1b-full/handoff.md`、`final-input-freeze.json`、`final-audit.json`。未stage/commit/Beads写；未提交不交付APK/M5不关闭。设备/runner发现执行/sentinel/provider/SAF/Binder/grant/InputConnection/真实IME/JNI-device/4K16K均not run，12方法compiled-only。

## 历史 2B 接线检查点（其后fresh接受且最终full已完成）

父已接受fresh定点回审B1 CLOSED/2A工程检查点，非Android或M5完成。本轮唯一writer接正式三包checker与负例、统一`android-test-build`入口、product/format live输入库存及N1安全文档。settings/module/fixture/config/lock保持B1冻结，不重开已闭B1。

- 正式checker实际读取三AGP单variant APK及当前SDK工具raw，扫描全部DEX class_def与12个public concrete `()V`方法、核原v3证书/测试无权限native-assets/组件runner-target-library-backup及7host JUnit，并绑定全部新module/live build库存SHA；不是Android运行。
- 新parsed-policy/合成DEX与temp输入库存/entry/format负例普通/-O已exit0，41维护文本通过。两次前置兼容错误均保留：旧format fixture因新增status目录重复mkdir（父授唯一单行exist_ok修正）、format loader缺scripts import路径（仅修loader），不计为目标red。
- 真实graph-fast精确35任务保持；新层dryrun155任务含目标三包/JVM/checker，无install/connected/managed-device/ADB/Lint执行链。fast实际exit0/23actions全执行，core34/runtime1=35JUnit全零；新层修正合法AGP宿主R类误拒后实际exit0/**118actions全部执行**、新host7JUnit全零，正式三包checker当前receipt绑定**209build/10module**输入。最终full/生产Lint明确pending另验证leaf。
- 当次三包实际产物：product 5216237bytes/SHA `3fe42d623e8f9b60e7d34fd63f432de2aa780520395fc8aeeb5bbbc1ca32df10`；host2584412bytes/SHA `856536f13c8fb7a975663bb27f35f8ce6ca586f2ffee36907d3aca757f58ee7d`；test40158bytes/SHA `770c1a7287730557afcf36aa4308d78f7b36744c102475285bbdd508b9442981`，均本次SDK工具原证书/v3核验。全DEX定义1327/1235/37，test精确12方法；不是runner运行。
- 新层首轮checker误拒合法宿主AGP生成R资源类，实际编译/7JUnit已过；仅补自身R/R$允许并加夹具仍拒负例，targeted普通/-O及新层重新exit0，原失败保留不当安全red。JNI object/实际符号与产品原checker最终receipt验证均完成；host Lint identity仅resolve核6consumer各66工件/171边，未执行Lint consumer。临时调用产品checker相对APK路径使首次最终receipt失败，后仅绝对路径补receipt exit0；不当目标red，不重跑已过resolve/JNI/build。
- 正式docs废弃旧主树deactivate+write-locks路径，仅未来另授隔离temp prepare/限定新锁导出与旧锁metadata前后SHA；本轮不prepare/下载/新依赖。
- 原35JUnit XML core SHA `519ffac1fc83be5dc7b117a311d5fccd9d580c222f6b31325b9e44ffd1bc73b4`、runtime SHA `426c8161118b81715e6b4ce56e4872890b49431d68f21f93054e492a3551233e`；新7host XML SHA `1cbb1f7f62d18296651160174f4d6f4a479109e8d6989508bea871623985df19`，均0failure/error/skip。产品原checker与三包receipt各209输入一致；native仍`d5bacffc…a416`/80native保护域不变。旧195域本轮6接线路径变化、14新增collector输入（10module+正式checker+三新测试/driver中既有driver此前未入195），完整delta见2B freeze。
- 新2B证据独占`.work/p1b-build/2b/`，旧2A/B1/review151文件保护。行为driver只换新输出路径，未改已审7case变异语义，本轮不重复B1链，full-only一次任务后续执行。

## 已接受 B1 窄修工程结果（非设备验收）

fresh审查B1指出同backend成功r1后request残留，使第二次queued r2在invalidate后cleanup断言失败。新增共享回归`reusedBackendQueuedCancellation`，**先保持旧FakePorts真实编译并取得唯一`java.lang.AssertionError: same request cleanup` red**（7case/其余6绿），随后仅在成功cleanup gate之后增加`request = null`，保留同request校验/cleanup gate/owner收尾检查，生产状态机不改。

- 修后主树：81actions全部执行，**7 tests/0failure/error/skip**，XML SHA256 `20c4a1501bdb8a0c1bf37c53504e6fd6a045a2be86518678d5f459decb76a29a`。
- 新共享回归覆盖同backend首次成功并commit→第二次queued后取消→cleanup，正确capture/infer/cleanup为1/1/2、owner释放/无迟到预览，第三次成功后2/2/3。instrumentation新增共享wrapper，actual DEX精确**12**个public concrete `()V`方法；不是runner发现。
- 原两个合法fixture行为变异已重跑：fresh-copy 5阶段**0/1/0/1/0**，每阶段7case，red各唯一`enginePayloadAndOrder / engine payload display`、`cleanupRetainsOwner / cleanup must wait for release` AssertionError，恢复green；不是工具/编译失败或设备red。
- 两APK真实重建并静态检查通过；host SHA保持`856536f1…ee7d`，新仪器包40158bytes、37DEX定义、SHA256 `770c1a7287730557afcf36aa4308d78f7b36744c102475285bbdd508b9442981`。29/35/35与原v3证书保持，无权限/native/assets/重复production定义，base/runner required=true，host非export无launcher。
- B1新证据全部独占`.work/p1b-build/b1-fix/`：`handoff-b1.md`、`final-freeze.json`、`final-audit.json`、`red-junit.xml`、`green-junit.xml`、`behavior-3h_j4t6p/`、`packages/`、`dex-methods.json`和命令收据。旧2A handoff/freeze/audit/log保持历史原样，以下6/11及旧仪器hash仅为**历史2A基线**，不能当current。
- 历史B1交接时没有重做prepare/旧35JUnit/full/2B，且当时待fresh定点回审；**其后B1回审已CLOSED并由父接受**，当前2B及full结果见顶部。无设备/Android运行、无stage/commit/Beads写，不据此交付APK。

## 历史2A真实结果（B1修前）
- 已落盘独立模块/两Manifest/非export无launcher的3输入框Activity、共享fake ports/assertions、6个host JUnit4 wrapper、11个JUnit3仪器方法及隔离可编译行为变异driver。
- 任务/configs查询真实exit0。新模块13个debug/JVM/androidTest配置strict/offline解析曾exit0，但主树prepare发生旧root lock删除事故，已按开工冻结SHA精确恢复；不将其当合格prepare。隔离副本真实prepare已exit0，新host lock逐bytes一致（SHA256 `d05a063c52689a10a5adc40fc0f64e5beec4f5e19c84fc3b6c87d5543dba976e`），旧四锁与metadata前后SHA exact。
- 修正仪器fixture CancelGate双方法实现后，模块JVM及两APK assemble真实exit0，81actions全部执行；host JUnit精确6tests/0skip/error/fail。SDK deprecated API note如实保留。
- 隔离行为driver真实exit0：fresh temp源码副本每阶段删除模块build/classes，5阶段Gradle退出为 **0/1/0/1/0**。合法不同协议文本仅使`enginePayloadAndOrder`报`java.lang.AssertionError: engine payload display`；cleanup提前完成标记仅使`cleanupRetainsOwner`报`java.lang.AssertionError: cleanup must wait for release`；均6case/无skip/error，恢复green。不是编译/工具失败或assert(false)充red；也不等于生产mutant或设备runner red。
- 两APK实际静态检查exit0：29/35、无permissions/native/assets、allowBackup=false、runner/base required shared libraries、host Activity非export无launcher，全部DEX无重复定义；host无fixtures/JUnit实现，test不重复target生产定义。仪器DEX精确11个public concrete void noarg test方法，**不是runner发现**。
- 临时包检查首轮因aapt2字段名sdkVersion/minSdkVersion写错失败，仅修checker重查，不能算行为red。全部错误与中间锁事故保留在handoff/plan/log，不隐去。

## 历史2A工程产物（B1修前身份，不交付）

| 产物 | 路径 | bytes / SHA256 |
|---|---|---|
| 测试宿主 | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2584412 / `856536f13c8fb7a975663bb27f35f8ce6ca586f2ffee36907d3aca757f58ee7d` |
| 仪器包 | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 37874 / `452ecd918b7ae21292d7a8ae77424fa95a8959b6d6c33af2a3a1c825e00f4cc2` |

均v3单签名、证书SHA256 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`；不读取/生成key。宿主version1/p1b-testhost；AGP生成仪器Manifest不设置独立versionCode/versionName，不冒称仪器有宿主版本。主树JUnit XML SHA256 `efafd0e69d0ea28379b0bdd0499799672a0d33fdaab7228e6a9ae393e04d551b`。

完整命令、各driver/输入/日志/XML/包hash与exact方法清单：`.work/p1b-build/{command-index.md,junit.json,instrumentation-methods.json,dex-methods.json,packages/result.json,behavior-_t86yvtb/result.json,final-freeze.json,handoff-2a.md}`。本轮保护域旧195build仅settings变化、80native与358旧证据逐SHA不变；新模块维护源码/fixture/config/lock与driver全部纳入新冻结库存，不用产品旧collector冒称已覆盖新模块。

## 边界
以上2A/B1/2B段为历史检查点；当前本地full工程结果见顶部，**不是M5完成、不是可交付APK**。本leaf不stage/commit/push/写Beads，无设备/模拟器/ADB/麦克风/私人文件/权重/远端CI访问。没有执行任何Android仪器测试，runner发现/执行/失败sentinel、provider、InputConnection均not run（已真实编译，仍仅compiled-only）；本地ContentResolver.wrap不是Binder或SAF grant，手动controller失效不是生产Service生命周期回调。平台runner/shared library设备可用性与deprecated风险仍待授权验证。

2B接线及针对性验证其后已通过fresh审查且父接受，最终full/生产Lint现已由独立验证leaf完成；仅最后限定安全审查/本地提交及committed-current绑定后置。历史2A错误/命令/exit/指纹在 `.work/p1b-build/handoff-2a.md`；B1以 `.work/p1b-build/b1-fix/handoff-b1.md` 及已接受的 `.work/p1b-review-2/final-review.md` 为准，不再待回审、不重开已闭B1。
