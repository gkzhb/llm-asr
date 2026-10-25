# llm-asr-23p 正式 full：成功，待安全本地提交

本报告是当前完整工程验证证据，**不是提交成功或 APK 交付指令**。父 `p1-steward` 已接受初审生产无阻塞与 fresh 同 sol / fork:false 定点 R1 CLOSED（同模型上下文隔离，非跨家族独立）。唯一验证 writer `ime-build`，测试基点 main `7a3aa5317dec905c6a952f16beff10a58ca9c481` 上的本轮 dirty freeze，不声称测试过尚不存在的新提交对象。

## 唯一执行及准备

```sh
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

真实唯一 formal full exit0，耗时 **2042.620秒**；后台 `b09197bd3` completed0 自动通知已消费。Gradle `184 actionable tasks: 184 executed`，pipeline **Gradle0 / tee0 / DEXscan1**（扫描未命中不兼容诊断）。完整 raw 在 ignored `.work/ime-full/formal-full.stdout`，原命令、stdout/stderr SHA、root/owned确认在 `formal-full.json`；没有重复成功 full、降低原子预算或删 gate。

准备阶段 `b84d4cc2f` exit0：当前10候选、223 build/10 module冻结；1135项生成 class/JUnit/APK/正式报告备份并全批核验后，只清22个明确生成路径。普通 test-results `.bin` 按实际生成路径分类，不笼统当模型或全局放行。缓存与578个 MNN对象未清，不是全 native clean、空缓存或 hermetic 复现。手工准备不读/复制模型权重、音频或签名密钥；既有正式构建的已批准 sample/config/签名输入沿原 gate 消费，不改变权限。

## 实际门槛结果

- 完整 host 原/新迁移 mutants、Java ABI、latch/owner、RecordingRace、模型/导出/ActivityWindow/picker等既有链全部随 full 执行；新 IME四独立生产 mutants 在本次 full 内真实 compile0→精确外部 AssertionError1→恢复compile0/run0，原默认五 mutants及compat亦执行。此前 targeted 普通/`-O`及R1合成故障证据保历史，不当作本次full替身。
- **42 JUnit**：core34、asr-runtime1、test-host7，全部零failure/error/skip；逐名方法及 XML SHA在 [输入/产物清单](full-inputs.json)。14个仪器方法只完成编译/parsed DEX库存，**未发现/执行 Android runner**。
- 真 JNI头、Android arm64 JNI对象编译与实际符号检查通过；native配置/来源/显式对象/ELF16KiB/static-runtime gate通过，578个缓存对象增量链接，最终唯一组合DSO SHA仍为 `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`。当次80 native input SHA与current逐项核同；无数学配置或native源变化，不额外重扫历史全部对象/模型。
- 真实 strict checksum **原0→唯一目标1→恢复0**，HTML目标expected全零/actual匹配；真实AGP默认Lint **green0→NewApi目标red→green1**，工具identity及共享validator负例/恢复执行。fixture两合法可编译mutants五阶段行为链通过。不是网络失败或mock冒红。
- 生产App与runtime Lint均 **Error/Fatal0**，没有baseline/抑制/关闭门槛。App24warnings（UnusedAttribute3、GradleDependency1、ChromeOsAbiSupport1、DataExtractionRules1、UsableSpace2、MissingApplicationIcon1、SetTextI18n15）；runtime17warnings（UsableSpace2、SetTextI18n15，同源重复）。这些是非阻塞发布/国际化/存储建议，保留并defer，不称零warning；host Lint未纳正式任务，identity resolve不冒执行。
- 三个当前签名包的parsed DEX唯一归属、无fixture/JUnit泄漏、权限/组件/资产/无权重/单DSO/签名与标准runner身份检查通过，生产包只有RECORD_AUDIO运行权限，API29/35、arm64与原证书保持。

## 当前三包身份（非设备验收）

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5282885 | `c7d8e3cf52e495480ea8f60f0301091ce9ba9e7b81be8d808e8476ebb8c0be2f` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2633452 | `0ce18f247410efe7483d7ca7bcc78ac8732be21e2da49a8d543b778188a028c9` |
| instrumentation | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 63942 | `ea180917c0910c79bd8a1ffa7f32544aea82d39789ab580600e666bb50a1bda3` |

生产 `org.llmasr.minimal / 0.6-debug / versionCode6 / min29 / target35`。三包均v3签名，原证书SHA256 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`。正式产品receipt与独立三包receipt引用同一product字节SHA，当前223build/10module、native80与实际三包身份核同。版本不变，由当前输入与产物SHA辨别本轮。

## 输入、保护及出口

精确build/module/native/external-tool/补充报告域见 [full-inputs.json](full-inputs.json)。启动freeze/current/两个正式receipt的build map完全一致，module map一致；产品、测试、driver、脚本冻结SHA未漂移。仅本报告、输入清单与status追加为full后supplemental变化，不属于APK构建输入。原旧三包/报告备份保留；本次允许的生成路径正常重建，旧tracked reports/apk未改写，原excluded保持；主index空/HEAD不变。终态owned/root确认，选定50个新IME/default/compat子账本全部closed，正式记录PID-start身份无活匹配，无相关活后台。

下一步仅由父另授只读候选安全审、精确逐路径stage/cached审及正常新本地commit。提交成功并由父核收据前，不宣称APK已交付可验证。

## NOT RUN

Android runner、真实系统输入法history/picker/窗口回调、fcitx5往返、Binder/SAF、AudioRecord/麦克风、native模型计算/真实设备4KiB或16KiB、私人输入或音频、下载/远端CI/release/R8均未运行。previous=true只代表系统请求返回，不能当作已回到fcitx5；host/编译/包静态通过不等于设备验收。旧outerUNKNOWN/signal15历史原样保留，不归因R1或用本次full抹除。
