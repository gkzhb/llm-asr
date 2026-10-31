# Findings

- 当前 HEAD 64d00d1 已修 IME return no-hide；llm-asr-lsk closed 仅工程完成，系统 IME/fcitx5 真机待验证。
- zcm 校验凭据/单 resident A+B 已提交、closed；不重跑旧 full。
- msj 原选择为模型校验/驻留与 IME 返回，其他 VAD/长音频/API/在线下载并未解锁。ap5 旧状态不能覆盖后续不再机械迁移的产品决定。
- bd status/blocked 的摘要混入 epic parent-child；必须以 show dependency_type=blocks 为准，不凭计数解除依赖。
- 本次恢复起初无同项目child；新planner wY:p6D已只读提案完成停写，产品尚未改。父不访问会话正文、设备或私人数据。
- 接受llm-asr-t0t：AsrImeService的render/startRecording只查readiness，而既有无IO ModelAccess.residentSnapshot可区分EMPTY/LOADED/pending。此为静态可达UX缺口非设备复现/安全绕过，worker最终requireResidentReady保持。只准六路径，App其他文案不顺手修。
