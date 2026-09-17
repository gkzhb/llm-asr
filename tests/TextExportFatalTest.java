import org.llmasr.minimal.transcription.ResultState;
import org.llmasr.minimal.transcription.TextExportController;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Compound error regression against production export worker; not Android IO. */
public final class TextExportFatalTest {
    public static void main(String[] args) {
        for (boolean primaryFatal : new boolean[]{false,true}) {
            Error fatal = new InternalError("sentinel-fatal");
            IOException ordinary = new IOException("sentinel-provider");
            AtomicInteger closes = new AtomicInteger();
            List<Runnable> jobs = new ArrayList<>();
            TextExportController<String> c = new TextExportController<>(target -> new OutputStream() {
                public void write(int b) throws IOException { if(primaryFatal) throw fatal; throw ordinary; }
                public void close() throws IOException { closes.incrementAndGet(); if(primaryFatal) throw ordinary; throw fatal; }
            }, jobs::add);
            ResultState result = new ResultState(); result.setText("secret");
            TextExportController.Ticket ticket = c.begin(c.newPage(),result);
            c.admit(ticket,"target");
            Throwable caught = null;
            try { jobs.remove(0).run(); } catch(Throwable e) { caught=e; }
            if(caught != fatal) throw new AssertionError("exact fatal must propagate even when close is suppressed under ordinary write failure");
            if(closes.get()!=1 || c.state().busy() || c.state().phase!=TextExportController.Phase.FAILED)
                throw new AssertionError("fatal must attempt close exactly once and release with FAILED");
        }
        System.out.println("PASS compound write/close exact fatal propagation and cleanup");
    }
}
