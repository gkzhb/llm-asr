# 脚本分类、调用方与验证入口

本页是 `llm-asr-djn` 的首个文档导航切片，源码快照为 **`8affcee9b528884c60560d80969fac2642650a5d`**。分类不改变脚本位置、CLI、导入接口、错误或安全策略，也不代表完成全仓重构。

库存范围：该快照 Git tracked 的 `scripts/`、`model-tools/`、`tests/`、项目 code-review skill 中的 Python/Bash 脚本，以及根 Gradle wrappers，共 **120** 项，其中测试/测试基础设施 **58** 项，分列于下文。数量是快照库存，不是永久门槛或本轮测试执行数。Java/Kotlin 测试体、C++ probe/header、Gradle/CMake/Nix 构建定义不计作这些 CLI/helper 脚本；它们仍可能是调用方或构建输入。

历史 `reports/` 中的脚本副本不作维护入口；忽略目录、用户未跟踪脚本、模型、音频、会话、缓存及产物不因此纳入维护或清理范围。**“历史/诊断”不是 unused、deprecated 或可删除的结论。** 以下为静态源码与文档调用关系，不证明任何命令已在本轮运行；没有因编写本页执行测试、构建、模型计算或设备操作。

## 1. 新运行优先从这里进入

| 目标 | 维护入口与契约 | 详细说明 |
|---|---|---|
| 获取固定原始快照 | [download.py](../model-tools/download.py)：按外部 manifest/output 参数下载并核大小/SHA；网络/依赖准备需适用授权，不自动扩大为转换或部署授权 | [本地模型与配置](local-models-and-config.md) |
| 新目录分阶段 FP16 复现 | [reproduce_fp16.py](../model-tools/reproduce_fp16.py)：显式 model/source/converter/audio-probe/python/output/evidence，串行 encoder→门槛→decoder；输出/证据必须不存在、互不嵌套且不重叠受保护输入；dry-run 仅为 NOT_RUN 计划 | [FP16 转换](mnn-fp16-conversion.md) |
| 仅 decoder 导出 | [convert_fp16.py](../model-tools/convert_fp16.py)：已有 encoder 的当前六长度数值结果和 graph/weight 身份先通过，再导出 decoder/tokenizer/config；拒绝覆盖 decoder 文件，不写可信 P0 清单；需要外层进程监督 | [分阶段与独立 helper](mnn-fp16-conversion.md) |
| 两套模型 host A/B | [compare_mnn_models.py](../model-tools/compare_mnn_models.py)：`--spec` 绑定输入，`--output` 是新目录；支持的两个配置有独立固定 SHA，**不接受任意 caller 重绑配置**；native EOS、失败、agreement、有限性能与独立 gold 评分分开 | [固定配置比较](mnn-model-comparison.md) |
| 七资产打包 | [package-mnn-model.py](../scripts/package-mnn-model.py)：默认 current-app 固定 tuple；generic 必须外部 policy 且明确 NOT-IMPORTABLE；源目录恰七资产，输出父目录已存在、输出在源外；fd/no-follow、确定性 ZIP32 与 hard-link no-clobber 发布 | [打包 CLI](mnn-model-packaging.md)、[格式](model-package-format.md) |

这些入口不是环境安装器或任意输入沙箱。源代码/Python 环境/converter/probe/动态库仍需事先审核并核同源；不要仅凭文件存在推定来源已验证。转换门槛 PASS 不关闭历史 strict-mel FAIL，不证明识别质量、APK 导入或设备验收；generic ZIP 合法也不会改变 Android 信任身份。

历史 [export_mnn.py](../model-tools/export_mnn.py) 与 [export_decoder.py](../model-tools/export_decoder.py) 的旧 CLI 保留，但使用固定 P0 输入、输出和报告路径。后者正常导出及 `--finalize-existing` 都可能覆盖 Git 追踪的 `reports/p0/mnn-model-manifest.json`；finalize 不重新转换，也不是数值验证。**新运行不要用历史命令覆盖唯一旧产物或更新可信 SHA 来凑导入**；历史复现与改写固定目录/可信清单必须另核范围授权。详见[历史链路与副作用](local-models-and-config.md)。

