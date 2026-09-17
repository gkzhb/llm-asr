# 0.5 模型管理实施验收记录

状态：**实现、完整构建和独立源码复核完成，可供用户手动设备验收**；未执行真实设备测试。规格见[模型管理规划](model-management-plan.md)，总计划Phase16。未commit/push或访问设备。

## 基线与产物保护

- 基线：0.4-ime-debug/code4，不透明背景修复，SHA256 `2070cd7f1d1eb7a51be971c16a328d5db1a1950b99e0506b657a9337fbd4044a`。
- 旧包：`dist/pre-model-management-0.4/qwen-asr-minimal-debug.apk`。
- 旧报告：`reports/apk/pre-model-management-0.4/`。
- 初始生产输入快照：`.work/model-management-baseline/`（44项，含源码/资源/测试/构建脚本）。
- 原405项host检查为历史基线，不是新功能验证；工作区未提交成果不能用Git HEAD替代。

## 验收矩阵

| 门槛 | 所需证据 | 当前状态 |
|---|---|---|
| M1 页面/状态隔离 | 独立非exported同进程Activity、主页/IME安全导航、共享readiness/epoch、维护无ASR结果写入 | 实现/编译/包与源码复核通过；导航设备待验收 |
| M2 进度/取消/恢复 | 生产组件测试覆盖chunk/close/发布/终态、缺源预检、part先回收、空间/复用、owner收尾 | host及源码复核通过；真实provider/生命周期待验收 |
| M3 删除 | 真实repo白名单/路径拒绝、部分失败、确认epoch竞争、外部源/结果/未知文件保留 | host及源码复核通过；设备待验收 |
| 生命周期与票据 | admission前取消、busy不触发IO、executor拒绝、新页面订阅/旧票据拒绝、前台启动 | 生产纯Java策略及源码复核通过；Android回调设备待验收 |
| Android编译 | 当前全部Java源码及资源实际编译 | 通过 |
| 完整构建 | host回归、DEX/native/签名/包权限与Activity/IME检查、APK_READY/退出码 | b0453981b exit0/APK_READY |
| 独立审查 | 源码冻结后fresh只读、完整报告、所有阻塞关闭并与构建指纹一致 | 首轮/修复双复核/末次三文件窄复核完成，无剩余阻塞；69冻结/78构建输入一致 |
| 手动设备 | 导航/SAF/权限/旋转/锁屏/取消/复用/删除/重导入/视觉 | 未执行，仅提供清单 |

## 父会话复核重点

1. 控件禁用不是互斥；实际请求/确认/回调必须在共享owner处重新验证。
2. cancel调用不跨慢IO持锁，不强关流，不提前释放owner。发布保留规则与最终成功仲裁分别测试。
3. inspecting不能自动hash大文件、递归删除、把未知空间显示成0；状态与操作错误分离。
4. App/IME懒校验和管理页只有一个就绪真值；epoch改变不留下旧READY。取消/部分校验不宣称可用。
5. 维护通过真实接线保护正文/raw/导出状态，不能只断言未参与操作的fake变量。
6. 返回/Home/锁屏/配置重建与SAF回调顺序按明确契约处理；host仅证明纯Java接收事件后的行为。
7. 错误文本不输出完整外部URI或私人路径；云provider可能联网，App权限不因此放开。
8. native DSO、录音gate、IME提交隔离保持不变；源码新增/修改必须进入构建输入指纹。

## 历史核心修复检查点（后续Android与最终证据见下方）

- 核心writer `1e38833a` 报告：`.pi-subagents/artifacts/outputs/1e38833a/.work/model-management-core-repair-output.md`。
- 父独立复跑 `b8cc832f1` exit0：15组、847项host检查通过，日志`.pi/tasks/session-1194494-1194494/b8cc832f1.output`。
- 生产part空间次序变异已能编译并以行为断言失败，不把先前缺依赖的javac失败当红证据；新增取消/发布/终态和真实IO故障测试有界收尾。
- 尚不包含Android页面/AppGraph/SAF/系统生命周期接线；不提升为设备或完整0.5验收。
- Android唯一writer `a25d515e` 已接续；此时不并行改其生产源码，不运行读取变化源码的验收构建。

## 最终交付证据

- APK `dist/qwen-asr-minimal-debug.apk`，0.5-debug/code5，2,442,088 bytes，SHA256 `6e4fbf7a4cf07262912019dc667258123a32f95931b13bb1c046cf6e77471073`。
- 父完整构建b0453981b exit0/APK_READY，日志`.pi/tasks/session-1194494-1194494/b0453981b.output`。945数值断言/16组、8组review回归、provider边界、3可编译行为mutants、source-policy及actual checker绑定fixtures通过；不将异类单位相加。监听器检查由35改33是修复吞断言，不沿用947历史计数。
- 首轮及聚焦复核报告位于`reports/review/model-management-*.md`，最终闭环见`model-management-disposition.md`和`model-management-final-publication-review.md`。所有review只做静态阅读/哈希，不冒称独立执行父测试。
- 父核对78构建输入全部现SHA、69最终冻结输入包含且匹配、当前APK/六报告绑定、native DSO与旧0.4逐字一致。末次复核后未改生产/测试/脚本，无需再次构建。
- 仍保留：真实SAF/系统生命周期/视觉设备待测；泛化外部owner提示、小manifest同步bootstrap、空目录删除失败未单列、规划完成前复用总数非已确认累计、失败inspect后未知文件数量可能仍是缓存。无后台服务/持续运行或fsync七文件事务承诺。

## 执行与限制

单writer修改生产源码/测试/构建输入脚本，父会话负责规格、验收记录与后续审查/构建。全量APK构建之前不覆盖当前result/status为0.5，不把未完成签名文件视为已交付。

本轮不改变P0数值、独立语料或性能限制。无模型驻留/后台服务/在线下载/多模型。真实SAF、AudioRecord、InputConnection、系统UI不在host测试范围。
