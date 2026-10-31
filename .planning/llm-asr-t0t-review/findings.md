# Fresh review findings — llm-asr-t0t

Admission: base/HEAD64d00d16e13577b9e6ad049511acd96b3e7d7d6b, six candidate hashes match assignment and postcheck handoff d85239e91ec0af8cb6a08e873f8f0dac531163191deee3f521aa49422aec1322. Only six tracked modifications; no product writes by reviewer. Owner p1-steward; t0t-build runtime done/frozen.

Static finding R1 (MEDIUM, verification contract): new readiness mutant finally at tests/kotlin_next_migration_test.py:543-545 can replace a propagating compile/run BaseException if quiescent() or target.write_bytes() raises; outer primary assignment happens only afterwards. Also corrupt valid-JSON non-object ledger reaches .get at504. Inner restoration needs primary-preserving handling as existing K2J driver uses. Not run as a fault injection; existing successful child evidence remains valid.

Product logic reviewed: fail-closed READY+LOADED/nonpending; worker authority unchanged; sensitive/permission/foreign/own-busy priority correct; preview separate. Six diff paths are bounded; host only four inserted lines and baseline only Service SHA.

Independently verified 66 compile/JVM raw records (1+7+7+9+9 routes), exact old red and three readiness/two-mode reds/restores, common 142-input manifests (only old Service differs), caller/driver/source frozen hashes, 10 outer record raw SHAs/terminal facts, current 238-input binding. R1 cancelled outer UNKNOWN excluded. stdlib postcheck graph38dryrun/format133 one actual action accepted only at respective layers. Complexity raw versions/scope/source SHA and immutable tool hashes consumed; render knots C29/M17/N5 toC46/M21/N7 reviewed; cccc Kotlin UNKNOWN. No new product blocking defect identified; R1 is a verification-tool failure-path issue, not invalidation of successful JVM results.
