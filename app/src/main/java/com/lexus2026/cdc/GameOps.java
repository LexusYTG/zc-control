package com.lexus2026.cdc;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.Resources;
import android.os.Build;
import android.provider.Settings;

public final class GameOps {
    private GameOps() {}

    public static String bucketName(int b) {
        switch (b) {
            case 10: return "active";
            case 20: return "working_set";
            case 30: return "frequent";
            case 40: return "rare";
            case 45: return "restricted";
            default: return null;
        }
    }

    public static int[] size(int w, int h, int pct) {
        int nw = Math.max(2, Math.round(w * pct / 100f) / 2 * 2);
        int nh = Math.max(2, Math.round(h * pct / 100f) / 2 * 2);
        return new int[]{nw, nh};
    }

    public static int dpi(int dpi, int pct) {
        return Math.max(72, Math.round(dpi * pct / 100f));
    }

    public static boolean resolution(Context c, int pct) {
        GameWatchdog.Orig o = GameWatchdog.orig(c);
        if (o == null) return false;
        GameWatchdog.mark(c, "chRes");
        int[] s = size(o.w, o.h, pct);
        ShellExec.Res r = ShellExec.exec("wm size " + s[0] + "x" + s[1] + "; wm density " + dpi(o.dpi, pct), 10000);
        if (r.code != 0) return false;
        GameWatchdog.setPct(c, pct);
        return true;
    }

    public static boolean alwaysFinish(Context c, boolean on) {
        GameWatchdog.mark(c, "chAct");
        return ShellExec.exec("settings put global always_finish_activities " + (on ? 1 : 0), 6000).code == 0;
    }

    public static boolean limit(Context c, int n) {
        return ShellExec.exec("CLASSPATH='" + c.getPackageCodePath() + "' app_process /system/bin com.lexus2026.cdc.ShellOps limit " + n, 15000).code == 0;
    }

    public static boolean bgLimit(Context c, int n) {
        GameWatchdog.mark(c, "chLimit");
        return limit(c, n);
    }

    public static boolean saver(Context c, boolean on) {
        GameWatchdog.mark(c, "chSaver");
        return ShellExec.exec("settings put global low_power " + (on ? 1 : 0), 6000).code == 0;
    }

    public static boolean saverOn() {
        return ShellExec.exec("settings get global low_power", 6000).out.trim().equals("1");
    }

    public static int maxBrightness() {
        try {
            int id = Resources.getSystem().getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android");
            if (id != 0) {
                int v = Resources.getSystem().getInteger(id);
                if (v > 0) return v;
            }
        } catch (Throwable e) {
        }
        return 255;
    }

    public static int brightness(Context c) {
        try {
            return Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            return 128;
        }
    }

    public static void brightnessMark(Context c) {
        GameWatchdog.mark(c, "chBright");
    }

    public static void setBrightness(Context c, int v, boolean shell) {
        int b = Math.max(1, Math.min(maxBrightness(), v));
        if (Settings.System.canWrite(c)) {
            try {
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, b);
            } catch (Exception e) {
            }
        } else if (shell) {
            ShellExec.exec("settings put system screen_brightness_mode 0; settings put system screen_brightness " + b, 6000);
        }
    }

    public static long versionCode(PackageInfo pi) {
        return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
    }
}
