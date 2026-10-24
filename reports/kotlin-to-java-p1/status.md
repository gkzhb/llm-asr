# Kotlin→Java P1 — RecordingControl / RequestContext

Task `llm-asr-2a7`, source base `d5ac21a2679eabdae1a94599ffc14893a1661745`.
This is the first two-owner production Kotlin→Java slice, not a revert of historical Java→Kotlin batches and not completion of all Kotlin migration. Product inventory changes from47Kt+16Java to45Kt+18Java, preserving63owners. Kotlin2.0.21 tooling/runtime and Gradle DSL remain required.

## Contracts and current evidence

- Before migration, the enhanced same Java caller compiled and ran against the original Kotlin sources: compile0/run0,883checks. Caller SHA256 `d16ac42f50774885297a9970114a5091a1411d684da01cdaa85b3dc9546a9f0c`; unchanged for candidate verification.
- Actual original javap confirmed9public final synchronized receiver methods,5ordinary private booleans, sole public zero-arg RecordingControl constructor; RequestContext sole nullable2-arg constructor,2public final nullable fields and3ordinary mutable direct fields with zero/false/null defaults, no getters.
- Original eligible start(null) is exactly NullPointerException with null message, null cause and0suppressed; Java explicitly preserves that shape after eligibility short circuit. Callback Throwable identity, receiver monitor, reentrancy and state ordering stay unchanged. Stack/SourceFile origin necessarily follows Java; no claim of identical debug bytecode.
- Existing Nullable annotations and actual RequestContext JvmField annotations are retained, rather than silently waived. Only Kotlin Metadata/compiler-debug language-origin differences are allowed.
- Dedicated focused driver reuses canonical host_sources and existing owned-session/dependency-lease/Workspace lifecycle. Four real compile-valid production mutants require green0/exact named assertion1/restored0, actual loaded-owner bytes and unchanged samecaller, ordinary and optimized Python. Compiler/tool failures never count as behavior red.

## Targeted results (2026-10-24, dirty candidate; not a tested product commit)

All commands use fixed Nix cache-only; Gradle strict/offline gates unchanged. Raw logs/owned terminal records and current identities are in ignored `.work/k2java-build/`; parent must independently consume them before fresh review acceptance.

| Evidence | Actual result |
|---|---|
| Original Kotlin samecaller baseline | compile0/run0,883checks including explicit loaded-owner checks |
| Full original targeted host, one invocation | exit0; all original gates retained, samecaller main876checks without optional owner property |
| New four production mutants, ordinary / `-O` | each12compile0,8green/restored Java0 and4exact named AssertionError1; all loaded-owner bytes/source/caller restored |
| Shared collector reciprocal inventory/SHA, ordinary / `-O` | both exit0 |
| Actual staging guard, exact existing native artifact, ordinary / `-O` | each12tests exit0; omission/drift/resurrected-Kt rejected before output; no native build |
| Real capture implementation + fake Android media | original12phases exit0, not microphone acceptance |
| Actual SDK35 all-source/JVM8 compile | exit0;45Kt+18Java |
| Strict offline fast rerun | exit0; exact34core+1runtime JUnit, failure/error/skip0 and real module boundary gates |
| Exact graph / maintained format | exit0 / exit0;38dry-run tasks, no heavy families; format99files |
| Original Kotlin vs candidate host and actual AGP Java javap | descriptors/member/field flags/Nullable/JvmField equal; selected owners only java/main, absent kotlin/main |
| Complexity tools before/after |13tool-regression tests0, both collects0; cccc1.8.3/knots1.18.0 |
| Complexity whole-scope compare | **exit2 FAIL-UNKNOWN scope change**, original stderr retained, not pass |

Complexity independent finite source snapshots before219files / after220files include2newJava+driver and exclude2deletedKt; they are analysis-only synthetic commits, not product delivery commits. Source base remains d5ac21a. Writer manually mapped all9methods and DTO fields: knots start C/M2/5→4/7 reflects explicit eligible-null preservation; other8methods unchanged. Original DTO Kotlin zero-record/cccc unsupported remains unknown, not zero. New driver/oracle test hotspots and capture resolver growth are surfaced in `.work/k2java-build/complexity-disposition.md` for fresh reviewer, not hidden by baseline refresh or claimed whole-project improvement.

Terminal tasks: before `b4e48a15e`, baseline `bf704c97b`, input/capture/SDK `b7a4a92ef`, complete host `b964da886`, after `b1c4b4b19`, fast/graph/format `b3ed2e3b9`, all wrapper exit0; compare remains2 as above. Owned engineering children confirmed exited/root-reaped, streams closed before evidence SHA. Old three APK/output metadata/formal receipt bytes and finite native/source/lock protection remain unchanged; generated core/runtime classes/results legitimately rebuilt. Existing native verification report remains historical (old Kotlin pins), not current full evidence.

