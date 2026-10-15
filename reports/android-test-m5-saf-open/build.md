# M5 SAF open 编译工程：当次 targeted 证据

任务 `llm-asr-76d.6`，base `add5d2c854ac8d2980b2bf13d92ad5bc373ad477`；单writer leaf `m5saf-build`。仅新增测试工程，非设备验收、非M5完成、非本轮APK交付。未stage/commit，父接受后另阶段fresh只读审、必要full与安全本地提交。

## 实际命令与结果

- `python3 tests/android_test_apk_test.py`、`python3 -O tests/android_test_apk_test.py`：各exit0。
- `python3 tests/android_test_inputs_test.py`、`python3 -O tests/android_test_inputs_test.py`：各exit0。
- `bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh android-test-build --offline --rerun-tasks`：exit0，118 actionable tasks: 118 executed。
- `bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh format --offline`：exit0，1 actionable task: 1 executed。
- `git diff --check`：exit0；当次动态collector/receipt/实际三包SHA/旧保护域审计通过。

checker正例使用同生产DEX parser/三包策略的合法14方法库存；逐名删除/重复两新增方法必须报inventory mismatch，逐名非法public/static/native/abstract flags、return、argc、code必须报JUnit3 signature/code mismatch。它们是主机parsed-report/DEX目标负例，不是签名APK变异、runner发现或新增SAF运行red/green。

原7个FixtureContractTest JVM XML精确名单全零failure/error/skip；总计14个仪器方法（新增2个）仅Java编译与全部DEX静态库存。原权限/组件/证书/v3/target/native/assets/产品泄漏检查均保留且三包实际工具exit0；产品独立full/JNI/Lint验收仍是后续阶段，不借targeted冒已完成full。实际日志含既有AGP aapt2 override experimental、Java deprecated API与CMake minimum compatibility warning；无D8/R8门槛诊断，未执行host Lint新层，不称零warning。

## 三包当前身份

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5216237 | `3fe42d623e8f9b60e7d34fd63f432de2aa780520395fc8aeeb5bbbc1ca32df10` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2584412 | `856536f13c8fb7a975663bb27f35f8ce6ca586f2ffee36907d3aca757f58ee7d` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 52110 | `46edf78e8510f0ffc4bc686fee9840459ef1f20a01a74733c780b374bf2a8a4c` |

产品保持 `org.llmasr.minimal` / `0.6-debug` / code6 / min29 target35 / arm64-v8a，产品APK字节与本批before一致。宿主 `org.llmasr.minimal.testhost` / `p1b-testhost` / code1；仪器 `org.llmasr.minimal.testhost.test`，标准 `android.test.InstrumentationTestRunner` target宿主；测试两包无权限/native/assets，原证书/v3。

## 当前输入与保护

当次动态build collector 209 项、module 10 项、native 80 项；全部四工程delta精确覆盖，docs仅追加。`input-sha256.json`绑定当次SHA、工具输入、receipt SHA、三包、7JVM XML；不沿用旧数量/旧SHA。before三包已备份自有ignored work。

804个非允许tracked文件内容及mode/mtime/inode保持；原20excluded内容/metadata保持；生产63源/native80/锁metadata/旧planning和报告/.gitignore/root均未改。调查三plan及父监督task/findings内容与初始恢复一致；父监督progress由父在异常恢复协调中追加，leaf未共写，未声称其metadata冻结不变。新源码没有依赖prepare/锁变化/下载，原578 MNN对象由统一链检查，不宣称全native clean/空cache/hermetic。

## 限制与失败历史

新增两方法直接构造生产SafModelSource→真实repository/controller。provider是in-process ContentProvider，wrap不是registered DocumentsProvider/Binder/grant。只读target cache UUID内3byte合成源，实算SHA，与repository隔离；cursor/PFD计数不是InputStream关闭故障证据。阻塞取消保持busy至真实worker终态，worker Throwable与终态断言在测试线程；有界释放/join，失败只清自己目录，不删活IO。

checker/input四命令与统一build/format本次均exit0；恢复读取曾超50KB已补齐，误猜两Kotlin生产类为.java的ENOENT已纠正，均非行为red。原build自动terminal未到，resume后exact bg registry丢失；父提供具体disk exit0/log及无命令存活证据后，授权一次exact id/PID核验（Unknown/PID不存在），消费既有真实终态/完整log/receipt，未重启成功链、未伪造通知。详`.work/m5saf-build/build-terminal-recovery.json`。format也在磁盘exit0后一次exact registry检查确认为completed/exit0，无循环等待或重跑，见`format-terminal.json`。

全部not run：新增SAF assertion运行red/green、标准runner发现/执行/运行失败sentinel、Binder/grant/系统DocumentsProvider、Activity/Service/IME真实生命周期、InputConnection运行、设备4K/16K、JNI-device/AudioRecord/mic、模型权重计算/下载、远端CI、release/R8/新签名。本批未执行full、Lint新层或旧fixture行为driver，不重做已提交P1-B full。

## 证据导航

- `.work/m5saf-build/{android-test-build,format}.log`及`.exit`：真实命令终态；checker普通/-O独立log。
- `.work/m5saf-build/{before,after,audit,inputfreeze,checker-evidence}.json`：动态输入/保护/目标负例证据。
- `.work/m5saf-build/receipt/`：正式三包receipt及实际工具raw逐SHA副本；`.work/m5saf-build/junit/`：当次7JVM XML。
- `.work/m5saf-build/handoff.md`及独占 `.planning/llm-asr-76d.6-saf-open-build/`：父消费入口。