## 2. 实际模型调用关系与兼容面

以下定位均以页首源码快照为准；文档 recipe 只是操作入口，不冒充自动化调用或实际成功证据。

- [reproduce_fp16.py](../model-tools/reproduce_fp16.py) 的 `plan()` 构造九个串行阶段：`check_audio_parity`、`check_prompt`、`onnx_audio_gate export/validate`、显式 converter、`mnn_audio_gate prepare`、显式 native audio probe、`mnn_audio_gate validate`、`convert_fp16`。helper 路径从自身目录拼接，不是执行目录中任意发现的脚本。
- `reproduce_fp16` 直接导入 `convert_fp16.validate_gate`；它与 `compare_mnn_models` 都已实际导入 [fp16_process.py](../model-tools/fp16_process.py) 的 `cancellation_signals`、`digest`、`run_bounded`。这是真实共享，不需要再包一层通用 runner。
- `fp16_process` 的保证是直接子进程 wait/reap 与采样到的 PID-start 身份停止确认，不是所有逃逸后代的沙箱。保持 started/PID/真实 exit/log SHA、未执行后续阶段、取消收尾和不确定时保留输入的语义；勿与测试侧 pidfd/subreaper/lease runner 混称同一保证。
- [check_prompt.py](../model-tools/check_prompt.py) 导入 [audio_contract.py](../model-tools/audio_contract.py) 的 `render_prompt`；[isolate_encoder.py](../model-tools/isolate_encoder.py) 在函数内导入 `encoded_length`。Python encoder helper 产生指标后，完整复现入口另做实际数值/身份拒绝检查，不能只看子进程 exit0。
- [test_convert_fp16.py](../model-tools/test_convert_fp16.py) 通过 `importlib.util.spec_from_file_location` 直接加载 `convert_fp16.py`，不由 caller 添加 `model-tools` 到 `sys.path`。[test_audio_contract.py](../tests/p0/test_audio_contract.py) 也直接按路径加载 helper。后续整理必须保此导入方式，不仅保 CLI 文件名。
- [test_reproduce_fp16.py](../model-tools/test_reproduce_fp16.py) 导入生产 `main()`，用真实 tiny child 测编排，取消测试另启动实际 CLI（默认 `export.__file__`）。[test_compare_mnn_models.py](../model-tools/test_compare_mnn_models.py) 导入 module 后以 `tool.__file__` 启动实际 CLI，且工作目录是 fixture。不能只搜索目标全路径就宣布已穷尽 caller。
- [mnn_model_package_test.py](../tests/mnn_model_package_test.py) 同时调用真实 CLI、直接 importlib 加载 module，并调用 `MODULE.package(...)` 注入发布故障；私有临时文件、大小/SHA/CRC 流式校验、无跟随目录 fd 与 no-clobber 发布不是普通 `mkdir`/hash wrapper 的同义词。
- 历史 [compare_smoke.py](../model-tools/compare_smoke.py) 与 [rescore_smoke.py](../model-tools/rescore_smoke.py) 导入 [official_text_parser.py](../model-tools/official_text_parser.py)，后者还导入前者的 `edit_distance`。历史重评分包含顶层报告写入，不应当作无副作用通用库执行。

调用图的明确限制：静态 import、字面量、上述已读的动态构造和维护文档可确认；shell 变量/循环、importlib、unittest discovery、外部用户命令和忽略任务脚本不能由一次 grep 全部证明。没有匹配只能记“在此范围未发现”，不能写 unused。函数/模块名能辅助推测职责，但不能替代行为审查。

## 3. 构建调用、输入收集与门槛升级

