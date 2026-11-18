package android.content;
public final class Context {
    public static final String CONNECTIVITY_SERVICE="connectivity";
    public final android.net.ConnectivityManager connectivity=new android.net.ConnectivityManager();
    public <T> T getSystemService(Class<T> type) { return type.cast(connectivity); }
    public Object getSystemService(String name) { return connectivity; }
}
