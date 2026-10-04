import org.junit.Test;

/** Real production port/owner regressions, never Android/JNI runtime acceptance. */
public final class RuntimeRegressionJUnitTest {
    @Test(timeout = 45000) public void inferenceAdapterAndJniConstruction() throws Exception {
        org.llmasr.minimal.InferenceAdapterTest.main(new String[0]);
    }
}
