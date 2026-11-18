package android.content;
public final class Context {
    public final android.net.ConnectivityManager connectivity=new android.net.ConnectivityManager();
    public <T> T getSystemService(Class<T> type) { return type.cast(connectivity); }
}
