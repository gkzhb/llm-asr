# M6 直接源码自审（非独立Agent审查）

## 范围与结论

核对三Activity完整源码、ActivityWindow、Manifest、产品冻结清单、主host接线、架构map与实际APK checker。首轮b4287df23全验证通过；自审发现并修复一项包验证防误证据缺口，末次b8f9fc4e0完整重跑通过，164freeze/current/report一致，7实际包identity负例全部通过。无设备运行证据。

## 接受修正

首轮checker以字符串拆分activity块，末尾LogsActivity块可包含随后的Service，且callback检查受revision_scope文案条件控制。即便实际本轮Manifest正确，检查器可能借用兄弟或嵌套属性，不能作为后续可靠gate。

修正：无条件校验三页面；按aapt树节点缩进界定component，仅直接属性计数；活动名字唯一。新增真实checker负例把日志页回调属性移动到Service或嵌套层，必须拒绝。源码与测试改动后必须重新跑全验证并绑定164输入，不能继承首轮指纹。

## 保持契约的证据

- 首页/模型页onBackPressed方法体未改，callback委派同入口；不直接finish首页绕过cancel/busy。
- 生命周期在resume登记与pause解除，重复登记/解除fake-platform断言；API33类型放嵌套类，老SDK只走版本守卫；实际Android verifier行为待设备。
- 对话框/软键盘属于平台window调度，本轮不自己拦截优先级OVERLAY，不声明predictive动画。真实IME/dialog返回优先级待验证。
- viewport初始padding只取一次，insets每边安全max，不累计，未consume，不应用到IME；API29cutout可空并有case。窗口layout改变的真实设备/厂商行为未由stub证明。
- 模型/日志页保持inner spacing，主页内容/权限和所有业务方法体不改；初始化失败页也装窗口处理且返回无需graph。
- 10Kotlin保留不再迁移，native d5bacffc...416/CPU配置/签名e9365bf6...c16c/公开资产不改；产品锁仅Manifest/三Activity/新增adapter改变，门槛未撤销。
- 原并发/owner/录音gate/IME/文件安全/导出/日志测试与三个生产mutants保持，新增生产adapter69checks与3可编译mutants；fake-platform不是Android运行。

## 明确限制

API29/Android15+/导航模式/大字体/分屏/键盘/真实返回（含launcher根页、初始化失败、录音及模型确认对话框）全部not run。主host不执行Activity、DisplayCutout或真实dispatcher；M5仪器与通用设备任务不因M6工程完成被关闭。API35部分deprecated接口为平台兼容所需，不吞D8/R8诊断。保持本轮debug版本，不作为release/UI完整现代化验收。
