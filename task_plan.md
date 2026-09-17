# Task Plan: Qwen3-ASR 通用 Android App

## Goal
优先广泛ARM64 Android SoC支持与离线ASR App功能完善；暂缓天玑专属优化，后续IME/API共用引擎。Nix锁定环境、模型不入Git，正确性与权限边界不放宽。

## Current Phase
Phase21 / 用户授权提交当前完整源码并新增AGENTS.md交付顺序；APK可供手动设备验证

## Phases
### Phase 1: 需求与技术事实核验
- [x] 恢复上下文并检查仓库
- [x] 创建持久化规划文件
- [x] 核验官方模型结构、许可、国内下载渠道
- [x] 核验 Android 推理框架；明确硬件来源限制和实机验证项
- **Status:** complete

### Phase 2: 架构与性能方案
- [x] 选择主路线、备选路线和技术验证门槛
- [x] 设计 IME / App / API 共用引擎、权限和生命周期
- [x] 制定内存预算、CPU/GPU 优化、精度与热稳定基准
- [x] 设计 Nix Flake 与可复现模型工具链
- **Status:** complete

### Phase 3: 文档交付与检查
- [x] 写入 docs/implementation-plan.md 和 README.md
- [x] 检查链接、需求覆盖和事实/假设区分
- [x] 总结实施顺序、阻塞点和需要实机验证的项目
- **Status:** complete

## Decisions Made
| Decision | Rationale |
|---|---|
| 先规划再开发 | 用户明确要求首先产出规划 |
| 所有性能数字分为预算/目标/实测 | 未连接目标手机，不虚构基准 |
| 外部资料仅写 findings.md 或来源证据文件 | 防止把不可信来源指令重复注入工作计划 |

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| 详见下方 Retrieval error log | — | 已记录替代路径和来源限制 |

## Boundaries
- 用户已授权自主推进 P0：允许下载官方模型/必要工具链、构建与推送专用测试程序和音频。来源代码先审查再执行；不下载无关模型。
- 不把普通 Qwen3 文本模型支持等同于 Qwen3-ASR 支持。
- 手机限定 100.64.0.3:33317，工作目录 /data/local/tmp/qwen-asr-p0；不访问个人数据/麦克风、不 root、不修改频率或系统设置、不删除工作目录外文件。

### Retrieval error log
| Error | Attempt | Resolution |
|---|---|---|
| MCP search 无匹配工具 | 1 | 改用已知官方 URL 的直接 HTTP 获取 |
| Python 并发获取超过 shell 50s 上限 | 1 | 检查已落盘结果，后续 curl 每 URL 使用硬超时 |
| MediaTek 旧产品页 HTTP 404 | 1 | 换用当前产品路径与厂商设备规格来源 |
| MediaTek 第二产品路径 HTTP 404 | 2 | 改用芯片家族官方页与 iQOO 规格页，未核验项目标记待验证 |
| MediaTek 家族旧页 HTTP 404 | 3 | 停止猜测路径，最终报告来源限制；不阻塞其他已证实规划 |
| www.iqoo.com.cn DNS 解析失败 | 1 | 不重复请求该域名，设备规格列为待实机确认 |
| llama conversion/qwen3a.py HTTP 404 | 1 | 按实际 tree 改查 conversion/qwenvl.py |
| Android 官方文档连接超时 | 1 | 保留规范入口，实施前重核，不假装已读取 |

## Delivery status
- 本轮仅规划已完成，不代表实施里程碑完成。
- 交付 README.md、docs/implementation-plan.md、研究证据索引与持久工作记录。
- 已验证本地链接、22 个证据哈希、4 份 JSON、KV 公式、需求关键词和 Markdown fence；未构建/测试 Android、Flake 或模型。
- 后续第一步：P0 官方参考/导出语义对齐/真机 CPU 原型，需可调试的目标设备。


### Phase 4: 最小 Nix ADB 环境与远程设备连接
- [x] 添加 flake.nix / flake.lock，提供 adb devShell 与 app
- [x] 验证 Nix 环境与 adb version
- [x] 使用用户提供的无线调试配对端口完成配对，连接 100.64.0.3:33317
- [x] 只读确认设备型号/API，记录结果和使用方法
- **Status:** complete

### Phase 4 errors
| Error | Attempt | Resolution |
|---|---|---|
| Nix dynamic attribute x86_64-linux already defined | 1 | 合并 packages.${system} 为单个属性集，保留 default/android-tools 两个输出；重新验证 |
| adb-version check cannot mkdir /homeless-shelter/.android | 1 | 为 Nix check 分配 TMPDIR 下可写 HOME；等待重新验证 |
| 后台命令被 fish 解释，set -e 未实现 Bash fail-fast | 1 | 后续多步任务显式 bash -euc；以逐项日志判断，不以末尾 exit 0 宣称全部成功 |
| 100.64.0.3:5555 Connection refused | 1 | 用户提供实际端口 33317，改连接该指定端口 |
| adb connect 100.64.0.3:33317 返回 failed to connect | 1 | 单次检查该端口 TCP 可达性，询问是否为配对端口/需先配对，不扫描端口 |


