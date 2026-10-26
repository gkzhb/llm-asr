# Findings — llm-asr-zcm resident-plan

## 恢复
- main/HEAD 7a3aa5317dec905c6a952f16beff10a58ca9c481，tracked/index 空；旧大量 planning、两 docs、.beads.gate.lock excluded。
- embedded llm_asr/project 6c8fcd38；zcm in_progress/p1-steward 无 blocks；msj当前选择明确仍open；z1w实际blocked于msj/tc3/ujz，不动依赖。psn旧结果未产品实施。
- 父已核Herdr同仓无产品writer；本leaf身份来自显式resident-plan交接，不二级委派、不查询私会话。
- 已读要求skills/父三plan、memory build-kleidiai-reproduction/asr-quality-evidence、本地验证/复杂度契约；复用psn/v07及P3投影被拒审查，拒绝重复空接口批。

## 当前路径
- AppGraph:96唯一InferenceAdapter以access::requireReady装配，App AsrOperation:113与IME ImeBackend:49共享；native:94/105每请求create/load，末尾析构。
- ModelAccess:15首次/epoch失效全量SHA；readiness仅进程内epoch。ModelRepository全复用导入每字节CHECKING_EXISTING+FINAL_VERIFY两次hash，新文件PART+FINAL两次；同进程READY后免hash但不免native load。
- 所有受管写入口：importFrom（mkdir/part回收、写part、publish）、deleteAll、公开reclaimOrphanedParts；controller只gate.beforeMutation失效与delete失效，不足以做持久协议。应下沉repository mandatory guard覆盖直接入口。
- allowBackup=false且同进程App/IME；debuggable=true意味着run-as/root/任意同UID工具篡改不在可证明“免hash”威胁模型内。noBackup anchor/receipt +资产stamp是受管连续性证据，不是任意篡改检测。

## 固定 MNN
- 路径 .work/native-m2/sources/mnn，revision a03b005cf6f888ebf092e4753840f935827f9c36，归档/patch/tree声明见native/source-lock.json；本批只逐小文本SHA核sources.json，不跑全tree checker。
- llm.cpp:860 reset仅清token/history/seq/音频统计，标记mMeta.remove=previous，清cached prompt；不清generate_str/os/end_with/log_buffer/GenerationParams/输入tensor，不是物理KV擦除。
- generate_init:876设置os、清text/token并按reuse_kv=false标KV删除/decoder reset；Omni tokenizer_encode:1961清音频与position，embedding:2200成功后清embedding列表；失败中途与请求结束空闲期不完整。reset非virtual，Omni无覆盖。
- CPUAttention:434下次forward才onClear/onAlloc，非立即KV清理；llm.hpp保inputsEmbeds/outputs/stream等引用。language set_config:135→setChatTemplate可更新，需每次显式Chinese/English/auto空字符串，reset本身不做。
- 结论：当前真实API不足以证明“请求结束后清敏感状态但权重驻留”；第二批必须最小审计扩展/真实API compile probe与获准native实验，不能用mock或单reset宣称完成。首批可独立落持久校验+显式文件准备，仍每请求native释放。

## 边界/错误
- 工程测试、checker、Gradle/native编译、模型、设备、下载均not run；无BG。
- 7个实际cached MNN小文本逐SHA均与prepared sources.json匹配；没有核完整tree，锁revision是声明身份。
- 封口发现原clean后tracked IME相关变更；初次8路径，input-sources采集9路径，HEAD仍base。非本leaf写入，停止重叠读取；base file:line保留，seal_current_sha/差异显式列于input-sources。父须核并行writer归属/授权/终态，之后再授权A，不凭本调查取得写权。
- 导航少数不存在路径/通配rg exit2及一次read ENOENT，已改用实际定位路径，非工程失败。不继续猜缓存位置或全扫。
