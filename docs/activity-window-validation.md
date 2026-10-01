# Activity 窗口与返回适配（M6）

任务：`llm-asr-76d.7`；执行上下文：`.planning/llm-asr-76d.7/`。

## 实现范围

首页、模型管理、日志三页（包括初始化失败页）共用平台适配器 `platform/ActivityWindow.java`，不改输入法 Service 窗口。API30+ 显式 edge-to-edge，API29 使用平台 layout flags；外层视口按系统栏、显示刘海与可见键盘的安全边距避让，内部内容继续保留原设计间距。重复 insets 分发从初始 padding 计算，不反复累加；保留系统三键导航对比度保护。

API33+ 在页面 resume 注册 `OnBackInvokedCallback`、pause 解除，指向原 `onBackPressed`；API29–32 继续平台旧入口。Manifest 仅三 Activity 显式开启回调，不为 IME 声明新返回策略。没有新增 AndroidX/Compose 依赖，也不宣称支持 predictive-back 动画。

保持旧返回语义：

- 首页：先尝试取消录音，等待 worker 释放；已进入转写等忙任务提示等待，不提前中断 JNI 或释放 owner；空闲走平台默认返回（包括系统对 launcher 根页的处理）。
- 模型页：本页可取消操作先显示确认；确认离开仅请求取消，清理期间共享 owner 仍由真实操作持有。
- 日志页：允许离开，已开始的独立 provider IO 不被伪装为撤回；原页面票据销毁与 slot 收尾不变。

未修改模型清单、CPU 数学、录音 gate、IME 字段/正文隔离、权限、包名/组件名、签名升级身份或版本（仍 `0.6-debug` / code6；本轮以 APK SHA 区分）。

## 主机验证层次

- `tests/activity_window_test.py`：编译并实际执行生产 adapter，对受控 fake platform 检查 API29/30/32/33/35 分支、每边安全边距/键盘mask、重复分发、基础 padding、回调登记/解除/委派；三个可编译生产 mutants 必须因行为断言失败。**不是 Android 平台、真实 WindowInsets 或手势测试。**
- `tests/activity_window_source_test.py`：三页正常/失败路径与 lifecycle/Manifest 接线、旧首页源码红测试和真实 weakening fixtures。属于 lexical 接线保护，不是运行三 Activity。
- 原 host 并发、录音/owner/模型文件/导出/日志/IME 与 production mutants 保留；`scripts/verify-gradle.sh --offline --rerun-tasks` 运行全源 Java/Kotlin/JNI、显式 native、签名 APK 与精确包/实际 Manifest 回调 opt-in/输入指纹门槛。
- 产品冻结清单明确刷新本轮三页/Manifest/新增 adapter；未改资产/native/签名 SHA 门槛。缓存准备后的 `--offline` 不等于完整 hermetic 构建。

本页描述验证契约；当轮实际通过/失败、APK 身份和提交以本任务 plan 及 `reports/window-m6/` 为准。没有通过证据时不宣称交付。

## 设备手动验收矩阵（尚未执行）

代理未获本轮设备、麦克风或私人内容授权，不安装、不自动录音。建议用户在自己授权的公开测试内容上核验：

1. API29 与 Android15+：分别手势、三键导航；三个页面顶部标题/返回、底部内容可见且可滚动，浅色系统栏图标清晰。
2. 竖屏/横屏、刘海/挖孔、分屏与窗口尺寸切换：没有遮挡、边距不累加、不重复留白；字体100%/大字体下按钮可读与可滚动。
3. 文本选择/编辑对话框出现键盘：键盘不覆盖编辑及确认按钮；键盘隐藏后边距恢复，返回先隐藏键盘/关闭对话框，不绕过录音/模型策略。
4. 首页空闲：返回按系统默认离开/回后台；录音：返回请求取消并等待麦克风 worker 清理；转写：返回忙提示，任务不提前结束/解锁。
5. 模型导入/校验：返回确认保留/取消并离开，重进显示真实终态；日志 provider 已开始写入时离开不会假报撤销或放开阻塞slot。
6. 切到语音 IME：原键盘显示、录音/停止/预览/换框/提交策略不变，不将 Activity 的 insets 应用到 IME 面板。

主机测试通过不替代以上设备验收；Android15视觉遮挡仅静态风险发现，未声称已在设备复现。