### Phase 5 / P0: 可行性与正确性实施
- [x] 设备探针及可审计硬件报告（权限受限项如实记录）
- [x] Nix model/native 环境与小内存主机可用工具链（NDK、host converter、最终Android runtime均构建并运行验证）
- [x] 固定官方 0.6B 模型 revision/hash、MNN commit 和转换依赖（兼容性补丁单独记录）
- [ ] 20 条有来源 golden 样本/官方参考；覆盖音频长度与边界
- [ ] 验证 MNN attention-window/mask/feature/prompt，必要修补并回归
- [x] 设备 CPU 原型完成至少一条完整转写，记录分阶段计时与 PSS（收尾复测EXIT=0；性能目标未达标）
- [x] 独立检查证据与重现步骤，提交 P0 报告（reports/p0/report.md；完整质量未关闭）
- **Status:** in_progress

## P0 resource policy
- 主机仅 7.7GiB RAM、约 4.3GiB available、8.2GiB swap，无已知 CUDA GPU。
- 模型转换/参考推理串行运行，优先 CPU-only Torch、低内存加载，编译并发 2；不要同时加载两套完整模型。
- 长下载/构建记录输出路径，失败须先诊断；同一阻塞三种方法无解后停止该分支并明确报告。

### P0 error log
| Error | Attempt | Resolution |
|---|---|---|
| 官方 raw pyproject 获取 25s 超时 | 1 | 改从 PyPI JSON 获取发行版精确依赖，并计划固定 source archive |
| nix eval 错误 output 路径/超时 | 1 | 改用已缓存的显式 nixpkgs revision metadata，成功取得 store path |
| 在 lazy nixpkgs store source 做广泛 find 超时 | 1 | 改用已知 androidenv 具体文件路径，不递归搜索完整 nixpkgs |
| 猜测 MNN transformers/llm/CMakeLists.txt 不存在 | 1 | 使用源码实际 engine/CMakeLists.txt，后续按已知树路径检查 |
| 根 path flake 在模型/源码下载后反复复制 GB 级文件，环境启动 IO 阻塞 | 1 | 新增受控 nix-env.sh 只镜像 flake/lock 后进入环境，停止并替换尚未开始安装的受影响任务 |
| nix parse/py_compile 组合在 IO 饱和时超时 | 1 | 待取消重复 root source 导入，拆分语法验证 |
| Python qwen_asr import SIGILL (exit132) | 1 | 分模块诊断：torch/transformers/onnx通过，qwen_asr失败；检查强制对齐可选依赖 nagisa/dynet，改用明确禁用无关对齐依赖的受控源码 |
| MNN wheel 无法启用 executable stack | 1 | 不降低宿主安全策略；检查 ELF GNU_STACK，使用 patchelf --clear-execstack 修复本地wheel并记录，或改源码构建 |
| patchelf 无 clear-execstack 参数 | 1 | 受控Python只清除MNN wheel PT_GNU_STACK PF_X位，记录前后SHA并验证 |
| 修补后组合import命令超过60s | 1 | 分进程import和faulthandler诊断，不重复无限等待 |
| 公开英文示例并非16kHz，smoke生成器拒绝 | 1 | 增加明确记录的scipy resample_poly单声道16k归一化，保留源文件SHA；不篡改原始来源 |
| 重建patch diff时重复解压大archive超15s | 1 | 不在短shell任务中重复遍历整包，后续用单次流式读取所需文件再生成diff |
| 递归du在NDK解包/模型加载IO压力下超时 | 1 | 停止非必要目录遍历，停止host编译并保留增量缓存，减少并行磁盘负载 |
| 专用native harness经Nix启动/编译超过60s | 1 | 检查无产物/无遗留编译进程，改为后台有界任务，避免短shell杀掉正常准备 |
| native部署时ADB transport消失(device not found) | 1 | 对原授权100.64.0.3:33317显式重连成功，复用已编译产物部署并执行探针 |
| strict harness部署经Nix命令超过65s，无输出 | 1 | 不声称已部署；改有界后台任务并逐阶段记录，保留已编译产物 |
| strict harness后台部署240s无输出超时 | 2 | PSI显示模型转换导致memory/IO full~70%；停止高内存全图导出，直接复用Nix已实现adb二进制部署成功 |
| 全模型驻留时再次导出3000帧audio严重换页 | 1 | 分进程转换已通过数值门槛的独立audio ONNX；后续解码器导出跳过audio重计算，避免同驻留 |
| decoder导出最后检查Missing artifact tokenizer.mtok | 1 | 确认实际tokenizer.txt且config指向一致；检查改为读取配置并添加finalize-existing，保留已完成转换避免重复 |
| adb push 1.19GB decoder权重超900s，远端无完整文件 | 1 | 改16MiB块持久化传输，关闭ADB压缩，逐块SHA及合并SHA核验；已验证文件跳过，失败后可续传 |
| 模型部署校验全部成功后采集脚本exit124，仅BEGIN | 1 | 取回远端结果确认status1、完整转写；疑似后台watchdog/管道收尾阻塞，去掉独立长sleep watchdog并关闭后台stdin后复测 |
| 首轮非中文case语言参数可能被模型配置覆盖 | 1 | 在createLLM合并配置后set_config语言，保存effective config并定向复测，不改模型输出来匹配参考 |
| native raw与官方parsed text比较口径不同 | 1 | 提取锁定官方纯文本parser统一重评分并保留raw；剩余内容差异继续前端/精度隔离测试 |
| feature_gate编译VARP不支持operator! | 1 | 改为MNN支持的VARP == nullptr，与现有上游代码一致；重新编译后才进行特征比较 |
| 真实音频frontend数值门槛5/5失败（shape一致） | 1 | 单变量核验Hann periodic默认差异，保留失败报告，不放宽容差或直接归因转写差异 |
| native position断言decode始终比expected少2 | 1 | ASR-only将音频start/end计入mPositionIds长度；原红测试及trace备份，重跑位置与文本回归 |
| 多块edit错误引用同一调用新插入文本 | 1 | 合并该区域为单个原文替换；随后成功，未产生部分改动 |

