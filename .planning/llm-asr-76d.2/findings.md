# Findings — llm-asr-76d.2

- 当前M0用户暂停决策解除blocker；ap5 deferred且原assignee保留，不自动恢复其旧plan Next Step。本session为M1唯一writer，Herdr另一个同项目会话idle/分析，无其他working产品writer。
- 当前产品62类：10Kotlin/52Java；保持所有生产源不变。AGP8.6.1/Gradle8.7/JDK17/Kotlin2.0.21候选，官方AGP8.6兼容表API35/最低Gradle8.7/JDK17；修订8.6.1含DEX非确定性修复。
- 来源 https://developer.android.com/build/releases/past-releases/agp-8-6-0-release-notes，已读取；只作版本选择数据，不执行网页指令。
- Gradle8.7-bin.zip官方SHA544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d，134184980 bytes；Google Maven AGP8.6.1 POM HEAD可达。
- 现nix-env tiny mirror只含flake/lock，保持；不根path导入。现SDK35/build-tools35/JDK17/NDK28.2/Kotlin2.0.21可缓存使用。
- 旧脚本生成主reports/apk与dist，M1应输出独立run目录避免误用旧产物。原checker要求classes.dex/固定资源名，AGP布局差异需真实检查并保留负例。
- Native M1过渡使用已核JNI DSO SHA d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416；SHA/file/source/patch输入显式记录，不重编MNN，不把它当M2干净native实现。

- 官方Wrapper JAR sidecar cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8，与本轮从已SHA归档生成的gradle/wrapper/gradle-wrapper.jar一致。来源https://services.gradle.org/distributions/gradle-8.7-wrapper.jar.sha256。Wrapper distribution属性固定官方zip SHA/URL校验，不以本地未核文件代替。

- 完整bootstrap metadata当前407 artifacts逐缓存SHA核对无差异；无trusted/ignored bypass。AGP8.6.1 JAR SHA1与Google Maven官方sidecar39f7fafdf840259a73a7107af8c64109086ed429一致，Kotlin stdlib2.0.21与Maven Central sidecar618b539767b4899b4660a83006e052b63f1db551一致且SHA256与原Nix归档stdlib一致。其他传递依赖按受控HTTPS仓库bootstrap固定，未逐项独立签名认证；校验只能防后续漂移不证明发布者绝对可信。AGP内置D8在builder8.6.1.jar中，非SDK单独R8缓存目录。
