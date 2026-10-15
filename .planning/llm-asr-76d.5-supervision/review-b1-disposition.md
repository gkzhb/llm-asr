# B1 candidate静态审查处置

Task llm-asr-76d.5；父协调/Beads writer p1-steward。p1-review-6 newapi/gpt-6.1-sol/high fresh standalone只读，非跨family；未执行测试/下载/解析。

## 已核通过
- 冻结handoff/source-manifest/graph/candidate/base/v4开始结束readhash匹配。
- actualdetached/named audit完整67nodes含selectionreasons/171edges/66artifact及cachepath一致，版本固定。
- 23新增artifact、412旧metadata语义保持、verificationconfig未改；168官方objects/66cacheJAR/candidateSHA与legalentrySHA匹配。
- ledger18及index64匹配，两discovery失败/source0/license2历史保持；strictoffline仍NOTRUN，bootstrap不是验收。
- fixtureonly锁不约束actualdetached事实保持，主图策略B2审查，CVE/SBOMunknown、sidecar非独立认证/asc未核key，不假安全放行。

## blocker收窄：证据脚本具体缺陷
1. audit-sources.py固定Mavennamespace漏无namespace POM：已下载javax.inject1、kxml2.3.0、BouncyCastle三1.77 POM有实际licenses/SCM，五actual UNKNOWN不是无许可。独立supplement支持两种schema，并引用原对象SHA/行号/坐标，不重写原manifest或exit2。
2. summarize只遍历已获取POM，exit0不能证明所有66actual覆盖，缺五POM的模块须强制图闭包对照。
3. metadataonly jvnet-parent1/oss-parent7/9已下载POM文件头Apache2.0，可审其metadata文件许可但不能继承成所有子JAR许可。
4. 五missingactual POM只是未取得非404：aapt2proto8.6.1-11315950/buildermodel8.6.1/manifestmerger31.6.1/checkerqual3.33.0/kotlinstdlib1.9.20，限准确官方Google/Central补POM/必要有限parent，不邻组件推断。
5. kxml双BSDstyle/PublicDomain范围保留org.kxml2/org.xmlpull.v1 comments并补适用条款；BouncyCastle需版本绑定条款/NOTICE范围核；声明不是法律全审通过。

## 调度
原p1-test-2限定补证仍活动，父不重派/打断busy或并写范围。完成后若本handoff未覆盖两schema/full66 gate，父集中窄补修而不扩全SDK审计。
补证candidate审接受后另授权strictoffline resolve前后candidate/locks/v4核；之后B2主真实消费图约束+metadata必要增量/真实Lintgreen-red-green，B3prod/full/APK。
未解决许可不进入B2；不删除UNKNOWN或更改旧hash/force版本/关strict凑green。不关闭M4、不提交或交付APK。最终reviewagent安全限定localcommit需全部相关验证/产物绑定完成后另授权。
