# 2A fresh审查处置

Task llm-asr-76d.6；父p1-steward唯一writer。输入审查.work/p1b-review/final-review.md及handoff已完整消费；同sol fresh上下文隔离，非跨家族。

## B1 P1：CLOSED（修复及fresh定点回审接受）
FakePorts.Backend.cleanup成功后未清空旧request。真实仪器InputConnection序列首次成功r1→同backend排队r2→invalidate→drain跳过capture/transcribe→cleanup(r2)命中残留r1断言。静态控制流发现，未执行Android；编译/11方法DEX不能排除此失败。

只授权原writer窄修fixture request生命周期，保留同request校验、cleanup gate/owner收尾。补共享host回归覆盖相同backend成功→第二次排队即取消→cleanup，先在未修fake上编译成功并精确AssertionError red，再修后green。必要更新host wrapper/driver精确清单与instrumentation共享调用，刷新两APK/行为链/freeze/status，保留原2A历史证据。禁止改生产状态机/弱化断言/提前2B/full/锁prepare/依赖变更。

## N1 P2：defer到2B已接受的prepare文档护栏
主树deactivate+--write-locks路径已废弃，后续只能隔离prepare并限定新锁导出、前后旧锁metadata保护。事故恢复已独立核before/current/base/隔离前后exact，无未知损失，不重启prepare。旧ignored历史init/log保留但不得发布为推荐入口。

## 已核且不重开
原6JUnit与五阶段fixture行为red、包签名raw/全DEX11方法、195/80/358域和旧锁metadata恢复证据已review实核。2B缺失是计划pending非本2A额外阻塞；设备runner/SAF/InputConnection运行notrun允许，不据此声称M5完成。

## B1关闭证据
父完整消费.work/p1b-review-2/final-review.md/handoff：一行request=null保留身份/gate/生产收尾，真实修前7case唯一目标red、修后7case绿及行为链0/1/0/1/0、新APK全DEX12方法及freeze47b27617...205bfb/旧域保持均review独立核齐。回审仅静态证据解析、未重跑测试或Android。父接受B1 CLOSED，原已核2A不重开；不是M5或设备通过。

## Next Step
原writer进入2B工程接线：正式checker/失败fixtures、统一入口/collector/维护库存/prepare废弃文档护栏及针对性验证；冻结handoff后fresh审查，最终full另派验证leaf，不提前提交或交付。