当前标准构建见[Gradle 构建](gradle-build.md)、[native 构建](native-build.md)、[本地分层验证](local-verification.md)、[模块/JUnit 映射](module-test-map.md)。这里只说明接线，不替代当批命令、输入指纹及 receipt。

- 根 [build.gradle.kts](../build.gradle.kts) 的 `verifyHost` 调用 [test-minimal-apk.sh](../scripts/test-minimal-apk.sh)，后者显式选择 host-safe 源码，用共享混编脚本及有界 JVM/行为 mutant/source/checker drivers。shell `run` helper 和 class-name 循环属于动态调用，不等于按文件名自动发现测试。
- [android/app/build.gradle.kts](../android/app/build.gradle.kts) 的 buildNativeArtifact/prepareInputs/verifyDebugPackage 调用 native builder、stager、APK checker。[build-native-artifact.sh](../scripts/build-native-artifact.sh) 先检查 prepared source，再编 JNI、CMake native、最终 verify；[finalize-mnn.cmake](../native/cmake/finalize-mnn.cmake) 调配置的 linker。preBuild 连接准备/native 链，不把 assemble 当轻量检查。
- [compile-android-java.sh](../scripts/compile-android-java.sh)、[compile-jni-object.sh](../scripts/compile-jni-object.sh)、历史 APK builder 递归发现 app Java/Kotlin 后调用同一 [compile-jvm-sources.sh](../scripts/compile-jvm-sources.sh)。core/runtime 的物理 owner、JUnit wrappers 与 test-host 则有各自明确映射；移动文件不能忽略这些 caller。

| 真实收集器 / checker | 快照中的路径策略 | 整理时必须保持 |
|---|---|---|
| [verify-gradle-apk.py](../scripts/verify-gradle-apk.py) `collect_build_inputs()` | baseline.files + 显式根构建/锁/wrapper/flake 文件；递归 gradle、scripts、tests、app/res、native/cmake、core/src、runtime/src，排除 `__pycache__`；合并 test-host module_inputs | 是 live 文件系统库存，含新/未跟踪文件，不是 Git-only；普通 docs/model-tools 不因本页分类自动入 APK 输入 |
| [verify-android-test-apk.py](../scripts/verify-android-test-apk.py) `module_inputs()` | test-host build/lock、递归 src/fixtures；要求指定 T5 源非空、拒 symlink/重复语言 owner | 三包 receipt、产品 collector、格式 checker 共用，不可删新路径凑绿 |
| [verify-native-artifact.py](../scripts/verify-native-artifact.py) `input_hashes()` | 显式 flake/native lock/JNI编译/混编/symbol/preparer/build/link/verify scripts + native/cmake、native/apk、app/src、patches 递归 | native 源码树/工具/对象 provenance 另有校验；普通 docs/model-tools 不在这个路径集合 |
| [prepare-gradle-inputs.py](../scripts/prepare-gradle-inputs.py) `validate()` | baseline 精确 SHA，app/src、app/assets、native/apk、patches 精确库存；native/sample 身份和配置 check | 不自动更新 baseline、可信 SHA 或忽略缺失 owner |
| 历史 [build-minimal-apk.sh](../scripts/build-minimal-apk.sh) | 固定列表加 android/app 递归、tests 根/fixtures、patches，输出历史报告 | 不等同当前 AGP collector，保旧入口但不能拿旧产物冒当前 |
| [check-maintained-text.py](../scripts/check-maintained-text.py) | 显式 INVENTORY + live test-host module_inputs | 不覆盖任意 scripts/model-tools/docs；本页与两个导航修改不因此自动受此 gate 检查，不扩清单只为宣称通过 |
| [复杂度收集器](../.agents/skills/llm-asr-code-review/scripts/baseline.py) | Git tracked 维护源码，含 model-tools/scripts/tests/根构建，排历史报告/generated/vendor | 不等同 build collector；新未跟踪候选需另列，不能擅动主 index 来制造覆盖；方法见[审查 skill](../.agents/skills/llm-asr-code-review/SKILL.md) |

