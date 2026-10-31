# Findings / core-prepare

Prior graph concrete stdlib2.0.21 offline unavailability is not jar absence: exact cached jar exists. Core runtime declares stdlib2.0.21 + annotations13; original lock coordinates match but runtimeClasspath is absent from lock configuration lists. No lock changes authorized; report cannot assert per-runtime lock enforcement. Need actual dependencies report same versions with no FAILED/unresolved, not outerexit alone.
