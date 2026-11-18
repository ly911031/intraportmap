package org.intraportmap.android;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

/** Runs only in the separate test APK against local echo sockets. */
public final class CombinedSmokeTest extends Instrumentation {
    private static final int SS=32188,ECHO=32181;
    private static final String PASSWORD="local-instrumentation-only";
    private static final String[] METHODS={"chacha20-ietf-poly1305","aes-128-gcm","aes-192-gcm","aes-256-gcm","xchacha20-ietf-poly1305"};
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static Object field(Activity a,String name) throws Exception {
        Field f=a.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(a);
    }
    private static void call(Activity a,String name,Class<?>[] types,Object... args) throws Exception {
        Method method=a.getClass().getDeclaredMethod(name,types); method.setAccessible(true); method.invoke(a,args);
    }
    private static boolean running(String name) throws Exception { return Class.forName("org.intraportmap.android."+name).getField("running").getBoolean(null); }
    private static void requireSingleNotification(Context context) throws Exception {
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        for(int i=0;i<30;i++) {
            android.service.notification.StatusBarNotification[] notifications=manager.getActiveNotifications();
            if(notifications.length==1 && notifications[0].getId()==100) return;
            Thread.sleep(100);
        }
        throw new AssertionError("expected exactly one shared foreground notification");
    }
    private static void waitPort(int port,boolean expected) throws Exception {
        for(int i=0;i<80;i++) {
            boolean open;
            try(Socket socket=new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1",port),100); open=true; }
            catch(IOException e) { open=false; }
            if(open==expected) return; Thread.sleep(100);
        }
        throw new AssertionError("port "+port+" expected open="+expected);
    }
    public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    public void onStart() {
        Context context=getTargetContext(); Bundle result=new Bundle(); Activity activity=null;
        Intent ipm=new Intent().setClassName(context,"org.intraportmap.android.TunnelService");
        Intent ss=new Intent().setClassName(context,"org.intraportmap.android.ShadowsocksService");
        ServerSocket tcp=null; DatagramSocket udp=null;
        try {
            ShadowsocksProbe.selfTest();
            tcp=new ServerSocket(ECHO,16,InetAddress.getByName("127.0.0.1"));
            udp=new DatagramSocket(ECHO,InetAddress.getByName("127.0.0.1"));
            final ServerSocket echoTcp=tcp; final DatagramSocket echoUdp=udp;
            new Thread(() -> {
                try { while(!echoTcp.isClosed()) {
                    final Socket socket=echoTcp.accept();
                    new Thread(() -> {
                        try(Socket c=socket) { byte[] b=new byte[16384]; int n;
                            while((n=c.getInputStream().read(b))!=-1) c.getOutputStream().write(b,0,n);
                        } catch(IOException ignored) {}
                    }).start();
                }} catch(IOException ignored) {}
            }).start();
            new Thread(() -> {
                try { while(!echoUdp.isClosed()) {
                    DatagramPacket p=new DatagramPacket(new byte[65535],65535); echoUdp.receive(p); echoUdp.send(p);
                }} catch(IOException ignored) {}
            }).start();
            activity=startActivitySync(new Intent().setClassName(context,"org.intraportmap.android.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Activity current=activity; final Throwable[] uiError={null};
            context.startForegroundService(ipm.putStringArrayListExtra("args",new ArrayList<>(Arrays.asList("-s","127.0.0.1:32120"))));
            waitPort(32120,true);
            for(int i=0;i<METHODS.length;i++) {
                final int selected=i;
                runOnMainSync(() -> {
                    try {
                        call(current,"showPage",new Class<?>[]{int.class},1);
                        require(((ViewFlipper)field(current,"pages")).getDisplayedChild()==1,"second page not visible");
                        ((EditText)field(current,"ssHost")).setText("127.0.0.1");
                        ((EditText)field(current,"ssPort")).setText(""+SS);
                        ((EditText)field(current,"ssPassword")).setText(PASSWORD);
                        ((EditText)field(current,"ssDns")).setText("127.0.0.1");
                        ((EditText)field(current,"ssMtu")).setText("9000");
                        ((Spinner)field(current,"ssMethod")).setSelection(selected);
                        ((Spinner)field(current,"ssProtocol")).setSelection(1);
                        call(current,"startShadowsocks",new Class<?>[]{});
                        require(PASSWORD.equals(current.getPreferences(0).getString("ss_password","")),"password not saved");
                    } catch(Throwable e) { uiError[0]=e; }
                });
                if(uiError[0]!=null) throw new AssertionError(uiError[0]);
                waitPort(SS,true); require(running("TunnelService") && running("ShadowsocksService"),"services not independent");
                requireSingleNotification(context);
                for(int attempt=0;attempt<30 && !Class.forName("org.intraportmap.android.ShadowsocksService").getField("state").get(null).toString().contains("启动成功");attempt++) Thread.sleep(100);
                require(Class.forName("org.intraportmap.android.ShadowsocksService").getField("state").get(null).toString().contains("启动成功"),"native readiness not displayed");
                ShadowsocksProbe.roundtrip(METHODS[i],PASSWORD,SS,ECHO);
                Bundle progress=new Bundle(); progress.putString("stream","PASS: "+METHODS[i]+" TCP 8192 bytes; UDP 32/1400/8192 bytes\n"); sendStatus(0,progress);
                context.stopService(ss); waitPort(SS,false);
                require(running("TunnelService"),"stopping Shadowsocks stopped port mapping"); waitPort(32120,true);
                requireSingleNotification(context);
            }
            // Repeated UI start replaces the running child, including its password/port.
            runOnMainSync(() -> {
                try {
                    require(!(((EditText)field(current,"ssPassword")).getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod),"password still masked");
                    require(!(((EditText)field(current,"key")).getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod),"mapping key still masked");
                    ((Spinner)field(current,"ssMethod")).setSelection(1);
                    call(current,"startShadowsocks",new Class<?>[]{});
                } catch(Throwable e) { uiError[0]=e; }
            });
            if(uiError[0]!=null) throw new AssertionError(uiError[0]);
            waitPort(SS,true);
            runOnMainSync(() -> {
                try {
                    ((EditText)field(current,"ssPort")).setText(""+(SS+1));
                    ((EditText)field(current,"ssPassword")).setText(PASSWORD+"-new");
                    call(current,"startShadowsocks",new Class<?>[]{});
                } catch(Throwable e) { uiError[0]=e; }
            });
            if(uiError[0]!=null) throw new AssertionError(uiError[0]);
            waitPort(SS,false); waitPort(SS+1,true);
            ShadowsocksProbe.roundtrip("aes-128-gcm",PASSWORD+"-new",SS+1,ECHO);
            waitPort(32120,true);
            // Repeated mapping start must also release its old listener.
            context.startForegroundService(ipm.putStringArrayListExtra("args",new ArrayList<>(Arrays.asList("-s","127.0.0.1:32121"))));
            waitPort(32120,false); waitPort(32121,true);
            ShadowsocksProbe.roundtrip("aes-128-gcm",PASSWORD+"-new",SS+1,ECHO);
            context.stopService(ss); waitPort(SS+1,false);
            // Check the reverse direction of service isolation as well.
            context.startForegroundService(ss.putStringArrayListExtra("args",new ArrayList<>(Arrays.asList("-s","127.0.0.1","-p",""+SS,"-k",PASSWORD,"-m","aes-128-gcm","-u","--mtu","9000","-d","127.0.0.1"))));
            waitPort(SS,true); context.stopService(ipm); waitPort(32121,false);
            require(running("ShadowsocksService"),"stopping port mapping stopped Shadowsocks");
            ShadowsocksProbe.roundtrip("aes-128-gcm",PASSWORD,SS,ECHO);
            result.putString("stream","PASS: combined pages, all five AEAD ciphers, encrypted TCP/UDP, independent service lifecycle, saved password, native readiness\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e) { result.putString("stream","FAIL: "+e+"\n"); finish(Activity.RESULT_CANCELED,result); }
        finally {
            context.stopService(ipm); context.stopService(ss);
            if(tcp!=null) try { tcp.close(); } catch(IOException ignored) {}
            if(udp!=null) udp.close();
            if(activity!=null) { final Activity current=activity; runOnMainSync(() -> current.finish()); }
        }
    }
}
