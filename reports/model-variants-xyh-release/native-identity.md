# native 身份安全采纳及当前严格核验

- 旧expected：`03959ae52cef3241a91a916170f16dd2b45d6671d78b6636298f8fb7792bf5a0`。
- 本批currentnative：`053962bdf047a03dd01201250f8d026fe60e9c50ab479441e2a26ae52b963917`，B候选5,421,168 bytes。
- graph policy header：`a58c21fe65fc6f78f886518a94d3b2b2a6aee8eca97392d644073e1d14adeb89`。

B原构建在54Kotlin+19Java/JNI对象/链接成功后，旧expected下真实exit1 `Native identity mismatch`已封存，不改当时失败。补充audit PASS_CANDIDATE_NOT_YET_ADOPTED后fresh接受，再C仅baseline6existingpins/native.sha/revision_scope追加；保86库存/其他80pins/schema/历史source_commit/native.path/sample/cert。原newexpected strict实际0并生成当前verification，formal内原gate继续执行。

578vendorobjects inventory/order/真实SHA与old/newprovenance一致且B前后inode/mtime/ctime不变；没有vendor数学/源码锁/patch/配置升级。23schema/flatbuffers头、8478MNN+559KleidiAI全树锁校验、26有效config、NDK28.2.13676358/CMake4.4.2/Ninja1.13.2、JNI唯一owner/4descriptors/4exports与单DSO/staticlibc++审计通过。ELF64LE/AArch64 LOAD16KiB/W^X/RELRO/nonexecstack，NEEDED仅liblog/libdl/libm/libandroid/libc。正常CMake header DEPENDS产生重链，无hash/gate绕过。

当批APKembedded DSO SHA=baseline.native.sha=newprovenance.output_sha=currentstrictverification.native_sha=`053962bdf047a03dd01201250f8d026fe60e9c50ab479441e2a26ae52b963917`。证据来自本次product report `android/app/build/verification/debug/run-c87cf2611bc141bda1ca8fc9579d77b4`，不借旧verification充当前；oldexpected mismatch/旧artifact备份保持历史。

这是编译/ELF/ABI/包装工程身份，未执行设备JNI、真实model、Android推理/质量/性能或4KiB/16KiB设备验收。
