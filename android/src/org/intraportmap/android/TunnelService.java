package org.intraportmap.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.wifi.WifiManager;
import java.io.*;
import java.util.*;

public class TunnelService extends Service {
    static final String STOP = "org.intraportmap.android.STOP";
    public static volatile boolean running;
    private LogStore log;
    private volatile java.lang.Process process;
    private volatile boolean stopping;
    private Thread worker;
    private ArrayList<String> requestedArgs;
    private long generation;
    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifi;
    private NetworkWatcher networkWatcher;
    protected String serviceName() { return "端口映射"; }
    protected String nativeBinary() { return "libintraportmap.so"; }
    protected int notificationId() { return 1; }
    protected int page() { return 0; }
    protected void setRunning(boolean value) { running=value; }
    protected void setState(String value) {}
    protected void onNativeLine(String line) {}
    // 0: no monitoring, 1: full network recovery, 2: DNS changes only.
    protected int networkWatchMode(ArrayList<String> args) { return 1; }
    protected ArrayList<String> prepareArguments(ArrayList<String> args) { return new ArrayList<>(args); }
    private void append(String text) {
        try { log.append("["+serviceName()+"] "+text); }
        catch(IOException e) { android.util.Log.e("IntraPortMap","写入运行日志失败",e); }
    }
    public IBinder onBind(Intent i) { return null; }
    private void configureNetworkWatcher(ArrayList<String> args) {
        if(networkWatcher!=null) { networkWatcher.close(); networkWatcher=null; }
        final int mode=networkWatchMode(args);
        if(mode==0) return;
        networkWatcher=new NetworkWatcher(this,available -> {
            synchronized(this) {
                if(stopping || worker==null) return;
                if(!available) {
                    setState("网络已断开，等待恢复");
                    append("默认网络已断开，等待新网络");
                    return;
                }
                append(mode==2 ? "自动 DNS 已变化，停止旧进程并重新读取 DNS" : "网络或 DNS 已变化，停止旧进程并在当前网络重新启动");
                setState(mode==2 ? "DNS 已变化，正在重新启动…" : "网络已切换，正在重新启动…");
                generation++;
                if(process!=null) process.destroyForcibly();
                notifyAll();
            }
        },mode==2);
        try { networkWatcher.start(); }
        catch(RuntimeException e) { append("网络变化监听失败："+e.getMessage()); }
    }
    public void onCreate() {
        super.onCreate();
        log = new LogStore(new File(getFilesDir(),"logs"));
        ServiceNotification.attach(this);
    }
    public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null && STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        ArrayList<String> args = intent==null ? null : intent.getStringArrayListExtra("args");
        if(args==null || args.isEmpty()) { stopSelf(); return START_NOT_STICKY; }
        configureNetworkWatcher(args);
        synchronized(this) {
            requestedArgs = new ArrayList<>(args);
            generation++;
            setState("正在启动…");
            if(worker!=null) {
                append("收到启动请求，先停止旧进程，再按当前配置重启");
                if(process!=null) process.destroyForcibly();
                notifyAll();
                return START_REDELIVER_INTENT;
            }
        }
        setRunning(true);
        wake = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"intraportmap:"+notificationId());
        wake.setReferenceCounted(false); wake.acquire();
        try {
            WifiManager manager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            if(manager!=null) {
                wifi = manager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"intraportmap:wifi");
                wifi.setReferenceCounted(false); wifi.acquire();
            }
        } catch(RuntimeException e) { append("系统未允许 Wi-Fi 锁，将继续运行"); }
        if((flags & START_FLAG_REDELIVERY)!=0) append("系统回收后重新恢复映射配置");
        worker = new Thread(() -> {
          int delay = 5;
          while(!stopping) {
            long started = SystemClock.elapsedRealtime();
            final long currentGeneration;
            final ArrayList<String> currentArgs;
            synchronized(this) {
                if(stopping) return;
                currentGeneration=generation;
                currentArgs=new ArrayList<>(requestedArgs);
                setState("正在启动…");
            }
            java.lang.Process child=null;
            try {
                ArrayList<String> command = new ArrayList<>();
                command.add(getApplicationInfo().nativeLibraryDir+"/"+nativeBinary()); command.addAll(prepareArguments(currentArgs));
                synchronized(this) {
                    if(stopping) return;
                    if(currentGeneration!=generation) continue;
                    child = new ProcessBuilder(command).redirectErrorStream(true).start();
                    process = child;
                }
                append("映射进程已启动");
                try(BufferedReader reader = new BufferedReader(new InputStreamReader(child.getInputStream()))) {
                    String line; while((line=reader.readLine())!=null) {
                        append(line);
                        synchronized(this) {
                            if(!stopping && currentGeneration==generation) onNativeLine(line);
                        }
                    }
                }
                append("映射进程已退出，代码："+child.waitFor());
            } catch(Exception e) {
                synchronized(this) {
                    if(!stopping && currentGeneration==generation) {
                        append("启动失败："+e.getMessage());
                        setState("启动失败："+e.getMessage());
                    }
                }
            }
            finally {
                // Reap the old child before allowing the next configuration to bind.
                if(child!=null) {
                    child.destroyForcibly();
                    boolean interrupted=false;
                    for(;;) try { child.waitFor(); break; }
                    catch(InterruptedException e) { interrupted=true; }
                    synchronized(this) { if(process==child) process=null; }
                    if(interrupted) Thread.currentThread().interrupt();
                }
            }
            synchronized(this) {
                if(stopping) return;
                if(currentGeneration!=generation) { delay=5; continue; }
            }
            if(SystemClock.elapsedRealtime()-started>=60000) delay=5;
            append(delay+" 秒后自动恢复；可随时点击停止");
            synchronized(this) {
                if(!stopping && currentGeneration==generation) setState("服务已退出，"+delay+" 秒后自动重试；详情见日志");
                long until=SystemClock.elapsedRealtime()+delay*1000L;
                while(!stopping && currentGeneration==generation && SystemClock.elapsedRealtime()<until) {
                    try { wait(Math.max(1,until-SystemClock.elapsedRealtime())); }
                    catch(InterruptedException e) { if(stopping) return; }
                }
                delay=currentGeneration!=generation ? 5 : Math.min(60,delay*2);
            }
          }
        },"intraportmap"); worker.start();
        // System redelivery retains the latest start intent.
        return START_REDELIVER_INTENT;
    }
    public void onDestroy() {
        if(networkWatcher!=null) networkWatcher.close();
        synchronized(this) { stopping=true; if(process!=null) process.destroyForcibly(); notifyAll(); }
        if(worker!=null) worker.interrupt();
        if(wifi!=null && wifi.isHeld()) wifi.release();
        if(wake!=null && wake.isHeld()) wake.release();
        setRunning(false); setState("已停止"); ServiceNotification.detach(this); append("映射服务已停止"); super.onDestroy();
    }
}
