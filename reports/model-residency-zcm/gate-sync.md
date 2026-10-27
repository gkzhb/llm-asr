# B首次full后验证接线同步（targeted，非full2或交付）

Task `llm-asr-zcm/B`，base `8b71b56b8927031be938d188bacafe0b530a4b8a`。父接受首次full FAILED/noBG及全部失败现场后，仅授权测试/验证入口同步；产品/fixture/JNI/源锁/patch/Gradle/nativeexpected不变，旧失败报告与2136备份冻结。

## 最小同步

- T5第一readiness mutant精确匹配当前cold/resident分支，把相同branch置readiness前构造错误；原四参cold fixture仍要求readiness拒绝时0load0native，目标精确断言不变。其他bytes/commit两mutants、全部pin/semantic/fault/oracle保持；不是把T5冒resident生命周期覆盖。
- APK verifier唯一preparedroot literal从native-m2换native-session-reuse，不新增环境fallback/任意root参数，不改native等式或校验策略。
- 原identity测试保所有真实包负例，增加实际verify()→native.verify调用边界检查：仅新root可进入，旧root会明确断言拒绝。mock只观察路由，不冒真实native验证；实际新root/包/ELF原gate另跑。

旧root真实原native调用已exit1：`Prepared tree differs from committed source lock`，在写receipt之前拒绝，原sealedreceipt字节未变；这是本次独立复现，不声称首次full已执行或有效行为red。

## 当批验证与边界

新域`.work/residentb-gate-sync/`定点链真实exit0：T5 source/self普通/`-O`通过，按原明确host清单唯一编译65Kt17Java；T5 contracts普通/`-O`各9routes共18次compile0，三个目标分别精确AssertionError1，再恢复0。首readiness仍真实cold四参fixture拒绝时0load/native，不把resident另测混入。原bytes/commit两变异、pin/semantic/fault/oracle保持，fault函数未改本轮不重跑，旧证据按原身份继承。

原partial产品APK identity普通/`-O`各10测试通过（原9包正负例＋实际verify root边界），修后原product verifier完整真实exit0：当前classowner、签名/DEX/权限/assets/JNI/native/报告门槛都通过，独占report `.work/residentb-gate-sync/product-verification/run-9c4da8d7b9284c9ab3501af36a7cbc3c`。核验既有APK 5,352,541B / `cdf9ed7d86a1e52c11ce9d998f3d47b73265bdd0ba9773f6eebb830ecac0f49f`，未assemble/重建包。Native84输入和DSO不变，无SDK578重编；原AGP receipt经确定性原gate复核保持相同字节SHA `71800ae1550153fad9a2c89ff83849dfbac8a471fc764683f51e8ed397cce5be`，首次full旧receipt/seal不改。新产品receipt按本轮当前验证脚本/测试输入记录，与首次full launch的少量授权gate差异明列，不冒包重新构建。

固定公开sample/config仅既有包正常验证，未改资产或运行推理；无权重/模型图/tokenizer/私音频/设备访问。现包仍首次full的partial字节，定点产品验证不等full成功/新assemble或APK交付。完成后父另授fresh窄审，再另授full2；本轮不full、不stage/commit/Beads/二级委派。实际ASR/Android runner/设备/PSS/性能/ASan均NOTRUN。