Original host retains its own precise fault5/6 NOTRUN boundary in T5 fault evidence; a successful host command is not a claim every conceivable fault/device path ran. Fresh reviewer/full/commit acceptance is still pending. Final writer inspection found a **static lifecycle blocker** in the new driver: its red-route unconditional finally restoration can rewrite mutant source even if the owned runner reports unconfirmed exit. All actual routes exited confirmed, so no live rewrite was observed; that fault path was not executed. Frozen candidate/raw evidence retained without silent fix or rerun. Parent disposition is required for minimal lifecycle-preserving correction and narrow fault verification before review acceptance. This status text is supplemental after the original measured source freeze; the authorized restoration fix below changes only the driver, never production or samecaller.

### Narrow restoration fix continuation

The original blocked candidate/raw/receipt remain historical. New driver SHA `90dfc284a481bcc4acd34012557c2fa84c57e63fa75f1c236e9304d7438fb26a` replaces unconditional restoration with shared guard plus every freshly attempted child record's strict owned/root confirmation; unknown retains mutant/tree/dependency lease, and the primary exception continues with a refusal note. Shared runner, production, caller, host wiring and pins are unchanged.

Old-driver actual-suite controlled synthetic unknown produced the precise wrong-restoration observation. Fixed driver ordinary/optimized seven-case suite and supplemental direct missing/bad/ownedfalse/rootfalse records preserve bytes/lease/primary; confirmed exceptions and non-target errors safely restore without misclassifying as red. These are actual driver paths with bounded real synthetic children and ledger unknown injected **after real exit**, not proof of handling an unkillable compiler. Initial harness notes alias/guard-shortcircuit limitations are preserved and explicitly documented, supplemental direct-record cases reach the actual ledger branch.

Original temporary host classes were normally deleted after success. A separately authorized one-shot canonical production+frozen caller dependency rebuild compiled0/sanity0/883checks with164emitted-file identities, without replaying original host/baseline/SDK/fast or claiming identical bytes to the deleted tree. The first new regression attempt failed before any compile because an ignored outer wrapper duplicated the inner dependency lease; correct guard refusal was retained as engineering exit1, not target red. Parent-authorized new wrapper/domain removed only duplicate outer lease while retaining original owned supervision and inner guard/lease. New real four-mutant ordinary/optimized routes both exit0: each12compile0,8green/restoredJava0 and4exactnamedred1; source/caller/loaded owner and retained164class inventory unchanged. Full narrow fix evidence is `.work/k2java-restoration-fix/`, distinct from original raw. Fresh reviewer acceptance and formal full/commit remain pending.

## Not run / delivery boundary

Fresh review, formal full/JNI/DEX/Lint/three-APK package build and safe local product commit require separate authorization. Existing APKs/formal receipts remain protected, not rebuilt or delivered by this targeted stage. Android runner, device/microphone/SAF/Binder/IME/native/model inference, downloads, release and remote CI are not run. Host/reflection/fake-media evidence is not Android or device acceptance. No new APK validation entry is offered before successful later full/review/commit and source-input binding.


## Formal full terminal and post closure (2026-10-24)

Fresh context-isolated same-sol review NO BLOCKING/R1CLOSED accepted (not cross-family); optional C1 null-message assert accepted without changing frozen caller. Exactly one authorized full `bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks` completed0 (`b3be32398`,1925.898seconds), Gradle0/tee0/DEXscan1(no diagnostic match),184tasks executed. Actual full host includes fixeddriver ordinary/-O each12compile0+8greenrestore0+4precisered1;42JUnit exact34core+1runtime+7host,0failure/error/skip. RealAGP/SDK/JNI/native578objectlink, isolated real checksum0→1→0 andLint0→NewApi1→0,fixture5phases and3signed parsedAPK checks all passed; no unchanged oldtargeted outcome substituted.

Current223build/10module input maps equal launchfreeze/bothformalreceipts/current; source13frozenunchanged,onlythisstatusappend+new [full.md](full.md) and [full-inputs.json](full-inputs.json) supplementary reports. Native9037pinnedsources/578cachedobjects unchanged bytes/mode/stat; finalsameSHA DSO relinked/newstat (not sameinodeclaim). Prepared generatedoutputs/APKs rebuilt under explicitpermission; original38ordinarytestbinary classifierpreparationstop and exactsupplementalbackup/21cleanup continuation retained, not formalfailedattempt. Sensitivekey/audio/model manuallymetadataonly; normalcachejournalwrites separate,not hermeticfreshcacheclaim.

Product APK actualSHA `e7375925034dacd158a505fab34b8b0493c391947b4842746bdfe3f18e5e9b2a`,5281549bytes,org.llmasr.minimal/0.6-debug/code6/min29target35/arm64/RECORD_AUDIOonly/originalv3cert. This is recorded output identity, **not an APK delivery instruction**. Parentconsumption/safetyphase1+2/productlocalcommit still pending. Device/Androidrunner/native-model/4K16Kruntime notrun; wholecomplexitycompareUNKNOWN/syntheticunknownfaultboundary/inheritedT5fault5/6NOTRUN preserved. Full evidence details, warning dispositions, actualJUnitmethods/current3package/twoformalreceipt fingerprints are in newfullreports. Sourcefreeze remains dirtyprecommit,never claim full tested a newcommit object.
