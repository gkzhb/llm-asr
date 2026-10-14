# 项目第三方依赖skill建设

## Goal
生成可发现的项目依赖管理skill，准确区分Gradle/JVM、Nix工具链、native源码与模型资产管理；包含准入、版本锁/校验、升级、安全与分层验证，验证后限定文本本地提交。不引入依赖或改构建。

## Binding
- task llm-asr-g53；owner/唯一writer dependency-skill-writer；cwd /home/zhb/gitrep/llm-asr。
- main base4ef49875fb4a3a81dc44bb1454ac27d5c6b7b6bb；session01a13b25-1d27-763a-9e43-ac2e821fbab7。
- 只新增.agents/skills/llm-asr-dependencies/、本目录与AGENTS导航；保护此前v07/mqn/他人分析目录、未知`.beads.gate.lock`，不操作。
- 不下载/网络/设备/产品测试构建/依赖升级/push，不改历史提交。

## Current Phase
Phase 2 skill/参考/导航与静态验证完成，限定文本安全提交待执行。

### Phase 1: 事实核查与编写
- [x] 读取Pi skills/settings/configuration文档及项目依赖配置。
- [x] 编写SKILL及项目依赖图/操作参考，增加AGENTS最小导航。
- **Status:** complete

### Phase 2: 验证及交付
- [x] 检查frontmatter/链接/相对路径/现状/安全边界，verification.json保存指纹；实际loader未运行，需reload后检查。
- [ ] 限定暂存审计与本地提交，关闭仅skill任务，不交付新APK。
- **Status:** in_progress

## Next Step
审计限定skill/导航/本planning文本暂存并创建本地提交，不夹带未提交规划。

## Errors Encountered
find不存在的可选skill目录返回非零，仅用于发现；确认可使用.agents/skills，无需改ignored .pi目录。
`git diff --no-index --check /dev/null <新文件>`输出为空但返回1：表示新文件有差异，不是空白检查失败；另行逐行空白检查已通过，暂存后用cached --check核。
