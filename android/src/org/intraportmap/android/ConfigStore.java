package org.intraportmap.android;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Atomic private snapshot plus an optional user-selected document surviving uninstall. */
final class ConfigStore implements SharedPreferences.OnSharedPreferenceChangeListener {
    interface Result { void done(String error); }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private final Context context;
    private final SharedPreferences ui,boot,storage;
    private final AtomicFile file;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean importing,closed;
    volatile String status="配置自动保存到本机";
    private final Runnable pending=this::flush;
    ConfigStore(Context context,SharedPreferences ui) {
        this.context=context.getApplicationContext(); this.ui=ui;
        boot=context.getSharedPreferences("boot_services",0);
        storage=context.getSharedPreferences("config_storage",0);
        File directory=new File(context.getFilesDir(),"config"); directory.mkdirs();
        file=new AtomicFile(new File(directory,"intraportmap-config.json"));
        if(ui.getAll().isEmpty()) {
            try(InputStream in=file.openRead()) { apply(ConfigCodec.decode(ConfigCodec.read(in))); }
            catch(FileNotFoundException e) { /* First installation: no local snapshot yet. */ }
            catch(Exception e) {
                storage.edit().remove("uri").commit();
                status="本地配置读取失败，请重新选择已有配置文件恢复";
            }
        }
        ui.registerOnSharedPreferenceChangeListener(this);
        boot.registerOnSharedPreferenceChangeListener(this);
    }
    public void onSharedPreferenceChanged(SharedPreferences prefs,String key) {
        if(closed || importing) return;
        main.removeCallbacks(pending); main.postDelayed(pending,400);
    }
    private byte[] snapshot() throws JSONException {
        return ConfigCodec.snapshot(ui.getAll(),boot.getAll());
    }
    void flush() {
        main.removeCallbacks(pending);
        if(importing) return;
        try {
            final byte[] bytes=snapshot(); final String uri=storage.getString("uri","");
            IO.execute(() -> {
                try {
                    writeLocal(bytes);
                    if(!uri.isEmpty()) writeDocument(Uri.parse(uri),bytes);
                    status=uri.isEmpty() ? "已自动保存 · 本机配置文件" : "已自动保存 · 本机和所选配置文件";
                } catch(Exception e) { status="配置文件同步失败；编辑内容仍保存在应用内，请重新选择文件"; }
            });
        } catch(JSONException e) { status="配置序列化失败"; }
    }
    void close() {
        closed=true; ui.unregisterOnSharedPreferenceChangeListener(this);
        boot.unregisterOnSharedPreferenceChangeListener(this); flush();
    }
    void select(Uri uri,boolean load,Result result) {
        importing=true; main.removeCallbacks(pending);
        final byte[] current;
        try { current=snapshot(); }
        catch(JSONException e) { importing=false; result.done("配置保存失败"); return; }
        IO.execute(() -> {
            String error=null;
            try {
                byte[] bytes=current;
                if(load) {
                    try(InputStream in=context.getContentResolver().openInputStream(uri)) {
                        if(in==null) throw new IOException(); bytes=ConfigCodec.read(in);
                    }
                    JSONObject root=ConfigCodec.decode(bytes); ConfigCodec.validate(root); // Validate completely before changing preferences.
                    // Check write access before binding the document for automatic synchronization.
                    writeDocument(uri,bytes);
                    apply(root);
                } else writeDocument(uri,bytes);
                writeLocal(bytes);
                if(!storage.edit().putString("uri",uri.toString()).commit()) throw new IOException();
                status="已自动保存 · 本机和所选配置文件";
            } catch(Exception e) { error="无法读取或写入配置：请确认文件格式正确，并选择可读写的文件"; status=error; }
            importing=false;
            final String failure=error; main.post(() -> result.done(failure));
        });
    }
    private void writeLocal(byte[] bytes) throws IOException {
        FileOutputStream out=null;
        try { out=file.startWrite(); out.write(bytes); file.finishWrite(out); }
        catch(IOException e) { if(out!=null) file.failWrite(out); throw e; }
    }
    private void writeDocument(Uri uri,byte[] bytes) throws IOException {
        try(OutputStream out=context.getContentResolver().openOutputStream(uri,"wt")) {
            if(out==null) throw new IOException(); out.write(bytes);
        }
    }
    private void apply(JSONObject root) throws JSONException,IOException {
        ConfigCodec.validate(root);
        SharedPreferences.Editor u=ui.edit().clear(), b=boot.edit().clear();
        copy(root.getJSONObject("settings"),u); copy(root.getJSONObject("boot"),b);
        if(!u.commit() || !b.commit()) throw new IOException("配置写入失败");
    }
    private static void copy(JSONObject source,SharedPreferences.Editor editor) throws JSONException {
        for(Iterator<String> keys=source.keys();keys.hasNext();) {
            String key=keys.next(); Object value=source.get(key);
            if(value instanceof Boolean) editor.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer) editor.putInt(key,(Integer)value);
            else editor.putString(key,(String)value);
        }
    }
}
