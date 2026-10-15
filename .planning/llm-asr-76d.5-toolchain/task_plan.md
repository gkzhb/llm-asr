# p1-test 固定 APK 工具闭包准备

## Binding
- Task llm-asr-76d.5；role project-agent/toolchain leaf；owner/唯一Beads writer p1-steward；本计划唯一writer p1-test。
- cwd /home/zhb/gitrep/llm-asr；main；base 7eace17a8ea636db6010f95d2229c28dc2f78467。
- 明确路径 .planning/llm-asr-76d.5-toolchain/ 三文件；工作输出 .work/p1-toolchain/。不改共享active_plan。

## Goal
不改现flake/lock、13审查文本或179构建输入，准备现锁定Nix apk工具闭包，实际版本/路径与严格cache-only验收并保存来源、许可、指纹与失败证据。不开Gradle/Lint/APK，不提交、不再委派、不写Beads。

## Current Phase
Phase 3完成：严格cache-only工具实际smoke exit0，前后原冻结匹配；handoff已写。仅工具bounded outcome完成，工程/M4仍未验收。

## Next Step
停止本leaf全部写入；父核handoff后顺序分派原writer修复review及后续工程验证。

### Phase 1: 恢复与来源/冻结核对
- 已读AGENTS、dependencies skill两参考、planning/project-stewardship skill、父监督三文件、handoff、flake/lock、nix-env、settings/wrapper及metadata配置。
- [x] 保存13/179输入与flake/lock前置SHA；固定nixpkgs官方来源/许可及Nix缓存配置。
- **Status:** complete

### Phase 2: 固定闭包准备
- [x] 独占tiny mirror；Nix锁禁写/禁更新、require-sigs=true、accept-flake-config=false、maxjobs2低并发；缓存签名和固定源hash不绕过。
- [x] 下载失败先诊断；必要本地http://localhost:10881无凭据代理路由（不宣称父已核单独用户代理授权），不换源。
- **Status:** complete

### Phase 3: 工具验收与交接
- [x] 真实java/python/cmake/ninja/aapt2/NDK clang/Kotlin路径版本。PATH python3 3.14.7与显式python312 3.12.14均如实记录。
- [x] 同mirror严格cache-only exit0；offline/substituters空/builders空/maxjobs0，不松门槛。
- [x] 前后原13/179冻结SHA核对，记录Lint缓存只读缺项及原freeze覆盖限制，完整handoff。
- **Status:** complete

## Boundaries
可写仅独占工作/三文件及受控工具缓存/Nix store；不改生产、脚本、metadata、锁、版本、签名、native、原报告；不设备/mic/private/model/remoteCI/stage/commit/push。p1-review已返回不通过，父只允许原writer只读修复方案；本leaf不解除freeze或修产品。任何输入漂移、未知来源/许可或需要扩scope立即停止报告。Nix固定内容与缓存签名不等于漏洞扫描；CVE unknown/not scanned。

## Errors Encountered
- b9377f508 exit1实际在本leaf smoke错误许可路径share/kotlin/LICENSE；现归档真实license/LICENSE.txt与NOTICE.txt，修独占检查后仅cache-only复验。PATH python3被传播依赖遮蔽成3.14.7，另执行固定python312绝对路径，不静默当3.12。
- nix eval flake#inputs.nixpkgs.outPath失败：inputs不是输出属性；改只读getFlake表达式成功定位锁定source，无下载。初次Android repo查询层级错误返回空，改packages子树后成功提取，不将空数据当来源。
- 本leaf不重复父已kill的命令进程，可复用已缓存内容。
- bg bc945986e跨接续后任务表unknown；保存prepare.exit=141，freeze后核exit0，ps无原prepare/nix/构建进程。日志只到NDK安装，非下载hash错误。141通常SIGPIPE，原exec进程替换tee依赖宿主输出pipe，准确触发来源未知；改直接文件重定向、独立attempt2日志并启用普通shell任务surviveReload，非同一在跑任务重复启动。
