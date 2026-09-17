import org.llmasr.minimal.transcription.ResultState;
import org.llmasr.minimal.transcription.TextExportController;

import java.io.*;
import java.util.*;

/** Regression: clear must revoke a selected ticket before provider side effects. */
public final class TextExportSafetyTest {
    public static void main(String[] args) {
        ResultState result = new ResultState(); result.setText("private-before-clear");
        List<Runnable> queued = new ArrayList<>();
        int[] opens = {0};
        TextExportController<String> exports = new TextExportController<>(target -> {
            opens[0]++; return new ByteArrayOutputStream();
        }, queued::add);
        TextExportController.Ticket ticket = exports.begin(exports.newPage(), result);
        result.clear();
        exports.admit(ticket, "chosen-target");
        for (Runnable work : queued) work.run();
        if (opens[0] != 0) throw new AssertionError("clear before write admission must cause ZERO provider opens");
        System.out.println("PASS clear-before-admission production regression");
    }
}
