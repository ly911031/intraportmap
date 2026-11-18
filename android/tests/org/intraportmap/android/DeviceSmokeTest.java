package org.intraportmap.android;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

/** Explicit test APK only. Uses a local listener, never contacts a remote server. */
public final class DeviceSmokeTest extends Instrumentation {
    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    public void onStart() {
        Bundle result=new Bundle(); Activity activity=null;
        Context context=getTargetContext();
        Intent service=new Intent().setClassName(context,"org.intraportmap.android.TunnelService");
        try {
            Intent launch=new Intent().setClassName(context,"org.intraportmap.android.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(launch); waitForIdleSync();
            final Activity current=activity;
            final Throwable[] failure={null};
            runOnMainSync(() -> {
                try {
                    Method apply=current.getClass().getDeclaredMethod("applyRecentVisibility",boolean.class);
                    apply.setAccessible(true);
                    ActivityManager manager=(ActivityManager)current.getSystemService(Context.ACTIVITY_SERVICE);
                    for(boolean hidden:new boolean[]{true,false}) {
                        require((Boolean)apply.invoke(current,hidden),"task visibility update failed");
                        for(ActivityManager.AppTask task:manager.getAppTasks()) {
                            boolean excluded=(task.getTaskInfo().baseIntent.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)!=0;
                            require(excluded==hidden,"recent-task exclusion flag did not change");
                        }
                    }
                } catch(Throwable e) { failure[0]=e; }
            });
            if(failure[0]!=null) throw new AssertionError(failure[0]);
            ArrayList<String> args=new ArrayList<>(Arrays.asList("-s","127.0.0.1:32120"));
            context.startForegroundService(service.putStringArrayListExtra("args",args));
            File log=new File(context.getFilesDir(),"logs/intraportmap.log");
            String text="";
            for(int i=0;i<40;i++) {
                Thread.sleep(250);
                if(log.isFile()) text=new String(Files.readAllBytes(log.toPath()),StandardCharsets.UTF_8);
                if(text.contains("run as server")) break;
            }
            require(text.contains("映射进程已启动") && text.contains("run as server"),"service/native logs were not written");
            require(!text.contains(" DEBUG "),"DEBUG leaked into file");
            require(!text.contains("E:/workspace-github"),"build-machine paths leaked into file");
            context.stopService(service);
            for(int i=0;i<40;i++) {
                Thread.sleep(100);
                text=new String(Files.readAllBytes(log.toPath()),StandardCharsets.UTF_8);
                if(text.contains("映射服务已停止")) break;
            }
            require(text.contains("映射服务已停止"),"stop event did not reach the log file");
            result.putString("stream","PASS: recents hide/restore flags; foreground native service file logs; stop persistence; no DEBUG or build paths\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e) {
            result.putString("stream","FAIL: "+e+"\n"); finish(Activity.RESULT_CANCELED,result);
        } finally {
            context.stopService(service);
            if(activity!=null) { final Activity current=activity; runOnMainSync(() -> current.finish()); }
        }
    }
}
