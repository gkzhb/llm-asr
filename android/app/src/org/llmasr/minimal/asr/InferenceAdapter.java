package org.llmasr.minimal.asr;

import java.io.IOException;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;
import org.llmasr.minimal.diagnostics.RuntimeLogStore;

/** Actual App/IME orchestration; no Android runtime needed to exercise it.
 * Caller retains task admission, WAV lifecycle and external result ownership.
 * Successful return means validated native protocol, not external side effects.
 */
public final class InferenceAdapter {
    public interface Readiness { void requireReady() throws IOException; }
    public interface Library { void load(); }
    private final RuntimeLogStore store;
    private final Readiness readiness;
    private final Library library;
    private final NativeTranscription nativeBridge;

    public InferenceAdapter(RuntimeLogStore store, Readiness readiness, Library library,
                            NativeTranscription nativeBridge) {
        this.store = store;
        this.readiness = readiness;
        this.library = library;
        this.nativeBridge = nativeBridge;
    }

    public NativeResponse transcribe(RuntimeLogSource source, String requestId,
            String config, String wav, String language, String cache) throws IOException {
        LoggingInferencePhaseListener phases = new LoggingInferencePhaseListener(store, source, requestId);
        String failure = "verify-failed";
        try {
            phases.accessStarted();
            readiness.requireReady();
            phases.accessCompleted();
            failure = "native-runtime";
            library.load();
            byte[] output = nativeBridge.invokeWithListener(config, wav, language, cache, phases);
            if (output == null) {
                failure = "native-null";
                throw new IOException("Native returned null");
            }
            failure = "parse-failed";
            NativeResponse result = parse(output);
            phases.returned();
            return result;
        } catch (Throwable original) {
            phases.failed(failure);
            // In particular preserve LinkageError/VM errors and native throwable identity.
            throw original;
        }
    }

    private static NativeResponse parse(byte[] output) throws IOException {
        try { return NativeResponse.parse(output); }
        catch (IllegalArgumentException malformed) {
            // Neither native payload nor parser messages cross into runtime logs/errors.
            throw new IOException("Invalid native response protocol");
        }
    }
}
