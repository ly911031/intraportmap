package org.intraportmap.android;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Independent JCE implementation of SIP004 framing, used only in the test APK. */
final class ShadowsocksProbe {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static byte[] random(int length) { byte[] b=new byte[length]; RANDOM.nextBytes(b); return b; }
    private static byte[] concat(byte[] a,byte[] b) {
        byte[] joined=Arrays.copyOf(a,a.length+b.length); System.arraycopy(b,0,joined,a.length,b.length); return joined;
    }
    private static byte[] key(String password,int length) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] previous=new byte[0];
        while(out.size()<length) {
            MessageDigest md5=MessageDigest.getInstance("MD5"); md5.update(previous);
            previous=md5.digest(password.getBytes(StandardCharsets.UTF_8)); out.write(previous);
        }
        return Arrays.copyOf(out.toByteArray(),length);
    }
    private static byte[] subkey(byte[] key,byte[] salt) throws Exception {
        Mac hmac=Mac.getInstance("HmacSHA1"); hmac.init(new SecretKeySpec(salt,"HmacSHA1"));
        byte[] prk=hmac.doFinal(key); ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] previous=new byte[0];
        for(int i=1;out.size()<key.length;i++) {
            hmac.init(new SecretKeySpec(prk,"HmacSHA1")); hmac.update(previous);
            hmac.update("ss-subkey".getBytes(StandardCharsets.US_ASCII)); previous=hmac.doFinal(new byte[]{(byte)i}); out.write(previous);
        }
        return Arrays.copyOf(out.toByteArray(),key.length);
    }
    private static void qr(int[] x,int a,int b,int c,int d) {
        x[a]+=x[b]; x[d]=Integer.rotateLeft(x[d]^x[a],16);
        x[c]+=x[d]; x[b]=Integer.rotateLeft(x[b]^x[c],12);
        x[a]+=x[b]; x[d]=Integer.rotateLeft(x[d]^x[a],8);
        x[c]+=x[d]; x[b]=Integer.rotateLeft(x[b]^x[c],7);
    }
    private static byte[] hchacha(byte[] key,byte[] nonce) {
        int[] x=new int[16]; x[0]=0x61707865; x[1]=0x3320646e; x[2]=0x79622d32; x[3]=0x6b206574;
        ByteBuffer k=ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<8;i++) x[i+4]=k.getInt();
        ByteBuffer n=ByteBuffer.wrap(nonce).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<4;i++) x[i+12]=n.getInt();
        for(int i=0;i<10;i++) {
            qr(x,0,4,8,12); qr(x,1,5,9,13); qr(x,2,6,10,14); qr(x,3,7,11,15);
            qr(x,0,5,10,15); qr(x,1,6,11,12); qr(x,2,7,8,13); qr(x,3,4,9,14);
        }
        ByteBuffer out=ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        for(int i:new int[]{0,1,2,3,12,13,14,15}) out.putInt(x[i]); return out.array();
    }
    private static byte[] hex(String value) {
        byte[] out=new byte[value.length()/2];
        for(int i=0;i<out.length;i++) out[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16); return out;
    }
    static void selfTest() {
        // Published HChaCha20 draft test vector, independent of the bundled C code.
        byte[] derived=hchacha(hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"),hex("000000090000004a0000000031415927"));
        if(!Arrays.equals(derived,hex("82413b4227b27bfed30e42508a877d73a0f9e4d58a74a853c12ec41326d3ecdc")))
            throw new AssertionError("HChaCha20 known-answer test failed");
    }
    private static byte[] crypt(String method,byte[] key,byte[] nonce,byte[] input,boolean encrypt) throws Exception {
        int mode=encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;
        Cipher cipher;
        if(method.startsWith("aes-")) {
            cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        } else {
            if(method.startsWith("xchacha")) {
                key=hchacha(key,Arrays.copyOf(nonce,16));
                byte[] shortened=new byte[12]; System.arraycopy(nonce,16,shortened,4,8); nonce=shortened;
            }
            try { cipher=Cipher.getInstance("ChaCha20/Poly1305/NoPadding"); }
            catch(NoSuchAlgorithmException e) { cipher=Cipher.getInstance("ChaCha20-Poly1305"); }
            cipher.init(mode,new SecretKeySpec(key,"ChaCha20"),new IvParameterSpec(nonce));
        }
        return cipher.doFinal(input);
    }
    private static void increment(byte[] nonce) { for(int i=0;i<nonce.length;i++) if(++nonce[i]!=0) break; }
    private static byte[] exact(InputStream in,int count) throws IOException {
        byte[] data=new byte[count]; int offset=0;
        while(offset<count) { int n=in.read(data,offset,count-offset); if(n<0) throw new EOFException(); offset+=n; }
        return data;
    }
    private static byte[] address(int port) { return new byte[]{1,127,0,0,1,(byte)(port>>>8),(byte)port}; }
    private static int keyLength(String method) { return method.startsWith("aes-128") ? 16 : method.startsWith("aes-192") ? 24 : 32; }
    static void roundtrip(String method,String password,int server,int echo) throws Exception {
        roundtrip(method,password,server,echo,true);
    }
    static void roundtrip(String method,String password,int server,int echo,boolean testUdp) throws Exception {
        roundtrip(method,password,server,echo,testUdp,0);
    }
    static void roundtrip(String method,String password,int server,int echo,boolean testUdp,int udpBridge) throws Exception {
        int length=keyLength(method),nonceLength=method.startsWith("xchacha") ? 24 : 12;
        byte[] master=key(password,length),salt=random(length),derived=subkey(master,salt),nonce=new byte[nonceLength];
        byte[] payload=random(8192),plain=concat(address(echo),payload);
        try(Socket socket=new Socket("127.0.0.1",server)) {
            socket.setSoTimeout(5000); OutputStream out=socket.getOutputStream();
            out.write(salt);
            out.write(crypt(method,derived,nonce,new byte[]{(byte)(plain.length>>>8),(byte)plain.length},true)); increment(nonce);
            out.write(crypt(method,derived,nonce,plain,true)); out.flush();
            InputStream in=socket.getInputStream(); derived=subkey(master,exact(in,length)); nonce=new byte[nonceLength];
            ByteArrayOutputStream response=new ByteArrayOutputStream();
            while(response.size()<payload.length) {
                byte[] size=crypt(method,derived,nonce,exact(in,18),false); increment(nonce);
                int count=((size[0]&255)<<8)|(size[1]&255);
                response.write(crypt(method,derived,nonce,exact(in,count+16),false)); increment(nonce);
            }
            if(!Arrays.equals(payload,response.toByteArray())) throw new AssertionError(method+" TCP mismatch");
        }
        if(!testUdp) return;
        try(DatagramSocket socket=new DatagramSocket(); Socket bridge=udpBridge>0 ? new Socket("127.0.0.1",udpBridge) : null) {
            socket.setSoTimeout(5000);
            if(bridge!=null) bridge.setSoTimeout(5000);
            for(int size:new int[]{32,1400,8192}) {
                payload=random(size); salt=random(length); derived=subkey(master,salt);
                byte[] request=concat(salt,crypt(method,derived,new byte[nonceLength],concat(address(echo),payload),true));
                byte[] data;
                if(bridge!=null) {
                    DataOutputStream out=new DataOutputStream(bridge.getOutputStream());
                    out.writeInt(request.length); out.write(request); out.flush();
                    int count=new DataInputStream(bridge.getInputStream()).readInt();
                    if(count<0 || count>65535) throw new IOException("Invalid test bridge frame");
                    data=exact(bridge.getInputStream(),count);
                } else {
                    socket.send(new DatagramPacket(request,request.length,InetAddress.getByName("127.0.0.1"),server));
                    DatagramPacket packet=new DatagramPacket(new byte[65535],65535); socket.receive(packet);
                    data=Arrays.copyOf(packet.getData(),packet.getLength());
                }
                derived=subkey(master,Arrays.copyOf(data,length));
                byte[] decoded=crypt(method,derived,new byte[nonceLength],Arrays.copyOfRange(data,length,data.length),false);
                if(!Arrays.equals(concat(address(echo),payload),decoded)) throw new AssertionError(method+" UDP mismatch "+size);
            }
        }
    }
}
