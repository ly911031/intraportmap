package android.net;
import android.os.Handler;
public final class ConnectivityManager {
    public Network network;
    public LinkProperties properties;
    public NetworkCallback callback;
    public Network getActiveNetwork() { return network; }
    public LinkProperties getLinkProperties(Network network) { return properties; }
    public void registerDefaultNetworkCallback(NetworkCallback callback,Handler handler) { this.callback=callback; }
    public void unregisterNetworkCallback(NetworkCallback callback) { this.callback=null; }
    public static class NetworkCallback {
        public void onAvailable(Network network) {}
        public void onLost(Network network) {}
        public void onLinkPropertiesChanged(Network network,LinkProperties properties) {}
    }
}
