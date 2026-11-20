package org.intraportmap.android;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ConfigCodec {
    private static final Set<String> TEXT=new HashSet<>(Arrays.asList("server","remote","local","key","retry","timeout","ss_host","ss_port","ss_password","ss_timeout","ss_dns","ss_mtu"));
    static byte[] snapshot(Map<String,?> settings,Map<String,?> boot) throws JSONException {
        JSONObject root=new JSONObject(); root.put("format","intraportmap-android"); root.put("version",1);
        root.put("settings",new JSONObject(settings)); root.put("boot",new JSONObject(boot));
        validate(root); return root.toString(2).getBytes(StandardCharsets.UTF_8);
    }
    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[4096]; int n;
        while((n=in.read(buffer))!=-1) { if(out.size()+n>1024*1024) throw new IOException("配置文件过大"); out.write(buffer,0,n); }
        return out.toByteArray();
    }
    static JSONObject decode(byte[] bytes) throws JSONException {
        try {
            String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            JSONObject root=new JSONObject(text); validate(root); return root;
        } catch(java.nio.charset.CharacterCodingException e) { throw new JSONException("配置文件不是有效 UTF-8"); }
    }
    static void validate(JSONObject root) throws JSONException {
        if(!"intraportmap-android".equals(root.getString("format")) || !(root.get("version") instanceof Integer) || root.getInt("version")!=1) throw new JSONException("配置格式不支持");
        JSONObject settings=root.getJSONObject("settings"), boot=root.getJSONObject("boot");
        for(Iterator<String> keys=settings.keys();keys.hasNext();) {
            String key=keys.next(); Object value=settings.get(key);
            if(TEXT.contains(key)) { if(!(value instanceof String) || ((String)value).length()>16384) throw new JSONException("配置文本无效"); }
            else if(key.equals("hideRecents")) { if(!(value instanceof Boolean)) throw new JSONException("开关无效"); }
            else {
                int max=key.equals("ss_method") ? 4 : key.equals("role") ? 1 : 2;
                if(!Arrays.asList("ss_method","role","protocol","ss_protocol","lastPage").contains(key)
                    || !(value instanceof Integer) || (Integer)value<0 || (Integer)value>max) throw new JSONException("配置选项无效");
            }
        }
        for(Iterator<String> keys=boot.keys();keys.hasNext();) {
            String key=keys.next(); Object value=boot.get(key);
            if(!key.matches("(TunnelService|ShadowsocksService)_(args|enabled)")) throw new JSONException("启动配置无效");
            if(key.endsWith("_enabled")) { if(!(value instanceof Boolean)) throw new JSONException("启动开关无效"); }
            else {
                if(!(value instanceof String)) throw new JSONException("启动参数无效");
                JSONArray args=new JSONArray((String)value); if(args.length()>64) throw new JSONException("启动参数过多");
                for(int i=0;i<args.length();i++) if(!(args.get(i) instanceof String) || args.getString(i).length()>16384) throw new JSONException("启动参数无效");
            }
        }
    }
}
