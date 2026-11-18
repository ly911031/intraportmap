package org.intraportmap.android;

import android.content.Context;
import android.net.*;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;

/** Observe the app's default network; coalesce handover and DNS/address updates. */
final class NetworkWatcher {
    private final ConnectivityManager manager;
    private final Handler handler=new Handler(Looper.getMainLooper());
    interface Listener { void accept(boolean available); }
    private final Listener changed;
    private final boolean dnsOnly;
    private String previous;
    private volatile boolean closed;
    private boolean registered;
    NetworkWatcher(Context context,Listener changed) {
        this(context,changed,false);
    }
    NetworkWatcher(Context context,Listener changed,boolean dnsOnly) {
        manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.changed=changed;
        this.dnsOnly=dnsOnly;
        previous=snapshot();
    }
    private String snapshot() {
        Network network=activeNetwork(manager);
        if(network==null) return "";
        LinkProperties properties=manager.getLinkProperties(network);
        if(properties==null) return "";
        if(dnsOnly) {
            java.util.TreeSet<String> dns=new java.util.TreeSet<>();
            for(Object address:properties.getDnsServers()) dns.add(address.toString());
            return DnsServers.join(",",dns);
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
    private synchronized void schedule() {
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
        if(Build.VERSION.SDK_INT>=26) manager.registerDefaultNetworkCallback(callback,handler);
        else if(Build.VERSION.SDK_INT>=24) manager.registerDefaultNetworkCallback(callback);
        else manager.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),callback);
        registered=true;
        if(Build.VERSION.SDK_INT<24) handler.postDelayed(poll,5000);
    }
    private final Runnable poll=new Runnable() {
        public void run() {
            if(closed) return;
            checkNow(); handler.postDelayed(this,5000);
        }
    };
    static Network activeNetwork(ConnectivityManager manager) {
        if(Build.VERSION.SDK_INT>=23) return manager.getActiveNetwork();
        NetworkInfo active=manager.getActiveNetworkInfo();
        if(active==null || !active.isConnected()) return null;
        for(Network network:manager.getAllNetworks()) {
            NetworkInfo info=manager.getNetworkInfo(network);
            if(info!=null && info.isConnected() && info.getType()==active.getType()) return network;
        }
        return null;
    }
    void close() {
        closed=true;
        handler.removeCallbacks(check);
        handler.removeCallbacks(poll);
        if(registered) manager.unregisterNetworkCallback(callback);
    }
}
