package android.net;
import java.util.*;
public final class LinkProperties {
    private final String iface,address,dns;
    public LinkProperties(String iface,String address,String dns) { this.iface=iface; this.address=address; this.dns=dns; }
    public String getInterfaceName() { return iface; }
    public List<String> getLinkAddresses() { return Arrays.asList(address); }
    public List<String> getDnsServers() { return Arrays.asList(dns.split(",")); }
}
