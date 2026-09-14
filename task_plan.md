# Task Plan: Qwen3-ASR Android / iQOO Z1 规划

## Goal
先交付可核验的详细技术规划：Android 离线语音转文字 App、系统输入法、本地推理 API、Qwen3-ASR 模型、天玑 1000+ / 8GB RAM 优化和 Nix Flake 开发环境。本轮不声称已实现应用或达到实机性能指标。

## Current Phase
Phase 7 / 首版代码提交完成 — 骁龙APK手动验证已记录；暂存/提交树安全审计与轻量测试通过；P0完整质量限制继续保留

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
