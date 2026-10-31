# llm-asr-t0t 第三次正式 full — 工程 PASS / 安全审与提交待另授

## 身份与真实命令

main/base `64d00d16e13577b9e6ad049511acd96b3e7d7d6b`；原七候选 dirty freeze 受测，**不是测试了尚未产生的新 commit**。唯一验证 writer t0t-build，唯一 Beads writer/owner p1-steward。旧 status/full1/full2 与 Lint prepare 失败/成功证据、原 plans/raw/归档冻结；本文及 [full3 输入](full-3-inputs.json) 仅当批 supplemental。

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

仅 supervisor 固定项目 Gradle home、记录白名单环境后 exec 原入口。无 prepare init、skip/-x、下载补绿、写锁/metadata、换版本/仓库/expected 或清 cache/native objects。原 SDK/JDK、strict/verify-metadata=true、worker1/JVM1536/JUnit512/native2 及全部内部预算保持。

`be3c1c8f4` completed **exit0**；root_reaped/owned_exited=true、primary=null。Gradle `BUILD SUCCESSFUL in 1h 2s`，**184 actions 全部 executed**；pipeline Gradle0/tee0/scan1。完整 log `.work/verify-local/full-h6svxL/build.log` SHA256 `85dc63e6bf341a90ec9cf1aad1f46b94aaab396669dc4d6c88c1d41ef80f45b7`。外层 raw `.work/t0t-full-3/commands/full/execution.stdout` SHA256 `4a7fc60b28a1c06661ab5abd66d8d56a8afaf5a5ecb618b8edb530a0d9bf8cc4`；二者分别记录，不误称 wrapper 前后缀属于 Gradle log。

## 当批完整验证

- 原 `verifyHost` 全链重跑：真实 ABI/compat、compiled mutants、旧 IME return、readiness 普通与 `-O`、永久 cleanup fake 故障、owner/取消/迟到/worker 清理等。独立 raw 审核 readiness 各7compile/7JVM（合28child）、return18child、next23child、compat9child：真实 exit、rawSHA、reaped/exited 均吻合；三 readiness mutants 编译0/目标 AssertionError1/恢复0，当前 Service/driver/caller SHA 一致。fake cleanup 不是 Android 执行证据。
- 实际 runner JUnit：core34、runtime1、test-host7，合42；均0failure/error/skip，XML SHA/方法表在 `.work/t0t-full-3/junit-audit.json`。不拿旧 fast35 或 host 直接 main 充本批 runner。
- 真正 AGP SDK 编译/D8/签名与三个 APK assemble；完整 parsed DEX/Manifest/签名/权限/资产隔离和当批独立 Android-test receipt 通过。14 instrumentation 方法只是 compiled/static 库存，runner discovery/执行 NOT RUN。
- 独立产品 `:app:verifyDebugPackage` 当批通过：单 arm64 combined DSO、确切资产、RECORD_AUDIO-only、API29/35、IME 声明、current module owners 及 report binding。不是拿三包 receipt 替代此门。
- `verifyJni` 真正 arm64 JNI object 编译/符号与 Java native descriptors 核对；native 原受控增量显式 link/provenance/ELF16KiB/static runtime 校验通过，84项当批输入绑定。DSO同原字节，不冒全 native clean rebuild、模型推理或4K/16K设备验收。
- app/runtime 正式 Lint：均0Error/Fatal，分别24/17 Warning；typed identity 13 gates/8 consumers 各66 artifacts/171 edges 通过。共享源码警告可能跨模块重复，不称41个独特问题。
- `verifyLintEvidence` 真正隔离 default AGP green0→唯一 NewApi/API33/min29/精确 file-line red1→原 bytes 恢复 green1；artifact/edge 身份及普通任务共享 validator 的 missing/extra/replacement filecollection 拒绝与恢复通过。普通任务恢复 marker 不冒 default typed worker；root 正式 Lint 是另行真实执行。
- `verifyDependencyEvidence` 原checksum green0→唯一 expected零值/actual `9697733a215f2ed42e5b2fc4053484ec832eab29584ebced94d92c91090051c1` 目标红1→恢复0；精确原 HTML/日志 SHA 已核。
- 末尾 `verifyAndroidTestFixtureEvidence` 五阶段全部真实执行：green-initial0、red-payload1（enginePayloadAndOrder）、green-payload-restored0、red-cleanup1（cleanupRetainsOwner）、green-final0；每阶段7方法发现，两可编译行为 AssertionError，最终原 bytes 恢复。非 Android runtime。
- 原 architecture、输入、APK checker、format、local entry/DEX/graph/tee 等负例与恢复门均通过，无 shortcut。

