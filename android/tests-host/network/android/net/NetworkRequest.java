package android.net;
public final class NetworkRequest {
    public static final class Builder {
        public Builder addCapability(int capability) { return this; }
        public NetworkRequest build() { return new NetworkRequest(); }
    }
}
