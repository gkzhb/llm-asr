# R1 process-lifecycle fix — targeted, pending fresh point review

Task llm-asr-ap5, sole product writer p2enum-build-2 resumed by parent p1-steward (sole Beads writer). Main/base a5204512cc779ed8e85116fdf7ea4453abf0b9ce. This is an accepted R1/P2 repair, not a second enum feature. Original77-candidate manifest `622c203ba513cbd4f5a6952ff485a733da9f96a5a5c286527608948064f46229`, original targeted/review and old successful command identities are historical and are not overwritten/relabelled. Product enums and Java caller are unchanged from that freeze.

## Accepted finding and real red

Fresh reviewer statically identified GNU timeout's default extra compiler process group: driver killpg(wrapper) and wait(wrapper) alone did not prove compiler work stopped before TemporaryDirectory deletion. Old successful ABI/mutant chains never triggered cancellation and remain valid as their limited success evidence; their cleanup claim was too strong. Reviewer did not run a red. This repair leaf then added a real lifecycle fault test calling actual driver.run and actual compile-jvm-sources.sh. On the old runnable implementation, fake kotlinc plus its child remained live in the inner timeout's PGID when deletion was about to occur. The test failed exit1 with **`RuntimeError: R1 live owned worker before tree cleanup:`**, actual PID/start/state/PGID observations preserved in cleanup-runs/run-crm_o82q. Its finally killed only matching owned identities and verified no live worker remained. This is a target lifecycle red, not a missing cache/compiler/parse failure.

## Minimum repair

- Shared wrapper: only its two timeout invocations change to `timeout --foreground --kill-after=5s 120 kotlinc/javac`. Original120s deadline, source arguments, compiler flags, classpath and success check stay unchanged. Foreground keeps ordinary compiler descendants in driver-owned group; a compiler ignoring TERM gets bounded KILL escalation.
- Driver: one small finish_group helper, invoked from existing run finally. KILL owned PGID, wait direct child, then bounded /proc observation until no non-Z/X process in that group is executing, before returning to caller/source-tree cleanup. It does not pretend waitpid reaps nonchildren. PID/PGID ownership is the isolated new session of the command. Scope is ordinary compiler/Java chains, not a sandbox for a malicious daemon escaping its session.
- New cleanup test invokes actual driver.run, actual shared wrapper, test-owned fake kotlinc/javac/Java with a TERM-ignoring child; records PID/start/state/PGID. TERM/HUP and driver timeout each cover kotlinc/javac/Java (9cases); two inner deadline/KILL cases cover kotlinc/javac. It asserts nonzero outward failure, no live owned workers **before** actual tree deletion, then actual tree disappearance and no survivors. All test finally paths reclaim fault workers, including old red. Python gates use require, not optimizable assert.
- Inner deadline test uses an explicit PATH timeout shim that execs the real GNU timeout while scaling original120→0.25s and KILL grace5s→0.15s; validates original120 argument, foreground/KILL flags and exit137 for TERM-ignoring compiler. It exercises the real escalation mechanism but **does not measure a120-second wall deadline**. No production parameter injection interface was added.
- Host calls cleanup test once before original enum driver; full inherits host. Fast is not expanded. No native/global shell policy/DSL/toolchain/lock change.

## Results

- Old cleanup lifecycle red: exit1, precise live-worker-before-deletion assertion; finally reclaimed all owned fault identities.
- Fixed cleanup normal:11cases exit0, all fault subprocess failures propagated; no live worker before cleanup.
- Fixed cleanup `python3 -O`:same11cases exit0.
- New wrapper ordinary enum mutants: all five fresh mixed compiles0, original Java322checks green0, independent Source/Kind inventory-order assertion1 then exact source SHA restored/recompiled green0. No compile/tool/timeout failure counted behavior red.
- Complete existing host:exit0, actual fresh12kt+67Java compile,322checks, one cleanup11case and one two-enum mutant chain, all original model/ActivityWindow/capture/source/checker gates retained and passed. Host fake-platform checks are not Android runtime evidence.
- Closing fast/format real results, current dynamic freeze and exact candidate identity are recorded after terminal consumption below and in the final handoff.

