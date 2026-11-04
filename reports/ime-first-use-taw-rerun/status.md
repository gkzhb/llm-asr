# IME 首次使用 taw：新正式 full 通过，待安全本地提交

Task `llm-asr-taw`；唯一验证 leaf `taw-full`；父 `p1-steward` 唯一 Beads writer。
**工程状态：FORMAL_FULL_PASS_PENDING_SAFE_COMMIT。尚未提交，不宣称 APK 已交付。**

用户授权必要依赖恢复与同需求自动推进；父消费正常 Gradle 恢复三项既有批准 POM、
六配置 offline/strict 语义验证后另授本次唯一新 full，并非盲重试。
后台 `b96238094` **completed / exit0**，184 actionable tasks 全部 executed。
原 cache-only Nix、strict/offline Gradle、single worker、全部原 gate/预算保持。
旧失败 full 与 `reports/ime-first-use-taw/` 四报告按原 SHA 只读保留。

当次实际：完整原 host/runtime15route/ABI/编译 mutants/cleanup、SDK Java/Kotlin、
JNI/native、42 新 JUnit、生产 Lint、隔离真实 NewApi/validator、checksum `0→1→0`、
五阶段 compiling fixture `0→1→0→1→0`、三包签名/DEX 与独立产品最终 gate 全部通过。
生产 Lint **Error/Fatal 均0；app24 / runtime17 warnings**，类别明确披露，未抑制。

五项 JUnit/Hamcrest 工件在启动与正式结束（checksum 子 daemon 清理之后）均存在，
批准 SHA、大小、mode、mtime 完全保持；没有 mid-run polling 或连续删除事件追踪。
后续真实 Lint/JUnit 成功不再遭旧 availability blocker；未改 GC policy 或补下载。

输入逐 SHA 与产物身份见 [inputs](inputs.json)、[artifacts](artifacts.json)，
完整分层事实见 [full](full.md)。19产品/测试/文档候选及baseline未漂移，当前
240 build / 10 module / 84 native / 3 external 输入与启动 freeze 全等，
1986 tracked / 762 原 excluded 均保持。新报告只属 supplemental。

新三包字节恰与启动前相同，但本次 compile/package/sign/check 与新 receipts 均真实执行；
不借旧 XML/receipt 代新证据。构建在 main `3899242` dirty freeze 下执行，未测试新 commit。
Android runner/install、设备/IME/SAF/Binder/grant、麦克风/private、ASR、latency/PSS、
4K16K runtime 仍 **NOT RUN**。父消费封口后另派两阶段安全提交，成功前不交付 APK。
