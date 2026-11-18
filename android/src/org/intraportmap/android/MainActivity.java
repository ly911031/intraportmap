package org.intraportmap.android;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout body;
    private Spinner role, protocol;
    private EditText server, remote, local, key, retry, timeout;
    private TextView status, battery;
    private boolean changingRecents;
    private static final int EXPORT_LOGS=100;
    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() {
        public void run() {
            status.setText(TunnelService.running ? "运行中 · 连接详情已写入日志文件" : "已停止");
            PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
            battery.setText("电池优化：" + (pm.isIgnoringBatteryOptimizations(getPackageName()) ? "已豁免" : "未豁免，休眠可能断网")
                + "\n省电模式：" + (pm.isPowerSaveMode() ? "开启" : "关闭")
                + " · 深度休眠：" + (pm.isDeviceIdleMode() ? "是" : "否"));
            handler.postDelayed(this, 1000);
        }
    };
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size) {
        TextView t = new TextView(this); t.setText(text); t.setTextSize(size);
        t.setTextColor(Color.rgb(24,45,65)); t.setPadding(0,dp(12),0,dp(4)); body.addView(t); return t;
    }
    private Spinner select(String title, String[] items, int value) {
        label(title,14); Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,items));
        s.setSelection(value); body.addView(s); return s;
    }
    private EditText field(String title, String name, String initial, boolean secret, boolean number) {
        label(title,14); EditText e = new EditText(this); e.setSingleLine(true); e.setTextSize(16);
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        e.setText(getPreferences(0).getString(name,initial)); e.setTag(name); body.addView(e); return e;
    }
    public void onCreate(Bundle state) {
        super.onCreate(state);
        applyRecentVisibility(getPreferences(0).getBoolean("hideRecents",false));
        ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(1);
        body.setPadding(dp(24),dp(20),dp(24),dp(30)); scroll.addView(body); setContentView(scroll);
        label("IntraPortMap",28); label("内网端口映射 · Android",16);
        status = label("已停止",16);
        role = select("运行模式",new String[]{"客户端（将内网服务映射到公网）","服务端（监听客户端连接）"},getPreferences(0).getInt("role",0));
        server = field("服务端地址 / 监听地址", "server", "",false,false);
        server.setHint("example.com:22120 或 [::]:22120");
        remote = field("公网映射地址（客户端）","remote","[::]:13389",false,false);
        local = field("内网目标地址（客户端）","local","127.0.0.1:3389",false,false);
        key = field("通信密钥（与服务端一致，可留空）","key","",true,false);
        protocol = select("映射协议（两端需一致）",new String[]{"TCP","TCP + UDP","仅 UDP"},getPreferences(0).getInt("protocol",0));
        retry = field("断线重连间隔（秒）","retry","15",false,true);
        timeout = field("UDP 会话超时（秒）","timeout","180",false,true);
        Button start = new Button(this); start.setText("保存并启动"); body.addView(start);
        Button stop = new Button(this); stop.setText("停止映射"); body.addView(stop);
        start.setOnClickListener(v -> startTunnel());
        stop.setOnClickListener(v -> stopService(new Intent(this,TunnelService.class)));
        role.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> a) {}
            public void onItemSelected(AdapterView<?> a,View v,int p,long id) { remote.setEnabled(p==0); local.setEnabled(p==0); }
        });
        label("运行日志",18);
        label("INFO / WARN / ERROR 自动写入日志文件，不再输出 DEBUG。每份最多 2 MB，保留当前文件及 3 份历史日志。",12);
        Button export = new Button(this); export.setText("导出日志文件"); body.addView(export);
        export.setOnClickListener(v -> {
            Intent create=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_TITLE,"intraportmap-logs.txt");
            try { startActivityForResult(create,EXPORT_LOGS); }
            catch(ActivityNotFoundException e) { Toast.makeText(this,"系统未提供文件保存入口",Toast.LENGTH_LONG).show(); }
        });
        label("后台保活",18); battery = label("",14);
        Switch hide = new Switch(this); hide.setText("在最近任务中隐藏这个应用"); body.addView(hide);
        hide.setChecked(getPreferences(0).getBoolean("hideRecents",false));
        hide.setOnCheckedChangeListener((button,checked) -> {
            if(changingRecents) return;
            if(applyRecentVisibility(checked)) getPreferences(0).edit().putBoolean("hideRecents",checked).apply();
            else {
                changingRecents=true; button.setChecked(!checked); changingRecents=false;
                Toast.makeText(this,"系统未能修改最近任务显示状态",Toast.LENGTH_LONG).show();
            }
        });
        label("隐藏后可从桌面图标或常驻通知重新打开；不会停止映射。此设置减少误清理，不能阻止系统或厂商杀进程。",12);
        Button exempt = new Button(this); exempt.setText("允许忽略电池优化"); body.addView(exempt);
        exempt.setOnClickListener(v -> {
            PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
            if(pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this,"已获得电池优化豁免",Toast.LENGTH_SHORT).show(); return;
            }
            openSettings(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));
        });
        Button settings = new Button(this); settings.setText("打开应用设置（允许后台运行）"); body.addView(settings);
        settings.setOnClickListener(v -> openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        label("建议允许电池无限制/后台运行，并在厂商设置中允许自启动、锁定最近任务。映射期间持有 CPU/Wi-Fi 锁，会增加耗电。划掉界面会继续运行；点击停止才结束。系统强行停止、重启手机或厂商清理仍可能中断，请重新打开应用启动。",12);
    }
    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch(ActivityNotFoundException e) { Toast.makeText(this,"系统未提供该入口，请手动打开设置 → 应用 → IntraPortMap → 电池",Toast.LENGTH_LONG).show(); }
    }
    private boolean applyRecentVisibility(boolean hidden) {
        try {
            ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
            boolean changed=false;
            for(ActivityManager.AppTask task:manager.getAppTasks()) {
                task.setExcludeFromRecents(hidden); changed=true;
            }
            return changed;
        } catch(RuntimeException e) { return false; }
    }
    protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=EXPORT_LOGS || result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        Toast.makeText(this,"正在导出日志",Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String message;
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
                if(out==null) throw new IOException("无法打开目标文件");
                new LogStore(new File(getFilesDir(),"logs")).exportTo(out);
                message="日志已导出";
            } catch(IOException | SecurityException e) { message="导出失败："+e.getMessage(); }
            final String notice=message;
            runOnUiThread(() -> Toast.makeText(this,notice,Toast.LENGTH_LONG).show());
        },"export-logs").start();
    }
    private String value(EditText e) { return e.getText().toString().trim(); }
    private String address(EditText e) {
        String a = value(e); int split = a.lastIndexOf(':');
        try {
            if (split<=0 || (a.indexOf(':')!=split && !(a.startsWith("[") && a.charAt(split-1)==']'))) throw new Exception();
            int p = Integer.parseInt(a.substring(split+1)); if(p<1 || p>65535) throw new Exception();
        } catch(Exception x) { throw new IllegalArgumentException("地址格式应为 主机:端口 或 [IPv6]:端口，端口范围 1–65535"); }
        return a;
    }
    private String seconds(EditText e) {
        try { int n = Integer.parseInt(value(e)); if(n<1 || n>86400) throw new Exception(); return ""+n; }
        catch(Exception x) { throw new IllegalArgumentException("时间需为 1–86400 秒"); }
    }
    private void startTunnel() {
        if(TunnelService.running) { Toast.makeText(this,"请先停止当前映射",0).show(); return; }
        try {
            ArrayList<String> args = new ArrayList<>();
            if(role.getSelectedItemPosition()==0) args.add("-c");
            Collections.addAll(args,"-s",address(server));
            if(role.getSelectedItemPosition()==0) Collections.addAll(args,"-t",address(remote),"-f",address(local));
            if(!key.getText().toString().isEmpty()) Collections.addAll(args,"-k",key.getText().toString());
            Collections.addAll(args,"-w",seconds(retry),"-T",seconds(timeout));
            if(protocol.getSelectedItemPosition()>0) args.add(protocol.getSelectedItemPosition()==1 ? "-u":"-U");
            SharedPreferences.Editor edit = getPreferences(0).edit();
            for(EditText e:new EditText[]{server,remote,local,retry,timeout}) edit.putString((String)e.getTag(),value(e));
            // Keep the communication key in memory only; never persist it or print arguments.
            edit.putInt("role",role.getSelectedItemPosition()).putInt("protocol",protocol.getSelectedItemPosition()).apply();
            startForegroundService(new Intent(this,TunnelService.class).putStringArrayListExtra("args",args));
        } catch(IllegalArgumentException e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
    }
    protected void onResume() {
        super.onResume(); applyRecentVisibility(getPreferences(0).getBoolean("hideRecents",false)); handler.post(refresh);
    }
    protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
