# Findings — llm-asr-76d.5 / p1-test

- HEAD实核7eace17a8ea636db6010f95d2229c28dc2f78467，Beads只读show确认blocked/assignee p1-steward；不claim或写Beads。
- 父监督与handoff确认真实Gradle/Lint/full未执行，缺固定Nix闭包；13审查文件/179inputs冻结。现4tracked修改与本轮新入口/测试/文档均非本leaf writer。
- 固定flake nixpkgs eaad089433ca2bb662274377d33df3d0e51ef28b / narHash sha256-H6GwaZzZf+4npqv0tph94w9tZddSjFjmQrVsW0z78uk=；apk JDK17、NDK28.2.13676358、SDK35/build-tools35、Kotlin2.0.21（sha256 0352c0a45bd22f80f6b26e485cd04da8047baa5de54865281fb9f89a4a7bcf2a）、python312等。
- Nix2.34.8配置substituter https://cache.nixos.org/、require-sigs=true、cache.nixos.org-1公钥、sandbox=true、accept-flake-config=false；默认maxjobs12须显式压2。
- settings仅Google/MavenCentral/PluginPortal，AGP8.6.1与Kotlin plugin2.0.21。Wrapper8.7官方downloads.gradle.org/sha256544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d。
- verification metadata verify-metadata=true、verify-signatures=false；Lint仅model/typedef-remover31.6.1声明，目前不能据此声称Lint运行闭包齐备。不会解析或自动写新metadata。
- 当前中国大陆网络，可用http://localhost:10881无凭据本地路由；父接续说明父未单独核验用户代理授权，故不宣称为父已核的单独授权。工具下载已有授权，使用代理不降TLS/哈希/签名或换源。
- 实际工具shell已进入：JDK17.0.20.1+1、CMake4.4.2、Ninja1.13.2、aapt2 2.19-11948202、NDK clang19.0.1、Kotlin2.0.21。PATH python3实为3.14.7-env，与显式pkgs.python312 3.12.14不同，需如实区分。
- Kotlin wrapper传播JDK21.0.12.1+1，JAVA_HOME使编译器实际运行JRE17；许可归档2.0.21实际license/LICENSE.txt/NOTICE.txt，不是recipe条件式share路径。独占检查修正后严格cache-only exit0（b7200f827），实际JDK21/固定python312/NDK/Kotlin等完整smoke通过。
- 原freeze 13/179及flake/lock/HEAD最终全部匹配，无漂移；review指出179不含native/source-lock.json，不能扩称完整产品绑定。本leaf仅追加最终该文件观测SHA，不造前置覆盖证据。
- Lint既有两组件四JAR/POM逐个实算匹配当前metadata，但完整Lint runtime仍未准备/解析。未做工程测试/产品构建/设备/提交。
- handoff .work/p1-toolchain/handoff.md已写；所有bg已终止无继续下载。bc945986e文件141、b9377f508失败1、b7200f827完成0，错误路径和SIGPIPE未知触发来源均保留。
- tool-store-info.json保留现存NAR hashes及签名（本地build的NDK/SDK组合/Kotlin可能无签名，不等于关闭require-sigs；该设置针对受信cache替代物）。tool-closure-paths.txt离线查询exit0；NDK既存原包实算sha1/sha256已保存，sha256是本地观测非上游独立证明。
