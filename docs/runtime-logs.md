# 运行日志（0.6）

## llm-asr-zcm B：显式准备与单进程复用（targeted工程，尚不交包）

文件校验证明与内存loaded分开：启动只轻恢复proof，模型管理页显式prepare/release；prepare在共享worker加载一次，同有效generation幂等，App/IME共用唯一resident。未prepare在asset/provider/capture之前拒绝，adapter末次复核；请求无load fallback，warm返回load_s=0且阶段仅3/4。prepare独立UUID的1/2事件，不生成REQUEST_SUCCESS。busy release立即关新准入，原native/cleanup/RequestRunner真退出后由既有worker卸载；release失败保EVICTING/pending，UI_HIDDEN20/页面离开/IME切换不卸载，无TTL或新线程。proof第四参maintenance lease覆盖current/generation/真实Repository写事务，先unload完成再DIRTY/资产首写，期间prepare拒绝。

本轮状态及证据见[模型驻留targeted](../reports/model-residency-zcm/status.md)。真实production状态/adapter/proof/repo/owner及可编译mutants、actual JNIbridge受控port只证明工程控制流；SDK/JNI对象编译不等Android执行。官方正常Session/Module复用设计已由用户明确授权实施，Module深清撤回而保留六成员请求语义；旧U1全私有状态枚举要求改为输入输出有效寿命/下次正确重绑定及KV隔离合同，历史报告保留，具体缺陷仍block。prepare日志保持phase2暂存、finished(true)才一次完成，失败/迟到/重复/重入failclosed；不凭官方建议宣称实际ASR。无模型资产读取/实际ASR/设备/PSS/性能/ASan验收。native期望SHA保A待另授identitybootstrap，full/新APK/安全提交未执行，旧A包不是B交付。

0.8 候选入口为：首页 → **日志**，不再有模型页→日志的兄弟捷径。页内返回与系统 Back 同入口，返回首页；异常 task-root 先建立 Main 首页。日志按先后显示，“最新”跳到底部；前台每秒刷新，不自动滚动打断阅读。说明默认收起，导出状态、错误和风险仍可见。见[导航说明](app-navigation.md)。本轮尚未完成 fresh 审查/full/本地提交，不因历史构建记录宣称新包可验。

每条包含中文说明、固定事件代码、时间戳（日期、毫秒、时区偏移）、App/IME/SHARED 来源、任务 UUID（有任务时）与可用的单调时钟耗时。模型导入/删除等管理事件也记录起止，但暂不提供其总耗时。

## 事件含义
- `MODEL_ACCESS_*`：模型路径与当前就绪检查；消耗已明确校验或轻恢复的文件证明，请求不隐式做 SHA；不是内存加载。
- `MODEL_LOAD_STARTED/COMPLETED`：真实 MNN create/config/load 的起止。
- `INFERENCE_STARTED/COMPLETED`：真实 response 的起止；只有正常结束才记录完成。
- `REQUEST_SUCCESS`：返回的转写响应解析成功，**不代表报告保存、编辑器提交或日志导出成功**。
- `REQUEST_FAILURE`：访问/库加载/native/响应协议失败。
- `LOG_TELEMETRY_FAILED`：阶段记录缺失或异常，**不代表转写失败**。日志故障不能改变正确的转写结果。

日志不记录转写正文、录音、外部 URI/路径或原始异常。固定英文细节码只表示安全分类。

## 保存与导出
本地最多1000条，超过后淘汰最旧事件，淘汰数按进程统计。保存与历史恢复在独立后台线程，突然杀进程可能丢失未刷盘事件；不是崩溃堆栈或系统 logcat。概况显示当前保存错误和初始历史恢复状态。清除转写结果或删除模型不会清除日志。

点击 **导出** 后选择系统保存位置。导出的是**点击时的不可变快照**，选位置期间新增的日志不包含在内。文件为UTF-8 TXT，固定英文事件代码与时间戳/任务/耗时和App中相同。单次导出在文件提供方成功关闭流后才显示成功，数量单位是 bytes。

- 取消选择不会写入日志；旋转或销毁页面会让未提交的旧选择失效，请重新导出。
- 已开始写入的导出可在离开页面后安全收尾；再进入页面可看到进程中的最近状态。
- 同时只允许一次选择/导出。提供方卡在打开/写入/关闭时，需要等待，不提前释放导出占用，也不占用ASR任务锁。
- 写入失败可能在用户选择的位置留下不完整文件，不自动重试或删除外部文件。
- 云文件提供方可能联网同步；App本身未增加网络或存储权限。

录音时返回首页需要明确三选；停止并转写留在转写页，或丢弃后返回首页再进入日志。native 推理期间可查看，不中止或释放原 owner。模型页先返回首页才能进入日志，列表离开仍遵守本页 own 导入/校验确认与实际收尾合同。

## 验证边界
主机测试覆盖日志核心、生产推理适配层、导出票据/后台占用及失败。Android编译/包检查不能证明真实文件选择器、JNI运行、IME和UI均正确；手机端请按 `runtime-logs-validation.md` 手动验证。
