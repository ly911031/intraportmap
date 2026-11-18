package org.intraportmap.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

public final class LogStoreTest {
    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    private static String export(LogStore store) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); store.exportTo(out);
        return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
    public static void main(String[] args) throws Exception {
        File directory=Files.createTempDirectory(new File(args[0]).toPath(),"logs-test-").toFile();
        LogStore first=new LogStore(directory,32768,3);
        first.append("中文日志 UTF-8");
        first.append("1118083500 DEBUG tunnel from event"); first.append(" ");
        LogStore reopened=new LogStore(directory,32768,3);
        require(export(reopened).contains("中文日志 UTF-8"),"log must survive store recreation");
        require(!export(reopened).contains("DEBUG"),"legacy DEBUG must be filtered");
        for(int i=0;i<1800;i++) first.append(String.format("INFO entry-%04d 01234567890123456789012345678901234567890123456789",i));
        File[] logs=directory.listFiles((d,name)->name.startsWith("intraportmap.log"));
        require(logs!=null && logs.length==4,"rotation must retain exactly four log files");
        for(File file:logs) require(file.length()<=32768,"file exceeds rotation limit");
        String all=export(reopened);
        int previous=-1;
        for(String line:all.split("\n")) {
            int marker=line.indexOf("entry-"); if(marker<0) continue;
            int index=Integer.parseInt(line.substring(marker+6,marker+10));
            require(index>previous,"export must be chronological"); previous=index;
        }
        require(previous==1799,"export must contain most recent entry");
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread writer=new Thread(() -> {
            try { for(int i=0;i<300;i++) first.append("INFO concurrent-"+i); }
            catch(Throwable e) { failure.set(e); }
        });
        writer.start();
        for(int i=0;i<12;i++) export(reopened);
        writer.join(); if(failure.get()!=null) throw new AssertionError(failure.get());
        require(export(reopened).contains("concurrent-299"),"concurrent export lost final entry");
        try {
            reopened.exportTo(new OutputStream() { public void write(int b) throws IOException { throw new IOException("test failure"); } });
            throw new AssertionError("export must report destination failure");
        } catch(IOException expected) {}
        require(directory.listFiles((d,name)->name.startsWith("export-")).length==0,"snapshot must be removed even when export fails");
        first.append("INFO after failed export");
        require(export(reopened).contains("after failed export"),"logging must continue after failed export");
        System.out.println("PASS: UTF-8 persistence, DEBUG filtering, bounded rotation, ordered export, concurrent writers/export, export failure cleanup");
    }
}
