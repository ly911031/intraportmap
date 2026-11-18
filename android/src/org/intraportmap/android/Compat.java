package org.intraportmap.android;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class Compat {
    static void start(Context context,Intent intent) {
        if(Build.VERSION.SDK_INT>=26) context.startForegroundService(intent);
        else context.startService(intent);
    }
    static void destroy(java.lang.Process child) {
        if(Build.VERSION.SDK_INT>=26) child.destroyForcibly();
        else child.destroy();
    }
    static int immutableFlag() { return Build.VERSION.SDK_INT>=23 ? android.app.PendingIntent.FLAG_IMMUTABLE : 0; }
}
