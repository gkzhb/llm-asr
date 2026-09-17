# Phase19 R3 / Android 接线与构建测试边界独立审查

## 结论

**在指定冻结输入的只读源码审查范围内，未发现需要阻塞本轮 R3 的新增功能回归。发现 1 项低优先级构建防回归缺口。**

- 基线：`.work/refactor-phase19-baseline/inputs`
- 冻结清单：`.work/refactor-phase19-final/frozen.json`
- 审查开始及结束均核对了 **112 个冻结输入的 SHA-256，全部匹配**。
- 未修改文件、未执行 tests、编译、构建或设备命令；全构建验证留给父会话。
- 以下“保持一致”“接线正确”均为源码审查结论，**不等于 JNI、Android 生命周期或报告落盘已通过运行时验证**。

## 一、具体发现

### LOW-1：完整 APK 构建尚未直接纳入 Java 派生的 C++ JNI 签名检查

**位置**

- `scripts/build-minimal-apk.sh:43,51–52`
- `scripts/link-apk-native.py:31–35`
- `scripts/check-jni-symbols.py:18–31,36–50`
- `scripts/compile-jni-object.sh:10–18`

**事实与影响**

完整构建确实检查了：

1. 全部生产 `.class` 中 native 声明的归属；
2. Java native 方法的访问形式、参数及返回值 descriptor；
3. 链接后 DSO 的真实动态 JNI 导出名，拒绝缺失、旧类名和额外导出。

但完整构建的 `link-apk-native.py` 编译 JNI 时**没有强制包含 `javac -h` 生成的头文件**。ELF 的短 JNI 导出名不携带参数类型，因此单凭 `javap` descriptor 与 `nm` 名称集合，不能证明 C++ 实现签名与 Java 参数列表一致。

真正由 Java 派生声明约束 C++ 签名的是单独的 `compile-jni-object.sh`，它使用：

```text
javac -h ...
clang++ ... -include org_llmasr_minimal_JniNativeTranscription.h
```

**定性**

这是本轮新增构建防线的覆盖缺口，**不是当前已确认的 ABI 错误**。当前冻结 Java/C++ 签名经人工核对一致；C++ 相对基线仅有四处 JNI 类名前缀替换，而且已有源码 SHA 守卫限制其变化，因此不判阻塞。

**建议**

后续让完整构建直接采用生成头文件检查，或把独立 object 检查设为明确的前置步骤。当前验收记录应分别列出“Java descriptor / DSO 导出名检查”与“Java 派生头文件 / C++ 编译检查”，避免合称为完整运行时 ABI 验证。

## 二、R3 逐项审查结果

### 1. JNI 符号、ABI、懒加载：未见新增回归

证据：

- `android/app/src/org/llmasr/minimal/JniNativeTranscription.java:6–13`
- `android/app/src/org/llmasr/minimal/InferenceAdapter.java:17–35`
- `native/apk/asr_jni.cpp:71–85`

两个 Java native 方法仍为 `private static`，四个字符串参数、可选 listener 和 `byte[]` 返回值保持；C++ 对应保留 `JNIEnv*`、`jclass`、四个 `jstring` 及 listener 的 `jobject`。空 listener 路径仍走无 listener 包装函数，包装函数再传空 listener 转发。

对基线的完整 native diff 只有四处：

```text
Java_org_llmasr_minimal_MainActivity_
→ Java_org_llmasr_minimal_JniNativeTranscription_
```

未发现协议、phase code、`onPhase(I)V`、pending exception 处理、mutex、每请求引擎释放、CPU/线程数/greedy/token 上限改变。基线 native SHA 与 `tests/fixtures/jni-pre-r3.sha256` 一致。

`JniNativeTranscription` 无静态加载块；Graph 中 `System.loadLibrary` 是注入的 lambda。真正调用顺序仍是：

```text
创建本请求 listener
→ requireReady()
→ loadLibrary()
→ native 调用
→ 协议解析
```

这里的懒加载是“构造/页面初始化不加载”，**不是 adapter 自行保证只调用一次 loadLibrary**；每次请求仍调用加载端口，与既有语义一致。

### 2. Graph 共享 adapter、ImeBackend 依赖：接线成立

证据：

- `AppGraph.java:37–56,111–130`
- `ImeBackend.java:9–27`
- `AsrOperation.java:35–49`
- `AsrImeService.java:35–38`
- `OperationContext.java:21–27`

Graph 只构造一个 `InferenceAdapter`，同一引用传入 `AsrOperation` 与 `ImeBackend`。共享 adapter 的请求 listener 是方法局部变量，不保存上一请求的 APP/IME attribution。

`ImeBackend` 已不持有整个 Graph，也不引用 MainActivity；依赖缩小为 context、repository、inference。生产 context 包装器将传入对象归一化为 application context，未见通过这条新装配链保留 Activity 或 Service。

IME 仍只返回 display、管理临时 WAV，不接 AppReportWriter；未见本轮使 IME 正文进入 App 最后报告的路径。

### 3. AppReportWriter：原报告、异常、IO 与发布顺序保持

证据：

- `AppReportWriter.java:23–119`
- `AsrOperation.java:140–157`
- `AppRequestPolicy.java:25–54`
- `RequestRunner.java:64–76`
- 基线 `AsrOperation.java:172–284`

