package org.intraportmap.android;

import android.content.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public final class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent) {
        if(intent==null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        LogStore log=new LogStore(new File(context.getFilesDir(),"logs"));
        write(log,"[开机恢复] 收到开机完成广播，检查自动启动配置");
        restore(context,TunnelService.class,log);
        restore(context,ShadowsocksService.class,log);
    }
    private void restore(Context context,Class<?> service,LogStore log) {
        if(!BootSettings.enabled(context,service)) return;
        ArrayList<String> args=BootSettings.arguments(context,service);
        if(args.isEmpty()) { write(log,"[开机恢复] "+service.getSimpleName()+" 没有已保存的启动配置"); return; }
        try {
            Compat.start(context,new Intent(context,service).putStringArrayListExtra("args",args));
            write(log,"[开机恢复] 已请求启动 "+service.getSimpleName());
        } catch(RuntimeException e) {
            write(log,"[开机恢复] "+service.getSimpleName()+" 启动失败："+e.getClass().getSimpleName()+"，请检查系统后台运行权限");
        }
    }
    private void write(LogStore log,String message) {
        try { log.append(message); } catch(IOException e) { android.util.Log.e("IntraPortMap","开机日志写入失败",e); }
    }
}