## P0 checkpoint disposition
- 最终补丁版本手机EXIT=0，文本匹配；host20/20工程回归通过。
- 未勾选golden/完整数值语义项：缺独立标注覆盖、严格frontend门槛仍失败、仅eager参考。不得声明完整P0质量通过。
- 最新报告 reports/p0/report.md，后续APK/优化按报告优先级开展。

### Phase 6: 最小可用 APK（用户要求继续）
- [x] 固定 Android SDK/JDK 构建环境，确定不嵌入大权重的最小应用边界
- [x] 实现 Java UI + JNI CPU 转写，模型导入、公开示例与 WAV 文件输入，严格错误处理
- [x] 构建并验证签名/ABI/权限/包内容，生成 APK 与可重现脚本
- [ ] 若原授权设备可用，安装并用公开示例验证普通 APK 进程；不自动访问麦克风或个人文件（blocked：原ADB端口拒绝连接）
- [x] 更新使用说明、测试报告和明确未实现功能
- **Status:** in_progress

## Current delivery objective
本轮生成能实际调用已验证 MNN 模型的最小 debug APK；不把 P0 未关闭质量项或完整 App/IME/API 产品功能视为完成。模型不打包入 APK，不使用网络权限；只读取用户选择的文件/目录或项目部署的公开测试数据。设备安装范围新增本项目包和专用 app 数据，不操作其他应用。

### APK error log
| Error | Attempt | Resolution |
|---|---|---|
| 探索时猜测 deployment-manifest 路径不存在/grep未命中 | 1 | 实际清单 reports/p0/mnn-model-manifest.json；改按已知路径读取 |
| nix-env edit 匹配两处被原子拒绝 | 1 | 明确对两处相同 shell 枚举做字符串替换；无部分更改 |
| APK工具链任务 b7d3d175f Permission denied | 1 | 既有 nix-env.sh 未设 executable；补 chmod +x，后续显式 bash 调用 |
| 原设备 ADB connect Connection refused | 1 | devices为空，对原授权地址显式重连一次失败；不扫描端口，构建继续，实机安装需用户恢复无线调试 |
| 独立审查期望output文件不存在 | 1 | workflow结果已完成但目标未落盘；从异步任务持久artifact恢复完整报告 |
| 独立审查B1跨Activity文件竞争/F3旧结果残留 | 1 | 增加全进程CAS所有权覆盖整个事务，UI弱引用重连，UUID独立WAV和pending/terminal报告；待编译验证 |
| 独立审查F2双DSO静态C++运行时风险 | 1 | 改为最终P0原对象+JNI单DSO链接，逐对象SHA归档；新链接产物不宣称继承原实机身份 |
| Nix cache下载HTTP/2 framing transient error | 1 | Nix执行带offset续传；未人工重复相同全量下载，工具链任务仍运行 |
| 直接调用尚在实现中的JDK报libjli.so缺失 | 1 | 不复用未完成store路径，不设置临时LD_LIBRARY_PATH掩盖；等待Nix环境完整实现再测试 |
| APK javac LambdaMetafactory.metafactory 缺失(exit3) | 1 | 最小单行Lambda用原bootclasspath参数复现exit3；改--release 8 + Android classpath后最小例及全部Activity编译通过，交由d8去糖 |
| APK末尾包检查误用旧aapt2字段sdkVersion | 1 | 实际badging为minSdkVersion:'29'且target35正确；修正字段名，保留API29断言，不放宽门槛；现有签名APK全量静态包检查通过 |

