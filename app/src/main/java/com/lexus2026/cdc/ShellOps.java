package com.lexus2026.cdc;

import android.os.IBinder;

import java.lang.reflect.Method;

public final class ShellOps {
    private ShellOps() {}

    public static void main(String[] a) {
        int code = 1;
        try {
            if (a.length >= 2 && a[0].equals("limit")) code = limit(Integer.parseInt(a[1]));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.exit(code);
    }

    private static int limit(int n) throws Exception {
        Object b = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "activity");
        Object am = Class.forName("android.app.IActivityManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, b);
        Method m = Class.forName("android.app.IActivityManager").getMethod("setProcessLimit", int.class);
        m.invoke(am, n);
        return 0;
    }
}
