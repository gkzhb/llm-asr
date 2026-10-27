# B native identity — 父接受候选后的精确rebase

Task `llm-asr-zcm/B`，base `8b71b56b8927031be938d188bacafe0b530a4b8a`。用户授权官方Session/Module正常复用，六成员请求语义清理经fresh review-6 NO BLOCKING父接受；原三finding与checker闭合保持。本报告只native身份与原严格gate，不是完整full/APK/设备交付。

## 身份准入依据

父在读取完整候选来源与差异后，明确批准：

| 项目 | A历史身份 | B批准身份 |
|---|---|---|
| SHA-256 | `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416` | `4ee0b300caa099a7ef0358fdf42901b9f963d5e2be8fb8e330fb5cad55394481` |
| bytes | 5,396,664 | 5,406,248 |
| 相对变化 | 历史固定值 | +9,584 bytes |

新产物位于`.work/residentb-native-identity/build/artifact/libqwen_asr_jni.so`。首次真实目标构建579actions（578本build唯一MNN对象＋JNI链接），只使用显式CMake OBJECT targets/finalizer，cache-only #apk、NDK28.2.13676358/API29/arm64/static C++、原参数逐项一致，不从旧对象凑数。新patch6 SHA `059521069394f08ac0a89bdd2d82aa605dd39fc537a0cc6c1b5de1d0450c8392`、MNN tree `cdf16da6e5bd5c66095ac9796d385e58267c3dc6b37f5548f37dc15c68069626`；原revision/archive/三数学patch/KleidiAI保持，Module4恢复原三数学后字节，无observer进生产锁。

真实导出3094共同、0删除、5新增：JNI prepareNative/releaseNative、Llm clearRequestState/clearRequestStateFields/supportsRequestCleanup。原两transcribe ABI保持。归一化仅替换旧/新prepared绝对路径后578库存同，4对象SHA同/574不同；路径、断言/诊断字符串及非运行元数据可能贡献，不声称每个字节已语义归因或运行等价。

## 原失败与当前严格gate分列

- **旧expected下真实拒绝**：`.work/residentb-native-identity/strict-original/verify.json` exit1，`ValueError: Native identity mismatch`。完整stderr/输入/root保留，不是mutant red，不回写pass。独立其他gateaudit通过不曾替代原strict verifier。
- **当前rebase**：只将baseline `native.sha256`精确改为父批准值，并更新`revision_scope`的B合同说明；native.path/source_commit/files/sample/signing完全不变。未运行build-native-artifact.sh、未重复SDK/578。
- **当前原严格gate实际通过**：`verify-native-artifact.py`对已接受build/source真实exit0，命令/root/完整流位于`.work/residentb-native-rebase/gate/strict-native.*`，artifact新增verification.json正式receipt。其84个native输入与父接受候选完全相同；当批`native-identity-inputs.json`绑定receipt与实际退出。未改verifier/monkeypatch/跳等式。

原strict检查包含完整578数量/唯一/inbuild路径/逐hashprovenance、JNI源/generatedheader/NDKcompiler、所有EXPECTED_CONFIG精确相等、KleidiAI根、ELF64LE AArch64/16KiB/W^X/RELRO/NX stack、五项DT_NEEDED、真实javap/nm四方法；候选额外audit核SONAME/static C++/no-undefined及原linkresponse。Omni有效OPENCV/IMGCODECS ON真实记载。原84 nativeinputmap必须同父接受候选，baseline/docs不属于该map。

本批另用显式NATIVE_TEST_ARTIFACT指向新DSO，原native_artifact_test.py普通/`-O`各4测试实际通过，覆盖真实ELF LOAD/stack/RELRO变异及对象count/duplicate/outside/symlink边界负例。普通与优化子退出均0，完整流按当批receipt，不以格式检查或候选audit冒测。

## 准确的编译与请求边界

SDK产物是**一个arm64 JNI对象、四个native方法descriptor/export ABI**，不是四种CPU架构。JNI顺序为`raw.str()`文本复制→请求语义cleanup→JNI byte array复制；任何失败仍default eviction。官方Session一次创建多次使用与Module正常next-rebind合同不自动证明所有Llm请求隔离/实际ASR质量。旧U1全私有状态枚举前置由用户明确合同取代，具体可达失效借用/KV错误仍block，历史不抹。

本批没有模型graph/weights/tokenizer/音频/private/设备访问，不动态加载DSO。实际loaded ASR/Android runner/设备/语种质量/PSS/性能/ASan均NOTRUN。原A三包与旧native artifact保持原字节。**原严格native gate成功也不等正式full/三APK/安全本地提交完成**；父消费后另授完整strict offline/full及必要产物覆盖，不在本轮启动。
