# bt4 当前 FP16 转换续接证据（CLI 提交后的新 delta）

基点 `e1e310e56f31c839f47ea269997c794b443e30e1`；本页为新转换阶段记录，不修改先前 `status.md` 的阻塞时点或历史 P0 证据。USER 已明确授权必要固定依赖准备及本地转换；父唯一 Beads writer，leaf 不提交。当前尚未完成全部模型交付。

## 已实际完成

- 固定包118文件587,354,402bytes获取/SHA/size/domain核验；117wheel根RECORD完整检查、597ELF静态依赖/GNU_STACK检查，展开约1.91GB。仅本地工具不再分发/不进APK；GPL/LGPL/MPL及缺license文本声明处置保留，非法律/CVE保证。
- sox1.5.0源码hook/实际BSD许可静态审后，用锁定setuptools84.0.0内嵌wheel backend在无网络bwrap隔离构建；派生wheel再次RECORD/版本/license校验。完整118锁版本安装并导入检查通过，无跳包。新venv执行已审lazy-nagisa及两ELF非执行栈修复，精确pre/postSHA留证，不写p0。
- cacheduv0.12.17为明确接受route例外，其他Python3.12.14/CMake4.4.2/Ninja1.13.2/GCC15.3.0/patchelf0.15.2缓存路径与SHA留证；不更新锁或恢复整个Nix shell。
- 当前preparedMNN完整树/四补丁检查通过，新hostMNNConvert/llm_demo构建exit0（851s），≤2jobs。hostgroupRSS非整棵compiler峰值，已明确限制；模型前改为ownedPPID树RSS采样。生产源码/数学补丁未改。
- 当前encoder链exit0（80.81s，owned-treeRSS采样峰值约2.80GiB）：Python六长度parity max_abs=0；ORT同session七次动态长度max_abs≤1.736e-6；三种语言prompt token parity通过；nativeMNN FP16六长度max_abs≤0.000431、relativeL2≤0.002590，均低于预设0.002/0.02门槛。六长度20/99/100/101/800/801，含同session复验801。不是识别质量验收。
- 当前audio.mnn2,989,192bytes/SHA `1054e5d40c9eca5f087c8e6bafbfb18d95780c4074ed8d6e9674fce455a1ab78`；audio.mnn.weight373,185,600bytes/SHA `1456a76d7039681fbfdd94ffdac486545da62859f80a58b43a6f527f42a72382`。

## 已发现兼容性边界

encoder外置权重与当前APK可信tuple相同，但audio图SHA不同。固定源码 `tools/converter/source/common/AddUUID.cpp` 在未提供UUID时用random_device/mt19937生成UUID4，当前图实际读出UUID字段，支持“重新导出不保证相同SHA”的原因。旧图本地不存在/未知旧UUID，**不能断言仅UUID不同，也不能改图/旧可信SHA来凑可导入**。完整七资产比较已完成：七文件大小均与历史相同，五项（audio/llm外置weight、tokenizer、两config）大小SHA完全匹配；仅audio.mnn和llm.mnn图SHA不同。llm.mnn当前SHA `3032d7085742438d385352062705e6b621582eb43b9a70aaea01937052e7774f`（1,628,400bytes）。当前APK会拒绝，不能交付current-app包。

后续只能在本轮decoder/前端/nativeprompt/positions/数值验证可辩护通过后，选择明确新身份generic包；当前APK/catalog变更不在授权范围。历史两配置不作为替代本次产物，synthetic包不作为模型交付。

## 维护工具验证与未运行项

decoder-only FP16导出exit0（59.58s，owned-treeRSS约3.27GiB），五个实际converter子命令全exit0，七真实资产完整；audio已验证字节未变。严格前端mel gate实际exit1：公开样本max_abs0.00402689/relL20.000151753高于原1e-4/1e-4，tone同样max_abs约0.00143325；不放宽门槛。独立三种语言nativeprompt token parity实际exit0。positions/decoder数值实际exit0（32.12s，ownedRSS约5.69GiB）：text/audio各prefill+两decode共六position tensor完全连续；FP16-rounded官方eager参考textlogits max_abs7.93e-5/relL23.71e-6，audio0.03609/0.001140，两者argmax相同且在预设0.5/0.03门槛内。完整151936vocablogits比较，不是只比生成token。同一公开样本两语言frontend单变量token隔离实际exit0：官方/当前native mel四次运行均EOS完成、8tokens，两语言生成tokenIDs与text分别完全一致（23.996s/ownedRSS约5.40GiB）。仅证明该样本/两语言，不证明任意音频无影响。真实native完整ASR smoke实际exit0：同一公开样本两种强制语言均status1/非truncated/8tokens，与本轮官方FP16-rounded输出逐值匹配（hostload约2.54s/infer约1.85s，仅工程测量非手机性能）。据当前encoder+全vocablogits+positions+prompt+完整EOS与frontend单变量证据，可生成本地工程限定generic包，不代表strictfrontend关闭/质量发布批准；真实generic ZIP已经CLI完整流式校验生成exit0：`dist/model-packages/Qwen3-ASR-0.6B-bt4-FP16-GENERIC-NOT-IMPORTABLE.zip`，1,573,493,925bytes，SHA256 `8f303cf1c2f226d5bc519082759154c0a7fec4febc1e43583db47f3b209ac906`，modelId=`GENERIC-NOT-IMPORTABLE-qwen3-asr-0.6b-bt4-fp16`，恰八根条目、STORED严格ZIP32/noDD-extra-comment-ZIP64。full真实生产parser复验exit0（15.74s/ownedRSS约0.72GiB）：fresh编译56个current生产源码，真实`ModelZipArchive.prepare`完整CRC/SHA后七流全部读取，通过generic策略；真实`FixedModelPackage.mnn`也明确拒绝实际新tuple。未提交/freshreview前仅候选工程产物，不宣称交付/当前APK支持。所有终态/日志/SHA在 `.work/bt4-prepare-convert/` 留证。最终工具回归exit0：五类旧/失败gate拒绝+四CLI帮助、15项真实CLI合成行为回归、语法/UTF8-LF/已提交六文件字节不变及diff-check。新增维护测试`model-tools/test_convert_fp16.py`三项通过，含多组错误gate身份/长度/finite/超门槛拒绝；不加载大权重。复杂度双工具同版本，对e1e310e三个既有helper前后同范围比较，新增转换/测试单列，增长热点按绑定哈希/边界控制人工说明；不是fresh独立批准。

Android设备/SAF/麦克风/私人音频、APK构建/catalog更改均NOT RUN，不因host绿而声称设备验收。fresh新上下文审查、后续安全提交由父组织，当前leaf不stage/commit/push。