### Phase 7: 首版代码检查点提交（用户明确授权）
- [x] 记录用户骁龙实机手动验证，区分用户反馈与自动采集证据
- [x] 加强模型/构建产物/密钥忽略规则，审计暂存文件及大小
- [x] 运行轻量检查并创建本地Git提交，核验提交不含模型文件
- **Status:** complete
- 不推送远端，不删除本地模型或APK，不修改签名身份。
- Git初始状态为unborn main（git log提示尚无提交），所有当前源码未跟踪；没有已有暂存用户变更。

### Phase 8: 通用SoC与App功能优先（替代旧天玑优化优先级）
- [x] 修订路线图：Android29+/arm64 CPU跨厂商基线；厂商GPU/NPU/绑核推迟，性能目标按设备分层
- [x] 第一增量：用户主动授权前台录音、停止转写/取消、30秒硬上限、失去前台取消采集（实现/编译通过，硬件行为待实测）
- [x] 纯Java PCM/状态边界测试，APK权限精确白名单、构建/签名与独立代码审查（32+20 host checks，R1/R2/R3复核关闭）
- [x] 交付新版本地开发APK与手动验收清单，保留0.1已验证检查点；录音硬件验收明确未实测
- **Status:** complete（本轮实现/构建/复核交付；真实设备录音验收仍pending）

## Updated product direction
先完善通用Android离线ASR App，再IME/API；首轮仍Android10+/arm64-v8a，不承诺32位/x86/所有内存配置。暂缓天玑专属调优、QNN/APU/GPU与CPU绑核；复用已验证CPU数学路径和固定模型。
允许实现仅由用户点击并授权的麦克风功能；代理不自动采集实机麦克风、不读取私人音频。模型/录音/构建产物/密钥不入Git。本次不自动commit/push。

### Recording review error log
| Finding | Attempt | Resolution |
|---|---|---|
| R1 check/start跨线程竞态 | 1 | 用同一session同步gate线性化start与cancel，取消胜出时零start调用；释放由worker负责且不承诺onPause同步硬件释放 |
| R2 capture→inference漏取消 | 1 | 明确captureReleased后同步tryCommitInference，cancel/commit同gate；commit胜出后拒绝取消，不假装native已中止 |
| R3 Back两次volatile读导致NPE | 1 | 单次快照与cancel返回值，按实际接受结果展示UI |


### Phase 9: 0.2检查点与0.3模型状态/结果管理
- [x] 记录用户0.2手动验收，提交前排除模型/产物/凭据并运行轻量检查
- [x] 创建0.2本地提交a4c6a65（不push），提交树无模型/产物/凭据
- [x] 0.3第一增量：结果编辑/分享/SAF导出/清除，模型状态/存储提示，受限临时文件清理
- [x] 单元测试93项、签名APK构建与独立复核（F1/F2/N1关闭）；交付用户手动验收，设备结果仍pending
- **Status:** in_progress
- 用户授权本次0.2提交；后续未验收0.3不自动提交。模型删除/历史数据库/下载/长音频留后续增量，不同时堆叠。

### 0.3 review follow-ups
- F1确认：startup二次cleanup使计数丢失、idle仍显示执行中。改为共同preflight计数交给startup终态，不移出事务所有权。
- F2：分享/SAF云provider隐私提示补App与文档，无新增网络权限。
- F3：扩展UUID report part/编辑文件/嵌套model/写入异常测试；pending导出增加epoch失效与请求身份，clear前旧快照不能在clear后新开始写入。
- N1后续复核确认源码允许export begin跨clear捕获新epoch旧文本；修复为begin持有与clear相同CAS owner，读取文本置于owner之后，文件选择器等待不持owner；补清除中点latch回归，不只检查按钮disabled。

### Phase 10: 原0.4模型导入与管理（已由Phase15 / 0.5详细规划接替，未实施）
- [ ] 提取固定模型文件检查/复制/删除helper及取消状态，按已完成文件续传（不是单文件字节续传）
- [ ] UI进度、取消导入/校验、确认删除内部模型；所有读写在同一任务owner，取消与发布/完成有明确边界
- [ ] host测试、构建与独立审查；保留0.3，交付设备验收；不自动commit/push
- **Status:** in_progress
- 外部SAF provider可能阻塞read/query，取消不承诺即时；不跨线程释放provider流，待当前IO返回后清理。UI不得提前解锁。


