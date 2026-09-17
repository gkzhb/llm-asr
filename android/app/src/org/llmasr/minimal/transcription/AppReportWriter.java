package org.llmasr.minimal.transcription;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;
import org.llmasr.minimal.asr.NativeResponse;
import org.llmasr.minimal.platform.OperationContext;
import org.llmasr.minimal.task.RequestContext;

/** App-only Android JSON/report adapter. Retains the existing on-disk contract.
 * Request lifecycle and rich-terminal suppression remain in AppRequestPolicy.
 */
public final class AppReportWriter implements AppRequestPolicy.Reports {
    private final OperationContext context;

    public AppReportWriter(OperationContext context) { this.context = context; }

    @Override public void writePending(RequestContext ctx) throws IOException { saveReport(ctx, pendingReport()); }
    @Override public void writeTerminal(RequestContext ctx) throws IOException { saveReport(ctx, modelOperationReport()); }
    @Override public void writeFailure(RequestContext ctx, Throwable cause) throws IOException { saveReport(ctx, failureReport(cause)); }
    @Override public void writeCancel(RequestContext ctx) throws IOException { saveReport(ctx, cancelReport()); }

    public void writeSuccess(RequestContext ctx, NativeResponse r, File wav,
                             double seconds, String language, String source) throws IOException {
        try {
            JSONObject report = new JSONObject();
            report.put("success", true);
            report.put("state", "complete");
            report.put("kind", "inference");
            report.put("source", source);
            report.put("language", language);
            report.put("text", r.display);
            report.put("raw", r.raw);
            report.put("load_s", r.loadSeconds);
            report.put("inference_s", r.inferenceSeconds);
            report.put("audio_s", seconds);
            report.put("rtf", r.inferenceSeconds / seconds);
            report.put("generated_tokens", r.generatedTokens);
            report.put("truncated", false);
            report.put("uid", android.os.Process.myUid());
            report.put("pid", android.os.Process.myPid());
            report.put("model_manifest_sha256", assetDigest("model-manifest.json"));
            report.put("audio_sha256", fileDigest(wav));
            saveReport(ctx, report);
        } catch (JSONException je) {
            throw new IOException("Cannot serialize inference report: " + je.getMessage());
        }
    }

    private String assetDigest(String name) throws IOException {
        try (InputStream in = context.openAsset(name)) {
            return hex(MessageDigest.getInstance("SHA-256").digest(readSmall(in)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable");
        }
    }

    private String fileDigest(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return hex(MessageDigest.getInstance("SHA-256").digest(readSmall(in)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable");
        }
    }

    private static byte[] readSmall(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) != -1) {
            if (out.size() + n > 1024 * 1024) throw new IOException("Metadata too large");
            out.write(b, 0, n);
        }
        return out.toByteArray();
    }

    private static String hex(byte[] data) {
        StringBuilder s = new StringBuilder();
        for (byte b : data) s.append(String.format(Locale.ROOT, "%02x", b & 255));
        return s.toString();
    }

    private void saveReport(RequestContext ctx, JSONObject report) throws IOException {
        try {
            report.put("request_id", ctx.requestId);
            report.put("timestamp_ms", System.currentTimeMillis());
            File part = new File(context.filesDir(), "last-result-" + ctx.requestId + ".part");
            try (FileOutputStream out = new FileOutputStream(part)) {
                out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            if (!part.renameTo(new File(context.filesDir(), "last-result.json"))) throw new IOException("Cannot save result");
        } catch (JSONException je) {
            throw new IOException("Cannot serialize report: " + je.getMessage());
        }
    }

    private JSONObject pendingReport() {
        JSONObject r = new JSONObject();
        try { r.put("state", "pending"); r.put("success", false); } catch (JSONException impossible) {}
        return r;
    }
    private JSONObject modelOperationReport() {
        JSONObject r = new JSONObject();
        try { r.put("state", "complete"); r.put("success", true); r.put("kind", "model-operation"); } catch (JSONException impossible) {}
        return r;
    }
    private JSONObject failureReport(Throwable cause) {
        JSONObject r = new JSONObject();
        try { r.put("state", "failed"); r.put("success", false); r.put("error", String.valueOf(cause)); } catch (JSONException impossible) {}
        return r;
    }
    private JSONObject cancelReport() {
        JSONObject r = new JSONObject();
        try { r.put("state", "cancelled"); r.put("success", false); } catch (JSONException impossible) {}
        return r;
    }

}
