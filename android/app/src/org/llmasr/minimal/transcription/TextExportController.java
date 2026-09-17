package org.llmasr.minimal.transcription;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One process-wide TXT slot, independent of the ASR owner. Lock order is
 * controller lock -> ResultState monitor. ResultState never calls back here.
 * Observers and executor/provider calls are outside both locks. QUEUED is
 * revocable; WRITING is the irreversible admission immediately before open.
 * A revoked queued slot stays held until its actual runnable unwinds. */
public final class TextExportController<T> {
    public interface Backend<T> { OutputStream open(T target) throws IOException; }
    public interface Listener { void onChange(); }
    public enum Phase { IDLE, SELECTING, QUEUED, WRITING, SUCCEEDED, FAILED, CANCELLED, EXPIRED }
    public static final int FIRST_CODE=5000, LAST_CODE=65534;
    public static final class State {
        public final long pageId, bytes;
        public final int requestCode;
        public final Phase phase;
        private State(Ticket t, Phase phase, long bytes) {
            pageId=t == null ? 0 : t.pageId; requestCode=t == null ? 0 : t.requestCode;
            this.phase=phase; this.bytes=bytes;
        }
        public boolean busy() { return phase==Phase.SELECTING || phase==Phase.QUEUED || phase==Phase.WRITING; }
    }
    public static final class Ticket {
        public final long pageId;
        public final int requestCode;
        public final ResultState.Snapshot snapshot;
        private final ResultState source;
        private boolean revoked;
        private Ticket(long pageId, int code, ResultState source, ResultState.Snapshot snapshot) {
            this.pageId=pageId; requestCode=code; this.source=source; this.snapshot=snapshot;
        }
    }
    private final Object lock=new Object();
    private final Backend<T> backend;
    private final Executor executor;
    private final CopyOnWriteArrayList<WeakReference<Listener>> listeners=new CopyOnWriteArrayList<>();
    private Ticket active;
    private long nextPage;
    private int nextCode=FIRST_CODE;
    private State state=new State(null, Phase.IDLE, 0);
    public TextExportController(Backend<T> backend) { this(backend, executor()); }
    /** Production executor must be asynchronous. Slot prevents unbounded enqueues. */
    public TextExportController(Backend<T> backend, Executor executor) {
        if (backend==null || executor==null) throw new IllegalArgumentException("export dependencies required");
        this.backend=backend; this.executor=executor;
    }
    private static Executor executor() {
        return new ThreadPoolExecutor(0,1,30L,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1),r -> {
            Thread t=new Thread(r,"text-export-worker"); t.setDaemon(true); return t;
        });
    }
    public long newPage() { synchronized(lock) { if(nextPage==Long.MAX_VALUE) throw new IllegalStateException("page limit"); return ++nextPage; } }
    public static boolean isRequest(int code) { return code>=FIRST_CODE && code<=LAST_CODE; }
    /** Lazily retire a cleared picker. Queued/active IO slots are never freed here. */
    private void expirePicker() {
        if(active!=null && state.phase==Phase.SELECTING && !active.source.exportValid(active.snapshot)) {
            state=new State(active,Phase.EXPIRED,0); active=null;
        }
    }
    public State state() { synchronized(lock) { expirePicker(); return state; } }
    public boolean owns(Ticket t) { synchronized(lock) { expirePicker(); return t!=null && active==t; } }
    public Ticket begin(long pageId, ResultState result) {
        if(result==null) return null;
        Ticket ticket;
        synchronized(lock) {
            expirePicker();
            if(active!=null || pageId<=0 || pageId>nextPage || nextCode>LAST_CODE) return null;
            synchronized(result) {
                ResultState.Snapshot snapshot=result.begin();
                if(snapshot.isEmpty() || snapshot.text.length()>100000) return null;
                ticket=new Ticket(pageId,nextCode++,result,snapshot);
                active=ticket; state=new State(ticket,Phase.SELECTING,0);
            }
        }
        notifyChange(); return ticket;
    }
    public void revoke(Ticket ticket) {
        synchronized(lock) {
            if(ticket==null || active!=ticket || state.phase==Phase.WRITING) return;
            ticket.revoked=true;
            if(state.phase==Phase.SELECTING) { state=new State(ticket,Phase.CANCELLED,0); active=null; }
            // QUEUED retains the slot until runnable or submission rejection finishes.
        }
        notifyChange();
    }
    /** Accept picker result into bounded worker, NOT irreversible write admission. */
    public boolean admit(Ticket ticket, T target) {
        if(ticket==null || target==null) return false;
        synchronized(lock) {
            expirePicker();
            if(active!=ticket || state.phase!=Phase.SELECTING) return false;
            synchronized(ticket.source) {
                if(!ticket.source.exportValid(ticket.snapshot)) {
                    state=new State(ticket,Phase.EXPIRED,0); active=null; return false;
                }
                state=new State(ticket,Phase.QUEUED,0);
            }
        }
        try { executor.execute(() -> write(ticket,target)); }
        catch(RuntimeException | LinkageError rejected) { complete(ticket,Phase.FAILED,0); return false; }
        catch(Error fatal) { complete(ticket,Phase.FAILED,0); throw fatal; }
        notifyChange(); return true;
    }
    private void write(Ticket ticket,T target) {
        Phase terminal=Phase.FAILED;
        long bytes=0;
        try {
            synchronized(lock) {
                if(active!=ticket) return;
                synchronized(ticket.source) {
                    if(ticket.revoked) { terminal=Phase.CANCELLED; return; }
                    if(!ticket.source.exportValid(ticket.snapshot)) { terminal=Phase.EXPIRED; return; }
                    // Atomic against explicit clear, immediately before provider open.
                    state=new State(ticket,Phase.WRITING,0);
                }
            }
            // No observer before open: do not insert arbitrary work between admission and IO.
            OutputStream out=backend.open(target);
            if(out==null) throw new IOException("open-failed");
            Throwable primary=null;
            try {
                ResultFiles.writeText(out,ticket.snapshot.text);
                bytes=ticket.snapshot.text.getBytes(StandardCharsets.UTF_8).length;
            } catch(IOException | RuntimeException | Error failure) {
                primary=failure;
                throw failure;
            } finally {
                try { out.close(); }
                catch(IOException | RuntimeException | Error closing) {
                    // A fatal close must not hide under a recoverable write failure.
                    // Preserve a primary fatal's identity; ordinary/Linkage failures
                    // remain recoverable and never expose diagnostics to the UI.
                    if(primary==null) throw closing;
                    if(closing instanceof Error && !(closing instanceof LinkageError)
                            && !(primary instanceof Error && !(primary instanceof LinkageError))) {
                        if(primary!=closing) closing.addSuppressed(primary);
                        throw (Error)closing;
                    }
                    if(primary!=closing) primary.addSuppressed(closing);
                }
            }
            terminal=Phase.SUCCEEDED; // close must succeed
        } catch(IOException | RuntimeException | LinkageError failure) {
            // Fixed state only; no provider URI, body, path or raw exception/log.
        } finally { complete(ticket,terminal,bytes); }
    }
    private void complete(Ticket ticket,Phase terminal,long bytes) {
        synchronized(lock) {
            if(active!=ticket) return;
            state=new State(ticket,terminal,terminal==Phase.SUCCEEDED ? bytes : 0); active=null;
        }
        notifyChange();
    }
    public void addListener(Listener l) {
        if(l==null) return; removeListener(l); listeners.add(new WeakReference<>(l));
    }
    public void removeListener(Listener l) { listeners.removeIf(ref -> ref.get()==null || ref.get()==l); }
    private void notifyChange() {
        for(WeakReference<Listener> ref:listeners) {
            Listener l=ref.get();
            if(l==null) listeners.remove(ref);
            else try { l.onChange(); } catch(Throwable ignored) { /* observation cannot change IO ownership */ }
        }
    }
    public static String statusText(State s) {
        switch(s.phase) {
            case SELECTING: return "TXT：等待选择保存位置。";
            case QUEUED: return "TXT：等待或即将写入；仅尚未开始的写入可被清除结果或关闭页面撤销。";
            case WRITING: return "TXT：正在写入；已开始的外部写入无法撤回。";
            case SUCCEEDED: return "TXT：已导出所选快照（UTF-8，"+s.bytes+" 字节）。";
            case FAILED: return "TXT：导出失败；外部位置可能留下空或部分文件，请自行检查。";
            case CANCELLED: return "TXT：已取消，未写入文本。";
            case EXPIRED: return "TXT：结果已清除，旧导出失效，未写入文本。";
            default: return "TXT：导出独立于转写；云存储提供方可能联网同步。";
        }
    }
}
