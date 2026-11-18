package org.intraportmap.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bounded, append-only-on-write logs. No long-lived descriptor or in-memory log. */
final class LogStore {
    private static final Object FILE_LOCK=new Object();
    private final File directory;
    private final long limit;
    private final int backups;
    LogStore(File directory) { this(directory, 2 * 1024 * 1024, 3); }
    LogStore(File directory, long limit, int backups) {
        if(limit<32768 || backups<1) throw new IllegalArgumentException();
        this.directory=directory; this.limit=limit; this.backups=backups;
    }
    private File file(int index) {
        return new File(directory, "intraportmap.log"+(index==0 ? "" : "."+index));
    }
    private void ensureDirectory() throws IOException {
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建日志目录");
    }
    void append(String text) throws IOException {
      synchronized(FILE_LOCK) {
        // Native DEBUG is compiled out; also filter accidental legacy child output.
        if(text.trim().isEmpty() || text.matches("^\\d+ DEBUG(?:\\s|$).*")) return;
        ensureDirectory();
        if(text.length()>4096) text=text.substring(0,4096)+" [日志过长，已截断]";
        String time=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.ROOT).format(new Date());
        byte[] bytes=(time+" "+text+"\n").getBytes(StandardCharsets.UTF_8);
        if(file(0).length()+bytes.length>limit) {
            if(file(backups).exists() && !file(backups).delete()) throw new IOException("无法轮转旧日志");
            for(int i=backups-1;i>=0;i--) {
                if(file(i).exists() && !file(i).renameTo(file(i+1))) throw new IOException("无法轮转日志");
            }
        }
        // Close after each entry: a killed process cannot strand buffered Java logs.
        try(FileOutputStream out=new FileOutputStream(file(0),true)) { out.write(bytes); }
      }
    }
    void exportTo(OutputStream destination) throws IOException {
        File snapshot;
        synchronized(FILE_LOCK) {
            ensureDirectory();
            snapshot=File.createTempFile("export-", ".tmp",directory);
            try(FileOutputStream out=new FileOutputStream(snapshot)) {
                for(int i=backups;i>=0;i--) if(file(i).isFile()) copy(file(i),out);
            } catch(IOException e) { snapshot.delete(); throw e; }
        }
        try { copy(snapshot,destination); }
        finally { snapshot.delete(); }
    }
    private static void copy(File source,OutputStream out) throws IOException {
        try(InputStream in=new FileInputStream(source)) {
            byte[] buffer=new byte[8192]; int count;
            while((count=in.read(buffer))!=-1) out.write(buffer,0,count);
        }
    }
}
