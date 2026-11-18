package org.intraportmap.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.wifi.WifiManager;
import java.io.*;
import java.util.*;

public class TunnelService extends Service {
    private static final String STOP = "org.intraportmap.android.STOP";
    public static volatile boolean running;
    private LogStore log;
    private volatile java.lang.Process process;
    private volatile boolean stopping;
    private Thread worker;
    private PowerManager.WakeLock wake;
    private WifiManager.WifiLock wifi;
    private void append(String text) {
        try { log.append(text); }
        catch(IOException e) { android.util.Log.e("IntraPortMap","写入运行日志失败",e); }
    }
    public IBinder onBind(Intent i) { return null; }
    public void onCreate() {
        super.onCreate();
        log = new LogStore(new File(getFilesDir(),"logs"));
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("tunnel","端口映射",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this,1,new Intent(this,TunnelService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE);
        startForeground(1,new Notification.Builder(this,"tunnel").setContentTitle("IntraPortMap 正在运行")
            .setContentText("点击查看连接日志或停止映射").setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null,"停止映射",stop).build()).build());
    }
    public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null && STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if(worker!=null) return START_REDELIVER_INTENT;
        ArrayList<String> args = intent==null ? null : intent.getStringArrayListExtra("args");
        if(args==null || args.isEmpty()) { stopSelf(); return START_NOT_STICKY; }
        running = true;
        wake = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"intraportmap:tunnel");
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
            try {
                ArrayList<String> command = new ArrayList<>();
                command.add(getApplicationInfo().nativeLibraryDir+"/libintraportmap.so"); command.addAll(args);
                synchronized(this) {
                    if(stopping) return;
                    process = new ProcessBuilder(command).redirectErrorStream(true).start();
                }
                append("映射进程已启动");
                try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line; while((line=reader.readLine())!=null) append(line);
                }
                append("映射进程已退出，代码："+process.waitFor());
            } catch(Exception e) { if(!stopping) append("启动失败："+e.getMessage()); }
            finally {
                synchronized(this) { if(process!=null) { process.destroy(); process=null; } }
            }
            if(stopping) return;
            if(SystemClock.elapsedRealtime()-started>=60000) delay=5;
            append(delay+" 秒后自动恢复；可随时点击停止");
            try { Thread.sleep(delay*1000L); }
            catch(InterruptedException e) { Thread.currentThread().interrupt(); return; }
            delay=Math.min(60,delay*2);
          }
        },"intraportmap"); worker.start();
        // System redelivery retains the start intent; preferences never store keys.
        return START_REDELIVER_INTENT;
    }
    public void onDestroy() {
        synchronized(this) { stopping=true; if(process!=null) process.destroy(); }
        if(worker!=null) worker.interrupt();
        if(wifi!=null && wifi.isHeld()) wifi.release();
        if(wake!=null && wake.isHeld()) wake.release();
        running=false; stopForeground(true); append("映射服务已停止"); super.onDestroy();
    }
}
