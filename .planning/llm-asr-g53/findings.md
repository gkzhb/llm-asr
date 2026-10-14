# Findings

- Pi项目skill可使用.agents/skills/<name>/SKILL.md，递归发现且可版本控制；.pi/全部被本项目ignore，不在其下写共享skill。
- 当前Gradle8.7/AGP8.6.1/Kotlin2.0.21固定；settings集中仓库与FAIL_ON_PROJECT_REPOS；module配置拒动态/range/latest/SNAPSHOT，activateDependencyLocking；verification-metadata SHA校验，verify-gradle脚本强制strict且拒重写锁/metadata。
- 目前没有libs.versions.toml，它是后续集中声明建议，不冒充已实现或完整锁。
- Nix固定JDK/SDK/NDK及开发工具；native/source-lock锁MNN/KleidiAI归档/补丁/tree，CMake经Gradle任务构建，单DSO/native provenance/ELF gate。
- Python模型工具独立model-tools/pyproject.toml+uv.lock；模型权重由model-lock与应用bundle管理，不当Gradle依赖；不得混代码license与权重许可。
- 源码锁/metadata只是来源固定，不证明无漏洞或首次publisher可信；offline只证明已准备缓存可用，不自动hermetic。
- 文档及skill不具权限执行任意第三方脚本/下载/设备/push或修改架构；模型驻留与sherpa方案是planning，不是现在runtime实现。
