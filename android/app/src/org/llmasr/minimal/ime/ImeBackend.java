package org.llmasr.minimal.ime;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import org.llmasr.minimal.asr.InferenceAdapter;
import org.llmasr.minimal.asr.NativeResponse;
import org.llmasr.minimal.audio.ForegroundRecorder;
import org.llmasr.minimal.audio.RecordingControl;
import org.llmasr.minimal.audio.WaveInput;
import org.llmasr.minimal.diagnostics.RuntimeLogSource;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.platform.OperationContext;
import org.llmasr.minimal.task.RequestContext;

/** Application-context-only adapter; no Service, View, report or text store. */
public final class ImeBackend implements ImeController.Backend {
    private final OperationContext context;
    private final ModelRepository modelRepository;
    private final InferenceAdapter inference;
    public ImeBackend(OperationContext context, ModelRepository modelRepository, InferenceAdapter inference) {
        this.context = context;
        this.modelRepository = modelRepository;
        this.inference = inference;
    }
    @Override public byte[] capture(RecordingControl control, ImeController.Progress progress) throws IOException {
        return ForegroundRecorder.capture(control, progress::update);
    }
    @Override public String transcribe(RequestContext request, byte[] wav, String language) throws Exception {
        request.inputWav = new File(context.filesDir(), "input-" + request.requestId + ".wav");
        try (ByteArrayInputStream in = new ByteArrayInputStream(wav)) {
            WaveInput.canonicalize(in, request.inputWav);
        }
        NativeResponse r = inference.transcribe(RuntimeLogSource.IME, request.requestId,
            new File(modelRepository.modelDir(), "config.json").getAbsolutePath(),
            request.inputWav.getAbsolutePath(), language, context.cacheDir().getAbsolutePath());
        return r.display;
    }
    @Override public void cleanup(RequestContext request) throws IOException {
        if (request.inputWav != null && request.inputWav.exists() && !request.inputWav.delete())
            throw new IOException("无法删除临时录音；下次启动将重试清理");
    }
}
