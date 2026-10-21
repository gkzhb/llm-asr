# Kotlin batch P4：唯一正式 full（提交前证据）

任务`llm-asr-ap5`，owner`p1-steward`，唯一验证leaf`ap5batch-plan`。生产20owner/ABI及R1/R2/C1/观测时点审查已父接受CLOSED；接线点审receipt8f5387a0已接受NO BLOCKING。审查为newapi/gpt-6.1-sol上下文隔离，非跨家族独立。原status和全部旧冻结证据保历史，不事后改写。

## 重要证据限制：限定通过，不是旧保护全部保持

**保护结果仍为qualified pass + explicit historical loss，非无保留通过；阶段1安全审S1待回审，全文安全/当前绑定尚未审完。** 撤回原版将full前`dcead009…/55f33141…`归为原R1 consumed的说法。同两个动态路径有三代不同运行身份，不能以旧manifest本身保持推断其引用字节保持：

| 路径 | 原R1实际consumed（bytes / SHA256） | full前protected快照（bytes / SHA256） | 本轮full immutable（bytes / SHA256） |
|---|---|---|---|
| `.work/red/compile.json` | 2303 / `61e0d08ae49bb9f5ce795ff785ad145741aa411e1bc0ce537ee9eda84a0fbb86` | 2378 / `dcead009f67e6ea16b3df239e390cc1bb41fed09a49a6a4ff24a5a404d0f372d` | 2378 / `8446789c97e2285d80eddc8838378daf57a1f797f2f966e0df442afa83135624` |
| `.work/red/run.json` | 811 / `e8dbe25c1491b2963fc1a4234887682b01541f616efb462ae3671d9ac58d5fe0` | 836 / `55f331418a1deb30950e36fd9c73d141b38f1b9d9ba4a06d4d4e95bb4d2c1d81` | 836 / `1ee7091b8c586f7de11fe5d26d822ba89e834f8b9844bf470efcf331b2a285ac` |

- 原审来源`.work/ap5batch-r1-review/consumed-identities.json`的`files[152]`/`files[155]`，37215bytes/SHA `894b9b8bec10066cecac4124c2198440a64671dfa7dae9f224cb02dd744aecad`，与原receipt输出身份相符。full preflight-v2:75–79/95、v3:58–62/75–85只提取引用**路径后重新hash当时内容**，不是保留原消费SHA。failure-details的prior_reference_sources是路径来源，不能证明同run。
- full前代直接来自`.work/ap5batch-full/protected-before.json`；后续failure-details、protection-after-v4、closure及原full报告只是其派生引用。在本次32个显式索引中，未见其他consumed记录实际绑定full前两SHA；不把保护快照当历史review消费。新逐源JSON定位见ignored `ledger-reference-sources.json`及`reconciliation.json`。
- 旧查找只针对full前代：3roots/1616files/0同size，不能回答原R1代。纠正轮仅做**一次**新有界查找：本批full的evidence（257）、evidence-v4（13）、backup（1318）及显式old-part-recovery-backup（41）四roots，加32既存索引的明确引用，去重2543files，先size筛后hash3files。原R1和full前四个SHA均无现存副本匹配；本轮immutable两SHA匹配。仅结论“该范围未找到，原R1及full前两运行代各不可独立重验”，不宣称全机器无副本，不新增损失路径数。未扫整个.work/home/session/模型音频，未重跑历史/full或造旧bytes。
- 时间边界：原R1读取发生于其receipt封口（created `2026-10-21T19:54:02.759533+00:00`）之前，但确切读取/覆盖时刻unknown；full前快照（早于full启动`21:11:51.544822+00:00`）已是不同身份。故原R1替换只可限定在原读取之后、full前快照之前，不能断言在receipt封口之后。中间cleanup/lock fast分别于20:03:35/20:25:14结束但无该代精确ledger绑定，不能指定哪次覆盖。full前→本轮则有before/after和full `21:11:51.544822–21:27:29.583380+00:00`内Part执行证据；精确覆盖点/中间代unknown，不凭mtime猜时点。
- 原R1两个正常Part运行ledger的原始argv/PID/session/owned-exit字段不能再独立重验；原receipt仅能保其被记录的身份。原四stream bytes匹配、32个独立protocol记录逐SHA仍在，但相同stdout不证明同run。原审结论本是R1剩余safe-remove静态P2 OPEN、R2/C1 CLOSED和观测时点更正，不改写为当时全绿；其静态来源/独立fault证据与后续cleanup/lock fresh回审应分别消费。本任务不据本轮proof回补旧run，也不重开已CLOSED机制。其他原消费的后续版本化source/docs/XML不以3271分区笼统担保全部历史同SHA。

父此前允许继续的非产品限制针对full前两ledger损失；准确三代及原审不可重验边界仍须父消费和原安全reviewer判断，**本纠正文档不自行判S1 CLOSED或安全通过，安全审仍可否决**。当前受测源、freshPart实际compile0/run0/EXPECTED_FAIL、full闭合日志、JUnit XML与actual包/formal输入绑定为独立本轮证明；当前两ledger及stdout/stderr保于evidence-v4，不能替代任一旧代。两XML事前备份匹配的是full前SHA，不自动等于更早各review消费的XML代。详见full-inputs.json与新ignored `.work/ap5batch-identity-fix/`；旧handoff/freeze/receipt/失败证据保历史，在新补充handoff勘误而非回写。

