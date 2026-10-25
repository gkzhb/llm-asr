# ime-build 首失败处置与续验

父p1-steward/session01a16ecc-7157-76a9-8065-dbcf8e84589a；main7a3aa53。父全文消费handoff-targeted/terminal-summary/三plan/candidate清单，读真实caller失败行与生产diff/rawstderr，独立10候选modebytesSHA及3owned ledger SHA/owned_exited/root_reaped核同，handoff30419298…7525核同。Herdr同仓仅父，无其他writer，index空；原writer真实completed/noBG。首次compile0/greenrun1的destroyed service断言是工程失败非mutant red。

## 接受诊断及最小修复
ImeFixture.root final保存旧tree；onDestroy后onCreateInputView返回新tree被忽略，f.button仍查旧按钮enabled，不能证明当前渲染。仅授权tests/fixtures/kotlin-next/NextAppContractTest.java该序列最小更正：保存旧restart listener引用；onDestroy后直接点击旧listener，外部断言session仍null且captures/writes不增；如继续显式重建view，接住返回的新tree并在该tree断言disabled，不从f.root读旧tree。不得删销毁保护/仅删断言/自动触发生命周期补洞，不改生产源或其他测试行为以凑绿。

## 顺序与范围
原9其他候选冻结；status可追加本次诊断/真实结果，新ignored证据域不得覆旧失败/receipt。修后同writer沿原ordinary/-O四mutants（9compile/run预算）、原默认/compat及host/SDK/fast/graph/format/复杂度剩余授权串行续验，原预算与stop-on-first-real-failure不变。普通初始green失败前无mutant执行，允许在全新证据目录重启该未成功链；不重跑已成功的前批工程。保留旧失败与精确源码身份，source恢复仍只在每次已登记ledger owned/root均确认后，unknown拒恢复/删树。

复杂度当前副本caller已过时：不改已提交快照历史；另授一个全新有限七源current-v2源码副本一次真实snapshot commit，base旧before快照不改（script644 mode差异保披露），新current准确保mode/bytesSHA。不动主index/ref、不绕hooks签名、不带权重/产物/session。只进行原必要collect/compare人工处置，不新增无界辅助框架。

当前仍不授formal full/assemble/D8/sign/覆正式三APK与receipt、stagecommit、下载/依赖prepare、设备mic/private/model/push。新失败首停附最小诊断；targeted完成无BG完整handoff后再fresh审。父不另开writer，模型产品写后置。
