# 0.6 运行日志审查处置

两路只读审查正式完成：`runtime-logs-safety.md`、`runtime-logs-export.md`。未发现普通生产路径发布阻塞。父采纳此有限范围结论，交付手机验证包而非宣称零缺陷/设备已验收。

| 发现 | 处置 | 理由与剩余影响 |
|---|---|---|
| Safety F1: sink Error导致publish标志不恢复 | 后续加固 | 当前唯一生产sink为受限写盘调度，普通IO/RuntimeException已有隔离；非RuntimeException Error后可能仅内存刷新而自动写盘停止。ASR当前结果仍保留，但不能声称logger自行恢复。 |
| Safety F2:近Long.MAX_VALUE恢复序号耗尽后续append | 后续加固 | 仅异常篡改但语法有效的私有历史或不现实的自然序号触发；无跨App边界。应补恢复规范化和多次append回归。 |
| Export F1: admitted未执行的维护任务缺终态日志 | 后续修补 | 执行器拒绝/异常提前退出时UI/owner仍失败与释放，但日志可能仅STARTED；不虚假成功。需logging-enabled rejection/finalizer回归。 |
| Export F2: picker打开失败文案被EXPIRED覆盖 | 后续易用性修补 | 释放/禁止写入/重试安全不受影响，但刷新后原因不准确。需固定picker-unavailable状态或局部通知持续。 |

当前不在最后审查后改源码，避免未经复核/重新构建的追加修补混入APK。上述问题已纳入result.json限制，不以测试通过隐去。

## 验证依据
- b599e59ef完整构建exit0/APK_READY；新JNI ABI与Java一起链接打包。
- 全量host：既有945数值检查、core8组、adapter9组330检查、export96检查，其他模型/owner/provider/mutation/binding/source检查通过。各单位不相加。
- 99构建输入/100冻结文件指纹和APK SHA复核；578底层MNN对象相同，但JNI DSO已改变。
- APK/6派生报告绑定与standalone包checker通过；只有RECORD_AUDIO权限。
- 审查者只读源码/测试，不执行测试/构建。真实JNI异常路径/阶段时序、SAF系统回调、Activity绘制和设备生命周期仍待手机验证。

APK `dist/qwen-asr-minimal-debug.apk`：0.6-debug/code6，2466664 bytes，SHA256 `d3aeda18c78f86b3a24fe9c030e29f83047e49cf8f1333fbf65ef59bf10c3851`。
