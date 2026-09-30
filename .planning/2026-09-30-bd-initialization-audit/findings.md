# Findings: Beads 初始化与规划审计

## 已核验事实
- cwd/worktree 为 /home/zhb/gitrep/llm-asr；main，HEAD d5c1b64，开工时 Git 工作区干净。
- bd 1.0.3(dev) 支持内嵌 Dolt。初始化前 bd context 报无 .beads；bootstrap --dry-run 返回 action=none/no active beads workspace；本地/已缓存 origin tree 无 .beads、Dolt refs 或既有库。没有从网络查询远端。
- Herdr agent list 返回同项目仅当前 working 会话 wJ:p1；另一项目 Agent 与此范围无关，不干预。
- 根规划已完整恢复，但 Current Phase / Next Step 仍写 Phase24 执行提交。Git 已有 d5c1b64 配置准备修复提交；该“待提交”不是当前可执行需求。
- 历史 Phase10 已被 Phase15/16 接替；Phase20 明确 R5/R6 已实现，不能按旧规划重复实施。
- 当前完整 P0 质量、设备回归与产品性能仍有未关闭限制，需对照当前报告与源码细分。

## 初始化与当前身份
- bd init 成功；context 为项目 .beads、embedded Dolt、database=llm_asr、无redirect；ping ok，初始化时0任务/0memory。
- bd init 默认自动本地提交9f843db（五个Beads配置/说明/ignore文本），已逐文件确认；未推送、未改历史、未包含库实体/备份/凭据。此行为不是另一次产品实现交付。
- 本轮审计task=llm-asr-874，assignee=llm-asr-steward，原子claim成功。
- 实际现有APK：0.6-debug/code6，2470760bytes，SHA66be525e2f69b12b23f5a76f5bc2d0815f97c7447a672000162d38b6d4e7dfed；122项当前build-input SHA全匹配。这是本轮只读完整性核对，不是重新构建或设备验收。
- app-roadmap.md以历史追加形式保存许多已被后文覆盖的“0.3/0.4未实现”；报告P0中的“无APK/IME”也是历史状态。应保留证据但增加当前导航，不把旧状态导入backlog。
- 当前device_verified=false；P0独立人工golden、strict frontend与eager/window仍未关闭。native历史load11.6s/PSS3.06GiB不是当前APK新测性能。

## 实现与保留项核对
- native/apk/asr_jni.cpp 每请求create/load/response并销毁Llm；cpu/2线程/max128，正常EOS才成功。没有常驻、native硬取消或量化新方案。
- MainActivity明确本版无VAD；PcmWave/ForegroundRecorder上限30秒，WaveInput仅16kHz单声道PCM16/0.1–30秒。无MediaCodec/长音频或Binder/AIDL推理服务，Manifest的service仅IME。
- Phase19已修 RuntimeLogStore publisher Error收尾与后续append恢复；不能重新登记为未修。RuntimeLogWorker.drain自身却缺异常finally恢复scheduled，仍为Phase20保留问题，适合窄工程加固。
- 日志剩余较低优先级项：极端恢复序号后多次append、模型executor拒绝/提前终止的STARTED缺终态、picker启动失败文案被EXPIRED覆盖，均需对照真实代码再做红绿，不能泛化为生产数据泄漏。
- P0报告包含历史“无APK/IME”等句子，但已被实际实现取代；保留独立golden、strict frontend/eager窗口和资源证据缺口，不重复导入已交付App任务。
- 待办应独立记录设备授权门槛、下一批功能决策，避免bd ready被误当作产品实施授权。
- 本机bd写入会自动导出并暂存 .beads/issues.jsonl；最终必须主动审计index、导出内容及非敏感边界，不使用git add .。

## 最终协调状态与查询限制
- 后续10任务准确状态为5 open / 5 blocked，均未分派；8条依赖无环。审计本身由当前writer收尾后关闭。
- bd status汇总本次ready=1/blocked=4，与ready返回5条及list显示5blocked不同；bd blocked只列依赖阻塞的4项，手动blocked设备授权项须list/show核对。保留原真实状态，不扩大范围修bd工具。
- 需求快照与导航已写docs/requirements-audit.md；根规划修正已提交Phase24身份，旧历史不删除。
- 已保存并读回memory三key：build-tiny-mirror、build-kleidiai-reproduction、asr-quality-evidence。AGENTS按已存类别补查询导航。
- 文档链接/围栏检查通过，122产品构建输入仍全匹配。没有产品实现测试/重建/设备验收。本轮提交范围仅非敏感协作配置/任务文本/规划文档。

## 边界
不读取本地 Agent 会话历史；Herdr 只读运行身份查询不代表访问会话内容。只保存非敏感项目事实，不提交库实体、产物、权重、凭证或代理会话数据。
