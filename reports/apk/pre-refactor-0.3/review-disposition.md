# Independent review disposition

Review: `independent-review.md` (read-only child 6b5f0c1b). Parent fixes follow the original review; this document is not a second independent approval.

| Finding | Change | Evidence / outstanding |
|---|---|---|
| B1 process-wide file races | Static executor + CAS ownership across full transaction; static verification state; WeakReference UI rebinding; singleTask; UUID-specific WAV and report part paths | Code applied; Android recreation runtime testing pending device connection |
| F2 duplicate static libc++ DSOs | Combine original 578 final-P0 object files and JNI into one DSO; omit libMNN.so from APK; no P0 source/library mutation | NDK link passed; NEEDED system libraries only; JNI symbol exported. New combined DSO APK inference still unverified |
| F3 stale result | Atomically publish UUID/pending before work; matching terminal state/kind/timestamp; deployment clears old report and checks freshness | Code applied; interrupted process test pending |
| F4 UTF release / exceptions | RAII UTF guard, checked exception class, stream outlives model | JNI compiled/linked |
| F5 unbounded directory | Retain only fixed names, cap 10,000 entries, reject expected-name duplicates | Code applied; hostile/stalled provider timeout still not implemented |
| Runtime provenance | Enforce supplied libMNN hash against final P0 record before linking; record each input object SHA | Build gate added; does not pretend new DSO equals old P0 binary |

Still required for APK functional sign-off: actual install/JNI execution under ordinary UID, repeated create/destroy, lifecycle recreation, malformed model import and SAF tests. ADB original endpoint refused connection this session. No production-readiness claim.
