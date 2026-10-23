import org.junit.Test

/** Same complete regression delegates; JVM runner evidence is separate from Android acceptance. */
class RuntimeRegressionJUnitTest {
    @Test(timeout = 45000) @Throws(Exception::class)
    fun inferenceAdapterAndJniConstruction() { org.llmasr.minimal.InferenceAdapterTest.main(emptyArray()) }
}
