package org.intraportmap.android;
import org.json.*;
import java.io.*;
import java.util.*;

public final class ConfigCodecTest {
    public static void main(String[] args) throws Exception {
        Map<String,Object> ui=new HashMap<>(),boot=new HashMap<>();
        String password="  中文密码\n\"quoted\"  ";
        ui.put("server","example.com:2120"); ui.put("ss_password",password);
        ui.put("key",password); ui.put("ss_method",4); ui.put("lastPage",2); ui.put("hideRecents",true);
        ui.put("ss_port",""); // In-progress edits must also be saved.
        String command=new JSONArray(Arrays.asList("-s","0.0.0.0","-p","8388","-k",password)).toString();
        boot.put("ShadowsocksService_args",command); boot.put("ShadowsocksService_enabled",true);
        byte[] bytes=ConfigCodec.snapshot(ui,boot);
        JSONObject restored=ConfigCodec.decode(ConfigCodec.read(new ByteArrayInputStream(bytes)));
        require(password.equals(restored.getJSONObject("settings").getString("ss_password")),"password changed");
        require(password.equals(restored.getJSONObject("settings").getString("key")),"key changed");
        require(restored.getJSONObject("settings").getString("ss_port").isEmpty(),"partial edit lost");
        require(command.equals(restored.getJSONObject("boot").getString("ShadowsocksService_args")),"boot args changed");
        reject(restored,"settings","role",2); reject(restored,"settings","protocol","1");
        reject(restored,"settings","ss_method",5); reject(restored,"settings","hideRecents","true");
        reject(restored,"settings","unknown","value"); reject(restored,"boot","ShadowsocksService_enabled","true");
        reject(restored,"boot","ShadowsocksService_args","not JSON");
        JSONObject future=new JSONObject(restored.toString()); future.put("version",2); invalid(future);
        future.put("version",1.5); invalid(future);
        future.put("version",1); future.put("format","other-app"); invalid(future);
        try { ConfigCodec.decode(new byte[]{(byte)0xff}); throw new AssertionError("invalid UTF-8 accepted"); }
        catch(JSONException expected) {}
        try { ConfigCodec.read(new ByteArrayInputStream(new byte[1024*1024+1])); throw new AssertionError("oversized config accepted"); }
        catch(IOException expected) {}
        System.out.println("PASS: configuration UTF-8/password/boot roundtrip, partial edits, invalid types/options/schema and size limit");
    }
    private static void reject(JSONObject source,String group,String key,Object value) throws Exception {
        JSONObject copy=new JSONObject(source.toString()); copy.getJSONObject(group).put(key,value); invalid(copy);
    }
    private static void invalid(JSONObject value) throws Exception {
        try { ConfigCodec.validate(value); throw new AssertionError("invalid configuration accepted"); }
        catch(JSONException expected) {}
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
