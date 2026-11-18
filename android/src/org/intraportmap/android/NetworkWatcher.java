package org.intraportmap.android;

import android.content.Context;
import android.net.*;
import android.os.Handler;
import android.os.Looper;
import java.util.function.Consumer;

/** Observe the app's default network; coalesce handover and DNS/address updates. */
final class NetworkWatcher {
    private final ConnectivityManager manager;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Consumer<Boolean> changed;
    private final boolean dnsOnly;
    private String previous;
    private boolean closed,registered;
    NetworkWatcher(Context context,Consumer<Boolean> changed) {
        this(context,changed,false);
    }
    NetworkWatcher(Context context,Consumer<Boolean> changed,boolean dnsOnly) {
        manager=context.getSystemService(ConnectivityManager.class);
        this.changed=changed;
        this.dnsOnly=dnsOnly;
        previous=snapshot();
    }
    private String snapshot() {
        Network network=manager.getActiveNetwork();
        if(network==null) return "";
        LinkProperties properties=manager.getLinkProperties(network);
        if(properties==null) return "";
        if(dnsOnly) {
            java.util.TreeSet<String> dns=new java.util.TreeSet<>();
            for(Object address:properties.getDnsServers()) dns.add(address.toString());
            return String.join(",",dns);
        }
        return network.toString()+"|"+properties.getInterfaceName()+"|"+
            properties.getLinkAddresses()+"|"+properties.getDnsServers();
    }
    private final Runnable check=this::checkNow;
    private void checkNow() {
        if(closed) return;
        String current=snapshot();
        // A temporary disconnect does not change the DNS used by the live server.
        if(dnsOnly && current.isEmpty()) return;
        if(!current.equals(previous)) {
            previous=current;
            changed.accept(!current.isEmpty());
        }
    }
    private void schedule() {
        if(closed) return;
        handler.removeCallbacks(check);
        handler.postDelayed(check,750);
    }
    private final ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback() {
        public void onAvailable(Network network) { schedule(); }
        public void onLost(Network network) { schedule(); }
        public void onLinkPropertiesChanged(Network network,LinkProperties properties) { schedule(); }
    };
    void start() {
        manager.registerDefaultNetworkCallback(callback,handler);
        registered=true;
    }
    void close() {
        closed=true;
        handler.removeCallbacks(check);
        if(registered) manager.unregisterNetworkCallback(callback);
    }
}
