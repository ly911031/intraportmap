package org.intraportmap.android;
import android.content.Context;
import android.net.*;
import android.os.Handler;
import java.util.*;

/** Deterministic callback simulation; not an on-device radio handover test. */
public final class NetworkWatcherTest {
    public static void main(String[] args) {
        if(args.length>0) android.os.Build.VERSION.SDK_INT=Integer.parseInt(args[0]);
        Context context=new Context(); ConnectivityManager cm=context.connectivity;
        cm.network=new Network("wifi"); cm.properties=new LinkProperties("wlan0","192.168.1.2","192.168.1.1");
        ArrayList<Boolean> events=new ArrayList<>();
        NetworkWatcher watcher=new NetworkWatcher(context,events::add); watcher.start();
        cm.callback.onAvailable(cm.network); cm.callback.onLinkPropertiesChanged(cm.network,cm.properties); Handler.drain();
        require(events.isEmpty(),"initial/same network must not restart");
        Network old=cm.network; cm.network=new Network("mobile");
        cm.properties=new LinkProperties("rmnet0","10.0.0.2","2001:4860:4860::8888");
        cm.callback.onLost(old); cm.callback.onAvailable(cm.network); cm.callback.onLinkPropertiesChanged(cm.network,cm.properties);
        Handler.drain(); require(events.equals(Arrays.asList(true)),"handover must coalesce into one restart");
        cm.properties=new LinkProperties("rmnet0","10.0.0.2","1.1.1.1");
        cm.callback.onLinkPropertiesChanged(cm.network,cm.properties); Handler.drain();
        require(events.size()==2 && events.get(1),"DNS change must refresh resolver");
        cm.properties=new LinkProperties("rmnet0","10.0.0.3","1.1.1.1");
        cm.callback.onLinkPropertiesChanged(cm.network,cm.properties); Handler.drain();
        require(events.size()==3,"address change must restart");
        old=cm.network; cm.network=null; cm.properties=null; cm.callback.onLost(old); Handler.drain();
        require(events.size()==4 && !events.get(3),"offline must be reported");
        cm.network=new Network("wifi2"); cm.properties=new LinkProperties("wlan0","192.168.2.2","192.168.2.1");
        cm.callback.onAvailable(cm.network); Handler.drain();
        require(events.size()==5 && events.get(4),"network return must restart");
        cm.callback.onLost(cm.network); watcher.close(); Handler.drain();
        require(cm.callback==null && events.size()==5,"stop must cancel pending recovery");
        events.clear();
        cm.properties=new LinkProperties("wlan0","192.168.2.2","1.1.1.1,8.8.8.8");
        watcher=new NetworkWatcher(context,events::add,true); watcher.start();
        cm.network=new Network("mobile3");
        cm.properties=new LinkProperties("rmnet0","10.0.0.4","8.8.8.8,1.1.1.1");
        cm.callback.onAvailable(cm.network); cm.callback.onLinkPropertiesChanged(cm.network,cm.properties); Handler.drain();
        require(events.isEmpty(),"DNS-only mode must ignore network/IP/DNS order changes");
        old=cm.network; cm.network=null; cm.properties=null; cm.callback.onLost(old); Handler.drain();
        require(events.isEmpty(),"temporary disconnect must not restart SS");
        cm.network=new Network("wifi4"); cm.properties=new LinkProperties("wlan0","192.168.3.2","1.1.1.1,8.8.8.8");
        cm.callback.onAvailable(cm.network); Handler.drain();
        require(events.isEmpty(),"same DNS after reconnection must not restart SS");
        cm.properties=new LinkProperties("wlan0","192.168.3.2","192.168.3.1");
        cm.callback.onLinkPropertiesChanged(cm.network,cm.properties); Handler.drain();
        require(events.equals(Arrays.asList(true)),"changed DNS must restart exactly once");
        watcher.close();
        System.out.println("PASS: initial network, handover debounce, DNS/address changes, offline/recovery, stop cleanup");
        System.out.println("PASS: SS DNS-only mode ignores network/IP/order/disconnect; restarts only on changed DNS");
        System.out.println("PASS: network compatibility API "+android.os.Build.VERSION.SDK_INT);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