独立消费证据位于 `.work/t0t-full-3/{execution-summary,receipt-audit,host-owned-raw-audit,junit-audit,native-audit,lint-audit,fixture-audit,checksum-audit}.json`。新 work 消费器起初对 wrapper 前后缀/restore 命名/compat label/checksum HTML 的四处假设校正已记录 `consumer-notes.md`；最终消费器exit0，没有重跑 full、改工程 expected 或覆盖旧失败。

## 当批三包身份（尚未交付）

| 包路径 | bytes | SHA256 |
|---|---:|---|
| android/app/build/outputs/apk/debug/app-debug.apk | 5352953 | a16027cbd7764a9cf46d944c1623a51763aa82ab9ebe67135c90efdd8858a4f3 |
| android/test-host/build/outputs/apk/debug/test-host-debug.apk | 2685540 | ee978b23ad5993c2d2055f49ca424a4522800c73fb249941a0c792fdfadc98cd |
| android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk | 66382 | 4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8 |

产品 `org.llmasr.minimal` / `0.6-debug` / code6 / min29 / target35。三包证书 SHA256均 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`。三包虽然同 full2 字节，本批有真实 assemble/D8/签名/checker/receipt，不复用旧报告。

本批 Android-test receipt：`build/verification/android-test/run-f0e52684738d4c8fb4f0758629839437/receipt.json`，SHA256 `f10cc928a45ba321edb1a11779e99a84c3d651afc97e341c2b5dd459e21e052e`。

独立产品报告：`android/app/build/verification/debug/run-7131e69e2c8a43bcb37bf275b6209062/`，`result.json` SHA256 `43be13baa6bbc12b5128c32e572b78636047ae6ddfaf584fef5b38c4fa82aae1`。当前 build239/module10/tool5/native84 分域逐 SHA 绑定；这些是动态观察数，不是永久门槛。外部受控固定合成音频/config仅原门读取，不代表私人数据或模型权重推理授权。

## 警告处置

app：UnusedAttribute3、GradleDependency1、ChromeOsAbiSupport1、DataExtractionRules1、UsableSpace2、MissingApplicationIcon1、SetTextI18n15。runtime：UsableSpace2、SetTextI18n15。与已接受定向 Lint 警告保持，非阻塞 defer；不升级依赖、加 baseline、suppress/disable 或为零警告改 scope。另保留实验 aapt2 override、CMake minimum 弃用、Java deprecated API 提示；未见 D8/R8 metadata 异常。原 T5 fault5（实际 fixture private worker 的直接 self/distinct/Error/interrupt 注入无 seam）与 fault6 Android SAF 如实 NOT RUN，不写 all faults passed。

## 保护封口与后续

前置 known generated 2960文件/325795840B tar逐SHA验证，精确域为 build、core/build、android/app/build、android/asr-runtime/build、android/test-host/build、.work/red；原41505历史输出加既有 host/raw 证据扩展原位保护333354文件，旧 t0t work/归档/报告/plans另冻，父 supervision 可变 supplemental 单列。未删除历史 parent/cache/nativeobjects。最终保护审计 `bbe650017` completedexit0、errors=[]：333354历史文件、1046旧plan文件、14旧handoff及全部旧t0t work/raw/归档/维护报告原字节保持，index未变。35项正常生成物变化均有前置备份，archive SHA256 `a3b094628fa84b30c4e13a2e43e80aaadcd7e4dfdae0c2bc3220303f6f0289de`。七candidate仍同R1manifest，当前239动态输入逐项=开跑前freeze；新两报告只supplemental，父SUP mutable不作不可变失败。本轮backup/full/audit三项后台均已真实终态，无本leaf遗留后台。

本报告属于工程成功记录，**安全候选审、精确 stage/cached 审及正常本地 commit 尚待父另授**；未提交，不宣称 APK 已交付、任务已关闭。设备/Android runner/系统 IME/fcitx5/Binder/SAF grant/InputConnection/真实低内存生命周期、mic/private、模型/PSS性能、远端CI均 NOT RUN。
