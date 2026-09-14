# 远程 ADB 开发环境

项目最小 Flake 提供 `android-tools`，目前宿主支持 `x86_64-linux`。这只是 ADB 工具环境，尚不包含完整 Android SDK/NDK 或模型转换依赖。

## 使用

仓库新文件尚未加入 Git 时，Nix 的 Git flake source 会忽略它们。**下载模型后不要使用根目录 `path:$PWD`，这会把被 Git 忽略的大文件也复制到 Nix store。** 使用项目包装脚本，仅镜像 flake/lock 后进入环境，无需改动暂存区：

```bash
bash scripts/nix-env.sh adb
adb version
adb connect 100.64.0.3:33317
adb devices -l
```

不进入 shell 的方式：

```bash
bash scripts/nix-env.sh adb adb version
bash scripts/nix-env.sh adb adb connect 100.64.0.3:33317
```

Flake/lock 被 Git 跟踪后也可以使用 `nix develop .#adb`（Git source 会过滤忽略文件）。`flake.lock` 固定 nixpkgs；进入环境不会自动连接设备、开端口或修改系统设置。

## 手机授权

- 常规 TCP ADB：手机必须已在可达地址监听指定端口。默认尝试 `5555`，并非所有设备都使用该端口。
- 出现“允许 USB 调试/允许此计算机调试”提示时，请确认是本次连接后授权。仅对可信开发机选择长期允许。
- Android 无线调试配对模式：需要手机界面的**配对 IP/端口**与一次性配对码，执行 `adb pair HOST:PAIR_PORT`；随后使用界面中的**连接端口**执行 `adb connect HOST:CONNECT_PORT`。两个端口通常不同，不应猜测或扫描。
- `unauthorized`：等待手机授权；`offline`：连接存在但暂不可用；`device`：ADB transport 已就绪，仍需只读命令确认。
- `Connection refused`：端口未监听或被拒绝；连接超时：检查路由、防火墙和端口。此时仅点击手机授权一般无法解决。

主机的 ADB 私钥由 adb 管理，通常位于 `~/.android/adbkey`，不复制到项目、不提交、不打印。需要配对时，不把一次性配对码写入文档或日志。

## 安全与范围

- 仅连接用户指定设备；不设置 `adb -a` 暴露本机 ADB server，不扫描端口。
- 授权后首先只读获取型号、Android/API 与 ABI，不读取个人内容，不安装 APK 或更改设置。
- TCP ADB 只应在可信网络/VPN 中使用，不开放到公网；工作结束可用 `adb disconnect HOST:PORT`，并在手机关闭无线调试。

## 当前状态

Flake 已锁定 nixpkgs，`nix develop` 中 ADB 37.0.0 可运行。已完成无线配对并成功连接 `100.64.0.3:33317`，transport 状态为 `device`；只读确认 vivo V1986A / Android 12 / API 31 / arm64-v8a。配对码不保存在项目文件中。

`5555` 仅为前述常规 TCP 示例，本设备当前需使用：

```bash
bash scripts/nix-env.sh adb adb connect 100.64.0.3:33317
bash scripts/nix-env.sh adb adb -s 100.64.0.3:33317 get-state
```

无线调试端口可能在重启或重新开启后变化，以手机界面为准。Nix check 已修正沙箱可写 HOME，`nix flake check` 全部通过，`nix run ...#adb -- version` 验证通过；详细记录见 `../progress.md`。
