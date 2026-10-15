# Findings
Task llm-asr-76d.6；writer p1b-review-2。

- 原2A唯一阻塞B1：成功cleanup残留r1，第二请求排队取消跳过capture/transcribe，cleanup(r2)身份断言失败。
- 原2A已闭模块/签名权限/旧锁恢复等不重开；N1 prepare废弃护栏defer 2B。
- 源码已实读：FakePorts.cleanup:117身份断言与:119阻塞gate仍在，:120新增request=null；共享Checks:85-108同一backend/controller/owner首次成功commit→第二次queued真实invalidate→第三次成功，无新线程或catch。
- JUnit4 wrapper精确7，仪器源码精确12；原InputConnection路径未变，新wrapper调用共享回归。RequestRunner finally不会吞AssertionError。
- red XML实读为新增回归唯一same request cleanup，堆栈经真实Runner→Queue→Checks:97；execution红exit1、绿exit0、behavior终态exit0，有真实end而非仅started。待独立hash/XML/DEX全量绑定。
- runtime首检Herdr wY:p1父done、wY:p1P本reviewer working，原writer不在运行；Beads本库llm_asr、M5 in_progress assignee p1-steward，M3依赖closed。
- 自写标准库解析全部通过：195旧build仅settings变化、80native无变化、358旧P1/87旧2A-review逐SHA保持；12维护库存exact/仅5delta、4补充和55新证据全部match，freeze首尾47b27617…205bfb。
- 修前fake=a2945734…830b，与原2A相同；新增Checks快照与current字节相同。旧fake→current精确只多一行request=null。红36actions/唯一新增回归same request cleanup；绿81actions/7case全零；五阶段7case0-1-0-1-0与两合法fixture mutant重算hash/目标AssertionError/恢复/fresh编译任务均match。
- 独立全DEX解析host1235/test37，无定义交叠/JUnit平台实现/native资产；仪器类继承InstrumentationTestCase，12public concrete ()V有code，与源码清单相同。实际testSHA770c1a72…2981；host重assemble有日志、字节保持856536f1…ee7d合理。
- 旧锁metadata额外与HEAD逐字节SHA一致，host lock等原2A freeze；无需prepare。普通收据有terminal end/exit，非attestation；仅一条已记录behavior链终态，不把先前普通final当完成。
- 不把设备not run、2B pending转为本B1新阻塞；不宣称Android通过。
