package org.intraportmap.android;

public final class DnsServersTest {
    public static void main(String[] args) {
        check("192.168.1.1,2408:8000:2000::1", "192.168.1.1,[2408:8000:2000::1]:53");
        check("[2001:4860:4860::8888]", "[2001:4860:4860::8888]:53");
        check("[::1]:53", "[::1]:53");
        check("1.1.1.1,8.8.8.8", "1.1.1.1,8.8.8.8");
        for(String invalid:new String[]{"", "1.2.3.999", "1.2.3", "dns.example", "1.1.1.1,", "[::xyz]", "::1%wlan0"}) {
            try { DnsServers.normalize(invalid); throw new AssertionError("accepted "+invalid); }
            catch(IllegalArgumentException expected) {}
        }
        System.out.println("PASS: IPv4/IPv6 DNS normalization, idempotence and invalid inputs");
    }
    private static void check(String input,String expected) {
        String actual=DnsServers.normalize(input);
        if(!expected.equals(actual)) throw new AssertionError(input+" -> "+actual);
        if(!actual.equals(DnsServers.normalize(actual))) throw new AssertionError("not idempotent");
    }
}
