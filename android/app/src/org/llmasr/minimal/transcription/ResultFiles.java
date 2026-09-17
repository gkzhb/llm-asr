package org.llmasr.minimal.transcription;

import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** App-private, non-recursive cleanup. Never deletes models or follows symlinks. */
public final class ResultFiles {
    private static final String UUID="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern TEMP=Pattern.compile("(?:input-"+UUID+"\\.wav|last-result-"+UUID+"\\.part)");
    public static String startupStatus(int count) {
        return "准备就绪，已清理"+count+"个遗留临时文件；请校验已部署模型或导入模型。";
    }
    public static int cleanTemporary(File directory) throws IOException { return clean(directory,false); }
    public static int clearResults(File directory) throws IOException { return clean(directory,true); }
    private static int clean(File directory,boolean results) throws IOException {
        File[] files=directory.listFiles(); if(files==null)throw new IOException("无法读取应用私有目录");
        int count=0;
        for(File file:files) {
            String name=file.getName();
            if(!TEMP.matcher(name).matches() && !(results && (name.equals("last-result.json") || name.equals("edited-result.txt"))))continue;
            if(Files.isSymbolicLink(file.toPath()) || !file.isFile())throw new IOException("拒绝清理非普通结果文件："+name);
            if(!file.delete())throw new IOException("无法清理："+name);
            count++;
        }
        return count;
    }
    public static void writeText(OutputStream out,String text) throws IOException {
        if(text.length()>100000)throw new IOException("结果文本超过100000字符上限");
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
