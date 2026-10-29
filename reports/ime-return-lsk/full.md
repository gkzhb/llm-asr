# llm-asr-lsk 正式 full：阻塞首停

基线/current HEAD `d5b38ac7713dfb91dd1f261ff9f6a71984fee040`；父 lsk-parent 唯一 Beads writer、lsk-build 唯一 full 执行 writer。fresh 同 sol fork:false 上下文隔离 NO BLOCKING 已由父接受（非跨家族）；两项 LOW 测试覆盖 gap defer，未修改冻结源码/测试。

## 当次真实失败

唯一正式命令：

```sh
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

后台 `b5a076152` 自动 failed 通知已消费，真实 exit1 / 362.145秒。Gradle1/tee0/DEXscan1，3 actionable tasks全部executed。`:app:prepareInputs` 拒绝：

```text
ValueError: Frozen product input mismatch: patches/mnn-asr-request-cleanup.patch
```

- baseline期望 SHA256 `059521069394f08ac0a89bdd2d82aa605dd39fc537a0cc6c1b5de1d0450c8392`。
- 当前/admission/HEAD真实 patch SHA256 均 `466ab7d52007e4d26b9791f60f13087fdde12793edcf5641694e2f8c1ac51f62`。
- HEAD内 baseline 的期望同上，故为本批修复前已存在的 frozen身份不一致，不是本轮 patch 漂移。不能擅自改期望凑绿，也不能把该非零当预期行为负例。

按授权首失败即停，未重试、未改 patch/native/baseline其他项、未降 gate 或下载。父须消费后另定身份修复范围/验证授权。

## 已执行与未完成

失败前 native实际编译/link、JNI真实符号、既定 native SHA/config/578objects/provenance/ELF16KiB gate通过；产物 native SHA仍 `4ee0b300caa099a7ef0358fdf42901b9f963d5e2be8fb8e330fb5cad55394481`。日志显示579个Ninja步骤，不称仅JNI增量执行；578原MNN对象最终SHA全保持，未做全nativeclean/缓存清除。没有native模型计算或设备运行证据。

正式42JUnit/完整host/生产Lint/真实checksum与Lintfixture/fixture链/三APK装配和正式包检查均未走完，不能沿用上批结果冒当前full成功。准备阶段已备份核验1336生成文件，并freshen22明确生成路径，三APK原位置目前缺失，旧包仅备份保留；**没有本批可交付APK或新正式receipt/input绑定**。未自动还原生成目录以伪装成功，也不重跑成功targeted。

## 证据封口

full前冻结238build/10module/84native输入与七受审候选保持；2628保护文件首封口核同，HEAD不变/index空。正式owned记录 owned_exited/root_reaped=true、timeout=false，记录的进程身份均已退出，无相关活命令。完整raw/argv/exit/日志SHA、生成变化与失败证据见 ignored `.work/lsk-build/full/failure-seal.json`、`handoff.md`；非敏感输入摘要见[full-inputs](full-inputs.json)。本页/status追加为supplemental，未改受审产品输入。

未stage/commit/push，不宣称APK可验证。Androidrunner/系统IME/fcitx5/Binder/窗口/SAF/JNI-device、4K16K、mic/private/模型运算/下载/远端CI均NOT RUN。Host no-hide模型与SDK/JNI编译不是用户设备验收。