### Phase 11: 当前代码复杂度审计与下一步规划（本轮）
- [x] 恢复规划与session-catchup，核对已有未提交差异；不覆盖或提交
- [x] 核对路线图、当前源码及测试，区分0.4计划与实际实现
- [x] 检查高风险复杂度与测试缺口，记录文件/行号及可验证的重构边界
- [x] 更新后续阶段、验收顺序并向用户报告
- **Status:** complete
- 本轮仅审计/规划与轻量验证，不改产品源码、不构建大模型、不访问设备/麦克风、不commit/push。

### Phase 11 inspection errors
| Error | Attempt | Resolution |
|---|---|---|
| wc假定android/app/jni存在，实际不存在 | 1 | 改按仓库native目录定位JNI，不继续猜路径；不影响Java行数结果 |
| 首次恢复合并输出超50KB | 1 | 分拆读取完整plan及日志尾部，避免把截断输出当已完整恢复 |
| 独立审查指定output路径未生成，read返回ENOENT | 1 | 通过workflow status返回的持久artifact目录恢复完整结果；不重复读取缺失路径 |


### Phase 12: 行为保持的结构提取（用户授权实施）
- [x] 补当前任务/报告/模型操作特征测试，明确既有可观察语义
- [x] 提取TaskCoordinator和请求上下文；封装同一owner，不改变录音start/cancel/commit契约
- [x] 提取ModelRepository与SAF适配层，注入文件/流失败；维持固定清单、大小/SHA、原子发布
- [x] 独立窄修复模型part残片低存储恢复，先回收受限残片再检查空间；新增生产代码顺序变异回归
- [x] 原93项host回归+新增生产组件测试（245 checks）、APK构建与生产源码独立复核
- **Status:** complete（源码/构建/独立复核；真实设备回归仍pending）
- 然后进入Phase10的0.4功能；ResultStore与InferenceResult按风险分独立改动。详见docs/code-complexity-review.md。


## Phase12 execution boundary
- 本轮授权重构源码与测试、构建验收；不自动commit/push，不访问设备，不新增0.4取消/删除/服务/驻留引擎/权限。
- 第一增量提取任务协调器/请求上下文、应用级操作层、模型仓库/SAF薄适配；JNI桥名称保留，录音gate/导出epoch不破坏。
- 保留现有模型维护清空正文/报告策略，避免结构改造夹带产品语义变更；该策略后续独立调整。
- 固定清单SHA/大小/原子发布/最终验证不放宽；仅修复已确认part恢复次序，避免把结果清理变成模型递归删除。
- 一个writer修改源码；主会话写规划和验收记录。源码冻结后独立read-only review，集中修复。

### Phase12 implementation findings/errors
| Finding | Attempt | Resolution |
|---|---|---|
| 初稿存在Activity闭包/worker更新UI/busy状态/终态丢失/重复cleanup回归，报告夸大纯Java与红绿覆盖 | 1 | 父审拒绝构建，要求writer按具体源码问题集中修正并用生产编排测试验证；不得继续使用模拟旧流程称真实红绿 |

- Phase12最终证据：reports/apk/result.json/status.md，独立review与disposition见reports/review；b4b1d2d31 exit0、47项输入指纹、native DSO与旧包相同。
- review非阻塞测试命名/覆盖误报已由父修正；SAF/真实磁盘故障未全部覆盖，不宣称245即完整集成测试。


### Phase 13: 首版离线语音输入法（用户改为最高优先级）
- [x] 记录重构版用户手动验收，调整路线与实现边界；保留未提交代码及旧APK
- [x] 实现InputMethodService注册/手动启用与权限引导、语音键盘录音/停止/预览/确认提交/切换输入法
- [x] 共享全进程推理owner；输入目标会话身份、密码/敏感字段保护、隐藏/失焦/重启输入失效；IME正文不写App全局结果/报告
- [x] 生产session/编排回归、APK组件与权限检查、完整构建、独立只读审查及修复
- [x] 文档与APK交付，明确真实Android输入框/麦克风测试待用户验收
- **Status:** complete（实现/构建/源码复核交付，真实IME设备测试pending）
- 首版非完整拼音键盘、非流式、无后台监听/自动提交/模型驻留/API；不修改native数学，不新增网络或广泛存储权限，不自动启用IME、不访问设备/私人数据、不commit/push。
- 原生推理开始后不可硬中断；隐藏/换输入框使结果作废，推理实际结束前不释放共享owner。录音取消沿用gate与worker清理，不承诺同步硬件释放。