逐段对照后，未发现报告字段、值来源或格式变化，包括成功/状态/kind、source/language、正文/raw、耗时/RTF、token、`truncated=false`、UID/PID、两个摘要、request ID 和时间戳。

保持了：

- UTF-8、`toString(2)`；
- 相同 `.part` 命名及 `renameTo(last-result.json)`；
- 同样的摘要读取上限、流关闭路径；
- JSON 序列化错误包装、rename 失败文案；
- 失败报告使用 `String.valueOf(cause)`；
- pending / complete / failed / cancelled 的既有内容。

成功发布顺序仍为：

```text
native 返回并解析
→ 完整成功报告写入
→ inferenceReported=true
→ 发布正文
→ 发布完成状态
→ cleanup / finalization
→ owner release
```

因此报告写入失败不会提前标记 richer terminal 已完成或发布成功正文。`AppRequestPolicy` 保留 terminal suppression、取消/失败持久化失败提示及 cleanup 失败追加文案。

**既有边界未被美化：** `.part + rename` 并不提供新增的 fsync/断电持久性保证；本轮也没有改变流关闭失败时的既有处理。摘要的 1 MiB 上限不是当前合法 WAV 的新增问题：`WaveInput.java:42–48` 将规范化音频限制为最多 960,000 字节 PCM 加 WAV 头。

### 4. Main / IME / 模型页直接订阅和弱生命周期：未见新增 UI 保留链

证据：

- `MainActivity.java:120–154`
- `AsrImeService.java:121–124,158–166`
- `ModelManagementActivity.java:201–218,239–259`
- `TaskCoordinator.java:35–36,74–82`

Main 和模型页在 resume/pause 配对注册、移除 coordinator listener；IME 在 create/destroy 配对注册、移除。三者均为静态 listener 类、弱 UI 引用、经主线程 Handler 分发，并在执行时读取当前状态。

Main 保留 foreground/destroyed/finishing 检查；模型页保留 visible 检查与合并刷新；IME 保留 alive 检查并新增 destroy 时移除待执行回调。owner release 通知发生在 `running=false` 后，不再依赖 App 正文通知“顺带”刷新忙闲状态。

移除 listener 不能撤回已取得的 CopyOnWrite 迭代快照，detach 后仍可能遇到在途通知；当前弱引用及执行时生命周期检查提供后续保护。**本轮没有观察到因此新增的功能错误，但未通过真实 Android 调度验证所有交错。**

## 三、测试与构建防线是否实质有效

**结论：不是靠删除旧断言使重构通过。**

- `tests/InferenceAdapterTest.java:104–120` 新增真实 JNI bridge 的无加载构造检查、同一 adapter 连续 APP/IME 请求及独立 listener/attribution 检查；旧协议、故障身份、native 顺序与设置断言仍保留。
- `tests/RequestRunnerTest.java` 将测试端口迁至生产 `AppRequestPolicy`，保留既有行为检查，并增加取消持久化失败、已报告终态抑制、maintenance 隔离、LinkageError 加 cleanup 失败组合。
- `tests/TaskCoordinatorTest.java:99–134` 增加直接订阅、去重、移除、坏 observer 隔离及 fatal 路径释放检查。
- `tests/model_android_source_test.py:27–37` 把旧的各调用方 readiness/JNI 接线断言迁到 Graph；`tests/architecture_boundary_test.py:55–84` 补上共享实例、注入、独立 JNI 类与成功发布顺序约束。
- `tests/app_report_source_test.py:28–51` 对报告方法体做固定 SHA 比较，检查端口映射，并有八类负向变异；native 内容则在反向替换新类名前缀后与基线 SHA 比较。
- `tests/jni_symbol_checker_test.py:12–21` 有错误 descriptor、声明形状及缺失/旧名/额外导出负向夹具。
- `scripts/test-minimal-apk.sh:58–66` 实际调用这些源契约及现有 mutation/package-binding 测试。
- `scripts/build-minimal-apk.sh:14,51–54,84–89` 将 host 测试、真实 DSO 符号检查、DEX 及新增 fixture/script 输入指纹纳入构建，原包检查未被移除。

**证据边界：**词法守卫不是 Java AST；报告 body hash 不是 Android JSONObject/文件系统运行测试；synthetic symbol fixtures 不是执行 JNI。独立 object 编译不链接或运行，完整包构建也不证明手机端 native 调用成功。

## 四、既有 defer 与剩余风险

以下不应归为本轮新增回归，也不应在验收中宣称解决：

1. **TXT 导出仍共享 ASR owner。** `AsrOperation.java:113–120` 的 provider open/write/close 仍可能长期占有公共执行通道。R5 已明确另批处理，见 `docs/android-refactor-plan.md:55–57`。
2. **全量拆包、文案类型化、controller 锁外通知仍为 R6。** 见同文件 `59–61` 行；当前新 listener 不能据此宣称所有通知已移出业务锁。
3. **真实 Android/JNI 行为待验证。** 包括冷启动 IME、进程重建、录音取消、InputConnection 切换、JNI callback 抛错/pending exception、真实报告 IO 故障及设备推理。
4. **源码冻结身份不等于最终 APK 身份。** 本审查未验证父会话正在生成的 APK、构建日志或报告绑定；需父会话将最终产物与冻结输入另行闭环。`check-minimal-apk.py` 也不能单独替代全构建及 JNI 签名检查。

**建议处置：本源码审查可非阻塞通过；保留 LOW-1，并以父会话完整构建结果作为产物层验收条件。**