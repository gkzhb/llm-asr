package org.llmasr.minimal.testhost;

import org.junit.Test;

/** Seven nonzero host tests; Android wrapper invokes these same shared checks. */
public final class FixtureContractTest {
    @Test public void enginePayloadAndOrder() throws Exception { FixtureChecks.enginePayloadAndOrder(); }
    @Test public void readinessRejectsBeforeLoad() throws Exception { FixtureChecks.readinessRejectsBeforeLoad(); }
    @Test public void badBytesAreSafe() throws Exception { FixtureChecks.badBytesAreSafe(); }
    @Test public void singleConfirmationAndPassword() { FixtureChecks.singleConfirmationAndPassword(); }
    @Test public void lateResultIsInvalidated() { FixtureChecks.lateResultIsInvalidated(); }
    @Test public void cleanupRetainsOwner() throws Exception { FixtureChecks.cleanupRetainsOwner(); }
    @Test public void reusedBackendQueuedCancellation() { FixtureChecks.reusedBackendQueuedCancellation(); }
}
