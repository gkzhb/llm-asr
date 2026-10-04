import org.junit.Test;

/** Transitional per-entry JUnit adapters. Each invokes the unchanged production
 * regression body, including deterministic latches, failure identity and cleanup.
 * main() entries remain independently runnable; no Android stub/runtime is used.
 */
public final class CoreRegressionJUnitTest {
    private static final String[] NONE = new String[0];
    @Test(timeout=45000) public void kotlinJavaAbi() throws Exception { KotlinMigrationTest.main(NONE); }
    @Test(timeout=45000) public void waveAndRecordingProtocol() throws Exception { MinimalApkTest.main(new String[]{"bench/audio/zh-original.wav"}); }
    @Test(timeout=45000) public void recordingRaces() throws Exception { RecordingRaceTest.main(NONE); }
    @Test(timeout=45000) public void resultFiles() throws Exception { ResultFilesTest.main(NONE); }
    @Test(timeout=45000) public void nativeResponseProtocol() throws Exception { NativeResponseTest.main(NONE); }
    @Test(timeout=45000) public void modelRepository() throws Exception { ModelRepositoryTest.main(NONE); }
    @Test(timeout=45000) public void privateModelRoot() throws Exception { ModelPrivateRootTest.main(NONE); }
    @Test(timeout=45000) public void partRecoveryProductionMutant() throws Exception { PartRecoveryTest.main(NONE); }
    @Test(timeout=45000) public void requestRunner() throws Exception { RequestRunnerTest.main(NONE); }
    @Test(timeout=45000) public void taskCoordinator() throws Exception { TaskCoordinatorTest.main(NONE); }
    @Test(timeout=45000) public void admissionOwnerBoundary() throws Exception { AdmissionBoundaryTest.main(NONE); }
    @Test(timeout=45000) public void imeSessionPrivacy() throws Exception { ImeSessionTest.main(NONE); }
    @Test(timeout=45000) public void modelReadiness() throws Exception { ModelReadinessTest.main(NONE); }
    @Test(timeout=45000) public void modelOperationControl() throws Exception { ModelOperationControlTest.main(NONE); }
    @Test(timeout=45000) public void modelCancelCleanup() throws Exception { ModelRepositoryCancelTest.main(NONE); }
    @Test(timeout=45000) public void modelWhitelistDeletion() throws Exception { ModelRepositoryDeleteTest.main(NONE); }
    @Test(timeout=45000) public void modelManagementController() throws Exception { ModelManagementControllerTest.main(NONE); }
    @Test(timeout=45000) public void modelPageHelpers() throws Exception { ModelAndroidHelpersTest.main(NONE); }
    @Test(timeout=45000) public void runtimeLogCore() throws Exception { org.llmasr.minimal.diagnostics.RuntimeLogCoreTest.main(NONE); }
    @Test(timeout=45000) public void runtimeLogErrorIdentity() throws Exception { org.llmasr.minimal.RuntimeLogStoreErrorTest.main(NONE); }
    @Test(timeout=45000) public void logExportGenerations() throws Exception { LogExportTest.main(NONE); }
    @Test(timeout=45000) public void textExport() throws Exception { TextExportTest.main(NONE); }
    @Test(timeout=45000) public void textExportSafety() throws Exception { TextExportSafetyTest.main(NONE); }
    @Test(timeout=45000) public void textExportFatalIdentity() throws Exception { TextExportFatalTest.main(NONE); }
    @Test(timeout=45000) public void modelNotifications() throws Exception { ModelNotificationTest.main(NONE); }
    @Test(timeout=45000) public void providerErrorAndStreamBoundary() throws Exception { ModelProviderBoundaryTest.main(NONE); }
    @Test(timeout=45000) public void reviewHistory() throws Exception { ModelReviewFixTest.main(new String[]{"history"}); }
    @Test(timeout=45000) public void reviewTerminal() throws Exception { ModelReviewFixTest.main(new String[]{"terminal"}); }
    @Test(timeout=45000) public void reviewWindow() throws Exception { ModelReviewFixTest.main(new String[]{"window"}); }
    @Test(timeout=45000) public void reviewBoundary() throws Exception { ModelReviewFixTest.main(new String[]{"boundary"}); }
    @Test(timeout=45000) public void reviewDeletion() throws Exception { ModelReviewFixTest.main(new String[]{"deletion"}); }
    @Test(timeout=45000) public void reviewLiveDeletion() throws Exception { ModelReviewFixTest.main(new String[]{"liveDeletion"}); }
    @Test(timeout=45000) public void reviewPlanning() throws Exception { ModelReviewFixTest.main(new String[]{"planning"}); }
    @Test(timeout=45000) public void reviewPublication() throws Exception { ModelReviewFixTest.main(new String[]{"publication"}); }
}
