# Findings — llm-asr-76d.3

## 认领核验
- 2026-10-01，本项目 Beads 为 embedded Dolt，database llm_asr，project id 6c8fcd38-9218-4430-9d2f-ea689c53b2a9，repo root /home/zhb/gitrep/llm-asr；未使用 global/初始化/切库。
- M0 llm-asr-76d.1 与 M1 llm-asr-76d.2 closed；M2 open/unassigned 且在真实 bd ready 中，唯一 blocking 前置为已完成 M1。claim 后 in_progress/assignee llm-asr-m2。
- Herdr wJ:p1 本 session working；同项目 wJ:p2 session 01a0f2dc-a56d-71af-a660-6ca7c8819ca4 为工具链分析 idle，无其他 working 产品 writer。ap5 仍 deferred、assignee llm-asr-kotlin，用户暂停决定保留。
- main HEAD 04462179a18ed87461c04221dcf213567ada551c；tracked/index 无差异。已有未跟踪 .planning/llm-asr-ap5-toolchain-analysis/ 与 .planning/llm-asr-msj-modern-android-analysis/ 属他人上下文，本轮不改、不提交。

## M1 交接事实（历史证据，尚未本轮复验）
- M1 标准构建为 Gradle8.7/AGP8.6.1/Kotlin2.0.21/JDK17，保留 NDK28.2、min29/target35 与产品/native/签名。
- M1 APK 路径 android/app/build/outputs/apk/debug/app-debug.apk，历史 SHA 5e1a1e072937b9509bb326f617daefe18ed40214115b8413a601d341a2fb84c0、5202124 bytes；M1 产品基线 native SHA d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416。
- M1 152 构建输入/73 产品输入、strict offline rerun 41 任务与真实 checksum 负例已记录。用户反馈手动测试通过，缺设备/OS/用例/端侧 hash，不等于全平台自动验收。
- M1 native 为显式 SHA 校验的现成 DSO 过渡，不满足 M2 干净 native 要求；相关脚本/任务接线待读。

## 适用知识与约束
已读取 modern-android-migration-boundaries、gradle-nix-verification、build-kleidiai-reproduction、build-tiny-mirror：
- 固定 MNN a03b005cf6f888ebf092e4753840f935827f9c36；KleidiAI v1.16.0 归档 SHA 57b0a95c559ec6a4b1460bc742af600bfb6cdff8dc39154b376c25347d7b0891。上游下载失败可能 warning 后降级，必须显式来源校验，不以 exit0 或更新 SHA 门槛接受降级。
- Nix tiny mirror 仅 flake/lock；不使用根 path:$PWD 导入权重/源码。fish 后台 shell 不支持 Bash 语法，多步命令显式 Bash。
- AGP 使用 Nix patched aapt2；strict/offline 是缓存后的证据，不自动 hermetic。全 DEX class_def 定义/重复检查须保留。
- 字节变化须解释并走质量/设备门槛；静态 16KiB 对齐不是运行验证。

## 开发核查
- 用户明确“开始开发”，继续本任务单 writer。当前 M1 APK 重新读取确认为 5202124 bytes/SHA 5e1a1e...84c0，ZIP native 为 d5bacffc...416。两个缓存源归档逐字 SHA 匹配锁定值；没有重新下载。
- link-apk-native.py 解析旧 build.ninja 的 link edge、读取旧 578 对象；prepare-gradle-inputs.py 与 app DSL 仍消费 .work/build/minimal-apk 的旧 DSO。这是本轮要替代的发布依赖，不修改历史复现脚本证据。
- 上游 CMake 已公开 MNN_OBJECTS_TO_LINK 的 TARGET_OBJECTS 与 MNN_TARGETS，适合通过 CMake 项目 hook/deferred 显式 artifact target 生成对象列表，不解析生成器文件；保留上游共享配置避免 MNN_BUILD_STATIC_LIBS 数学/导出差异。
- 旧对象顺序为公开 OBJECT targets 后接 cmake/dummy.cpp 对象，JNI 位于链接首位。拟显式 CMake 目标重用同序与原命令保证字节身份；技术试验尚未编译验证。
- MNN_BUILD_LLM_OMNI 强制 OPENCV/audio/imgcodecs ON，不能只按原脚本 OFF 参数错误记录有效值；旧 NDK Release cache C/CXX/ASM release flags为空，须核实际新配置。
- 主机本轮 free 显示约15.5GiB RAM、12GiB available；仍按低并发2/Gradle1保护，不自动扩并发。

## 首轮干净 native 结果与接线
- bf6460e8e exit0：新源树/clean-build 实际 579 步（578对象+artifact）完成，JNI库 SHA d5bacffc...416 与原基线完全一致；生成 JNI 头/10Kotlin52Java/NDK object 通过。
- 新对象574/578完整SHA与旧对象不同，文件包含源绝对路径，最终 stripped DSO却逐字相同；不声称对象逐字复现，后续报告区分路径/非运行元数据与最终身份。
- 直接读ELF动态依赖仅 liblog/libdl/libm/libandroid/libc，无MNN第二DSO或libc++_shared。
- 自审发现native/build被通用.gitignore忽略，已改native/cmake；将源码tree总摘要锁在native/source-lock.json，prepared report不能自称安全。safe path在realpath前检查、串行flock，特殊文件/树漂移拒绝。
- app新增buildNativeArtifact→prepareInputs→AGP编译打包；staging显式传当前native，不读旧DSO。verify-gradle-apk读取真实native provenance与config/ELF报告，不再制造M1 transition声明。
- 新8源码负例、8staging及3native ELF/object负例普通通过；新源码与artifact测试也python-O通过（ELF3/源码先前7，最终8待整轮再核）。AGP full strict/offline/rerun bbda599af 已启动，第二个新native-debug构建目录与实际包验证进行中。
- 短bash的独立native gate超过10秒被超时，不当功能失败/成功；整轮gate在后台执行，无并行修改源/native产物。

## 整轮通过与长期知识
- be4fde0bc完整strict offline验证exit0：43 actionable任务实际执行，host/3生产mutants/Java-Kotlin/JNI/包identity/签名权限/当前native config ELF gate通过，161包输入/79native输入/73产品逐项匹配。
- APK5202124bytes SHA5e1a1e072937b9509bb326f617daefe18ed40214115b8413a601d341a2fb84c0与M1相同，native d5bacffc...416同字节；证书e9365b...c16c/组件权限min29target35保持。compressed native extractNativeLibs=true；四ELF LOAD alignment16384。
- 代表AutoTime对象差异section仅debug及debug relocation，574/578对象完整SHA不同不代表最终二进制不同；不宣称逐对象相同。现base hash门槛不变。
- 最后补CMake/Ninja实际SHA/version与报告伪造负例（9source普通/-O通过），冻结161输入，bc3339b51最终全gate与隔离checksum负例进行，不以先前报告覆盖最终变化。
- 长期知识查重/读原文后原位更新gradle-nix-verification，保留Nix/aapt2/locking/multiDEX/M1背景，补M2显式目标/路径debug差异及runtime边界；保存读回成功。已有AGENTS Gradle/复现导航覆盖，不机械新增索引。

## 未验证
bc3339b51末次证据绑定运行中，未安全提交；4KiB/16KiB设备运行、麦克风、SAF/IME/JNI实机全部not run。未push、未委派。

## Resources
- docs/modern-android-roadmap.md（M2 规格）
- .planning/llm-asr-76d.2/（M1 三文件已完整读取）
- docs/gradle-build.md、docs/local-apk-reproduction.md（下一阶段按需读取）
