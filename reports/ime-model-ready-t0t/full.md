# llm-asr-t0t 唯一正式 full — FAIL / 缺离线 compiler 配置闭包

## 输入与真实命令

HEAD/main `64d00d16e13577b9e6ad049511acd96b3e7d7d6b`，七维护候选以 `.work/t0t-r1-fix/candidate-sha256.json` 冻结；新增本目录报告只 supplemental，不修改被测产品/测试输入。未 stage/commit，不将 dirty freeze 标成已测试新 commit。

原入口命令：

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

实际 supervisor 仅包一层 `.work/t0t-full/full-command.sh`，固定项目 `GRADLE_USER_HOME`、白名单环境落盘后 exec上述verify-gradle入口。Nix无下载，Gradle strict/offline，不写锁/verification metadata/expected、不提高原worker1/JVM1536/JUnit512/native2或任一内部timeout。full原无统一外超时，owned observer不添加/放宽内部预算。

宿主 `b9c45abab` **failed exit1**；outer root_reaped/owned_exited=true，primary=null。真实日志 `.work/verify-local/full-maAm4X/build.log`，完整raw `.work/t0t-full/commands/full/{execution.json,execution.stdout,execution.stderr}`。Gradle `BUILD FAILED in 2m28s`、`4 actionable tasks: 4 executed`、pipeline Gradle1/tee0/scan1；没有自动第二full。

## 失败层级

`:core:compileKotlin` 参数隔离/快照转换创建失败，根配置 `:core:kotlinBuildToolsApiClasspath` 缺 `org.jetbrains.kotlin:kotlin-build-tools-impl:2.0.21` 离线可用版本，随后列17项原锁依赖未resolve级联。源码、原锁、metadata不改；不将“无离线版本”简化成jar物理不存在，不推测全部17项各自缺失。没有为补绿联网/prepare。

## 实际执行与未到达

| 层级 | 本次证据 |
|---|---|
| Android测试包/checker纯负例 | `:verifyAndroidTestChecks`执行；不是runner/设备 |
| JNI | 真JDK native描述符/arm64 JNI object/实际符号检查通过 |
| native | prepared source check，原对象增量链接，DSO SHA `4ee0b300caa099a7ef0358fdf42901b9f963d5e2be8fb8e330fb5cad55394481`，配置/provenance/ELF16KiB/static runtime通过；非全clean/模型推理 |
| staging | `:app:prepareInputs` 现native和冻结资产/config验证通过 |
| SDK编译层 | JNI脚本已用SDK35 android.jar真实混编全部维护Android源码含Service；AGP core编译失败，app正式SDK任务尚未执行，不将JNI辅助混编冒完整AGP链 |
| host/ABI/compat/mutants/fault | 本次full未到；早前targeted成功保历史，不冒本次full通过 |
| JUnit/Lint/checksum故障/三APK/签名包门 | 本次未到达；没有本轮新APK/receipt |

已见警告是SDK aapt2 override experimental、CMake旧minimum版本弃用提示；未抑制/改gate。未到Lint，不能报告本轮Lint0Error或沿用旧warning数量。

## 备份与保护

full前只备份实际会覆盖的已知generated域：`build/`、`core/build/`、`android/app/build/`、`android/asr-runtime/build/`、`android/test-host/build/`、`.work/red/`。2,944文件共325,488,640B归档并逐SHA验证，`.work/t0t-full/known-generated-before.tar` 与 `before-backup.json` 精确清单。不归档key/模型/私人内容，不清缓存/native objects，不删整个`.work`。旧三APK身份在输入JSON中；archive ignored、不纳Git。

41,505原历史输出在上述generated域之外保原位字节，旧plan/handoff/raw/formal报告/excluded保护；本轮允许正常generated更新不误报历史41944全部原地不变。后置protection-audit实际exit1，唯一mismatch为父拥有的`.planning/llm-asr-msj-next-supervision/progress.md`；原SHA f3f082b502a788afcff71bf9e894c2fa4894e19f2a595068ec48fdbb31cc25d8、当前SHA16e4e21517372985e1b37432b0ac5823a7b142228fff4854f19a1b4c7ac26fe2。请求父确认并发自有变更，leaf不回写、不自行接受为例外。其他41,505history/七候选/旧handoff/备份/index均核同；六项正常generated变化列JSON，未构建新APK。当前七候选SHA保持，新增报告仅supplemental，完整分域见full-inputs.json。

## 验证边界与后续

设备/runner/IME系统往返/低内存真实回调/Binder/mic/private/model/performance/远端CI均 NOT RUN。父须消费具体cache blocker并另授最小受控prepare；准备或再次full均不可本leaf自动重试。实际full通过后仍须安全审与限定本地提交，再告知APK可验证；当前不可交旧包或关闭任务。
