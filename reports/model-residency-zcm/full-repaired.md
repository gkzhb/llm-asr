# B formal full attempt2：FAILED，非APK交付

Task `llm-asr-zcm/B`，base `8b71b56b8927031be938d188bacafe0b530a4b8a`。父接受review-7 NO BLOCKING后仅授权第二次完整原链，无产品修改。唯一命令`bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks`；bg `bf2adab17`实际failed1/rootreaped/ownedexited/无timeout，176 actionable tasks全部executed，Gradle37m58s、外层2280.80s。原atomic budgets/strict/offline/1worker1536m/JUnit512m/nativej2不变。

## 首required失败与停止

`:asr-runtime:compileDebugUnitTestKotlin`：

```text
InferenceAdapterTest.kt:94:72 Unresolved reference ModelAndroidHelpersTest
InferenceAdapterTest.kt:294:20 Unresolved reference ModelAndroidHelpersTest
InferenceAdapterTest.kt:306:33 /312:29,31,33,35 An explicit type is required on a value parameter
```

当前runtime测试源集及KotlinCompile精确过滤只纳wrapper与InferenceAdapterTest.kt，而该body引用ModelAndroidHelpersTest.MemoryGuard/TestIo；此helper未在该编译闭包。后续lambda类型错与未解析TestIo关联。canonical host65Kt17Java包含helper所以通过，不能冒此AGP单模块编译。**不是compiled行为red、未证明生产推理缺陷**；本次无源码/pin/test修复或自动第三次full。父须另授具体测试闭包修复，不能放宽production编译源白名单凑绿。

## 真正通过的本轮子gate（仍不是full成功）

- AGP真实SDK/KGP/D8/三包构建与parsedDEX/Manifest/隔离/原v3签名gate通过，host7JUnit0、仪器14方法仅编译/库存，不Android runner。
- 原native task正常执行SDK/prepare/CMake/Ninja/strict链；native-debug当前源根/578对象保留，未盲删重建、不复制candidate.o/so。DSO `4ee0b300caa099a7ef0358fdf42901b9f963d5e2be8fb8e330fb5cad55394481` /5,406,248B及84输入符合launch/approved，原strict gate通过。
- verifyHost本轮已过：原ABI/取消owner/IME/runtime/next/T5 contracts普通O各9compiledroutes与故障oracle/proof/IO/corefault等其required子检查按完整日志为准；不是重复上轮targeted记录。独立产品最终report已实际生成且其238build输入与launch/current相等。
- strict checksum具体目标变异expected全0/actual原SHA拒绝及恢复通过；实际Lint identity工具门槛/targetNewApi red/restore通过。原Lint app24、runtime17 Warning，Error/Fatal0。app分类SetTextI18n15、UnusedAttribute3、UsableSpace2、GradleDependency/ChromeOsAbiSupport/DataExtractionRules/MissingApplicationIcon各1；runtime SetTextI18n15、UsableSpace2。旧i18n/storage/Manifest/tooling/ABI/icon类别保留defer，不新增suppression/baseline、不称零warning。
- verifyFormat126维护文本/ordinaryO负例及原NativeInputs11/4普通O通过。Core/runtime JUnit XML未产生，runtime编译失败意味着核心完整JUnit/verifyDebug出口仍不满足，不能交付。

## 本轮报告与部分包，禁止交付

- 独立产品报告：`android/app/build/verification/debug/run-989a3bce10fe44ec855cc47bb4d543ff`。
- 三包receipt：`build/verification/android-test/run-04526d3ac8804785b0e0ffa697c2ec37/receipt.json`。
- app5352541B SHA `cdf9ed7d86a1e52c11ce9d998f3d47b73265bdd0ba9773f6eebb830ecac0f49f`。
- host2685540B SHA `ee978b23ad5993c2d2055f49ca424a4522800c73fb249941a0c792fdfadc98cd`。
- instrumentation66382B SHA `4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8`。

三包与先前partial字节相同仍是本轮真task构建核验结果，不用旧receipt代替执行，v3证书/0.6-debug code6/API29/35/RECORD_AUDIO唯一权限保持。失败full产物不可告知用户可验，独立产品gate0不等完整full通过。

## 输入/保护/收尾

60候选核同后只launch前formatter加两新report路径、status/full2报告supplemental形成62候选；动态build238/module10/native84/tool11。955当前known generated逐mode/bytes/SHA备份、12必要fresh根；native-debug578保留。没有gradleclean/全.work/cache/model/key复制，旧fullFAILED/2136备份/gate-sync/reviewseals有限锚保持，不重复全历史树扫描。运行期间所有collector/source输入冻结，post-audit核launch=current，两当前receipt均绑定本次输入。

完整raw根`.work/residentb-full-2/formal-full.*`、source-freeze/post-audit/failure-details/owned-terminal-ledger保实际退出与所有streamSHA、knownPIDstart/nolease/noBG。终态后仅status/两新报告更新，launch原字节另保，不手改receipt/maps。旧首次失败与旧root拒绝不重标pass；新nativehostprobe未接full的证据仍targeted继承，不称本次重跑。

## 下一步

父消费具体AGP测试闭包失败后精确授权修复及定点compile/test/fresh窄审，再另授是否第三full。本writer停止，没有stagecommit/Beads/二级派/push/reset/新下载权限。只required task读取固定公开sample和两config检查打包，无模型图weight/tokenizer/私音频/设备/DSO推理/密钥读取导出。实际loadedASR/Android runner/设备/质量PSS/性能/ASan均NOTRUN。
