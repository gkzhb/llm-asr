# Findings / ime-plan

## Admission
- llm-asr-23p，父唯一 owner/Beads writer p1-steward；本 leaf 只方案。
- main7a3aa5317dec905c6a952f16beff10a58ca9c481，tracked/index 无差异；旧未跟踪 planning 与两 docs 不动。
- bd context 项目 embedded llm_asr；show IN_PROGRESS、无显示 blocks。runtime 以父交接为准，无本 leaf Herdr 查询接口，不另查 session 正文。

## 当前源码
- AsrImeService.kt:65–83 显式确认与切换 picker；leave 先 visible=false/session=null 再 controller invalidate→hide→launch。onStartInputView:99–100 恢复 fresh session，onWindowShown:102 仅 render；不能假设 picker 取消必触发生命周期。
- ImeController.kt:33–45 invalidate 清旧 preview/valid，cancel recording；leaveForExternalUi 先 invalidate/hide 再 catch RuntimeException launch，未释放 owner。hide 在 try 外，异常路径需补。
- ImeController.kt:76–98 worker 返回用 live(session,rev) 拒迟到；recording 清除在 runner terminal；commit:101–112 显式 once-only，无自动提交。
- RequestRunner.kt:64–80 cleanup finally，TaskCoordinator 实际释放需要读其调用点；RecordingControl.java:28–40 committed 后 cancel=false，不等于中止 native。
- tests/ImeSessionTest.kt:106–125 覆盖生产 controller leave 顺序/取消/迟到/fresh begin/launch throw，但不是系统 picker 生命周期证明。
- NextAppContractTest.java:90–113 真 Service+Controller host fixture；kotlin_next_migration_test.py:60–71 有 InputMethodService/IMM fakes，可扩既有框架，不复制生产分支。当前 fake hide 无状态、showInputView 恒 true，需可控状态/记录调用再证明故障路径。
- build.gradle.kts:43–51 verifyHost 接 scripts/test-minimal-apk.sh；:core 测试已含 ImeSessionTest（core/build.gradle.kts:37）。无需新增模块或测试框架。

## 本地平台证据
- compile35/min29（android/app/build.gradle.kts:51/56），flake.nix:25–32 固定 platform35。
- SDK /nix/store/hac3cfg5nx1hzzfbyln5pi54xv0rk5nf-android-sdk-platforms-35/libexec/android-sdk/platforms/android-35。
- android-stubs-src.jar!android/inputmethodservice/InputMethodService.java:63 为 public final boolean switchToPreviousInputMethod()，实现仅 throw Stub；data/api-versions.xml:30768 since28。
- android-stubs-src.jar!android/view/inputmethod/InputMethodManager.java:128 showInputMethodPicker() void；api-versions.xml:72246 class since3，:72285 方法未覆写 since，即继承 since3。
- 没有完整 AOSP 实现/Javadoc sources 本地证据；不能从 boolean 签名推导精准成功/历史/异步完成语义。true 暂按请求报告成功处理、不额外弹 picker，false/RuntimeException 降级；父实施需把真实设备历史和结果明确 unknown，不能说必返 fcitx5。

## Proposal decision
- 直接扩 Service私有helper/两个现有生产文件，不新导航层/历史状态。
- hide RuntimeException不可阻断平台尝试；无回调仍可见的无效会话，沿原丢弃按钮扩“丢弃 / 重新开始”，只手动fresh begin、不自动record/commit，防止假设系统隐藏必生效。
- TaskCoordinator.kt:49–55 task finally后release，RequestRunner cleanup/finished均在其内，按钮不得改owner释放。
- 四项输出位于 .work/ime-plan；输入SHA清单与handoff绑定。父接受后另授唯一writer，本leaf不实施。

## Errors / boundaries
- 一次 rg 引用了不存在 android/build.gradle.kts；一次 nl 猜错 RequestRunner.java/RecordingControl.kt（实际 kt/java），一次 scripts/host* glob 未匹配；均只读检索错误，不是测试失败。已按真实路径读取。
- 未运行测试/编译/构建/复杂度工具；未下载/联网/设备；旧 .work 中 fake 不是平台证据。
