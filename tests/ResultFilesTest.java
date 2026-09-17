import org.llmasr.minimal.transcription.ResultFiles;
import org.llmasr.minimal.transcription.ResultState;
import org.llmasr.minimal.transcription.TextExportController;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class ResultFilesTest {
    static int checks;
    static void check(boolean c) { if(!c)throw new AssertionError("check "+checks); checks++; }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("asr-results-");
        String id="12345678-1234-1234-1234-123456789abc";
        try {
            Path temp=dir.resolve("input-"+id+".wav"), result=dir.resolve("last-result.json"), model=dir.resolve("llm.mnn.weight"), personal=dir.resolve("input-personal.wav");
            for(Path p:new Path[]{temp,result,model,personal})Files.write(p,new byte[]{1});
            check(ResultFiles.cleanTemporary(dir.toFile())==1); check(!Files.exists(temp)); check(Files.exists(result));
            check(Files.exists(model) && Files.exists(personal));
            check(ResultFiles.clearResults(dir.toFile())==1); check(!Files.exists(result));
            check(Files.exists(model) && Files.exists(personal)); check(ResultFiles.clearResults(dir.toFile())==0);
            Files.createSymbolicLink(temp,personal); boolean rejected=false;
            try { ResultFiles.cleanTemporary(dir.toFile()); } catch(IOException e) { rejected=true; }
            check(rejected); check(Files.exists(personal)); Files.delete(temp);
            Files.createDirectory(temp); rejected=false;
            try { ResultFiles.cleanTemporary(dir.toFile()); } catch(IOException e) { rejected=true; }
            check(rejected); Files.delete(temp);
            ByteArrayOutputStream out=new ByteArrayOutputStream();ResultFiles.writeText(out,"中文😀\nsecond line");
            check(new String(out.toByteArray(),StandardCharsets.UTF_8).equals("中文😀\nsecond line"));
            rejected=false; try { ResultFiles.writeText(out,new String(new char[100001])); } catch(IOException e) { rejected=true; } check(rejected);
            check(ResultFiles.cleanTemporary(dir.toFile())==0);
            Path nested=Files.createDirectory(dir.resolve("model"));
            Files.write(nested.resolve("llm.mnn.weight.part"),new byte[]{2});
            Files.write(dir.resolve("last-result-"+id+".part"),new byte[]{1}); Files.write(temp,new byte[]{1});
            Files.write(result,new byte[]{1}); Files.write(dir.resolve("edited-result.txt"),new byte[]{1});
            int count=ResultFiles.cleanTemporary(dir.toFile()); check(count==2);
            check(ResultFiles.startupStatus(count).contains("2个")); check(ResultFiles.startupStatus(0).contains("准备就绪"));
            check(!ResultFiles.startupStatus(0).contains("执行中"));
            check(Files.exists(result) && Files.exists(dir.resolve("edited-result.txt")));
            check(ResultFiles.clearResults(dir.toFile())==2); check(Files.exists(nested.resolve("llm.mnn.weight.part")));
            Files.delete(nested.resolve("llm.mnn.weight.part")); Files.delete(nested);
            boolean writeFailed=false;
            try { ResultFiles.writeText(new OutputStream(){ public void write(int b)throws IOException{throw new IOException("injected");}},"secret"); }
            catch(IOException expected) { writeFailed=true; } check(writeFailed);
            boolean closeFailed=false;
            try(OutputStream failing=new ByteArrayOutputStream(){ public void close()throws IOException{throw new IOException("close");}}) {
                ResultFiles.writeText(failing,"text");
            } catch(IOException expected) { closeFailed=true; } check(closeFailed);
            // ResultState + TextExportController: real clear ordering, ABA-safe
            // edit, immutable text+revision snapshot, duplicate/replay/foreign
            // rejection. The R5 design replaces the old ExportSession facade.
            ResultState rs = new ResultState();
            rs.setText("before clear");
            TextExportController<String> session = new TextExportController<>(target -> new ByteArrayOutputStream(), new java.util.concurrent.Executor(){
                public void execute(Runnable r) { r.run(); }
            });
            TextExportController.Ticket first = session.begin(session.newPage(), rs);
            check(first != null && first.snapshot.text.equals("before clear"));
            // Busy no queue: second begin() while slot is held returns null.
            check(session.begin(session.newPage(), rs) == null);
            // Clear before admit: invalidate the snapshot's epoch.
            rs.clear();
            // Actual clear revokes the ticket even if the page has not received a callback.
            check(!session.admit(first, "uri-old"));
            check(session.state().phase == TextExportController.Phase.EXPIRED);
            check(session.begin(session.newPage(), rs) == null); // no empty export
            check(rs.applyEdit(first.snapshot, "stale") == -1L);
            ResultState.Snapshot live = rs.begin();
            check(rs.applyEdit(live, "after clear") > 0);
            TextExportController.Ticket second = session.begin(session.newPage(), rs);
            check(second != null && second.requestCode != first.requestCode);
            check(session.admit(second, "uri-new"));
            check(session.state().phase == TextExportController.Phase.SUCCEEDED);
            check(TextExportController.isRequest(5000) && !TextExportController.isRequest(13));

            System.out.println("PASS "+checks+" result cleanup/export checks (host only)");
        } finally { try(java.util.stream.Stream<Path> files=Files.list(dir)) { for(Path p:(Iterable<Path>)files::iterator)Files.delete(p); } Files.delete(dir); }
    }
}
