package org.intraportmap.android;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;

/** Numeric DNS addresses in the syntax accepted by bundled c-ares 1.18.1. */
final class DnsServers {
    static String normalize(String csv) {
        ArrayList<String> result=new ArrayList<>();
        for(String entry:csv.split(",",-1)) {
            String ip=entry.trim();
            // Explicit port is required for bracketed IPv6 by this c-ares version.
            if(ip.startsWith("[") && ip.endsWith("]:53")) ip=ip.substring(1,ip.length()-4);
            else if(ip.startsWith("[") && ip.endsWith("]")) ip=ip.substring(1,ip.length()-1);
            try {
                if(ip.contains(":")) {
                    if(!ip.matches("[0-9a-fA-F:]+") || !(InetAddress.getByName(ip) instanceof Inet6Address))
                        throw new IllegalArgumentException();
                    result.add("["+ip+"]:53");
                } else {
                    String[] parts=ip.split("\\.",-1);
                    if(parts.length!=4) throw new IllegalArgumentException();
                    for(String part:parts) if(!part.matches("[0-9]{1,3}") || Integer.parseInt(part)>255)
                        throw new IllegalArgumentException();
                    result.add(ip);
                }
            } catch(Exception e) { throw new IllegalArgumentException("DNS 地址无效："+entry); }
        }
        return String.join(",",result);
    }
}