### Phase13 errors / findings
- 初稿父审拒绝：native链路缺失/格式崩溃/生命周期与隐私缺口，停止使用初稿自述编译作为功能证据；父接管修复并重写针对生产流程测试。
- 读取默认.work/ime输出ENOENT：报告实际在.pi-subagents/artifacts/outputs/918e0dd5/.work/ime-implementation.md，使用artifact定位，不重复错误路径。
- IME完整构建bff361037 exit1：Java334/单DSO/资源/DEX/签名均已执行，末尾check-minimal-apk.py按4空格匹配service得到[]。实际aapt2树service为10空格，exported输出true而非0xffffffff；需修强制结构解析而不删除检查。独立审查进行中，先保留冻结源码与失败日志，待审查后合并修复。

### Phase14: 输入法不透明背景修复
- [x] 确认输入法根布局无显式背景，用户反馈文字难辨
- [x] 设置不透明浅色面板与一致浅色控件/文字；不变更会话/推理行为
- [x] 完整重建与回归、更新APK身份交付；视觉实机效果待用户确认
- **Status:** complete（构建交付，用户反馈验证无问题；未提供逐项设备日志）

### Phase15: 模型管理独立页面详细规划（仅文档）
- [x] 恢复当前规划与用户最新需求：模型管理必须有独立UI页面
- [x] 核对现有模型仓库/任务/页面边界，细化功能、交互、状态与安全契约
- [x] 新建独立功能规划文件，拆分实施阶段与可验证验收项
- [x] 核查文档一致性/链接，更新总路线，交付用户确认
- **Status:** complete（仅规划文档，待用户确认实施）
- 本轮不实施生产代码、不构建、不访问设备、不commit/push。新的0.5规划替代Phase10旧的模型管理实施描述，历史保留。
- Phase15恢复日志：合并历史文件读取触发50KB截断，已补读缺失progress段；不将截断输出视为完整读取。catchup无额外恢复内容。

### 交接恢复读取记录
- D3pb0j恢复时首次合并读取超50KB：已改用分文件有界读取补齐，不把截断输出当作完整恢复；不改变Phase15仅规划完成状态。


### Phase16: 0.5 独立模型管理实施（用户授权“开始”）
- [x] M1：独立Activity、主页/IME入口、统一模型状态/空间/详情、迁移原导入校验且保护转写结果
- [x] M2：阶段进度、协作取消、原子发布/成功仲裁、epoch、文件复用、前台生命周期与故障恢复
- [x] M3：确认与epoch复核、受限白名单删除、部分失败、重试与空间刷新
- [x] M4：生产host回归、Android编译/完整APK、独立只读审查、指纹绑定和文档交付
- **Status:** complete（实现/host/完整APK/独立源码复核交付，真实设备验收pending）
- 实施规格docs/model-management-plan.md；固定MNN单模型、前台管理、旋转保留/离开或锁屏请求取消，文件级恢复、仅删除内部副本。
- 一个writer负责生产源码/测试/构建输入脚本；父会话维护规划与交付文档/报告并验证，不并行修改writer源码。
- 不修改JNI/MNN数学、录音gate、IME提交隔离；不新增网络/存储权限/后台服务/驻留提速；不访问设备、私人数据或麦克风，不commit/push。
- 原0.4背景修复APK及报告保留在dist/pre-model-management-0.4和reports/apk/pre-model-management-0.4；备份不覆盖既有文件。

### Phase16 errors
| Error | Attempt | Resolution |
|---|---|---|
| workflow0789736f在解析时SyntaxError，单引号task跨实际换行 | 1 | 尚未启动源码writer；改用JS模板字面量，保留失败记录，不当作实现失败或测试结果 |
| writer ada832dd/173038b7 在1800000ms上限超时，输出停在C1测试失败定位 | 2 | 已终止，无正常实现报告；先恢复实际代码/测试证据，拆成纯Java修复与后续Android接线两个较小writer任务，不宣称M1~M3完成 |
- Phase16修正实施节奏：超时初稿不作为M1完成；先完成纯Java核心仲裁/测试修复，再接独立Activity/共享readiness/导航与生命周期，最后构建/独立审查。
- 父复跑错误：PartRecoveryTest isolated javac未包含ModelManagementState依赖（check7），交由核心writer修复依赖并恢复真实行为变异红绿证据；新增测试挂起要求有界latch/join/teardown。
- 修复writer工具错误：缺Java PATH exit127改显式既有JDK；SDK广泛find超时改既有SDK绝对路径；均非功能红证据，已解决。
- E1/E2末次小修：首个edit因精确oldText拼写不匹配原子拒绝，无部分修改；改为真实原文后成功。先运行生产回归PUBLICATION_RED_EXIT=1（明确发布状态断言），零空间planning已通过，再加发布后历史状态通知。


