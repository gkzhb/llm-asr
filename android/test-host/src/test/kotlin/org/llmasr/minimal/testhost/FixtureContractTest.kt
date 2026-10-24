package org.llmasr.minimal.testhost

import org.junit.Test

/** Seven nonzero host tests; Android wrapper invokes these same shared checks. */
class FixtureContractTest {
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Test @Throws(Exception::class) open fun enginePayloadAndOrder() { FixtureChecks.enginePayloadAndOrder() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Test @Throws(Exception::class) open fun readinessRejectsBeforeLoad() { FixtureChecks.readinessRejectsBeforeLoad() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Test @Throws(Exception::class) open fun badBytesAreSafe() { FixtureChecks.badBytesAreSafe() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Test open fun singleConfirmationAndPassword() { FixtureChecks.singleConfirmationAndPassword() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Test open fun lateResultIsInvalidated() { FixtureChecks.lateResultIsInvalidated() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Test @Throws(Exception::class) open fun cleanupRetainsOwner() { FixtureChecks.cleanupRetainsOwner() }
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    @Test open fun reusedBackendQueuedCancellation() { FixtureChecks.reusedBackendQueuedCancellation() }
}
