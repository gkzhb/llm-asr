# Findings / resident-review-4

- 首冻结45文件一致，HEAD f7d1d79 main/index空。142 owned record原JSON、argv/exit/root事实与stdout/stderr SHA逐条同；全部owned_exited/root_reaped，116×0/26×1，不能将历史编译失败叫目标红。18 terminal seal文件同。
- PLATFORM-R1 可 CLOSED：AndroidIO read/write/syncDirectory统一ownedFd，public Os read/write；partial write loop与zero-progress拒绝，close返回成功才rename，primary与distinct suppressed保留。actual driver ordinary/-O各compile0/JVM0，oldactualcompile0/exact rawclose AssertionError1；current生产copy/fakes/caller逐字节同。zero-write/Errno/fatal/目录fsync等部分分支是静态审读，不误称host都注入过。
- actual driver在独立raw capability map计数，旧借用wrapper不会从map注销，因此能检测旧漏关，非TestIo替跑。故障目前RuntimeException注入，Android Errno包装与真实设备均不能由其green冒证。
- PLATFORM-R2 可 CLOSED：PersistentFixture注册非daemon线程，close在任何isAlive时拒绝删除；reservation及新并发case使用该registry。workerRetention真实打断join并保目录/primary，最后真实join后外层use才删除；corefault ordinary/-O均真正执行workers模式。新controller矩阵同用registry和外线程断言。
- 65源/7module/31supplemental/28旧产物/14delta核同。finiteSDKowner当前171个依赖文件（不是旧169）、24rawclass唯一无extra映射、8app真实SDK35编译及物理ROOT checker一致；非actualGradleApp。
- 原203ledger及406streams未改；新142ledger/284streams实际内外SHA一致，116exit0/26exit1，其中2真实编译失败另列、24精确目标red。514个捕获owned PID-start一次读均已消失。
- 223→224只加新driver，双CLI身份/选项/raw一致，UNKNOWN未消；AndroidIO.write knots25/M16→33/M19、ownedFd15/M6/nesting5均人工解释。新drivermain cccc8/Y8、knots11/M11；内嵌Java不在函数指标中，已全文人工读。
- actual adapter host无直接READY断言（其不拥有readiness）；Controller拒READY由同源corefault+协议调用顺序佐证，不能把TestIo冒actual adapter或声称Android故障实测。未见平台剩余阻塞。
