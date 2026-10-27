# B首次正式full：FAILED，非APK交付

Task `llm-asr-zcm/B`，base `8b71b56b8927031be938d188bacafe0b530a4b8a`；同residentb-build唯一writer、p1-steward唯一Beads writer。唯一命令：`bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks`。本批B attempt1，bg `b3f8eb355`真实failed1、rootreaped/ownedexited、无timeout；完整流`.work/residentb-full/formal-full.stdout/.stderr`及execution记录保留，72000仅包络，全部原子预算/单worker1536m/JUnit512m/nativej2不变。

## 首required失败与停止

128 actionable tasks:128 executed，Gradle构建约10m24s（外层627.67s）。`:verifyHost`首失败：

```text
ValueError: T5 unique real production mutant anchor: asr/InferenceAdapter.kt
```

`tests/kotlin_testhost_migration_test.py:252`唯一真实生产mutant锚点仍要求readiness→accessCompleted→failure→无条件`library!!.load()`；当前已接受B `InferenceAdapter.kt:30–31`是`if (session == null) library!!.load()` / resident session.enter分支。因字符串不再命中，测试在mutant编译前拒绝。这是维护contract适配缺口，**非有效编译行为red，也未证明生产推理错误**。本轮没有改源码/metadata/pin/测试修绿、没有自动第二次full；具体修复需父精确授权。另此前静态观察到独立APK verifier有旧prepared根常量，只是潜在后续风险，本次未到达该gate，不冒已执行失败。

## 已真实执行的部分成功（不升级为full）

- 原AGP/KGP/D8及三包构建闭包执行；`:app:buildNativeArtifact`从新production源根在已fresh的正常AGP native-debug重建578对象＋JNI，DSO `4ee0b300caa099a7ef0358fdf42901b9f963d5e2be8fb8e330fb5cad55394481` /5,406,248B，原native verifier实际通过，84输入等于launch；未复制候选对象/DSO替代任务。
- `verifyAndroidTestBuild`子gate真实通过，三包parsedDEX/Manifest/签名/隔离receipt绑定当前build238/module10；host JUnit7通过、14仪器方法仅编译/静态库存，不是runner执行。
- strict依赖checksum正例0→具体AGP jar checksum变异1（expected全0/actual原SHA）→恢复0通过，完整negative日志保留。
- verifyHost已到原Java ABI876、K2J四mutants普通O、测试切片三mutants普通O、jvmnext五owner普通O、T5 source/self普通O等子检查；随后T5 contracts唯一anchor失败。不得据此前通过称完整verifyHost/proof/IME/runtime/全host通过。
- core/runtime JUnit XML本次尚未产生；Lint任务没有执行，不能拿旧Lint XML声明本轮零Error/Warning。独立产品最终验证报告未到达，原全部required gates未完成。

## 部分产物，仅现场证据不得交付

| 产物 | bytes | SHA-256 |
|---|---:|---|
| app-debug.apk | 5,352,541 | `cdf9ed7d86a1e52c11ce9d998f3d47b73265bdd0ba9773f6eebb830ecac0f49f` |
| test-host-debug.apk | 2,685,540 | `ee978b23ad5993c2d2055f49ca424a4522800c73fb249941a0c792fdfadc98cd` |
| test-host-debug-androidTest.apk | 66,382 | `4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8` |

原v3证书指纹不变`e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`。三包子receipt：`build/verification/android-test/run-64f83f7b08b34e7e89453821f15e87e3/receipt.json`；这不是完整product最终receipt/本批可验证APK。原A三包与旧native已在2136文件逐SHA备份中保存，不自动恢复覆盖现场。

## 输入/备份/终态

launchfreeze动态build238/module10/native84/tool11，55维护候选=继承53+两full报告，formatter开跑前只加两报告路径。运行期间输入完全冻结，post-audit确认全部currentmaps/sourcecandidate=launch且686有限旧候选/审查/native身份保护文件原值保持；20个精确known生成根fresh、2136备份逐SHA复核。没有gradleclean/笼统清.work/cache或复制秘密key、模型、用户音频。公开固定sample/config仅由required task检查打包，没有播放或推理。

正式root与实际已执行子命令终态/流SHA/PIDstart/nolease/noBG详`.work/residentb-full/owned-terminal-ledger.json`；launch时三份status/full报告原字节另保存，终态报告仅supplemental更新，不手改collector/receipt凑相等。本full未接新nativehostprobe的地方不声称重跑，targeted历史按原身份继承。

## 后续与权限

父消费失败现场后另授精确T5 contract适配/必要定点回归，修复接受后方可另授修后full。当前没有stage/commit/push/Beads写入/二级委派权限，不交APK。真实loadedASR/Android runner/设备/麦克风/私音频/语种质量/PSS/性能/ASan均NOTRUN；静态包与native gate不能冒设备验收。
