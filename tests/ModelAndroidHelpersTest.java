import org.llmasr.minimal.model.ModelAccess;
import org.llmasr.minimal.modelmanagement.ModelManagementController;
import org.llmasr.minimal.modelmanagement.ModelManagementState;
import org.llmasr.minimal.model.ModelManifest;
import org.llmasr.minimal.modelmanagement.ModelOperationControl;
import org.llmasr.minimal.modelmanagement.ModelPageSession;
import org.llmasr.minimal.modelmanagement.ModelPickerTickets;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.model.ModelSource;
import org.llmasr.minimal.modelmanagement.ModelUiText;
import org.llmasr.minimal.task.TaskCoordinator;
import org.llmasr.minimal.transcription.TextExportController;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Runs real production page policy + owner/controller/repository + lazy SHA.
 * No Android stubs, fake AppGraph, disconnected transcript assertions or unbounded waits. */
public final class ModelAndroidHelpersTest {
    private static int checks;
    private static void check(boolean b) { checks++; if (!b) throw new AssertionError("check " + checks); }
    private static final byte[] DATA={1,2,3};
    private static final class Queue implements Executor {
        Runnable task;
        public void execute(Runnable r) { if(task!=null) throw new AssertionError("duplicate task"); task=r; }
        void run() { Runnable r=task; task=null; r.run(); }
    }
    private static final class Fixture implements AutoCloseable {
        final Queue queue=new Queue();
        final AtomicInteger releases=new AtomicInteger();
        final TaskCoordinator owner=new TaskCoordinator(queue);
        { owner.addListener(releases::incrementAndGet); }
        final ModelReadiness ready=new ModelReadiness();
        final ModelManagementState state=new ModelManagementState();
        final ModelOperationControl control=new ModelOperationControl();
        final ModelPickerTickets tickets=new ModelPickerTickets();
        final File dir;
        final ModelRepository repo;
        final ModelManagementController controller;
        Fixture() throws Exception {
            dir=Files.createTempDirectory("model-android-policy-").toFile();
            ModelManifest manifest=ModelRepositoryCancelTest.manifest(DATA,"a","b");
            repo=new ModelRepository(manifest,dir,()->123456789);
            controller=new ModelManagementController(owner,repo,ready,control,state);
        }
        ModelSource source() { return new ModelRepositoryCancelTest.Source(DATA,"a","b"); }
        ModelPageSession<ModelSource> page(String id) { return new ModelPageSession<>(tickets,controller,id); }
        void install() throws Exception { for(String n:new String[]{"a","b"})Files.write(new File(dir,n).toPath(),DATA); }
        public void close() throws Exception {
            if(queue.task!=null) queue.run();
            if (!dir.exists()) return;
            try(java.util.stream.Stream<Path> paths=Files.walk(dir.toPath())) {
                for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args) throws Exception {
        // Waiting picker owns nothing. Callback before resume is deferred exactly once.
        try(Fixture f=new Fixture()) {
            ModelPageSession<ModelSource> p=f.page(null); p.foreground(true); int code=p.beginPicker();
            check(!f.owner.isBusy()); check(!TextExportController.isRequest(code)); check(code!=11 && code!=12);
            p.foreground(false); check(p.result(code,f.source())); check(p.hasDeferred());
            check(!p.consume(f.controller::startImport)); check(!f.owner.isBusy());
            check(!p.result(code,f.source())); p.foreground(true); check(p.consume(f.controller::startImport));
            check(!p.hasDeferred()); check(f.owner.isBusy()); check(p.ownsActive());
            check(!p.consume(f.controller::startImport)); check(!p.result(code,f.source()));
            f.queue.run(); check(f.ready.isReady()); check(!f.owner.isBusy()); p.destroy();
        }
        // Recreated picker rejects; old destroy cannot release another Activity's newer ticket.
        try(Fixture f=new Fixture()) {
            ModelPageSession<ModelSource> old=f.page(null);old.foreground(true);int code=old.beginPicker();
            ModelPageSession<ModelSource> fresh=f.page(null); fresh.foreground(true);
            check(!fresh.result(code,f.source()));
            try { fresh.beginPicker(); throw new AssertionError(); } catch(IllegalStateException expected) {check(true);}
            old.destroy(); int next=fresh.beginPicker(); check(next!=code); old.destroy();
            check(fresh.result(next,f.source()));check(fresh.consume(f.controller::startImport)); f.queue.run();
            check(!old.result(code,f.source())); fresh.destroy();
        }
        // Chooser cancel preserves actual READY/epoch and files; busy result consumed, never replayed.
        try(Fixture f=new Fixture()) {
            f.install(); ModelAccess access=new ModelAccess(f.repo,f.ready);
            check(f.owner.submit(access::requireReady));f.queue.run();check(f.ready.isReady());long epoch=f.ready.epoch();
            ModelPageSession<ModelSource> p=f.page(null);p.foreground(true);int code=p.beginPicker();
            check(p.result(code,null));check(!p.consume(f.controller::startImport));check(f.ready.isReady());check(f.ready.epoch()==epoch);
            int next=p.beginPicker(); check(f.owner.submit(()->{}));check(p.result(next,f.source()));
            check(!p.consume(f.controller::startImport)); check(!p.hasDeferred());f.queue.run();
            check(!p.consume(f.controller::startImport));check(f.ready.isReady());p.destroy();
        }
        // Rotation keeps process task; only restored owner ID may cancel it. onPause alone does not cancel.
        try(Fixture f=new Fixture()) {
            ModelPageSession<ModelSource> old=f.page(null);old.foreground(true);
            check(f.controller.startImport(f.source()));old.adoptStarted();String id=old.ownedId();
            old.foreground(false);check(!f.control.peekActive().isCancelRequested());old.stopped(true);old.destroy();
            check(!f.control.peekActive().isCancelRequested());
            ModelPageSession<ModelSource> stranger=f.page(null);stranger.stopped(false);check(!f.control.peekActive().isCancelRequested());
            ModelPageSession<ModelSource> fresh=f.page(id);check(fresh.ownsActive());check(fresh.cancellable());fresh.stopped(false);
            check(f.control.peekActive().isCancelRequested());check(f.owner.isBusy());check(!fresh.cancellable());
            f.queue.run();check(f.state.current().phase==ModelManagementState.Phase.CANCELLED);check(!f.owner.isBusy());
            fresh.destroy();stranger.destroy();
        }
        // Delete continues on nonconfiguration stop; stale confirmation is rejected under owner.
        try(Fixture f=new Fixture()) {
            f.install();ModelPageSession<ModelSource> p=f.page(null);
            long epoch=f.ready.epoch();f.ready.invalidate();check(!f.controller.startDelete(epoch,true));check(!f.owner.isBusy());
            check(f.controller.startDelete(f.ready.epoch(),true));p.adoptStarted();check(!p.cancellable());p.stopped(false);
            check(!f.control.peekActive().isCancelRequested());check(f.owner.isBusy());f.queue.run();
            check(f.ready.state()==ModelReadiness.State.NOT_INSTALLED);check(f.state.current().deleteFailed==0);p.destroy();
        }
        // Lazy success, cache hit, real hash failure and conservative notifications. No true setter proof.
        try(Fixture f=new Fixture()) {
            f.install();AtomicInteger hashCalls=new AtomicInteger(), notifications=new AtomicInteger();
            ModelRepository repo=new ModelRepository(f.repo.manifest(),f.dir,()->123456789) {
                @Override public void verifyAll(Progress p,CancelGate c) throws IOException {hashCalls.incrementAndGet();super.verifyAll(p,c);}
            };
            ModelReadiness.Listener listener=notifications::incrementAndGet;f.ready.addListener(listener);
            ModelAccess access=new ModelAccess(repo,f.ready);
            f.ready.markVerified(null);check(!f.ready.isReady()); // No token cannot manufacture proof.
            check(f.owner.submit(access::requireReady));f.queue.run();check(hashCalls.get()==1);check(f.ready.isReady());
            check(f.owner.submit(access::requireReady));f.queue.run();check(hashCalls.get()==1);
            f.ready.invalidate();check(!f.ready.isReady());Files.write(new File(f.dir,"a").toPath(),new byte[]{9,9,9});
            AtomicBoolean failed=new AtomicBoolean();int before=notifications.get();
            check(f.owner.submit(()->{try{access.requireReady();}catch(IOException e){failed.set(true);check(e.getMessage().contains("模型管理"));}}));
            f.queue.run();check(failed.get());check(!f.ready.isReady());check(f.ready.state()==ModelReadiness.State.INVALID);check(notifications.get()>before);
            f.ready.removeListener(listener);
        }
        // Invalidate precisely after real SHA but before commit: native continuation must not run.
        try(Fixture f=new Fixture()) {
            f.install();AtomicBoolean continuation=new AtomicBoolean();AtomicInteger failures=new AtomicInteger();
            ModelRepository repo=new ModelRepository(f.repo.manifest(),f.dir,()->123456789) {
                @Override public void verifyAll(Progress p,CancelGate c) throws IOException {
                    check(f.ready.state()==ModelReadiness.State.VERIFYING);super.verifyAll(p,c);f.ready.invalidate();
                }
            };
            ModelAccess access=new ModelAccess(repo,f.ready);
            check(f.owner.submit(()->{try{access.requireReady();continuation.set(true);}catch(IOException e){failures.incrementAndGet();}}));f.queue.run();
            check(!continuation.get());check(failures.get()==1);check(!f.ready.isReady());
        }
        // Real blocked hash retains shared owner, not a synthetic busy variable. All gates bounded/finally released.
        try(Fixture f=new Fixture()) {
            f.install();CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
            AtomicReference<Throwable> fault=new AtomicReference<>();
            ModelRepository repo=new ModelRepository(f.repo.manifest(),f.dir,()->123456789) {
                @Override public void verifyAll(Progress p,CancelGate c) throws IOException {
                    entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IOException("gate timeout");}
                    catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}super.verifyAll(p,c);
                }
            };
            ModelAccess access=new ModelAccess(repo,f.ready);check(f.owner.submit(access::requireReady));
            Thread worker=new Thread(()->{try{f.queue.run();}catch(Throwable t){fault.set(t);}},"lazy-test-worker");
            worker.start();
            try {check(entered.await(2,TimeUnit.SECONDS));check(f.owner.isBusy());check(!f.controller.startDelete(f.ready.epoch(),true));check(!f.controller.startVerify());}
            finally {release.countDown();worker.join(4000);}
            check(!worker.isAlive());check(fault.get()==null);check(f.ready.isReady());check(!f.owner.isBusy());
        }
        // Missing files notify NOT_INSTALLED, not permanent VERIFYING or zero-sized READY.
        try(Fixture f=new Fixture()) {
            ModelAccess access=new ModelAccess(f.repo,f.ready);AtomicInteger notifications=new AtomicInteger();
            ModelReadiness.Listener listener=notifications::incrementAndGet;f.ready.addListener(listener);
            check(f.owner.submit(()->{try{access.requireReady();throw new AssertionError();}catch(IOException expected){check(true);}}));f.queue.run();
            check(f.ready.state()==ModelReadiness.State.NOT_INSTALLED);check(notifications.get()>=2);check(!f.owner.isBusy());
        }
        // IO-free graph repository factory: root validation is deferred, never skipped by worker operations.
        try(Fixture f=new Fixture()) {
            File poison=new File(f.dir,"not-statted-at-construction") {
                @Override public String getCanonicalPath() throws IOException { throw new AssertionError("constructor IO"); }
                @Override public boolean exists() { throw new AssertionError("constructor IO"); }
            };
            check(ModelRepository.forWorker(f.repo.manifest(),poison,()->0).modelDir()==poison);
            File link=new File(f.dir,"linked");File outside=Files.createTempDirectory("model-root-boundary-").toFile();
            try {
                Files.createSymbolicLink(link.toPath(),outside.toPath());
                ModelRepository deferred=ModelRepository.forWorker(f.repo.manifest(),link,()->0);
                try{deferred.inspect(ModelRepository.NEVER_CANCEL);throw new AssertionError();}catch(IOException expected){check(true);}
                try{deferred.verifyAll();throw new AssertionError();}catch(IOException expected){check(true);}
                try{deferred.importFrom(f.source(),null);throw new AssertionError();}catch(IOException expected){check(true);}
                try{deferred.deleteAll();throw new AssertionError();}catch(IOException expected){check(true);}
                check(outside.list().length==0);
            } finally {Files.deleteIfExists(link.toPath());Files.deleteIfExists(outside.toPath());}
        }
        // Ticket exhaustion is bounded/fail-closed without wrapping into export/audio identities.
        ModelPickerTickets tickets=new ModelPickerTickets();
        for(int code=ModelPickerTickets.FIRST;code<=ModelPickerTickets.LAST;code++) {
            ModelPickerTickets.Ticket t=tickets.begin();if(t.requestCode!=code || TextExportController.isRequest(code))throw new AssertionError();tickets.release(t);
        }
        try{tickets.begin();throw new AssertionError();}catch(IllegalStateException expected){check(true);}
        check(ModelUiText.bytes(-1).contains("未知"));check(!ModelUiText.bytes(0).contains("未知"));
        check(ModelUiText.readiness(ModelReadiness.State.UNVERIFIED).contains("不等于"));
        check(ModelUiText.operation(ModelManagementState.OperationKind.INSPECT).contains("不进行 SHA"));
        check(ModelUiText.phase(ModelManagementState.Phase.COPYING).contains("仍需"));
        check(!ModelUiText.safeError("open /data/user/0/private").contains("/data"));
        check(!ModelUiText.safeError("content://provider/private").contains("content:"));
        check(ModelUiText.safeError("正在取消，等待文件读取/清理结束").contains("正在取消"));
        System.out.println("PASS Android production policy/lazy helpers "+checks+" checks (host, not Android runtime)");
    }
}