Each command uses fixed Nix apk --cache-only; Gradle adds strict/offline via the existing entry. All commands, true start/end/exit and complete log SHA are in verified-command-index.json and commands/logs. Fault observations and subprocess logs are cleanup-runs; actual production compile/run/red/restored logs are enum-runs; before/after driver/wrapper snapshots are source-before/source-after for this R1 repair only. The original pre-migration driver had only SHA, no full snapshot: its complete text differences cannot be independently reconstructed. We retain this limitation and do not fabricate an old snapshot or rerun the successful Java baseline.

## Errors, review and boundaries

One mistaken background placeholder invoked the original runner without executable arguments:IndexError exit1 before test execution, recorded launch-error.json, not lifecycle red. It created only an unused empty ignored log, original command-index remained unchanged. One progress-append Python literal SyntaxError wrote nothing, corrected separately. No cache/download/tool failure was counted a behavioral result.

Leaf self-review checks exact two wrapper-line diff, run cleanup path, explicit lifecycle observations/PID-start-safe fallback cleanup, host-only additional call, unchanged enums/caller and public text identities. This is not fresh R1 point review. Successful cleanup demonstrates tested normal process chains, not arbitrary daemon containment or Android cancellation behavior.

Not run: full/verifyDebug, Lint, app/all-source/three-APK build, new DEX/package/signature acceptance, JNI/native compile/link, Android runner/device/emulator/ADB/SAF/IME/Binder/mic/private, model computation/download/prepare, remote CI/release/R8. No stage/commit/Beads writes/delegation/push. Native578objects/DSO/provenance,32old artifact/receipt records and original26excluded stay protected; old APK not relabeled current. ap5 still12kt51Java, not Java0.

Sole next gate:parent consumes new frozen handoff → fresh exact sol fork:false read-only R1 point review (same-model context-isolated, not cross-family) → CLOSED before sole full validation → separately authorized two-phase safe local commit. No new APK delivery in this leaf.

## Closing targeted results and identity

Fast strict/offline rerun exit0:23 actions all executed, core34/runtime1 JUnit zero failure/error/skip; format exit0:1 action, maintained41 gate. Both pipelines Gradle0/tee0/scan1, existing aapt2 experimental override warning disclosed. Fast source/task wiring is unchanged; no need to rerun the previous successful real35-task graph solely for this host/wrapper repair.

Pre-R1→current dynamic inputs:build210→211 (one cleanup-test addition; four delta entries are wrapper/host/driver/new cleanup); module10 unchanged; native collector80 unchanged cardinality but exactly **one wrapper SHA delta** because the collector includes the mixed compiler script, not native implementation/link changes; source63 unchanged (12kt51Java). Tracked3changed paths are wrapper/host/docs. Kotlin enums/Java caller/baseline exact unchanged. Public collector maps and delta in current-input-freeze.json. Native578objects/DSO/provenance and32old artifacts, original26excluded and old targeted/reviewer reports/SHA files all preserved; historical-protected.json stays in ignored work. Parent plans legitimately changed and reviewer has its own new read-only plan, both noncandidate supplemental.

Seven actual engineering commands are retained:one valid old lifecycle red1 and six success commands0 (normal/-O cleanup, ordinary enum mutants, host, fast, format). The earlier erroneous placeholder background has separate launch-error.json; it is not an engineering command nor behavioral red. All seven background invocations including that erroneous launch delivered terminal notifications; no related compiler/Java/Gradle/driver/fault worker remains at closure. No successful old baseline/targeted chain was rerun merely for a notification; new tests explicitly bind the changed wrapper/driver/current inputs. Final new candidate manifest and closure are `.work/p2enum-build/r1-fix/{candidate-manifest,closure}.json`; original manifest remains unchanged history.