改 `scripts/`、`tests/` 或 native/Android/Gradle 接线，即使 APK payload 理论上不变，也可能改变正式构建输入身份。必须先按实际 scope 选择原必要 targeted、host/JNI/native/包/full 与身份验证，**不得通过删 gate、挪到 collector 外或缩窄库存来规避验证**。纯文档且不入实际输入集合时，核静态内容/链接/完整 diff/输入不变即可；本页不是以 tracked 扫描替代实际 receipt 收集器。未来代码改动才按同版本同范围 cccc/knots 基线看相对变化和盲区；本次无代码变化，复杂度运行 NOT RUN，不填0。

### 分层 gate 与独立模型测试不能混称

| 入口 | 当前接线范围（非本轮运行结果） |
|---|---|
| `verify-local.sh fast` | `verifyFast`：core/runtime 34+1 JUnit、实际模块编译界限、architecture/format/local guards；不含 host 大 driver/native/APK/test-host |
| `graph-fast` | Gradle dry-run + 精确图 checker，不执行上述测试 |
| `full` / 旧 `verify-gradle.sh` | `verifyDebug`：保 host/JNI/native/build-input/APK/签名权限资产、core/runtime、Lint、真实 checksum/Lint fixture 负例、三包工程与 fixture driver |
| `android-test-build` | test-host 7 JVM、产品/host/仪器三 APK assemble/静态 checker/负例；14 仪器方法库存只是编译/DEX 事实，不是 runner 发现/执行 |
| `lint` / `format` | 独立对应层；外层 Nix cache-only 与内层 Gradle strict/offline 是不同约束 |
| 独立模型 targeted | `model-tools/test_reproduce_fp16.py`、`test_convert_fp16.py`、`test_compare_mnn_models.py`、`tests/mnn_model_package_test.py`、`tests/p0/test_audio_contract.py`；在该快照未接入 root host/full，不能因文件存在就称自动覆盖 |

模型测试运行方式见各专门文档；comparison 的 unittest discover 是显式操作入口，不是 full 的隐式发现。tiny fake 真实 CLI 可验证工程编排/拒绝/取消，不证明真实模型数值；host fake、SDK/JNI 编译、包静态、Android runner、设备验收分层记录。设备部署/麦克风/私人文件仍需独立授权。

## 4. 完整脚本分类库存（页首快照）

语言按后缀：`.py` 为 Python，`.sh` 为 Bash；`gradlew` 为 POSIX sh、`gradlew.bat` 为 Windows batch。多数脚本通过解释器调用，不能仅凭 Git executable bit 判断可用或未使用。下列链接保持现有路径；同一脚本只在库存列一次，前文引用不重复计数。

### 模型获取、转换、验证与打包（10）

- 获取：[download.py](../model-tools/download.py)。锁：[model-lock.json](../model-tools/model-lock.json)、[source-lock.json](../model-tools/source-lock.json)、[pyproject.toml](../model-tools/pyproject.toml)、[uv.lock](../model-tools/uv.lock) 是数据/依赖输入，不计脚本。
- 显式转换：[reproduce_fp16.py](../model-tools/reproduce_fp16.py)、[convert_fp16.py](../model-tools/convert_fp16.py)。
- encoder/prompt 门槛：[check_audio_parity.py](../model-tools/check_audio_parity.py)、[check_prompt.py](../model-tools/check_prompt.py)、[onnx_audio_gate.py](../model-tools/onnx_audio_gate.py)、[mnn_audio_gate.py](../model-tools/mnn_audio_gate.py)。
- host 比较/官方参考：[compare_mnn_models.py](../model-tools/compare_mnn_models.py)、[reference.py](../model-tools/reference.py)。参考输出不是人工 gold。
- 打包：[package-mnn-model.py](../scripts/package-mnn-model.py)。

### Android 构建与本地 gates（22）

