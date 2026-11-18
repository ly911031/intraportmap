package org.intraportmap.android;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import java.net.InetAddress;
import java.util.ArrayList;

/** Separate Android service instance: stopping either module leaves the other running. */
public final class ShadowsocksService extends TunnelService {
    public static volatile boolean running;
    public static volatile String state="已停止";
    protected String serviceName() { return "Shadowsocks 服务端"; }
    protected String nativeBinary() { return "libss_server.so"; }
    protected int notificationId() { return 2; }
    protected int page() { return 1; }
    protected void setRunning(boolean value) { running=value; }
    protected int networkWatchMode(ArrayList<String> args) { return args.contains("-d") ? 0 : 2; }
    protected void setState(String value) {
        if(value.startsWith("服务已退出") && state.startsWith("运行失败：")) state=state+"；"+value;
        else state=value;
    }
    protected void onNativeLine(String line) {
        if(line.equals("IPM_SS_READY")) setState("启动成功 · Shadowsocks 服务端监听中");
        else if(line.startsWith("ERROR ")) setState("运行失败："+line.substring(6));
    }
    protected ArrayList<String> prepareArguments(ArrayList<String> args) {
        ArrayList<String> prepared=new ArrayList<>(args);
        int explicit=prepared.indexOf("-d");
        if(explicit>=0 && explicit+1<prepared.size())
            prepared.set(explicit+1,DnsServers.normalize(prepared.get(explicit+1)));
        if(!prepared.contains("-d")) {
            ConnectivityManager manager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            LinkProperties properties=manager.getLinkProperties(manager.getActiveNetwork());
            ArrayList<String> dns=new ArrayList<>();
            if(properties!=null) for(InetAddress address:properties.getDnsServers()) {
                String ip=address.getHostAddress();
                if(ip.indexOf('%')>=0) continue;
                dns.add(ip);
            }
            if(dns.isEmpty()) throw new IllegalArgumentException("当前网络没有 DNS，请在 Shadowsocks 页面填写 DNS 服务器");
            prepared.add("-d"); prepared.add(DnsServers.normalize(String.join(",",dns)));
        }
        return prepared;
    }
}
