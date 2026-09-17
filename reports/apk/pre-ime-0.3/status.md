# 0.3 Phase12 解耦重构 — 构建与生产源码独立复核通过

- APK：`dist/qwen-asr-minimal-debug.apk`，2,405,160 bytes。
- SHA-256：`3772e2049106301f7553fdc48d25e6c11ab27f32577491de0347fdecf70152d3`。
- 版本仍为0.3-debug/versionCode3，以hash区分重构包；不冒称0.4模型管理版本。
- Android29+/arm64、target35、唯一RECORD_AUDIO权限；无网络权限或模型权重。
- 最终构建`b4b1d2d31` exit0：245 host checks、Java8/DEX/单DSO/签名/包检查通过，47项构建输入SHA全部匹配。
- 5项P0纯契约测试通过，不是新模型推理/准确率证据。
- 独立review`de31b20c`未发现正常生产路径发布阻塞，独立运行243项；其后仅修改2份测试修正证据质量，父会话245项通过，生产源码未变。
- APK内native DSO与重构前0.3逐字一致，JNI/录音gate/模型清单/权限未改。

## 交付内容

Activity只持UI/生命周期；任务协调器与请求事务独立；ApplicationContext操作层持长任务；模型仓库与SAF分离；native响应解析可host测试。
录音session同步admission后才enqueue，丢前台可取消尚未开始的采集；owner释放后通知UI读当前状态。
固定清单part先安全回收再空间检查，保留SHA/关闭流后rename/最终全量校验。没有降低校验以换性能。

## 证据与边界

- `reports/review/phase12-independent.md`、`phase12-disposition.md`；设计说明`docs/refactor-phase12.md`。
- 旧APK `dist/pre-refactor-0.3/`，旧报告`reports/apk/pre-refactor-0.3/`；本轮未提交或push。
- 未安装/访问手机、未录音/实机推理。真实Activity/SAF/硬件/存储故障、跨设备和性能仍需验收。
- host断言数不等于分支覆盖率。真实SAF枚举、写/删故障与Android报告adapter未全部自动验证。
- 单文件rename不保证7文件整体原子或断电耐久。磁盘不可写时只保证尝试终态并明确提示失败。
- 模型操作仍清空当前正文/报告；导入取消、模型删除、IME/API、服务、量化/常驻引擎均未新增。

构建日志：`.pi/tasks/session-525127-525127/b4b1d2d31.output`。