- 标准 native build/staging：[build-native-artifact.sh](../scripts/build-native-artifact.sh)、[prepare-native-sources.py](../scripts/prepare-native-sources.py)、[prepare-gradle-inputs.py](../scripts/prepare-gradle-inputs.py)、[prepare-model-configs.py](../scripts/prepare-model-configs.py)、[link-native-artifact.py](../scripts/link-native-artifact.py)、[verify-native-artifact.py](../scripts/verify-native-artifact.py)。
- 混编/JNI：[compile-jvm-sources.sh](../scripts/compile-jvm-sources.sh)、[compile-android-java.sh](../scripts/compile-android-java.sh)、[compile-jni-object.sh](../scripts/compile-jni-object.sh)、[check-jni-symbols.py](../scripts/check-jni-symbols.py)。
- 验证入口：[verify-local.sh](../scripts/verify-local.sh)、[verify-gradle.sh](../scripts/verify-gradle.sh)、[test-minimal-apk.sh](../scripts/test-minimal-apk.sh)、[check-fast-task-graph.py](../scripts/check-fast-task-graph.py)、[check-maintained-text.py](../scripts/check-maintained-text.py)。
- 包 checker：[verify-gradle-apk.py](../scripts/verify-gradle-apk.py)、[verify-android-test-apk.py](../scripts/verify-android-test-apk.py)、[check-minimal-apk.py](../scripts/check-minimal-apk.py)。
- 保留的历史/P0 构建：[build-minimal-apk.sh](../scripts/build-minimal-apk.sh)、[build-mnn-p0.sh](../scripts/build-mnn-p0.sh)、[build-p0-harness.sh](../scripts/build-p0-harness.sh)、[link-apk-native.py](../scripts/link-apk-native.py)。可能写固定输出/报告，不当清理目标。

### 设备部署与探针（4；需独立授权）

- [deploy-minimal-apk.py](../scripts/deploy-minimal-apk.py)：APK 身份检查后部署。
- [deploy-p0-model.py](../scripts/deploy-p0-model.py)：模型分块/续传/hash。
- [device-probe.sh](../scripts/device-probe.sh)：ADB 设备能力探针。
- [run-device-p0.sh](../scripts/run-device-p0.sh)：设备 harness 执行/收集。

### 低层共享与 wrappers（8）

- 模型 import helper：[audio_contract.py](../model-tools/audio_contract.py)、[official_text_parser.py](../model-tools/official_text_parser.py)、[fp16_process.py](../model-tools/fp16_process.py)。parser 仅加载受审纯文本函数；不是加载整个未知上游包的许可。
- APK helper：[apk_report_binding.py](../scripts/apk_report_binding.py)、[dex_class_inventory.py](../scripts/dex_class_inventory.py)。报告绑定不是独立签名认证。
- 环境/wrapper：[nix-env.sh](../scripts/nix-env.sh)、[gradlew](../gradlew)、[gradlew.bat](../gradlew.bat)。Nix mirror 仅复制 flake/lock，不拷模型；命令来自显式 caller，不是固定内置转换链。

### 历史诊断、补丁与准备（17；保留，不声明废弃）

