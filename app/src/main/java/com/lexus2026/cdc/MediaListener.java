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

    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    /** Elimina de verdad la notificación del sistema (no solo del panel) y comprueba que haya desaparecido. */
    public static void dismiss(final Note n) {
        final MediaListener ml = instance;
        if (ml == null) { hidden.add(n.key); return; }
        try { ml.cancelNotification(n.key); } catch (Throwable ignored) {}
        MAIN.postDelayed(() -> {
            try {
                boolean still = false;
                StatusBarNotification[] all = ml.getActiveNotifications();
                if (all != null) for (StatusBarNotification s : all) if (s.getKey().equals(n.key)) { still = true; break; }
                if (still) {
                    try { ml.cancelNotifications(new String[]{n.key}); } catch (Throwable ignored) {}
                    MAIN.postDelayed(() -> {
                        try {
                            StatusBarNotification[] a2 = ml.getActiveNotifications();
                            if (a2 != null) for (StatusBarNotification s : a2) if (s.getKey().equals(n.key)) { hidden.add(n.key); break; }
                        } catch (Throwable ignored) {}
                        cleanSummaries(ml);
                    }, 300);
                } else cleanSummaries(ml);
            } catch (Throwable ignored) { hidden.add(n.key); }
        }, 250);
    }

    /** Quita los resúmenes de grupo que se quedaron sin hijos (siguen en la barra aunque no tengan nada dentro). */
    private static void cleanSummaries(MediaListener ml) {
        try {
            StatusBarNotification[] all = ml.getActiveNotifications();
            if (all == null) return;
            for (StatusBarNotification s : all) {
                if ((s.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) == 0) continue;
                if ((s.getNotification().flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0) continue;
                boolean hasChild = false;
                for (StatusBarNotification o : all) {
                    if (o == s || (o.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                    if (o.getPackageName().equals(s.getPackageName()) && java.util.Objects.equals(o.getGroupKey(), s.getGroupKey())) { hasChild = true; break; }
                }
                if (!hasChild) { try { ml.cancelNotification(s.getKey()); } catch (Throwable ignored) {} }
            }
        } catch (Throwable ignored) {}
    }

    /** Abre la notificación: PendingIntent con permiso para lanzar desde segundo plano y, si falla, abre la app. */
    public static boolean open(android.content.Context c, Note n) {
        try {
            if (n.intent != null) {
                Bundle opts = null;
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    android.app.ActivityOptions ao = android.app.ActivityOptions.makeBasic();
                    ao.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    opts = ao.toBundle();
                }
                n.intent.send(c, 0, null, null, null, null, opts);
                return true;
            }
        } catch (Throwable ignored) { /* cae al plan B */ }
        try {
            android.content.Intent li = c.getPackageManager().getLaunchIntentForPackage(n.pkg);
            if (li != null) {
                li.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(li);
                return true;
            }
        } catch (Throwable ignored) {}
        if (Privileged.available()) {
            Privileged.run("monkey -p " + n.pkg + " -c android.intent.category.LAUNCHER 1");
            return true;
        }
        return false;
    }

    private static void fire() {
        Runnable r = onChange;
        if (r != null) r.run();
    }

    @Override public void onListenerConnected() { instance = this; fire(); }
    @Override public void onListenerDisconnected() {
        if (instance == this) instance = null;
        rebind(this);
    }

    public static final java.util.Set<String> hidden = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    public static void rebind(android.content.Context c) {
        try { NotificationListenerService.requestRebind(new android.content.ComponentName(c, MediaListener.class)); } catch (Throwable ignored) {}
    }
    /** Aviso de notificación nueva para la interfaz lateral (la burbuja animada). Ya viene filtrada. */
    public static volatile java.util.function.Consumer<Note> onPosted = null;

    private final java.util.Map<String, String> lastShown = new java.util.HashMap<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        fire();
        java.util.function.Consumer<Note> hook = onPosted;
        if (hook == null || sbn == null) return;
        try {
            Note n = forPopup(sbn);
            if (n != null) hook.accept(n);
        } catch (Throwable ignored) { /* nunca romper el listener por esto */ }
    }

    /** Solo lo que merece una tarjeta emergente: nada propio, ni fijas, ni resúmenes, ni silenciosas, ni repetidas. */
    private Note forPopup(StatusBarNotification sbn) {
        if (sbn.getPackageName().equals(getPackageName())) return null;
        Notification n = sbn.getNotification();
        int f = n.flags;
        if ((f & (Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE)) != 0) return null;
        if (Notification.CATEGORY_PROGRESS.equals(n.category) || Notification.CATEGORY_TRANSPORT.equals(n.category)) return null;
        try {
            RankingMap rm = getCurrentRanking();
            Ranking rk = new Ranking();
            if (rm != null && rm.getRanking(sbn.getKey(), rk)) {
                if (rk.getImportance() < android.app.NotificationManager.IMPORTANCE_DEFAULT) return null;
                if (!rk.matchesInterruptionFilter()) return null;
            }
        } catch (Throwable ignored) { /* sin ranking: se muestra igual */ }
        Note note = convert(getPackageManager(), sbn);
        if (note == null) return null;
        String sig = note.title + "\u0001" + note.text;
        String prev = lastShown.put(sbn.getKey(), sig);
        if (sig.equals(prev)) return null;   // actualización sin cambios (por ejemplo, una barra de progreso)
        if (lastShown.size() > 200) lastShown.clear();
        return note;
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) hidden.remove(sbn.getKey());
        fire();
    }

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

    /** Convierte una notificación del sistema en una {@link Note}; null si no tiene título ni texto. */
    private Note convert(PackageManager pm, StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        Bundle ex = n.extras;
        String app = appLabel(pm, sbn.getPackageName());
        CharSequence t0 = ex.getCharSequence(Notification.EXTRA_TITLE);
        String title = t0 != null ? t0.toString() : "";
        CharSequence x0 = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (x0 == null) x0 = ex.getCharSequence(Notification.EXTRA_TEXT);
        String text = x0 != null ? x0.toString() : "";
        try {
            CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null && lines.length > 0) {
                StringBuilder sbl = new StringBuilder();
                for (CharSequence l : lines) { if (l != null && l.length() > 0) { if (sbl.length() > 0) sbl.append('\n'); sbl.append(l); } }
                if (sbl.length() > 0) text = sbl.toString();
            }
            android.os.Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (msgs != null && msgs.length > 0) {
                List<Notification.MessagingStyle.Message> ml = Notification.MessagingStyle.Message.getMessagesFromBundleArray(msgs);
                StringBuilder sbm = new StringBuilder();
                int from = Math.max(0, ml.size() - 8);
                for (int i = from; i < ml.size(); i++) {
                    Notification.MessagingStyle.Message m = ml.get(i);
                    if (m == null || m.getText() == null) continue;
                    if (sbm.length() > 0) sbm.append('\n');
                    CharSequence who = m.getSenderPerson() != null ? m.getSenderPerson().getName() : null;
                    if (who != null && who.length() > 0) sbm.append(who).append(": ");
                    sbm.append(m.getText());
                }
                if (sbm.length() > 0) text = sbm.toString();
            }
        } catch (Throwable ignored) { /* nos quedamos con el texto simple */ }
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
        if (title.isEmpty() && text.isEmpty()) return null;
        return new Note(sbn.getKey(), sbn.getPackageName(), app, title, text, n.color, ic, appIc,
                n.contentIntent, sbn.isClearable(), (n.flags & Notification.FLAG_AUTO_CANCEL) != 0,
                sbn.getPostTime(), acts);
    }

    public List<Note> notes() {
        try {
            PackageManager pm = getPackageManager();
            StatusBarNotification[] all = getActiveNotifications();
            List<StatusBarNotification> src = new ArrayList<>();
            if (all != null) {
                for (StatusBarNotification s : all) {
                    if (!s.getPackageName().equals(getPackageName()) && !hidden.contains(s.getKey())
                            && (s.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) == 0) {
                        src.add(s);
                    }
                }
            }
            Collections.sort(src, (x, y) -> Long.compare(y.getPostTime(), x.getPostTime()));
            List<Note> out = new ArrayList<>();
            for (StatusBarNotification sbn : src) {
                Note note = convert(pm, sbn);
                if (note != null) out.add(note);
            }
            return out;
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }
}
