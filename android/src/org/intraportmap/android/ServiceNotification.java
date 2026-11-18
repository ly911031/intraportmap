package org.intraportmap.android;

import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import java.util.LinkedHashSet;

/** Both foreground services share one notification, retaining independent lifecycles. */
final class ServiceNotification {
    private static final int ID=100;
    private static final String CHANNEL="intraportmap-services";
    private static final LinkedHashSet<TunnelService> services=new LinkedHashSet<>();
    static void attach(TunnelService service) {
        services.add(service);
        NotificationManager manager=(NotificationManager)service.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if(android.os.Build.VERSION.SDK_INT>=26)
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,"后台服务",NotificationManager.IMPORTANCE_LOW));
        service.startForeground(ID,build(service));
    }
    private static Notification build(TunnelService context) {
        Intent activity=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(context,100,activity,Compat.immutableFlag()|PendingIntent.FLAG_UPDATE_CURRENT);
        StringBuilder summary=new StringBuilder();
        Notification.Builder builder=(android.os.Build.VERSION.SDK_INT>=26 ? new Notification.Builder(context,CHANNEL) : new Notification.Builder(context)).setContentTitle("IntraPortMap · 后台服务")
            .setSmallIcon(context.getResources().getIdentifier("ic_status","drawable",context.getPackageName()))
            .setColor(Color.rgb(23,107,206)).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true);
        for(TunnelService service:services) {
            if(summary.length()>0) summary.append(" · ");
            summary.append(service.serviceName());
            PendingIntent stop=PendingIntent.getService(context,service.notificationId(),
                new Intent(context,service.getClass()).setAction(TunnelService.STOP),Compat.immutableFlag());
            builder.addAction(new Notification.Action.Builder(0,"停止"+service.serviceName(),stop).build());
        }
        return builder.setContentText(summary+" · 点击打开设置").build();
    }
    static void detach(TunnelService service) {
        services.remove(service);
        service.stopForeground(true);
        if(!services.isEmpty()) {
            TunnelService remaining=services.iterator().next();
            remaining.startForeground(ID,build(remaining));
        }
    }
}