### Phase16 final delivery
- APK0.5-debug/code5，2442088 bytes，SHA6e4fbf7a4cf07262912019dc667258123a32f95931b13bb1c046cf6e77471073。
- 最终b0453981b完整exit0/APK_READY，945数值checks+8review groups/provider/3mutants/checker fixtures；78 build inputs/69 frozen inputs及APK报告绑定一致。
- 原审查问题及末次E1/E2均独立源码关闭，无剩余阻塞；review不执行测试，父完整构建提供测试证据。使用说明docs/model-management.md，验收边界docs/model-management-validation.md。
- 未改最后复核之后的生产源码/测试/构建输入，不再重复重建。未commit/push/访问设备或麦克风；设备验收和P0已有数值/性能限制保留。

## Phase17：模型导入私有根路径兼容修复
- [x] 记录反馈，定位并主机复现相同路径拒绝错误；保留旧APK与报告。
- [x] 限定可信filesDir父目录的worker延迟解析，保持模型叶/文件符号链接拒绝，新增回归。
- [x] 全量host和APK构建/签名/包检查，79输入指纹及native一致核验。
- [x] 独立窄审无阻塞，保留两项LOW测试加强建议；报告统一并交付手动验证包。
- [ ] 用户真实Android选择目录/导入验证（未访问设备）。

## Phase18：运行日志页面与导出
- [x] 用户确认路径修复无问题；记录日志需求和隐私/边界默认值，备份已验收APK。
- [x] 单writer分阶段实现有界持久日志、App/IME真实JNI阶段事件、独立日志页面及SAF导出。
- [x] 主机回归及Android Java/native编译通过；普通日志故障隔离，Error后logger存活hardening明确defer。
- [x] 独立只读双审完成，无普通生产路径阻塞，4项非阻塞发现明确defer；完整构建与身份核验通过。
- [x] 交付APK及手机验证步骤；真实设备验收仍待用户确认。

### Phase18 defaults / boundaries
- 墙钟时间戳含日期、毫秒和时区；耗时使用单调时钟，任务ID与App/IME来源关联。
- 日志仅固定事件/安全错误码/耗时，不记录转写文本、音频、provider URI/文件路径或原始异常堆栈。
- 有界本地持久保存和页面快照，独立于模型/转写结果；不使用系统logcat读取权限，不联网。
- 真实native load/response边界回调，不从最终耗时倒推或补造加载完成时间；回调不可改变推理/异常结果。
- UTF-8日志快照通过系统另存为导出，取消不写、IO不阻塞主线程，provider失败脱敏；导出方可能联网须提示。
- 不访问设备/麦克风/私人数据，不commit/push，不扩展模型驻留或推理策略。


## Phase19：Android 架构边界重构（用户授权规划后执行）
- [x] 完整恢复三份 planning 文件、catchup（无输出）、核对原有未提交成果；建立 docs/android-refactor-plan.md。
- [x] R0：100输入快照与旧APK备份，全量 host/Android javac 基线通过。
- [x] R1：ModelReports提取、架构负例、host/Android编译通过；日志Error恢复红绿已完成，待最终独立复核。
- [x] R2：生产AppRequestPolicy/Lifecycle分离，直接coordinator观察，父全量host/Android编译通过。
- [x] R3：独立JniNativeTranscription、Graph共享adapter、AppReportWriter；host/Android与JNI object符号验证通过，父准备完整构建。
- [x] R4：日志Error恢复红绿、全回归/完整APK/双审+窄复核/111构建与112冻结输入核对通过。
- **Status:** complete（本轮R0–R4工程交付；设备回归pending）
- R5/R6 是后续独立批次，不把TXT并发语义改变和全量包迁移混入本次。
- 不访问设备/麦克风/私有数据、不commit/push、不改模型/MNN数学/录音gate/权限；单源码writer。

### Phase19 errors
- 前一轮审计广泛 find .. 查 AGENTS 超10秒：本轮只查项目及已知祖先路径，不再广泛递归。
- R1 writer ed473135/7390ecb4 达20分钟上限，无最终报告；父确认无遗留javac/test进程。初稿日志正常返回在finally才释放publisher存在丢失通知窗口；新增测试有CME和无限重入。父接管窄修复，不将子工具管道exit0当测试通过。
- 恢复时glob读取无日志/架构文件返回exit1：按实际diff确认writer尚未完成这些产物，不重复读取不存在路径。
- R1父全量回归失败于InferenceAdapterTest no request success：吞观测Error改变已定义遥测语义。已改为Error传播+异常恢复，保留RuntimeException隔离，不放宽原adapter断言。
- R2首次fixture初始化语法/计数API编译失败、第三次lexical guard过窄拒绝postDelayed；writer修复，父完整host与Android编译复跑通过。
- R4独立双审完成：无新增生产阻塞，发现TaskCoordinatorTest回调内断言被异常隔离吞掉（测试验收阻塞）；父改为回调记录/外部断言。LOW完整构建缺强制生成JNI头检查，父将现有object检查加入全构建前置。正常publisher交接定向测试/RuntimeLogWorker Error恢复仍明确defer，不扩大修复范围。

