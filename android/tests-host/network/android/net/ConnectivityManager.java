package android.net;
import android.os.Handler;
public final class ConnectivityManager {
    public Network network;
    public LinkProperties properties;
    public NetworkCallback callback;
    public Network getActiveNetwork() { return network; }
    public NetworkInfo getActiveNetworkInfo() { return network==null ? null : new NetworkInfo(); }
    public Network[] getAllNetworks() { return network==null ? new Network[0] : new Network[]{network}; }
    public NetworkInfo getNetworkInfo(Network network) { return new NetworkInfo(); }
    public LinkProperties getLinkProperties(Network network) { return properties; }
    public void registerDefaultNetworkCallback(NetworkCallback callback,Handler handler) { this.callback=callback; }
    public void registerDefaultNetworkCallback(NetworkCallback callback) { this.callback=callback; }
    public void registerNetworkCallback(NetworkRequest request,NetworkCallback callback) { this.callback=callback; }
    public void unregisterNetworkCallback(NetworkCallback callback) { this.callback=null; }
    public static class NetworkCallback {
        public void onAvailable(Network network) {}
        public void onLost(Network network) {}
        public void onLinkPropertiesChanged(Network network,LinkProperties properties) {}
    }
}