- 固定 P0 导出：[export_mnn.py](../model-tools/export_mnn.py)、[export_decoder.py](../model-tools/export_decoder.py)。
- frontend/prompt/position 诊断：[check_features.py](../model-tools/check_features.py)、[check_hann_hypothesis.py](../model-tools/check_hann_hypothesis.py)、[check_native_prompt.py](../model-tools/check_native_prompt.py)、[check_positions.py](../model-tools/check_positions.py)、[instrument_positions.py](../model-tools/instrument_positions.py)。部分脚本有顶层 IO/固定报告覆盖，不当纯函数 import。
- 单变量隔离：[isolate_encoder.py](../model-tools/isolate_encoder.py)、[isolate_frontend.py](../model-tools/isolate_frontend.py)、[isolate_weight_rounding.py](../model-tools/isolate_weight_rounding.py)。
- 公开工程样本/历史比较：[prepare_smoke_audio.py](../model-tools/prepare_smoke_audio.py)、[compare_smoke.py](../model-tools/compare_smoke.py)、[rescore_smoke.py](../model-tools/rescore_smoke.py)。衍生样本不是独立标注语料。
- 主机兼容/补丁生成：[fix_local_wheels.py](../model-tools/fix_local_wheels.py)、[patch_mnn.py](../model-tools/patch_mnn.py)、[patch_whisper_hann.py](../model-tools/patch_whisper_hann.py)、[patch_asr_positions.py](../model-tools/patch_asr_positions.py)。可能改本地环境、源码、patch/provenance；本页不授权运行或升级依赖。

### 审查工具（1；测试另列）

- [baseline.py](../.agents/skills/llm-asr-code-review/scripts/baseline.py)：静态复杂度 evidence collect/compare；读[基线边界](../reports/complexity-baseline/README.md)，不是自动缺陷或交付 gate。

## 5. 测试与测试基础设施库存（58，单独分类）

这里按职责分组，不承诺组内每项都由 full 直接调用；nested caller/独立 targeted 见源码和[模块测试映射](module-test-map.md)。测试脚本不是生产导出入口，名称中的 migration/native/Android 不代表真实设备执行。