### Phase19 最终交付
- 0.6-debug/code6，2466664 bytes，SHA0b891db946992cb649d33b4ec8bf0347738070e4593c306a0558d7f68460e43c；完整日志.work/refactor-phase19-final/build-final.log。
- 41改动输入全部绑定，111 build-input/112 frozen核对；578 MNN对象与R0不变。
- 双审c3e1535b/1732b7b8无新增生产阻塞，测试/构建两个接受项父修后708e5aac窄复核关闭。
- 规划docs/android-refactor-plan.md，验收docs/android-refactor-validation.md，APK状态reports/apk/status.md。未commit/push/设备操作；R5/R6和设备验收未完成，非整个路线完成。


## Phase20：R5/R6 项目优化（用户授权继续）
- [x] 基线：111小文件、APK/报告和SHA已保存；host/Android编译通过。
- [x] R5：独立有界TXT导出worker/owner；ResultState/revision、页面command/render分离；clear与admit写入线性化和隐私回归。
- [x] R6策略：模型文件结果类型化、controller锁外通知、日志编码归位及有证据的SHA入口整理（不削弱最终验证）。
- [x] R6拆包：先约束依赖方向，再迁移功能包及递归构建/测试/Manifest/JNI/指纹检查。
- [x] 全回归、独立fresh只读复核、集中修复、完整APK/产物身份及文档交付。
- **Status:** complete（实现/构建/源码复核交付；设备回归pending）
- 每次一个源码writer；父负责规划/验收；不改模型数学、录音gate、权限，不访问设备/私人数据/麦克风，不commit/push。
- 未开始写入的票据可撤销，已admit的provider IO不承诺撤回或硬取消；堵塞不得提前释放slot。安全/测试阻塞最多三种方案后报告。

### Phase20 errors
- 恢复历史合并输出超过50KB：按文件/有界段补读，不把截断当恢复完成。

- R5初稿验收拒绝：clear/admit未共享验证、Activity生命周期未接、无导出通知、编辑快照非原子。父接管窄修复；原host通过不代表产品契约满足。

- R6策略writer25分钟超时、缺报告/指定日志；已落typed文件结果/锁外通知/RuntimeLogCodec。父读取实际源码与独立复跑，未经验证不标完成。R5完整报告含两项P2及明确测试覆盖缺口，待集中处置。

- 检索假设tests/*R6*不存在返回exit1：改用源码实际变化与rg定位，无新增测试文件确认；不是产品测试失败。

- 父精准edit替换progress末尾时一度移除publishPlan方法头，立即读取检查发现并恢复，未作为测试证据；后续完整编译验证。

- 迁包child30分钟timeout，未完整交付；父恢复实际62类映射与日志，不将partial末句当完成。首次ps筛选无匹配exit1表示无遗留任务，不是产品错误。


### Phase20 最终交付
- APK0.6-debug/code6，2470760 bytes，SHA a03c0876ba0f73ccec6532a0eb91c0416a1edb2488b5e57f19f7ffb58b4947f1。
- 119构建输入/126冻结输入一致；实际APK报告绑定、生成JNI头与linked DSO符号通过；578 MNN对象与baseline一致，录音/模型逻辑迁包保持。
- 双审193f09cb/9df12f23及最终窄审875e523f完成，接受的5项修复独立关闭；最终完整构建b19042db8 exit0/APK_READY且fresh classpath行为mutants通过。
- 62Java按功能拆包，原Android/IME注册入口与权限不变；重复SHA入口因取消/限额/异常契约不同明确不强行合并。
- 不访问设备/麦克风/私人数据、未commit/stage/push。SAF/Handler/Android图与JNI运行仍pending，不声称推理性能提升。

## Phase21：用户授权提交当前代码与协作规则
- [x] 恢复当前交付状态，确认index初始为空、上次提交仍为0.2；此次提交当前已完成的累计App/IME/模型管理/日志/R5-R6成果。
- [x] 新建标准大小写`AGENTS.md`，规定每轮实现/测试/必要审查完成后先本地提交，再告知用户验证APK；不自动push，不提交模型/产物/凭据。
- [x] 复跑host/包检查、核对最终APK和119构建/126冻结输入，审计531个文本文件无模型/产物/凭据；提交当前完整检查点（实际提交身份以Git日志为准）。
- **Status:** complete（验证/规则/本地提交；未push，设备验收pending）
- 此处为用户新增明确提交授权，取代历史开发阶段的“不commit”边界；历史记录保留。不变更生产源码或APK，不访问设备。

### Phase21 检查说明
- 全量cached diff-check发现5份历史readelf原始报告各2处工具自带尾随空格；保留原证据字节，其余源码/测试/文档/当前报告的cached diff-check通过。不修改已绑定历史报告凑格式green。
