# Java → Kotlin 行为保持迁移

## Goal
用户授权核查技术问题，无阻塞则实施。任务 llm-asr-ap5，owner/writer llm-asr-kotlin，session 01a0f290-8712-7015-b76f-af1852068512；cwd /home/zhb/gitrep/llm-asr，main 基线 d929cb1。直接执行、不委派。最终生产 Java 剩余 0 才算全量完成；采用可单独构建验证的增量，不把第一批当全部迁移。

## Current Phase
Phase 3: 兼容工具链全构建/自审/127 build与132 frozen核对通过，首批提交收尾；Phase4全量迁移仍pending。

## Next Step
审计本轮暂存文件/安全边界与输入SHA，创建首批本地提交，然后继续Phase4下一批。

### Phase 1: 技术核查和第一批边界
- [x] 完整恢复历史 planning、git 和 Beads；新增并 claim llm-asr-ap5。
- [x] 核对 runtime，无重叠 writer。
- [x] 核查 Nix Kotlin 包、混合编译、运行时/许可、JNI ABI 和既有源检查。
- **Status:** complete

### Phase 2: 第一批实施
- [x] 接入锁定 Kotlin 工具链及共享混合编译脚本，保留 Java 测试。
- [x] 迁移小型纯生产组件，保留公开 JVM API、nullable 与录音 gate。
- [x] 更新相关源检查/负例和文档，不放宽既有测试门槛。
- **Status:** complete

### Phase 3: 第一批验证与交付
- [x] host 回归、Android Java/Kotlin/JNI 编译、完整签名 APK/包检查。
- [x] 自审、输入/产物指纹检查，设备验收明确 pending。
- [x] 暂存前文件清单/输入/安全检查、Beads记录未迁移范围；本轮创建本地提交（成功身份以Git为准，失败则不交付）。
- **Status:** complete

### Phase 4: 后续生产类迁移
- [ ] 协调器/模型与管理/诊断/推理/Android 平台及入口分批迁移。
- [ ] 每批独立验证和提交；全量生产 Java=0 后关闭任务。
- **Status:** pending

## Constraints
不改 MNN/JNI 数学、包名、权限、隐私、录音取消/交接 gate、导出 epoch。不引入协程/Compose/Gradle。不访问设备/麦克风/私人文件；模型、音频、APK、编译缓存、签名密钥不入 Git，不 push。编译工具依赖可下载但不下载模型。既有工作区干净；一 writer。

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| 恢复 progress/findings 超 50KB | 1 | 按 offset 补齐完整文本，不访问会话历史 |
| 猜测 scripts/check-jni-object.py 不存在 | 1 | 实际入口 compile-jni-object.sh/check-jni-symbols.py |
| nix eval root flake 20s 超时 | 1 | 改 tiny-mirror 环境后台准备，不反复复制 root |
| 首轮host在空classpath参数校验退出1 | 1 | `${2:?}`误拒绝合法空字符串，改`${2?}`仅拒绝缺参，再重跑 |
| ba88ee850 exit0但D8/R8 metadata2.4>支持2.1且重写异常 | 1 | 当前APK拒绝交付；固定Kotlin2.0.21，新增D8警告fail-closed门槛重建 |
| nix-prefetch-url归档600s无输出超时 | 1 | curl HEAD确认官方302→200/85822011字节；改有界curl |
| 官方curl归档低速~35KiB/s且retry覆盖partial，360s超时 | 2 | 第三路径使用Nix内容寻址tarballs mirror并强制同官方SHA |
| Nix tarballs镜像180s取得70,741,132/85,822,011后超时28 | 3 | 用户新增代理授权后官方Range续传成功，整包官方SHA通过 |
| 代理下载任务sha通过后unzip缺失exit127 | 1 | Python zipfile CRC+大小+SHA校验成功，add-fixed Nix store后单独准备env |
| 覆盖先前从只读Nix cp的LICENSE PermissionError | 1 | chmod该新建许可文件u+w，改Python从2.0.21归档读取真实许可，旧2.4 threeten不适用于2.0，按同版README更新 |

## Blocked stop condition
有所有权冲突、无法获得可复现 Kotlin 工具链、无法保留 ABI/安全语义或三种诊断仍失败时，停止该分支并记录证据、尝试、解除输入；不以旧 APK 替代本轮验证。
