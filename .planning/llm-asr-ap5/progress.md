# Progress — llm-asr-ap5

## 2026-09-30 开始
- 已完整恢复根三份历史规划，git diff/stat/status 干净，HEAD d929cb1。
- 用户新增迁移任务并授权无技术阻塞时实施；按执行职责直接执行，不派 worker。
- bd 新建/claim 并读回 llm-asr-ap5。活动计划绑定当前 task/owner/session；不共写根历史。
- 已读当前 APK/host 构建与 flake；Herdr确认本项目仅当前session working。Kotlin2.4.10/Nix工具链准备任务 b9de278a7 已启动。
- 第一批4生产类已改为Kotlin，共享混合编译/host runtime/DEX runtime/真实JNI头接线已落盘。源码依赖guard改为Java/Kotlin唯一源解析，15负例与Android policy源fixture已通过；shell语法与diff-check通过。
- 新Java迁移回归覆盖静态/字段ABI、synchronized flags、Java trim/split、PCM signed字节和null short-circuit/Error identity。工具链b9de278a7正式exit0，真实kotlinc2.4.10验证通过。
- 主机+Android回归任务b5ba6ef1b正式exit1：配置16测试通过，空classpath检查误拒绝使编译未开始；修`${2?}`后启动b48f7db3f，日志host-fixed.log/android-fixed.log。失败日志保留。
- 独立补许可/DEX class_def强制runtime检查与missing-runtime负例。实际checker bound正例与缺runtime class_def/旧绑定负例全部通过，日志.work/model-review-fixes/apk-binding-fkk25qr7。这些末次检查需最终完整构建重新运行。
- 已只读备份旧APK与reports到.work/kotlin-migration/baseline，未删除或替换旧签名。
- b48f7db3f正式exit0：完整host及4 Kotlin+58 Java全源SDK35编译通过，23新ABI/语义checks、既有生产回归/3可编译mutants/checker负例均通过。
- 去掉PcmWave重复非空assert编译warning（不改语义），132小文本输入冻结`.work/kotlin-migration/frozen.json`。完整APK构建ba88ee850已启动，日志build.log；当前包/报告正在覆盖，result旧身份不用于本批。
- 直接自审报告reports/review/kotlin-batch1-self-review.md落盘；不冒称独立review。
- ba88ee850正式exit0但父读取完整build日志发现大量D8 metadata2.4>支持2.1与Unexpected rewrite异常（日志约1.6万行）。不因exit0交付；status明确中间包拒绝，原baseline保留。
- 新增D8 warnings fail-closed；兼容Kotlin2.0.21归档SHA任务bf0075b3f启动，准备Nix override且不改Android SDK/NDK。工具链修复后全部重跑，当前未stage/commit/设备访问。
- bf0075b3f官方Nix prefetch600s无输出超时；替代bae354644低速~35KiB/s下载360s仍超时，保留失败记录。官方独立sha256 sidecar返回0352c0a...7bcf2a，Nix override已按2.0.21和该SHA固定。
- 第三路径b09e80eb2改Nix内容寻址镜像，强制同官方SHA/ZIP验证；获取180s上限，实际Nix包准备600s上限。等待正式终态，不轮询。
- 用户明确授权使用本地HTTP代理。显式curl --proxy的官方归档HEAD已通过200/85822011字节；仅命令级使用，不改系统配置、不保存代理会话或凭据。
- b09e80eb2正式exit28：180s下载70,741,132/85,822,011字节，不是哈希失败；尚无完整校验。已用新增代理授权启动b6757313c官方Range续传剩余约15MB（240s），强制官方整包SHA+ZIP后add-fixed到Nix store并准备同版编译器。无并发重复大下载。
- b6757313c代理续传/官方整包SHA成功，后续unzip缺宿主命令exit127；改Python zipfile逐entry CRC+大小/SHA全部通过130entries，导入Nix fixed store sm5ag8...zip。b207f5970单独准备兼容env。
- Kotlin2.0.21真实许可从已验证归档读取；旧从store cp的许可只读，窄chmod新建文件后成功更新。2.0 README无threeten，notices按本版gwt/guava/boost，不混用2.4许可。
- b207f5970正式exit0，真实kotlinc2.0.21/JRE17验证成功。132输入更新冻结，旧2.4冻结另存；兼容最终全构建b5a7aff69已启动，日志build-compatible.log。不改冻结源码，不继承2.4 host/构建作为最终证据。
- b5a7aff69正式exit0/APK_READY，完整log读取确认Kotlin2.0.21全host/23新checks/3mutants/Java-Kotlin/JNI/DEX/签名包检查通过，d8.txt为空。standalone checker再次通过；127build/132frozen/current全匹配，native provenance/578对象与baseline相同，JNI DSO逐字相同。
- APK3167222bytes SHAf24b4da1f254f17a324b756990408e3f4474c2b09afce94290d06d608562a163，同既有本地debug证书。result/status/docs/直接自审报告统一首批身份，设备pending。
- 保存并读回kotlin-d8-compatibility memory，AGENTS仅增加Kotlin/D8导航。任务仍in_progress：58生产Java剩余，不把首批当全量。
- 暂存前diff-check及文件归属核对，本轮从干净d929cb1开始，仅相关文本源码/测试/工具/许可/报告/规划/Beads导出；即将安全审计index并本地提交，不提交产物/音频/模型/密钥/缓存，不push。提交失败不得交付，成功身份以Git为准。