## 唯一执行

```sh
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

一次真实exit0，938.039秒，174 actionable tasks全部executed，Gradle0/tee0/scan1；closedlog 47920bytes/SHA `f8dab08647a8563f0b0f4dbc04fdd8e3d710e3494bbee0fe490b0959025f0b29`。Nix cache-only/Gradle strict/offline及单worker/既有内存和nativejobs2门槛保持；成功不是全新缓存/hermetic。

HEAD/base`74f6acf3d2ed2e4589805bf3d3e144764ffeae35`，执行对象是已审**precommit dirtyfreeze内容**，不是未来commit对象。精确候选由原allowlist及各修复delta合并52路径，再仅新增本页/full-inputs.json两报告为54路径；数量是交叉核，逐path/mode/SHA为身份。当前生产37Kt26Java/63topowner，非Java0。

## 当批实际gate

- actualcoremain Kotlin/Java本轮fresh编译先于verifyHost；旧class已列明清理，不能用旧294class冒fresh。正式host内defaultlifecycle、cleanup-only、lock-only三调用实际成功；各临时Part/Probe优先于固定coremaincp，env不覆盖。三方锁T1blockedinterrupt/T2拒返回/C独立JVM拒及退出恢复实跑，旧R1机制不重新扩域；独立directhostcleanbuild能力不作承诺。
- host801 Java ABI/行为checks；新十生产mutants和原enum/ports/phase每项compile0→精确AssertionError1→原SHA恢复green0；13cleanup、原model3、平台fakes/picker全部旧gate保持。Part真实mixed5Kt4Java空间预算mutant实跑。
- actualcore20迁移owner及nested/Companion真实javap，原publicsubsetdelta0；B1五private原构造+单marker publicsynthetic精确形状保持，actual$core桥与此前受审实际core一致，Java源访问负例不无故重跑。实际三包DEXclass_def单独核，不以字符串引用冒class或claim全反射一样。
- JUnit core34/runtime1/test-host7全零failure/error/skipped；module真实classpath正/负例、JNI生成头/NDKarm64object/linked符号/nativeprovenance与ELF静态16KiB门槛保持，不冒设备native执行。
- app/runtime生产Lint Error/Fatal0，警告android/app {'Warning': 17}；android/asr-runtime {'Warning': 10}；既有警告如实保留defer，未增Suppress/baseline/禁error。实际NewApi API33/min29精确位置0/1/0、共享validator拒绝恢复、strictchecksum唯一目标0/1/0与fixture五阶段0/1/0/1/0均本轮执行。
- C1旧空compare无独立终态仍是历史缺证，后续独立补证已接受且本轮不重跑CLI，不追溯替换历史。

## 实际三包（未提交，不宣称交付）

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5256509 | `56e72fea3dfb5038b75cb67d0465874b5a7a807e23791a97b6a1fb454f91189d` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2624588 | `fbbcb1894ef94fec155dc0a075e8cd2f427a8df126748fdc0f7409614fe68e69` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 52318 | `950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41` |

独立产品receipt `android/app/build/verification/debug/run-3a3c20718e344a35880aaf190bf3fb0a/result.json` SHA `cac306ea83740f99155522942058982e3fc233917af71e915d8228d54bbab9ac`；三包receipt `build/verification/android-test/run-5afc3b39ab664a3089354247352f097a/receipt.json` SHA `4421ed7398d62141cc3fc9909259d9ee80e3d9c7eb3954c458c45e505d2c9920`。actualbytes/AGPmetadata/原证书v3/签名、权限、assets、native、ZIP与DEX/currentinputs逐项核。仪器方法仅编译与DEX库存，不冒runner发现/执行。

## 输入与保护

动态build216/module10/native80逐SHA前后/current与formalreceipts相等，外部tools entry及resolvedtarget分列；详见[full-inputs.json](full-inputs.json)。native来源相对base的20Java→Kt重命名如实保留，并非base所有nativeinput同。

前置逐字备份1318列明generated/旧三包与formalreceipts文件，只清13精确generated目录，未全clean/nativeclean。3271保护条目仅限full前后窗口精确分区：3267严格保持、2事前备份XML覆盖、2未事前保存full前ledger损失；不得延伸为全部历史consumed运行代或所有旧记录保持。其他source/report/freeze及receipt保护，原排除用户资料不夹带；578MNNobjects SHA/inode/mtime全保持，DSO `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`保持，仅JNI增量复编/链接检查，不触cache/key/model/audio。父coordination变化仅supplemental，不刷新旧freeze凑同。

尚未stage/commit/push；父消费后另两阶段安全本地提交，未成功commit不交可验证APK、不关闭ap5/M4/M5/设备任务。设备ADB/模拟器/micprivate/runner发现执行/真实SAF/Bindergrant/IME/JNI4K16K/模型推理下载prepare/远端CI/releaseR8全部notrun；host/JVM/DEX/签名ELF静态不等设备验收。

`.work/ap5batch-full/`保当批closedfull/post、实际gate/receipts/ABI/输入保护与精确candidate/evidenceclosure；Git仅新增本页与full-inputs.json两非敏感规范摘要，不纳APK/缓存/keys/海量raw。
