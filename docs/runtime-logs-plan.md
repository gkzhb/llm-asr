# 0.6 运行日志实施契约

用户需求：App中独立日志页面，可见日志时间戳；模型加载、加载完成、推理完成等事件记录；导出日志文件。用户已反馈0.5私有模型路径修复无问题。

## 产品与隐私默认值
- 首页和模型管理页均有“运行日志”入口（繁忙时可进入看推理日志；正在采集录音时不能静默取消，沿用明确确认/限制入口策略）。日志页非exported、单进程，浅色不透明背景、system bar insets、可选中文字。
- App与IME统一日志，按真实事件时间记录：YYYY-MM-DD HH:mm:ss.SSS ±HH:mm（或等价清晰ISO时间含毫秒偏移）；任务来源和ID、固定事件名、耗时（单调时间）。日志按顺序显示，最新日志易查看，页面前台自动更新且不抢用户滚动。
- 至少包括：运行启动、模型校验开始/结果、MODEL_LOAD_STARTED、MODEL_LOAD_COMPLETED、INFERENCE_STARTED、INFERENCE_COMPLETED、请求成功/失败；模型管理import/verify/delete起止/取消可记录固定事件，不逐数据块刷日志。
- 日志中的“模型加载”指实际MNN内存加载，不得把SHA验证冒充加载。native response非正常完成不可标推理成功。callback失败不可引入新native错误或吞掉原native异常。
- 不记录文本（含IME预览）、音频、语言用户内容、URI、文件名路径、provider异常原文、raw堆栈。用固定事件和安全枚举/错误类型，不使用简单字符串替换脱敏。
- 本地有界保存，建议最近1000条/上限约512KiB，UTF-8；跨普通进程重启可读，异常被杀可能丢失尚未刷盘事件并需说明。不读logcat，不加存储/网络权限。磁盘失败可见且不影响ASR/READY/owner释放。
- 不自动清除日志，暂不需要新增“清空”能力以避免导出/清空额外竞态；自然滚动淘汰明确显示。模型删除与结果清理不影响日志。

## 推荐架构边界
- Pure Java typed/bounded日志存储+不可变快照/安全导出+可测试时钟。Android graph拥有单例，持久IO在独立有界队列/单线程worker；listener不持有Activity、不在锁中IO/调用UI。缓冲/任务有界，不能逐事件无限executor排队。不要在JNI回调直接同步磁盘/Android UI。
- 统一Java native调用adapter，可注入fake bridge以host覆盖App/IME共享阶段顺序、失败及脱敏。JNI加入listener参数（或等价明确生命周期），同步在真正load和response边界通知；不得全局当前Activity/global static callback串请求。
- JNI模型策略/模型hash/输入波形/返回协议不改，保留每请求释放和CPU参数。MainActivity符号若迁移须同步package/symbol检查，最小改动可保留类方法+加listener参数。新增C++异常清理路径必须可审。
- 日志页读取缓存有界快照，IO异步；SAF ACTION_CREATE_DOCUMENT导出独立固定快照。选择前捕获哪个时点应明确，不与转写TXT ExportSession混用，页面旋转/销毁/迟到picker结果不能写错快照、重复导出或泄漏Activity。取消不写；provider open/write/close后台、报错固定文案；无需持久化URI授权。
- 导出用App context和不可变snapshot，状态线程安全，页面退出可以安全收尾；不占ASR共享任务锁而阻塞正在运行的推理日志。非前台不乱启动picker/UI，应用明确提示云provider可能联网和部分写失败可能留外部不完整文件。
- 日志内部目录仅管理固定名称、拒绝叶文件/目录符号链接，父filesDir系统别名兼容（参考刚修复ModelRepository），不要对未知文件递归清理。持久文件损坏不能阻塞应用启动，不把磁盘任意文本重新作为可信日志隐私边界。

## 测试/交付
- 测试typed事件与可控时钟时间戳、顺序、日志上限/轮转、重启恢复/坏持久文件/写失败、并发append/export一致快照、失败日志不含注入私密字符串、listener异常不改推理结果。
- 生产可注入native adapter tests须覆盖成功实际事件次序、加载失败无load-complete/infer-success、推理失败无infer-complete、request失败、callback故障隔离。模型校验不可当load-complete。
- Android页面/manifest、真实JNI callback源代码门槛，Android javac和native编译；不把host fake当真机JNI已测。
- 版本0.6-debug/code6；更新全部依赖版本/Activity数量的checker/source fixtures/report-binding fixtures，不削弱旧包签名/权限/边界检查。
- 完整构建由父执行并备份旧包；新JNI DSO必然不同，MNN 578对象指纹必须保持。result/status在父全构建和独立review后更新，不冒称已验收。
