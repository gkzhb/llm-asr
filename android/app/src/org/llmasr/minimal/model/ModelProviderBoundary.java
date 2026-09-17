package org.llmasr.minimal.model;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Pure-Java provider boundary. Only provider calls belong inside call(); own gate
 * checkpoints and validation stay outside so no provider-authored type is trusted. */
public final class ModelProviderBoundary {
    private ModelProviderBoundary() {}
    public static final String DIRECTORY_FAILURE="无法读取所选目录，请重新授权或改用本地目录";
    public static final String FILE_FAILURE="无法读取所选模型文件，请重新选择并授权或改用本地目录";
    public interface Call<T> { T run() throws IOException; }
    public static <T> T call(String safeMessage, Call<T> provider) throws IOException {
        try { return provider.run(); }
        catch (IOException | RuntimeException | LinkageError e) { throw new IOException(safeMessage); }
    }
    public static void checkpoint(ModelRepository.CancelGate gate) throws IOException {
        if (gate.cancelled()) throw new ModelRepository.CancelledException();
    }
    public static InputStream open(Call<InputStream> provider) throws IOException {
        InputStream stream=call(FILE_FAILURE,provider);
        if(stream==null)throw new IOException(FILE_FAILURE);
        return new FilterInputStream(stream) {
            @Override public int read() throws IOException { return call(FILE_FAILURE,()->in.read()); }
            @Override public int read(byte[] b,int off,int len)throws IOException { return call(FILE_FAILURE,()->in.read(b,off,len)); }
            @Override public void close()throws IOException { call(FILE_FAILURE,()->{in.close();return null;}); }
        };
    }
}
