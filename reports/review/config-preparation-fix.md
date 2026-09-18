# 离线模型配置准备：审查修复与验证

## 范围

在 `d9ea37f` 后新增标准库离线准备脚本、主机回归和 APK 构建只读预检；本轮关闭上一轮审查的路径规范化缺陷及历史证据混用问题。没有修改 Android/Java/JNI、模型权重、录音/IME逻辑。由当前主代理直接实现并自审，未声称独立审查或设备验收。

## R1：父路径跳转绕过逐级检查——已修复

旧实现先执行 `abspath`，将 `link/..` 折叠，导致 `O_NOFOLLOW` 不再检查原请求里的符号链接。真实CLI新回归在旧实现明确失败：请求被接受并返回0，而非规定的拒绝。

修复在 `output_directory` 规范化或打开/创建目录前拒绝任何 `..` 分量。此为明确的路径输入限制：即使普通目录的父跳转也拒绝；用户应直接指定目标目录。正常相对路径仍按cwd解析，默认输出仍按脚本仓库定位。不改两目标预检、大小/SHA核对、普通文件验证或排他hardlink发布；不用 `resolve()` 跟随链接掩盖问题。

新增生产CLI测试：
- symlink、普通目录、缺失目录、普通文件、多层缺失路径的父跳转；相对/绝对 × create/check 共20种组合，含 `python -O`。
- `--check` 不得通过折叠路径接受已存在的正确配置；内容/inode/mtime保持。
- 普通相对目录的生成与只读校验正常。
- 原幂等、不覆盖、坏来源、重复JSON、symlink/FIFO、发布异常与并发测试保留。

红测试日志 `.work/config-preparation-fix/red.log`，旧脚本副本 `pre-fix.py`；绿测试 `green.log`、`optimized.log`。16项测试在普通与优化Python下均通过。

## R2：历史与当前构建证据混用——已修正文档

`docs/local-apk-reproduction.md` 明确分出 `d9ea37f` 历史119项构建证据与当前准备脚本增量，不再称历史指纹等于当前文件；当前产物以 `reports/apk/status.md` 和构建清单为准。删除未提交时“已提交脚本”的错误表述，并说明 `..` 拒绝策略及测试入口自动准备临时父目录。

## 完整验证状态

完整构建 `b64ce963e` exit0 / APK_READY：全host（含新16项）、Java/JNI生成头与实际linked DSO符号、DEX/资源/签名/包检查全部通过；之后standalone checker再次通过。

- 122项构建输入 = 冻结输入 = 当前源码摘要，包含新准备脚本、测试和来源日志。
- 578个MNN对象及JNI DSO与上轮逐项一致；所有APK ZIP条目内容与上轮逐字相同，容器SHA不同。
- APK：2470760 bytes，SHA `66be525e2f69b12b23f5a76f5bc2d0815f97c7447a672000162d38b6d4e7dfed`；0.6-debug/code6，复用上轮本机签名。
- 日志 `.work/config-preparation-fix/build.log`、`verification.log`；产物身份见 `reports/apk/result.json`。
- shell语法、最终diff-check通过。无设备验收结论。

## 保留边界

- 仅支持具有目录fd、O_NOFOLLOW和hardlink的Linux/POSIX主机。
- 两配置不是跨文件事务；强杀可能留临时文件，不承诺目录fsync持久化或抵御可任意改写目录的恶意本机进程。
- 日志与清单为可信仓库输入，哈希不是独立来源签名；未扩展为通用模型配置导出器。
- 未访问设备、麦克风或私人数据；主机/构建通过不等同于手机运行验收。