- 模型接口与打包（5）：[test_compare_mnn_models.py](../model-tools/test_compare_mnn_models.py)、[test_convert_fp16.py](../model-tools/test_convert_fp16.py)、[test_reproduce_fp16.py](../model-tools/test_reproduce_fp16.py)、[mnn_model_package_test.py](../tests/mnn_model_package_test.py)、[p0/test_audio_contract.py](../tests/p0/test_audio_contract.py)。
- 源码/平台/隐私接线（9）：[activity_window_source_test.py](../tests/activity_window_source_test.py)、[app_report_source_test.py](../tests/app_report_source_test.py)、[architecture_boundary_test.py](../tests/architecture_boundary_test.py)、[model_android_source_test.py](../tests/model_android_source_test.py)、[text_export_source_test.py](../tests/text_export_source_test.py)、[manifest_permission_test.py](../tests/manifest_permission_test.py)、[prepare_model_configs_test.py](../tests/prepare_model_configs_test.py)、[jni_symbol_checker_test.py](../tests/jni_symbol_checker_test.py)、[apk_report_binding_test.py](../tests/apk_report_binding_test.py)。源码/XML/checker fixture 不冒 runtime。
- host 行为、IO、取消和模型事务（11）：[activity_window_test.py](../tests/activity_window_test.py)、[foreground_recorder_test.py](../tests/foreground_recorder_test.py)、[logs_activity_picker_test.py](../tests/logs_activity_picker_test.py)、[ime_start_boundary_test.py](../tests/ime_start_boundary_test.py)、[ime_readiness_cleanup_test.py](../tests/ime_readiness_cleanup_test.py)、[android_model_verification_io_test.py](../tests/android_model_verification_io_test.py)、[model_residency_contract_test.py](../tests/model_residency_contract_test.py)、[model_verification_contract_test.py](../tests/model_verification_contract_test.py)、[model_zip_import_contract_test.py](../tests/model_zip_import_contract_test.py)、[model_zip_picker_test.py](../tests/model_zip_picker_test.py)、[model_review_mutation_test.py](../tests/model_review_mutation_test.py)。平台 fakes 与可编译生产 mutants 的证据分开。
- mixed compiler/迁移/终态（10）：[mixed_compiler_lifecycle.py](../tests/mixed_compiler_lifecycle.py)（有 CLI 的窄测试基础设施）、[mixed_compiler_lifecycle_test.py](../tests/mixed_compiler_lifecycle_test.py)、[kotlin_next_migration_test.py](../tests/kotlin_next_migration_test.py)、[kotlin_runtime_migration_test.py](../tests/kotlin_runtime_migration_test.py)、[kotlin_test_slice_test.py](../tests/kotlin_test_slice_test.py)、[kotlin_testhost_migration_test.py](../tests/kotlin_testhost_migration_test.py)、[kotlin_to_java_contract_test.py](../tests/kotlin_to_java_contract_test.py)、[runtime_log_enum_cleanup_test.py](../tests/runtime_log_enum_cleanup_test.py)、[runtime_log_enum_mutant_test.py](../tests/runtime_log_enum_mutant_test.py)、[module_boundary_test.py](../tests/module_boundary_test.py)。真实进程/lease 与编译/JVM 结果不能被源码 grep 替代。
- AGP/三包/输入与 owner（8）：[android_test_apk_test.py](../tests/android_test_apk_test.py)、[android_test_inputs_test.py](../tests/android_test_inputs_test.py)、[android_test_fixture_inputs_test.py](../tests/android_test_fixture_inputs_test.py)、[android_test_fixture_test.py](../tests/android_test_fixture_test.py)、[gradle_apk_identity_test.py](../tests/gradle_apk_identity_test.py)、[gradle_inputs_test.py](../tests/gradle_inputs_test.py)、[module_owners_test.py](../tests/module_owners_test.py)、[module_owners_guard_test.py](../tests/module_owners_guard_test.py)。实际 class/APK/JUnit driver 和纯 checker/input fixtures 不是同一种验证。
- 本地入口/格式/依赖/Lint（10）：[local_verify_entry_test.py](../tests/local_verify_entry_test.py)、[local_verify_fault_test.py](../tests/local_verify_fault_test.py)、[fast_task_graph_test.py](../tests/fast_task_graph_test.py)、[maintained_text_test.py](../tests/maintained_text_test.py)、[dependency_checksum_fixture.py](../tests/dependency_checksum_fixture.py)（有 CLI 的 fixture helper）、[dependency_checksum_fixture_test.py](../tests/dependency_checksum_fixture_test.py)、[gradle_dependency_verification_test.sh](../tests/gradle_dependency_verification_test.sh)、[gradle_lint_verification_test.py](../tests/gradle_lint_verification_test.py)、[lint_identity_test.py](../tests/lint_identity_test.py)、[lint_report_test.py](../tests/lint_report_test.py)。纯 fixture 不等真实 checksum/Lint 负例执行。
- native 专项（4）：[native_sources_test.py](../tests/native_sources_test.py)、[native_artifact_test.py](../tests/native_artifact_test.py)、[native_residency_bridge_test.py](../tests/native_residency_bridge_test.py)、[native_request_cleanup_test.sh](../tests/native_request_cleanup_test.sh)。后两项为独立受控 bridge/host synthetic probe，不等模型推理或 Android native runtime。
- 审查工具自测（1）：[test_baseline.py](../.agents/skills/llm-asr-code-review/scripts/test_baseline.py)。只验证证据结构/比较逻辑，不替代代码行为审查。

## 6. 后续整理的最小原则

保旧 CLI 与直接导入 Interface，先核实际 caller、输入身份和失败/取消/发布行为，再选择一个窄切片。现有 hash、JSON、fresh-path 代码可能看起来相似，但 reproduction 的 `.tmp`/默认 JSON、comparison 的 `.pending`/UTF-8/拒 NaN、packager 的 no-follow/CRC/原子 no-clobber 不是可随意合并的策略。历史 exporter 还会写可信清单；不直接转发到新 helper 改旧语义。

只有真实重复复杂性值得集中时才建共享模块：删除它若只是少了一层转发，没有复杂性重新分散到 caller，就是浅封装；不能为了分类创造通用 helper。代码改动的行为负例、同口径复杂度、fresh 审查和安全提交另按项目规则执行。本页只改善发现与导航，不关闭整个 `llm-asr-djn`，也不交付新 APK。
