# M2 直接源码自审（非独立审查）

## 范围与方法
本 session llm-asr-m2 对本轮 CMake/source preparation/link/artifact verifier/AGP staging 与测试做直接自审；未委派，不把自审当独立 reviewer。验收仍需实际完整测试/包与指纹，构建进行中则不批准交付。

## 已接受并修复
1. 初稿 native/build 被通用 ignore 排除：移到 native/cmake，确保目标定义可纳入源码/指纹。
2. 初稿 prepared inventory 仅信本地 report：新增 native/source-lock.json 全公开树摘要，归档/patch/tree 复验；任意重新写 sources.json 不应绕过 committed tree lock。
3. realpath 先于边界检查可折叠 traversal/symlink：source/build 在创建/规范化前检查，拒绝已有 build descendant symlink，source/build 合作进程 flock 串行。不宣称恶意本地目录并发攻击防御。
4. 报告输入引用不存在 scripts/kotlin_abi_contract.py：实际 bbda599af 在 native gate 报告阶段失败；移除虚构路径并新增 input_hashes 存在性回归。保留完整失败证据，未删门槛。
5. staging/negative fixture 仍读 M1 native：API 改显式native输入，Gradle tests使用当前artifact，fixture旧native路径根本不创建；独立fallback仅用于旧公开产物开发测试。
6. native物理信息仅link返回无 gate：新增578 unique/当前build对象SHA、JNI源/头/compiler、effective config、AArch64 LOAD16KiB/W^X/RELRO/nonexec-stack/staticruntime及actual symbol检查，包验收再次复验。
7. 缺新回归Gradle接线：verifyNativeInputs普通及-O运行源码/ELF/对象/报告清单负例。

## 证据/未知区分
- bf6460e8e首个独立新目录579步通过，native SHA与原基线相同；bbda599af第二目录也实际578对象/链接及config/ELF/JNI通过，之后report FileNotFoundError，全轮失败，APK尚未执行。
- 574/578旧对象完整SHA不同。代表AutoTime.cpp对象不同section仅.rela.debug_info/.debug_str/.debug_line/.rela.debug_line，text/rodata/EH相同；此为代表性诊断，不声称已逐节核完全部对象。最终strip DSO完全一致为本轮有效二进制身份门槛。
- 源码准备8/staging8/native4普通通过，native4优化模式通过；最终全gate含新测试仍进行中。正常模板里禁用assert安全判断，测试assert不替代production gate。
- 原MNN compiler警告来自固定上游，不修改数学或掩盖；最终DEX警告仍fail closed。

## 接受的边界（非阻塞但必须披露）
- source获取默认只离线受控归档；缺归档需用户/既定允许来源另行准备，不配置时下载或静默降级。
- CMake顶层project hook/deferred依赖固定上游公开对象变量，非对任意新版MNN保证。CMake/Ninja由锁定Nix提供。
- prepared完整文件SHA lock精确绑定本轮三补丁、略过无关symlink及restricted目录；不自动修未知已有树。
- 合作进程flock，非恶意本地写权限sandbox；断电/强杀不保证事务持久。
- static ELF16KiB与byte identity不代替4KiB/16KiB设备运行；两者not run，普通APKJNI/SAF/IME/麦克风未验证。
- 保留旧native链接脚本与历史reports作为回退，不作为当前AGPnative输入；不重写历史/上传/设备访问。

## 当前处置
be4fde0bc exit0，全43 actionable任务执行，host/3mutants/8staging/8source普通及-O/4native普通及-O/5包identity/Java-Kotlin/JNI/native config及ELF/实际签名包检查通过。161包输入/79native输入/73产品SHA逐项一致；APK和native均与M1字节相同。generateDebugAssets为无action生命周期节点显示UP-TO-DATE，不能声称每个图节点都执行；43实际action全执行。

末次证据加强：native provenance补锁定Nix CMake/Ninja binary SHA及版本（此前flake/lock已绑定，但工具事实缺字段），新增source report修改后仍被commit lock拒绝负例，source9普通/-O通过。两项是测试/报告修改，不改产品/native数学/期望值，必须再次绑定；最终bc3339b51 full gate＋隔离真实dependency checksum负例已启动。新native/包输入冻结161条到.work/native-m2/final-inputs.json。

最终bc3339b51 exit0，全43action再次执行、source9/-O、native4/-O、全host/JNI/实际APK与隔离checksum负例通过；161 frozen/current/report相同，79native/73product匹配。CMake4.4.2/Ninja1.13.2实际SHA/version已绑定。必要非敏感报告保存reports/native-m2，未修改最终构建读取的输入。直接自审无剩余阻塞；等待安全index/commit核验，提交失败不交付；设备两种页大小仍not run。
