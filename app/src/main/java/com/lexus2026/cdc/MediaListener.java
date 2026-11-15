package com.lexus2026.cdc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Da acceso a la sesión de medios activa y a las notificaciones que muestra el panel. */
public class MediaListener extends NotificationListenerService {

    public static class Act {
        public final String title;
        public final PendingIntent intent;

        public Act(String title, PendingIntent intent) {
            this.title = title;
            this.intent = intent;
        }
    }

    public static class Note {
        public final String key, pkg, app, title, text;
        public final int color;
        public final Drawable icon, appIcon;
        public final PendingIntent intent;
        public final boolean clearable, autoCancel;
        public final long time;
        public final List<Act> actions;

        public Note(String key, String pkg, String app, String title, String text,
                    int color, Drawable icon, Drawable appIcon, PendingIntent intent,
                    boolean clearable, boolean autoCancel, long time,
                    List<Act> actions) {
            this.key = key;
            this.pkg = pkg;
            this.app = app;
            this.title = title;
            this.text = text;
            this.color = color;
            this.icon = icon;
            this.appIcon = appIcon;
            this.intent = intent;
            this.clearable = clearable;
            this.autoCancel = autoCancel;
            this.time = time;
            this.actions = actions;
        }
    }

    public static volatile MediaListener instance = null;
    public static volatile Runnable onChange = null;

    private static void fire() {
        Runnable r = onChange;
        if (r != null) r.run();
    }

    @Override public void onListenerConnected() { instance = this; fire(); }
    @Override public void onListenerDisconnected() { if (instance == this) instance = null; }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { fire(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { fire(); }

    private final java.util.Map<String, String> labelCache = new java.util.HashMap<>();
    private final java.util.Map<String, Drawable.ConstantState> iconCache = new java.util.HashMap<>();

    private String appLabel(PackageManager pm, String pkg) {
        String v = labelCache.get(pkg);
        if (v != null) return v;
        try { v = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception e) { v = pkg; }
        labelCache.put(pkg, v);
        return v;
    }

    private Drawable appIcon(PackageManager pm, String pkg) {
        Drawable.ConstantState cs = iconCache.get(pkg);
        if (cs != null) return cs.newDrawable();
        try {
            Drawable d = pm.getApplicationIcon(pkg);
            Drawable.ConstantState st = d.getConstantState();
            if (st != null) { iconCache.put(pkg, st); return st.newDrawable(); }
            return d;
        } catch (Exception e) { return null; }
    }

    public List<Note> notes() {
        try {
            PackageManager pm = getPackageManager();
            StatusBarNotification[] all = getActiveNotifications();
            List<StatusBarNotification> src = new ArrayList<>();
            if (all != null) {
                for (StatusBarNotification s : all) {
                    if (!s.getPackageName().equals(getPackageName())
                            && (s.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) == 0) {
                        src.add(s);
                    }
                }
            }
            Collections.sort(src, (x, y) -> Long.compare(y.getPostTime(), x.getPostTime()));
            List<Note> out = new ArrayList<>();
            for (StatusBarNotification sbn : src) {
                Notification n = sbn.getNotification();
                Bundle ex = n.extras;
                String app = appLabel(pm, sbn.getPackageName());
                CharSequence t0 = ex.getCharSequence(Notification.EXTRA_TITLE);
                String title = t0 != null ? t0.toString() : "";
                CharSequence x0 = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
                if (x0 == null) x0 = ex.getCharSequence(Notification.EXTRA_TEXT);
                String text = x0 != null ? x0.toString() : "";
                Drawable ic = null;
                try { if (n.getSmallIcon() != null) ic = n.getSmallIcon().loadDrawable(this); } catch (Exception e) { ic = null; }
                List<Act> acts = new ArrayList<>();
                if (n.actions != null) {
                    for (Notification.Action a : n.actions) {
                        if (a.getRemoteInputs() == null) {
                            acts.add(new Act(a.title != null ? a.title.toString() : "", a.actionIntent));
                        }
                    }
                }
                Drawable appIc = appIcon(pm, sbn.getPackageName());
                if (!title.isEmpty() || !text.isEmpty()) {
                    out.add(new Note(sbn.getKey(), sbn.getPackageName(), app, title, text, n.color, ic, appIc,
                            n.contentIntent, sbn.isClearable(), (n.flags & Notification.FLAG_AUTO_CANCEL) != 0,
                            sbn.getPostTime(), acts));
                }
            }
            return out;
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }
}
