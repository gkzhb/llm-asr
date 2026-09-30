# M0 基线与交接核查

## 结论
**候选稳定基线已核对，交接未确认，M0 必须 blocked。**本轮不实施 M1/M6 或 Kotlin 后续批次，不关闭原任务。下面的产物是既有 Kotlin 第二批 APK，不是本轮新构建/交付。

## Git 与输入
- 当前 HEAD：`449f01a3d711f72094ca1a14fe8b1c713fbee1ce`，branch `main`，唯一 worktree `/home/zhb/gitrep/llm-asr`。
- 最近产品提交：`2fd75116ca51c478071417a6e6906221113f37b8`。两提交之间只有路线/协作文本；建议以当前 HEAD 作为交接 ref，产品输入对应该 Kotlin 第二批提交。需要用户/原 owner 确认，尚未正式冻结写入。
- `reports/apk/build-input-sha256.json`：127 项，清单 SHA256 `c776034b1ada38e34568db888707c06f5b697bd3dbc976fc9e09f283a257c138`。
- `.work/kotlin-batch2/frozen.json`：133 项，清单 SHA256 `2c845069359cfbf7d3f6c8d1cedd0974ec33e911b9c168c3f919538803020b83`（本地忽略文件，不入 Git）。
- 两集合对当前文件、HEAD Git blob、2fd7511 Git blob 逐项 SHA 全部一致；没有 missing/mismatch。生产源10 Kotlin/52 Java。
- 起始 tracked/index clean，只有两个既有独立分析目录未跟踪。本轮只新增此计划并更新 M0 Beads 导出。

## 既有 APK / native / 签名
| 身份 | 核对结果 |
|---|---|
| APK | `dist/qwen-asr-minimal-debug.apk`；3171318 bytes |
| APK SHA256 | `5de230d55bc81b641682c6be38a13c01fe839ff39e4045475e1df02a2b0a8e26`，实际字节与报告一致 |
| 包 / 版本 | `org.llmasr.minimal` / `0.6-debug` / code6 |
| 平台 / 权限 | min29 / target35 / arm64-v8a / RECORD_AUDIO-only |
| JNI DSO SHA256 | `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`，APK实际字节匹配provenance与批次1 |
| MNN 对象 | 当前578项实际文件 SHA 全部匹配；完整native provenance与批次1一致 |
| debug证书 SHA256 | `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`，来自已绑定APK的既有signature.txt |
| D8报告 | 当前`reports/apk/d8.txt`为0 bytes；这是既有构建诊断，不是本轮重新DEX |

本轮执行 `PYTHONDONTWRITEBYTECODE=1 python3 scripts/check-minimal-apk.py` 通过：APK/6派生报告绑定、单DSO、资产/权限/组件/API、ZIP与runtime class_def。此checker检查已存在签名工具报告，不是独立密码学重验签；本轮未另运行apksigner，不读取密钥。没有安装或设备验收。

## 历史测试与可重建/回退路径
- 历史完整构建：`.work/kotlin-batch2/build.log`，task `bb3c4f1e9`，43新增Java ABI/语义checks、全回归/3mutants、10 Kotlin+52 Java全编译、JNI/DEX/签名/包检查、APK_READY；原owner直接自审`reports/review/kotlin-batch2-self-review.md`，不是独立review。本轮只读取证据，没有重跑这些测试或构建。
- 构建入口：`bash scripts/nix-env.sh apk bash scripts/build-minimal-apk.sh`；会覆盖主APK/报告，**本轮没有执行**。准备与native复现限制见`docs/local-apk-reproduction.md`，M2尚未提供不依赖旧.work的干净原生任务。
- Git保存了产品/脚本/127项输入与补丁；权重/音频/密钥/缓存/二进制不在Git。源码commit不等于无需外部缓存和同一签名密钥即可整包逐字复现。
- 前一检查点 `a23277d1f5191b0c9ca7ec6f76d66aefaf704230`；本地原owner备份`.work/kotlin-batch2/baseline/`，前一APK SHA `f24b4da1f254f17a324b756990408e3f4474c2b09afce94290d06d608562a163`，native相同。
- 回退建议：获准后用独立临时worktree/隔离输出在上述提交复现并核身份；不reset/checkout覆盖共享main，不删除原目录、不自动卸载签名不同旧App。备份/原key是否继续提供需合法owner确认。

## Owner/runtime 与依赖
- M0当前合法assignee/Beads writer：`llm-asr-m0`，session `01a0f34c-beea-7015-b76f-af1b670e3a91`，Herdr `wJ:p1`。仅拥有M0审计范围。
- `llm-asr-ap5`仍assignee `llm-asr-kotlin` / in_progress，目标生产Java=0，原范围排除Gradle/Compose/协程。
- 原Kotlin计划session `01a0f290-8712-7015-b76f-af1852068512`未出现在当前Herdr live列表：runtime和交接状态**未确认**，不是已证明闲置/释放。
- 同项目`wJ:p2` idle的session `01a0f2dc-a56d-71af-a660-6ca7c8819ca4`是只读工具链分析身份线索，不能自动当原owner或让其移交原task。
- 原planning仍写提交批次2，Git已提交；不替原owner修改其记录或抢占。
- M0无blocking前置，parent关系不自动阻塞；M1 `llm-asr-76d.2`、M6 `llm-asr-76d.7`仍被M0阻塞，其他阶段gate保持。dep cycles通过。status/ready统计口径不一致，不据汇总领取。

## 工具与资源边界
现有已验证回退组合为Kotlin2.0.21/JDK17/SDK35/build-tools35.0.0，NDK28.2.13676358；Kotlin与stdlib同版，JVM8、真实default、D8 warning fail closed。这不等于已经确定AGP/Gradle兼容组合。

M0的AGP/Wrapper候选版本尚未完成官方兼容表核查和选择；在交接未知情况下不下载、不实施、不把既有分析建议冒充验证。后续候选先保持当前Kotlin/JDK/SDK/native输入，按官方AGP+Gradle表精确选pin并校验，不同时升级语言/数学/UI。

主机约8GiB：建议后续Gradle单worker、禁止与native大编译/模型推理并跑，native并发最多2；JVM heap/daemon等具体配置应在M1窄授权和实际资源测试后固定，不声称本轮已验证。

## 阻塞与单一下一输入
请用户/合法原owner确认交接契约：
1. 是否批准当前449f01a（产品输入2fd7511）作为交接ref？
2. 原`llm-asr-kotlin`目前对应哪个runtime/session，剩余52Java选择继续、暂停还是隔离？
3. 后续共享产品/构建文件由谁唯一写入，原owner如何确认释放/隔离？建议在M0期间禁止本Agent产品写入；后续若推进M1，明确暂停或隔离Kotlin写入，保留原assignee，不代替其关闭task。

用户本轮仅授权M0，不自动授权M1/M6/设备。得到确认后重新核Git/Beads/runtime，再继续M0的工具候选与完整交接记录；未确认前停在blocked。当前APK设备验收pending，未新增APK交付。

## 用户后续决策（取代上述等待状态）
用户明确暂停ap5，不继续推进，优先基建并开始执行。已保留ap5 assignee并改deferred；原runtime仍不伪造为空闲，当前无其他live working产品writer。当前基线449f01a/产品2fd7511作为基建输入，本session唯一writer。M0官方候选AGP8.6.1/Gradle8.7/JDK17/Kotlin2.0.21（API35）确认，实际验证归M1。M0已关闭，M1另建独占计划；设备仍pending。
