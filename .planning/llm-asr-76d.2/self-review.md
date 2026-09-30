# M1 直接自审（非独立 review）

## 核查范围
settings/root/app Kotlin DSL、Wrapper/固定SHA、gradle.properties、metadata/locks、输入staging与产物checker、host/JNI接线、负例、文档及Beads暂停/交接。未委派，无独立review声明；设备未授权。

## 已处理的验收阻塞
1. Gradle8.7 locking与failOnDynamicVersions互斥：保留锁，显式selector拒绝空/动态/range/latest/SNAPSHOT，不删除门槛。
2. 旧checker只读classes.dex错误拒绝AGP多DEX：遍历实际class_def联合、重复定义拒绝，新增multidex正例/缺owner/重复owner负例；原missing-runtime引用不算定义继续拒绝。
3. 输入清单缺root/app locks和gradlew.bat：已补，外部native/sample/config及Nix Java/aapt2/signing工具SHA纳入report，需最终重跑绑定。
4. identity test依赖旧dist APK：Gradle已指定本次assemble产物并实际apksigner/aapt2采集；首次运行不需要旧APK。standalone回退仅开发测试保留。
5. 输入增量可悄然新增asset/source：完整inventory/固定SHA强制拒绝；staging额外残留asset/native及symlink拒绝，8输入负例/正例通过。
6. JNI验证必须对应AGP class：检查真实AGP Java/Kotlin输出与包内DSO descriptors/exports，不将standalone object等同新包ABI。
7. configuration执行/旧脚本包装：所有输入生成在Exec任务执行期；AGP负责资源/编译/DEX/签名；未调用旧整包脚本。旧host和JNI对象验证可Exec复用，不是AGP替代。
8. Manifest namespace/版本/SDK/extractNativeLibs去冗余仅生成副本，原Manifest和所有业务输入未改；最终签名/组件/权限/allowBackup/debuggable/extractNativeLibs均检查。
9. 依赖bootstrap与strict/offline分开：407校验文件匹配缓存、无豁免，重要AGP/stdlib官方sidecar核对；其他受控HTTPS首次信任固定，不声称签名/完全hermetic。checksum实际Gradle负例待最终结果。
10. NixOS aapt2显式override是同受控SDK工具，AGP管理任务；verify脚本固定strict并拒绝write/relax参数，DEX warning外层fail closed。

## 证据与剩余门槛
- 在线bf9b19e54通过；strict/offline/rerun b2e649810通过、41任务全执行，APK与在线相同。
- 自审收尾后b7658194f实际strict offline rerun通过，41任务全执行，新AGP实际identity5测试与staging8通过；最终152输入/current与73产品输入全一致，外部输入/toolchain和6报告绑定成功。不是继承旧148输入。
- checksum篡改负例b066d3598和仓库脚本在b7658194f均通过：实际checksum原因拒绝，非fixture语法/缺文件失败。无本轮剩余阻塞。
- 最终提交前检查所有相关文本和唯一必要官方Wrapper JAR（官方SHA已核），不提交生成产物/密钥/音频/模型/缓存/会话数据或两个其他owner分析目录。

## 明确保留的限制
M1仅现native hash过渡，不实现M2 clean native；debug原包名/证书不变、无release/R8优化、无UI/语言迁移。AGP debug9DEX/容器导致包大小变化，不要求与旧手工包逐字相同、不承诺性能提升。offline证据使用已准备缓存与本地小资产/native/key，不是干净缓存hermetic。源码根私有文件/设备/麦克风未访问。设备SAF/IME/JNI/录音仍pending。无push/历史重写。
