# Findings / resident-plan-2

- base f7d1d795f591299a3aa70194537652ad33691275，IME23p已提交/closed但设备not run；本次只读收窄A。
- zcm in_progress/owner p1-steward，无显式blocks；z1w blocked仍依赖tc3/ujz/msj，msj只选择本轮模型/IME产品方向，不解除设备质量前置。
- 旧37路径是提案不是写权。有效receipt正常重启不应强制手动全SHA；后台恢复可轻检，但无效/未知只能明确引导完整校验，不能在请求中降级hash/load。
- 旧B有锁定MNN小文本证据：reset非完整请求清理，KV延迟清；本轮复用，不重新扫描或执行模型。
- IME增量仅修改外部UI离开时hide异常不阻止launch、返回上一IME/picker和重新开始；未改变owner/capture流水线。A须保留leaveForExternalUi当前两段try、restartSession与字段隔离，不能把隐藏当owner释放。
- Herdr单次agent list：本leaf wY:p59 working；父wY:p1 done，原IME wY:p55 idle；无其他同库working writer。只读元数据，不读session正文/不控制agent。
- TaskCoordinator.submit在caller上admitted、worker上task；不能把stat放admitted。AppGraph当前startupOnce已提交MAINTENANCE task；最小自动快恢复可复用该worker，不增加执行器/排队框架，也不在转写worker触发恢复或full SHA。
- SDK编译脚本递归find，无硬coded package map；core生产默认收所有非app/runtime源，runtime Kotlin从root精确表导出。新core+runtime各一类可不改模块Gradle文件。
- 旧提案37路径不足以代表最小安全scope：删无需改的kotlin_runtime_migration_test.py（canonical host自动纳core新源），补AsrImeService、ModelOperationControlTest、ModelReviewFixTest、RuntimeContractTest，共40。后两分别含safe orphan允许READY的旧契约和真实ImeBackend构造器，必须精确适配而不能删测试。
- 推荐已存在startupOnce MAINTENANCE一次自动轻恢复；管理页独立startRestore（复用INSPECT的明确分支）只快验，普通inspect不授READY；VERIFY仍强制全SHA。实际请求只消费proof/轻检，不在转写内恢复，不新executor。
- 独立新四负例driver普通/-O每模式9次有限编译×180秒+20JVM×30秒、2400秒总上限；不改旧suite timeout/负例，恢复后逐mutant重编green。SDK、JVM、runner、设备证据分层。
- 错误：只读旧扩展名TaskCoordinator.java、FileSafety.java、ModelUiText.java各ENOENT，均随后改读现存.kt；无产品或测试执行。
