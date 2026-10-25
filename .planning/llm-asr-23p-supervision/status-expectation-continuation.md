# 第二green失败处置 / 同writer有界续验

父p1-steward；main7a3aa53/indexempty。父全文消费第二handoff（05e0d393…b19）/三plan/terminalsummary-v2，独立10当前候选modebytesSHA与3owned账本/root退出全同；实读tests/ImeSessionTest.kt externalExitRetainsOwner与真实ImeSession.java。前次caller修正已到达controller测试，不能冒全套通过。

## 接受的单点修正
fresh.status().isEmpty()与真实构造非空默认提示冲突。仅tests/ImeSessionTest.kt：fresh=c.begin后立即保存初始status，worker真实终态后assert fresh.status()==初始status，同时保旧invalid/previewempty/新previewempty、owner/cancel/顺序/worker错误与join断言。不能改产品默认状态或删迟到隔离检查；状态比较比contains失败更完整，不硬编码中文字串。
其他候选仅status真实追加，不变生产与原功能验收。旧失败树/outputs/快照保留，新的输入/命令/receipt另域。允许修后先对实际修改的controller回归执行一次fresh有界compile/run sanity，再进入原ordinary/-O功能链，避免重复完整编译链才暴露基本测试问题。sanity复用现compiled sources/driver真实入口，不复制生产实现或弱化默认路由；额外sanity最大315s一个compile270/run45。

## 后续迭代权限收敛
原已授权路径内，若再发现确定的新测试fixture引用/初始预期错误，可自行做最多两处最小测试修正并记录源码证据、失败raw与恢复结果，不需每处都退出等待父；禁止改产品、删断言、放宽标准/预算/工具identity/owner收尾来凑green。生产行为错误、未知归属、需扩大路径/权限/依赖/改门槛、未确认子进程退出时必须首停。三种有效路径无解亦停；两次不同测试bug不计同一诊断三次。

其他所有原授权预算/门槛保持，普通/-O四mutants/原defaultcompat/hostSDKfastgraphformat和复杂度串行，未成功链可新域执行，成功链不无理由重跑。新测试caller影响current-v2：不改旧snapshot，允许最后修正封口后一个全新有限current-final七源副本一次正常snapshot commit，精确保modebytesSHA且不动主index/ref；不再每次失败先造复杂度snapshot，先完成基本测试。
当前仍不授formal full/assemble/sign/正式APK与receipt覆盖、fresh审、stagecommit、设备下载mic/private/model/push。完整targeted成功noBG后交handoff/候选/分域与保护/真实terminal，父接受才fresh审。父不另开writer，模型产品写后置。
