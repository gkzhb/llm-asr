# 7vl 固定ZIP导入：正式完整工程验证通过

Task `llm-asr-7vl`；唯一验证leaf `zip-7vl-full`，父 `p1-steward` 唯一Beads writer。
**FORMAL FULL PASS / PENDING SAFE COMMIT。未提交，尚不交付APK。**

双caller最小修复获父fresh接受后，唯一获准原入口完整full BG `b34489620` 真实
completed/exit0；184 actionable tasks全部executed，Gradle1h5m11s。Nix cache-only、
Gradle strict/offline、singleworker、原所有timeout/JUnit45s/资源门槛保持，无额外targeted
重跑或降gate。完整当前输入、三包及独立产品receipt绑定通过。

[完整结果与有效负例](full.md) · [输入/任务/产物/证据逐SHA](inputs.json) ·
[父已接受fresh审查摘要与边界](review.md)。

本次正式执行：ZIP/picker普通/-O、全部C1/C2/U1/N1及11个最终owner出口body、原host
尾链/ABI/IME/proof/runtime15/compiledmutants、PartRecovery真实编译提前预算缺陷拒绝、
42 fresh JUnit全零、production Lint Error/Fatal0（app24/runtime17 warnings披露）、
isolatedLint精确NewApi拒绝恢复、checksum0→1→0、fixture五phase0/1/0/1/0、真实
SDK/JNI/native、三包签名/DEX隔离与独立最终产品checker全部通过。

产品APK `android/app/build/outputs/apk/debug/app-debug.apk`，`org.llmasr.minimal` /
`0.6-debug` / code6，5432945bytes，SHA256
`63981787e54ce061d107d8bedd5f9d8fa758cb6ce21bd0850a71a725587d153b`。
版本未改，无模型嵌入/新增权限。字节与前次失败full产物相同，但本次完整compile/package/
签名/check/gates均真实fresh执行；不借旧failed证据冒通过。

source37+baseline+两已审caller、247build/10module/88native/3external及工具pre/post
全等；70owner实际module/DEX正确，旧两批failed报告与历史runtime透明例外保留。
构建为main caddb67 dirtyfreeze，不冒测试过新commit对象。

Androidrunner发现执行/install/device/systemSAF/Bindergrant/lifecycle/micprivate、实际
大模型ZIP/ASR/latencyPSS/掉电/4K16K运行及empty-cache hermetic均NOT RUN。
父消费封口后另行两阶段安全审与本地提交成功，才可向用户交付ZIP APK。
