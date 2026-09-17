import org.llmasr.minimal.modelmanagement.ModelManagementController;
import org.llmasr.minimal.modelmanagement.ModelManagementState;
import org.llmasr.minimal.model.ModelManifest;
import org.llmasr.minimal.modelmanagement.ModelOperationControl;
import org.llmasr.minimal.model.ModelReadiness;
import org.llmasr.minimal.model.ModelRepository;
import org.llmasr.minimal.model.ModelSource;
import org.llmasr.minimal.task.TaskCoordinator;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real host filesystem aliases + production controller; no Android device claim. */
public final class ModelPrivateRootTest {
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("model-private-root-");
        try {
            Path real = Files.createDirectories(base.resolve("user/0/app/files"));
            Path alias = base.resolve("data");
            Files.createSymbolicLink(alias, base.resolve("user/0"));
            File files = alias.resolve("app/files").toFile();
            ModelManifest manifest = new ModelManifest(Collections.singletonList(ModelRepositoryTest.entry("a.bin", new byte[]{1,2,3})));
            ModelRepository repo = ModelRepository.forAppFiles(manifest, files, () -> Long.MAX_VALUE);
            check(!Files.exists(real.resolve("model")), "bootstrap does not mkdir");
            check(repo.inspect(ModelRepository.NEVER_CANCEL).installedBytes == 0, "alias inspect succeeds");
            check(!Files.exists(real.resolve("model")), "inspect does not mkdir");
            ModelRepositoryTest.FakeSource source = new ModelRepositoryTest.FakeSource();
            source.store.put("a.bin", new byte[]{1,2,3});
            ModelReadiness ready = new ModelReadiness();
            ModelManagementState state = new ModelManagementState();
            TaskCoordinator owner = new TaskCoordinator(Runnable::run);
            ModelManagementController controller = new ModelManagementController(owner, repo, ready, new ModelOperationControl(), state);
            check(controller.startImport(source), "import admitted");
            check(state.current().phase == ModelManagementState.Phase.SUCCEEDED, "alias import succeeds: " + state.current().errorCode);
            check(state.current().cleanupOutcome == ModelManagementState.Outcome.SUCCEEDED && ready.snapshot().ready(), "cleanup and READY");
            check(repo.modelDir().equals(real.resolve("model").toFile().getCanonicalFile()), "native path uses pinned parent");
            ModelSource noCopy = new ModelSource() {
                public Map<String,UriRef> enumerate(Collection<String> names, int max) { return Collections.emptyMap(); }
                public InputStream open(UriRef ref) { throw new AssertionError("must reuse"); }
            };
            check(controller.startImport(noCopy), "retry admitted");
            check(state.current().phase == ModelManagementState.Phase.SUCCEEDED && state.current().reusedBytes == 3, "SHA reuse succeeds");
            controller.refreshInspect();
            check(state.current().inspectError == null, "overview refresh succeeds");
            check(controller.startVerify() && ready.snapshot().ready(), "verify alias");
            check(controller.startDelete(ready.epoch(), true), "delete admitted");
            check(!Files.exists(real.resolve("model/a.bin")), "managed file deleted");
            // Never canonicalize the model leaf or managed final/part to make them appear safe.
            Path outside = Files.createDirectory(base.resolve("outside"));
            Files.write(outside.resolve("a.bin"), new byte[]{9});
            for (String leaf : new String[]{"model", "a.bin", "a.bin.part"}) {
                Path model = real.resolve("model");
                if (!leaf.equals("model")) Files.createDirectories(model);
                Path link = leaf.equals("model") ? model : model.resolve(leaf);
                Files.createSymbolicLink(link, leaf.equals("model") ? outside : outside.resolve("a.bin"));
                ModelRepository unsafe = ModelRepository.forAppFiles(manifest, files, () -> Long.MAX_VALUE);
                expectRejected(() -> unsafe.inspect(ModelRepository.NEVER_CANCEL));
                expectRejected(() -> unsafe.importFrom(source, null));
                expectRejected(unsafe::verifyAll);
                expectRejected(() -> unsafe.deleteAll());
                check(Arrays.equals(Files.readAllBytes(outside.resolve("a.bin")), new byte[]{9}), "outside unchanged");
                Files.delete(link);
                if (!leaf.equals("model")) Files.delete(model);
            }
            Files.createSymbolicLink(real.resolve("model"), base.resolve("absent"));
            expectRejected(() -> ModelRepository.forAppFiles(manifest, files, () -> 0).inspect(ModelRepository.NEVER_CANCEL));
            Files.delete(real.resolve("model"));
            Files.write(real.resolve("model"), new byte[]{7});
            expectRejected(() -> ModelRepository.forAppFiles(manifest, files, () -> 0).importFrom(source, null));
            Files.delete(real.resolve("model"));
            // Existing general-purpose repository contract still rejects ancestor aliases.
            expectRejected(() -> new ModelRepository(manifest, new File(files,"model"), () -> 0));
            File noIo = new File(files.getPath()) {
                @Override public File getCanonicalFile() { throw new AssertionError("bootstrap IO"); }
            };
            ModelRepository.forAppFiles(manifest, noIo, () -> 0);
            System.out.println("PASS private-root alias import/inspect/verify/reuse/delete and symlink safety (host filesystem only)");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(base)) {
                for (Path p : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(p);
            }
        }
    }
    interface Action { void run() throws Exception; }
    static void expectRejected(Action action) throws Exception {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("unsafe root/file accepted");
    }
}
