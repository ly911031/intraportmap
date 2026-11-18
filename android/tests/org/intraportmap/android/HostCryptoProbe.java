package org.intraportmap.android;

import java.io.*;
import java.net.*;

/** Host JDK test; adb reverse makes the Android server reach this echo socket. */
public final class HostCryptoProbe {
    public static void main(String[] args) throws Exception {
        ShadowsocksProbe.selfTest();
        try(ServerSocket listener=new ServerSocket()) {
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress("127.0.0.1",Integer.parseInt(args[3])));
            Thread accept=new Thread(() -> {
                try(Socket socket=listener.accept()) {
                    byte[] b=new byte[16384]; int n;
                    while((n=socket.getInputStream().read(b))!=-1) socket.getOutputStream().write(b,0,n);
                } catch(IOException ignored) {}
            });
            accept.setDaemon(true); accept.start();
            int bridge=args.length>4 ? Integer.parseInt(args[4]) : 0;
            ShadowsocksProbe.roundtrip(args[0],args[1],Integer.parseInt(args[2]),Integer.parseInt(args[3]),bridge>0,bridge);
            System.out.println("PASS: "+args[0]+" independent host JCE -> Android ss-server -> echo, TCP 8192 bytes"+(bridge>0 ? "; UDP 32/1400/8192 bytes" : ""));
        }
    }
}
