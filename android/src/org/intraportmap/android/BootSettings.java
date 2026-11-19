package org.intraportmap.android;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;

final class BootSettings {
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("boot_services",0); }
    static void save(Context context,Class<?> service,ArrayList<String> args) {
        if(!prefs(context).edit().putString(service.getSimpleName()+"_args",new JSONArray(args).toString()).commit())
            throw new IllegalStateException("无法保存开机启动配置");
    }
    static ArrayList<String> arguments(Context context,Class<?> service) {
        ArrayList<String> args=new ArrayList<>();
        try {
            JSONArray array=new JSONArray(prefs(context).getString(service.getSimpleName()+"_args","[]"));
            for(int i=0;i<array.length();i++) args.add(array.getString(i));
        } catch(JSONException e) { args.clear(); }
        return args;
    }
    static boolean enabled(Context context,Class<?> service) { return prefs(context).getBoolean(service.getSimpleName()+"_enabled",false); }
    static boolean enable(Context context,Class<?> service,boolean enabled) {
        return prefs(context).edit().putBoolean(service.getSimpleName()+"_enabled",enabled).commit();
    }
}
