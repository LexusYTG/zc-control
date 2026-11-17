package com.lexus2026.cdc;

import android.accessibilityservice.AccessibilityService;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Panel de ajustes rápidos, dibujado como overlay de accesibilidad. Su aspecto sale del Skin que armó el usuario. */
public class PanelView extends FrameLayout {
    private static final int MP = -1;
    private static final int WC = -2;

    /** ScrollView que no crece más de maxH (lista de notificaciones). */
    private static class MaxScroll extends ScrollView {
        private final int maxH;

        MaxScroll(Context c, int maxH) {
            super(c);
            this.maxH = maxH;
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
        }
    }

    private final Context ctx = getContext();
    private Runnable onClose;

    private final Skin sk = new Skin(ctx);
    private final boolean night = (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES;
    private final boolean dark = sk.autoTheme != 0 ? inNightHours() : sk.theme == 1 ? false : sk.theme == 2 ? true : night;

    private int withA(int c, int pct) {
        int a = ((c >>> 24) & 0xFF) * Math.max(0, Math.min(100, pct)) / 100;
        return (c & 0x00FFFFFF) | (a << 24);
    }

    private double lum(int c) { return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0; }

    private ColorStateList tint(int c) { return ColorStateList.valueOf(c); }

    private final int cText = sk.textColor != 0 ? sk.textColor : dark ? 0xFFF2EEF5 : 0xFF1D1B1E;
    private final int cSub = withA(cText, 72);
    private final int cAccent = sk.lowBatRed != 0 && lowBat() ? 0xFFE53935 : sk.accent != 0 ? sk.accent
        : Build.VERSION.SDK_INT >= 31
            ? ctx.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600)
            : dark ? 0xFFCFBCFF : 0xFF6750A4;
    private final int cOnAccent = lum(cAccent) > 0.6 ? 0xFF1C1B1F : 0xFFFFFFFF;
    private final int offBase = sk.tileOff != 0 ? sk.tileOff : dark ? 0xFF49454F : 0xFFDDD6DE;
    private final int cOn = withA(cAccent, sk.onAlpha);
    private final int cOff = withA(offBase, sk.offAlpha);
    private final int cCard = withA(sk.cardColor != 0 ? sk.cardColor : sk.tileOff != 0 ? sk.tileOff : dark ? 0xFF2B2930 : 0xFFE6E0E3, sk.cardAlpha);
    private final int cTrack = dark ? 0xFF49454F : 0xFFD0C9D3;
    private final int cSheet = withA(sk.sheetColor != 0 ? sk.sheetColor : dark ? 0xFF141218 : 0xFFF3EEF0, sk.sheetAlpha);

    private static class Tile {
        final String label;
        final int icon;
        final BooleanSupplier on;
        final String onText;
        final String offText;
        final Supplier<String> subFn;
        final Runnable tap;
        String key;
        Drawable iconDrawable = null;

        Tile(String label, int icon, BooleanSupplier on, String onText, String offText, Runnable tap) {
            this(label, icon, on, onText, offText, null, tap);
        }

        Tile(String label, int icon, BooleanSupplier on, String onText, String offText, Supplier<String> subFn, Runnable tap) {
            this.label = label;
            this.icon = icon;
            this.on = on;
            this.onText = onText;
            this.offText = offText;
            this.subFn = subFn;
            this.tap = tap;
            this.key = label;
        }
    }

    /** Una instancia dibujada de un acceso (un mismo acceso puede estar en la vista acotada y en la completa). */
    private static class TileView {
        final Tile t;
        final View box;
        final Shaped shape;
        final ImageView img;
        final TextView title;
        final TextView sub;
        final boolean pill;

        TileView(Tile t, View box, Shaped shape, ImageView img, TextView title, TextView sub, boolean pill) {
            this.t = t;
            this.box = box;
            this.shape = shape;
            this.img = img;
            this.title = title;
            this.sub = sub;
            this.pill = pill;
        }
    }

    private final List<TileView> holders = new ArrayList<>();

    private final android.content.SharedPreferences prefs = ctx.getSharedPreferences("cc", Context.MODE_PRIVATE);
    private final CameraManager cam = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
    private final AudioManager audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    private final NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    private boolean torch = false;
    private final String camId = findCamId();

    private String findCamId() {
        try {
            for (String id : cam.getCameraIdList()) {
                Boolean f = cam.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(f)) return id;
            }
            return null;
        } catch (Exception e) { return null; }
    }

    private final CameraManager.TorchCallback torchCb = new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeChanged(String cameraId, boolean enabled) {
            if (cameraId.equals(camId)) { torch = enabled; refresh(); }
        }
    };

    private int dp(int v) { return (int) (v * ctx.getResources().getDisplayMetrics().density); }

    private WifiManager wifi() { return (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE); }

    private boolean settingsOn(String key) { return Settings.Global.getInt(ctx.getContentResolver(), key, 0) == 1; }

    private boolean secureOk() { return ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED; }

    private boolean secureOn(String key) { return Settings.Secure.getInt(ctx.getContentResolver(), key, 0) == 1; }

    private void toggleSecure(String key, String fallback) {
        int v = secureOn(key) ? 0 : 1;
        if (secureOk()) { Settings.Secure.putInt(ctx.getContentResolver(), key, v); refresh(); }
        else priv("settings put secure " + key + " " + v, fallback);
    }

    private void later(Runnable block) { onClose.run(); new Handler(Looper.getMainLooper()).postDelayed(block, 450); }

    private void svc(final int action) {
        later(() -> {
            ControlService s = ControlService.instance;
            if (s != null) s.global(action);
        });
    }

    private boolean nfcOn() {
        try {
            NfcAdapter a = NfcAdapter.getDefaultAdapter(ctx);
            return a != null && a.isEnabled();
        } catch (Exception e) { return false; }
    }

    private boolean btOn() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            return a != null && a.isEnabled();
        } catch (Exception e) { return false; }
    }

    private boolean mobileData() { return Settings.Global.getInt(ctx.getContentResolver(), "mobile_data", 1) == 1; }

    private boolean dataSaver() {
        try {
            return ((ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE)).getRestrictBackgroundStatus() !=
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
        } catch (Exception e) { return false; }
    }

    private void open(String action) { open(action, false); }

    private void open(String action, boolean pkg) {
        try {
            Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (pkg) i.setData(Uri.parse("package:" + ctx.getPackageName()));
            ctx.startActivity(i);
            if (sk.closeOnAct != 0) onClose.run();
        } catch (Exception e) {
            Toast.makeText(ctx, "No se pudo abrir el ajuste", Toast.LENGTH_SHORT).show();
        }
    }

    /** Usa Shizuku si está autorizado; si no, abre el ajuste del sistema correspondiente. */
    private void priv(String cmd, String fallback) {
        // Ajustes simples: con WRITE_SECURE_SETTINGS se escriben directo por API, sin pasar por la shell.
        java.util.regex.Matcher sm = java.util.regex.Pattern.compile("^settings put (global|secure|system) (\\S+) (\\S+)$").matcher(cmd.trim());
        if (sm.matches() && secureOk()) {
            try {
                boolean okw;
                String ns = sm.group(1), k = sm.group(2), v = sm.group(3);
                if (ns.equals("global")) okw = Settings.Global.putString(ctx.getContentResolver(), k, v);
                else if (ns.equals("secure")) okw = Settings.Secure.putString(ctx.getContentResolver(), k, v);
                else okw = Settings.System.putString(ctx.getContentResolver(), k, v);
                if (okw) { refresh(); postDelayed(() -> refresh(), 250); return; }
            } catch (Throwable ignored) { /* cae a la shell */ }
        }
        if (Privileged.available()) Privileged.runFast(cmd, () -> { refresh(); postDelayed(() -> refresh(), 250); postDelayed(() -> refresh(), 900); });
        else open(fallback);
    }

    private final List<Tile> tiles = Arrays.asList(
        new Tile("Internet", R.drawable.ic_wifi, () -> wifi().isWifiEnabled(), "Wi-Fi activado", "Desactivado", () ->
            priv("svc wifi " + (wifi().isWifiEnabled() ? "disable" : "enable"), Settings.Panel.ACTION_INTERNET_CONNECTIVITY)),
        new Tile("Bluetooth", R.drawable.ic_bt, () -> btOn(), "Activado", "Desactivado", () ->
            priv("svc bluetooth " + (btOn() ? "disable" : "enable"), Settings.ACTION_BLUETOOTH_SETTINGS)),
        new Tile("Linterna", R.drawable.ic_torch, () -> torch, "Encendida", "Apagada", () -> {
            try { if (camId != null) cam.setTorchMode(camId, !torch); } catch (Exception e) {}
        }),
        new Tile("Giro automático", R.drawable.ic_rotate,
            () -> Settings.System.getInt(ctx.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1,
            "Activado", "Desactivado", () -> {
            if (Settings.System.canWrite(ctx)) {
                int cur = Settings.System.getInt(ctx.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                Settings.System.putInt(ctx.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1 - cur);
                refresh();
            } else open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true);
        }),
        new Tile("Ahorro de batería", R.drawable.ic_battery,
            () -> ((PowerManager) ctx.getSystemService(Context.POWER_SERVICE)).isPowerSaveMode(),
            "Activado", "Desactivado", () -> {
            boolean on = ((PowerManager) ctx.getSystemService(Context.POWER_SERVICE)).isPowerSaveMode();
            priv("settings put global low_power " + (on ? 0 : 1), Settings.ACTION_BATTERY_SAVER_SETTINGS);
        }),
        new Tile("No molestar", R.drawable.ic_dnd,
            () -> nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL,
            "Activado", "Desactivado", () -> {
            if (nm.isNotificationPolicyAccessGranted()) {
                boolean all = nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_ALL;
                nm.setInterruptionFilter(
                    all ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        : NotificationManager.INTERRUPTION_FILTER_ALL
                );
                postDelayed(() -> refresh(), 300);
            } else open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        }),
        new Tile("Modo avión", R.drawable.ic_airplane, () -> settingsOn(Settings.Global.AIRPLANE_MODE_ON), "Activado", "Desactivado", () -> {
            boolean on = settingsOn(Settings.Global.AIRPLANE_MODE_ON);
            priv("cmd connectivity airplane-mode " + (on ? "disable" : "enable"), Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        }),
        new Tile("Tema oscuro", R.drawable.ic_dark, () -> night, "Activado", "Desactivado", () -> {
            priv("cmd uimode night " + (night ? "no" : "yes"), Settings.ACTION_DISPLAY_SETTINGS);
            onClose.run();
        }),
        new Tile("Ubicación", R.drawable.ic_location,
            () -> ((LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE)).isLocationEnabled(),
            "Activada", "Desactivada", () -> {
            boolean on = ((LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE)).isLocationEnabled();
            priv("cmd location set-location-enabled " + (on ? "false" : "true"), Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        }),
        new Tile("Datos móviles", R.drawable.ic_data, () -> mobileData(), "Activados", "Desactivados", () ->
            priv("svc data " + (mobileData() ? "disable" : "enable"), Settings.ACTION_DATA_ROAMING_SETTINGS)),
        new Tile("NFC", R.drawable.ic_nfc, () -> nfcOn(), "Activado", "Desactivado", () ->
            priv("svc nfc " + (nfcOn() ? "disable" : "enable"), Settings.ACTION_NFC_SETTINGS)),
        new Tile("Sonido", R.drawable.ic_sound, () -> audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL, "", "",
            () -> ringerLabel(), () -> {
            if (nm.isNotificationPolicyAccessGranted()) {
                int cur = audio.getRingerMode();
                int next = cur == AudioManager.RINGER_MODE_NORMAL ? AudioManager.RINGER_MODE_VIBRATE
                    : cur == AudioManager.RINGER_MODE_VIBRATE ? AudioManager.RINGER_MODE_SILENT
                    : AudioManager.RINGER_MODE_NORMAL;
                audio.setRingerMode(next);
                postDelayed(() -> refresh(), 200);
            } else open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        }),
        new Tile("Brillo automático", R.drawable.ic_brightness,
            () -> Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1, "Activado", "Desactivado", () -> {
            if (Settings.System.canWrite(ctx)) {
                int cur = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
                Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 1 - cur);
                refresh();
            } else open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true);
        }),
        new Tile("Luz nocturna", R.drawable.ic_night, () -> secureOn("night_display_activated"), "Activada", "Desactivada", () ->
            toggleSecure("night_display_activated", Settings.ACTION_DISPLAY_SETTINGS)),
        new Tile("Atenuar extra", R.drawable.ic_dim, () -> secureOn("reduce_bright_colors_activated"), "Activado", "Desactivado", () ->
            toggleSecure("reduce_bright_colors_activated", Settings.ACTION_DISPLAY_SETTINGS)),
        new Tile("Invertir colores", R.drawable.ic_invert, () -> secureOn("accessibility_display_inversion_enabled"), "Activado", "Desactivado", () ->
            toggleSecure("accessibility_display_inversion_enabled", Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        new Tile("Ahorro de datos", R.drawable.ic_datasaver, () -> dataSaver(), "Activado", "Desactivado", () ->
            priv("cmd netpolicy set restrict-background " + (dataSaver() ? "false" : "true"), Settings.ACTION_DATA_ROAMING_SETTINGS)),
        new Tile("Pantalla activa", R.drawable.ic_screen,
            () -> Settings.Global.getInt(ctx.getContentResolver(), "stay_on_while_plugged_in", 0) != 0, "Al cargar", "Normal", () -> {
            boolean on = Settings.Global.getInt(ctx.getContentResolver(), "stay_on_while_plugged_in", 0) != 0;
            priv("settings put global stay_on_while_plugged_in " + (on ? 0 : 7), Settings.ACTION_DISPLAY_SETTINGS);
        }),
        new Tile("Captura", R.drawable.ic_screenshot, () -> false, "", "Tocar", () -> svc(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)),
        new Tile("Bloquear", R.drawable.ic_padlock, () -> false, "", "Pantalla", () -> svc(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)),
        new Tile("Apagar", R.drawable.ic_power, () -> false, "", "Menú", () -> svc(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)),
        new Tile("Panel del sistema", R.drawable.ic_lock, () -> prefs.getBoolean("lock", false), "Bloqueado", "Libre", () -> {
            if (Privileged.available()) {
                final boolean locked = prefs.getBoolean("lock", false);
                Privileged.run("cmd statusbar send-disable-flag " + (locked ? "none" : "statusbar-expansion"), () -> {
                    prefs.edit().putBoolean("lock", !locked).apply(); refresh();
                });
            } else Toast.makeText(ctx, "Autorizá Shizuku en la app", Toast.LENGTH_SHORT).show();
        })
    );

    private String ringerLabel() {
        switch (audio.getRingerMode()) {
            case AudioManager.RINGER_MODE_VIBRATE: return "Vibrar";
            case AudioManager.RINGER_MODE_SILENT: return "Silencio";
            default: return "Sonar";
        }
    }

    // ---- estado de la vista ----
    private int u = 0;
    private final TextView mediaTitle = new TextView(ctx);
    private final TextView mediaArtist = new TextView(ctx);
    private final ImageView mediaArt = new ImageView(ctx);
    private final ImageView mediaPlay = new ImageView(ctx);
    private final List<VSlider> vsliders = new ArrayList<>();
    private boolean warned = false;

    // panel en dos etapas: 0 = cerrado, 1 = vista acotada (notificaciones), 2 = vista completa
    private final int slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
    private float progress = 0f;
    private ValueAnimator anim = null;
    private final FrameLayout backdrop = new FrameLayout(ctx);
    private final ImageView blurImg = new ImageView(ctx) {
        @Override public boolean hasOverlappingRendering() { return false; }
    };
    private final View scrim = new View(ctx) {
        @Override public boolean hasOverlappingRendering() { return false; }
    };
    private float blurK = 0f;
    private boolean winBlur = false;
    private int lastBlur = -1;
    public IntConsumer blurHook = null;
    private String cacheKey = "";
    private Bitmap cacheBmp = null;

    private final TextView cClock = new TextView(ctx);
    private final TextView cDate = new TextView(ctx);
    private final LinearLayout notifList = new LinearLayout(ctx);
    private final TextView clearBtn = new TextView(ctx);
    private MaxScroll notifScroll;
    private final android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
    private final boolean land = dm.widthPixels > dm.heightPixels;
    private List<Tile> extraCache = null;

    private List<Tile> extra() {
        if (extraCache == null) extraCache = thirdParty();
        return extraCache;
    }

    private List<String> work = null;
    private final int sheetW = (land ? Math.min(dm.widthPixels, dp(560)) : dm.widthPixels) * sk.sheetWidth / 100;
    private final LinearLayout sheet = new LinearLayout(ctx);
    private FrameLayout pager;
    private LinearLayout page0;
    private LinearLayout page1;
    private FrameLayout qs;
    private View qsCompact;
    private ScrollView qsFull;
    private View tabInd;
    private final List<TextView> tabLabels = new ArrayList<>();
    private float tabPos = 0f;
    private ValueAnimator tabAnim = null;
    private int hC = 0;
    private int hF = 0;
    private int stage1H = 0;
    private boolean hdrag = false;
    private float lX = 0f;
    private boolean onNotifs = false;
    private ScrollView editor = null;
    private final java.util.Set<String> expandedKeys = new java.util.HashSet<>();
    private final java.util.Set<String> expandedGroups = new java.util.HashSet<>();
    private float dX = 0f;
    private float dY = 0f;
    private float lY = 0f;
    private boolean drag = false;
    private VelocityTracker vt = null;
    private boolean closing = false;
    private long lastAct = SystemClock.uptimeMillis();

    public PanelView(Context c, Runnable onClose) {
        super(c);
        this.onClose = onClose;
        scrim.setBackgroundColor(scrimColor());
        backdrop.addView(blurImg, new LayoutParams(MP, MP));
        backdrop.addView(scrim, new LayoutParams(MP, MP));
        blurImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(backdrop, new LayoutParams(MP, MP));
        addView(sheet, new LayoutParams(sheetW, WC, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        buildFab();
        addView(fab, fabLp());
        sheet.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            stage1H = qs != null ? sheet.getHeight() - (qs.getLayoutParams().height - hC) : sheet.getHeight();
            setProgress(progress);
        });
        tabPos = (float) sk.defaultTab;
        if (sk.bgMode == BgMode.IMAGE) loadImage();
        buildShade();
        setProgress(0f);
        refresh();
    }

    public static final String[] TILE_KEYS = {"Internet", "Bluetooth", "Linterna", "Giro automático", "Ahorro de batería", "No molestar",
        "Modo avión", "Tema oscuro", "Ubicación", "Datos móviles", "NFC", "Sonido", "Brillo automático", "Luz nocturna", "Atenuar extra",
        "Invertir colores", "Ahorro de datos", "Pantalla activa", "Captura", "Bloquear", "Apagar", "Panel del sistema"};

    private static final String[] FAMILIES = {"sans-serif", "serif", "monospace", "casual", "sans-serif-condensed", "cursive",
        "sans-serif-thin", "sans-serif-medium"};

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            postDelayed(this, 1000);
        }
    };

    private String ss(String key, String def) {
        String v = Skin.getStr(ctx, key, def);
        return v == null || v.isEmpty() ? def : v;
    }

    private String labelOf(Tile t) {
        String v = Skin.getStr(ctx, "lbl_" + t.key, null);
        return v == null || v.isEmpty() ? t.label : v;
    }

    private int strokeCol() { return sk.strokeColor != 0 ? sk.strokeColor : withA(cText, 40); }

    private GradientDrawable cardBg() {
        GradientDrawable g = round(cCard, sk.cardR);
        if (sk.cardStroke > 0) g.setStroke(dp(sk.cardStroke), sk.cardStrokeColor != 0 ? sk.cardStrokeColor : withA(cText, 30));
        return g;
    }

    private android.view.animation.Interpolator interp() {
        switch (sk.animStyle) {
            case 1: return new android.view.animation.AccelerateDecelerateInterpolator();
            case 2: return new android.view.animation.OvershootInterpolator();
            case 3: return new android.view.animation.BounceInterpolator();
            case 4: return new android.view.animation.LinearInterpolator();
            case 5: return new android.view.animation.AnticipateOvershootInterpolator();
            default: return new DecelerateInterpolator();
        }
    }

    private void applyType(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (sk.textScale != 100) t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, t.getTextSize() * sk.textScale / 100f);
            if (t != cClock) {
                Typeface cur = t.getTypeface();
                int st = cur != null ? cur.getStyle() : 0;
                if (sk.textBold != 0) st |= Typeface.BOLD;
                if (sk.textItalic != 0) st |= Typeface.ITALIC;
                if (sk.fontFamily != 0 || st != 0) t.setTypeface(Typeface.create(FAMILIES[sk.fontFamily], st));
            }
            if (sk.letterSp != 0) t.setLetterSpacing(sk.letterSp / 100f);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyType(g.getChildAt(i));
        }
    }

    private void updateClock() {
        String tp = sk.clockFormat == 1 ? (sk.showSeconds != 0 ? "h:mm:ss a" : "h:mm a") : (sk.showSeconds != 0 ? "H:mm:ss" : "H:mm");
        cClock.setText(new SimpleDateFormat(tp, Locale.getDefault()).format(new Date()));
        String[] pats = {"EEEE d 'de' MMMM", "EEE d MMM", "dd/MM/yyyy", "yyyy-MM-dd"};
        int di = Math.max(0, Math.min(3, sk.dateFormat));
        cDate.setText(new SimpleDateFormat(pats[di], di < 2 ? new Locale("es") : Locale.getDefault()).format(new Date()));
    }

    private String longAction(Tile t) {
        switch (t.key) {
            case "Internet": return Settings.ACTION_WIFI_SETTINGS;
            case "Bluetooth": return Settings.ACTION_BLUETOOTH_SETTINGS;
            case "Giro automático": return Settings.ACTION_DISPLAY_SETTINGS;
            case "Ahorro de batería": return Settings.ACTION_BATTERY_SAVER_SETTINGS;
            case "No molestar": return "android.settings.ZEN_MODE_SETTINGS";
            case "Modo avión": return Settings.ACTION_AIRPLANE_MODE_SETTINGS;
            case "Tema oscuro": return Settings.ACTION_DISPLAY_SETTINGS;
            case "Ubicación": return Settings.ACTION_LOCATION_SOURCE_SETTINGS;
            case "Datos móviles": return Settings.ACTION_DATA_ROAMING_SETTINGS;
            case "NFC": return Settings.ACTION_NFC_SETTINGS;
            case "Sonido": return Settings.ACTION_SOUND_SETTINGS;
            case "Brillo automático": return Settings.ACTION_DISPLAY_SETTINGS;
            case "Luz nocturna": return Settings.ACTION_DISPLAY_SETTINGS;
            case "Atenuar extra": return Settings.ACTION_ACCESSIBILITY_SETTINGS;
            case "Invertir colores": return Settings.ACTION_ACCESSIBILITY_SETTINGS;
            case "Ahorro de datos": return "android.settings.DATA_USAGE_SETTINGS";
            case "Pantalla activa": return Settings.ACTION_DISPLAY_SETTINGS;
            default: return null;
        }
    }

    public void openEditor() { showEditor(); }

    private void launch(Intent i) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            onClose.run();
        } catch (Exception e) {
            Toast.makeText(ctx, "No se pudo abrir el ajuste", Toast.LENGTH_SHORT).show();
        }
    }

    private void longPress(Tile t) {
        if (sk.haptic != 0) performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        String a = longAction(t);
        if (a != null) { launch(new Intent(a)); return; }
        if (t.key.startsWith("tp:") && t.key.indexOf('/') > 3) {
            String pkg = t.key.substring(3, t.key.indexOf('/'));
            launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        }
    }

    private final TextView fab = new TextView(ctx);
    private MaxScroll cScroll0 = null;
    private MaxScroll cScroll1 = null;
    private java.util.Map<String, Tile> tmapCache = new java.util.HashMap<>();
    private android.appwidget.AppWidgetHost widgetHost = null;

    public static final int[] TILE_ICONS = {R.drawable.ic_wifi, R.drawable.ic_bt, R.drawable.ic_torch, R.drawable.ic_rotate,
        R.drawable.ic_battery, R.drawable.ic_dnd, R.drawable.ic_airplane, R.drawable.ic_dark, R.drawable.ic_location,
        R.drawable.ic_data, R.drawable.ic_nfc, R.drawable.ic_sound, R.drawable.ic_brightness, R.drawable.ic_night,
        R.drawable.ic_dim, R.drawable.ic_invert, R.drawable.ic_datasaver, R.drawable.ic_screen, R.drawable.ic_screenshot,
        R.drawable.ic_padlock, R.drawable.ic_power, R.drawable.ic_lock};

    private android.appwidget.AppWidgetHost widgetHost() {
        if (widgetHost == null) widgetHost = new android.appwidget.AppWidgetHost(ctx.getApplicationContext(), 1337);
        return widgetHost;
    }

    private void roundClip(View v) {
        final int r = dp(sk.cardR);
        v.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View vv, android.graphics.Outline o) {
                o.setRoundRect(0, 0, vv.getWidth(), vv.getHeight(), (float) r);
            }
        });
        v.setClipToOutline(true);
    }

    private View placeholder(String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(12f);
        t.setTextColor(cSub);
        t.setGravity(Gravity.CENTER);
        t.setBackground(cardBg());
        return t;
    }

    private void setupClear() {
        clearBtn.setOnClickListener(v -> {
            MediaListener ml = MediaListener.instance;
            if (ml != null) ml.cancelAllNotifications();
            postDelayed(() -> refreshNotes(), 250);
        });
        clearBtn.setText(ss("txtClear", "Borrar todo"));
        clearBtn.setTextSize(13f);
        clearBtn.setTextColor(cText);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        clearBtn.setBackground(round(cCard, 20));
    }

    private View mediaView(Elem e) {
        Uri u = Uri.parse(e.a);
        if (e.a.isEmpty()) return placeholder("Sin archivo");
        View v;
        try {
            if (Media.isVideo(ctx, u)) {
                v = new VideoCard(ctx, u, e.b == 1);
            } else {
                ImageView iv = new ImageView(ctx);
                iv.setScaleType(e.b == 1 ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
                Media.loadImage(iv, ctx, u);
                v = iv;
            }
        } catch (Throwable t) {
            return placeholder("No se pudo abrir el archivo");
        }
        roundClip(v);
        return v;
    }

    private View widgetView(Elem e, int wPx, int hPx) {
        try {
            int id = Integer.parseInt(e.a);
            android.appwidget.AppWidgetProviderInfo info = android.appwidget.AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id);
            if (info == null) return placeholder("Widget no disponible");
            android.appwidget.AppWidgetHostView hv = widgetHost().createView(ctx, id, info);
            float dd = ctx.getResources().getDisplayMetrics().density;
            hv.updateAppWidgetSize(new android.os.Bundle(), (int) (wPx / dd), (int) (hPx / dd), (int) (wPx / dd), (int) (hPx / dd));
            roundClip(hv);
            return hv;
        } catch (Throwable t) {
            return placeholder("Widget no disponible");
        }
    }

    private View elemView(Elem e, int wPx, int hPx) {
        switch (e.type) {
            case "tile": {
                Tile t = tmapCache.get(e.a);
                if (t == null) return null;
                TileView tv = tileView(t, wPx, hPx);
                holders.add(tv);
                return tv.box;
            }
            case "clock": return buildHeader();
            case "media": return mediaCard();
            case "notifs": {
                free(notifList);
                notifList.setOrientation(LinearLayout.VERTICAL);
                notifScroll = new MaxScroll(ctx, Math.max(dp(60), hPx - dp(48)));
                notifScroll.setVerticalScrollBarEnabled(false);
                notifScroll.setOverScrollMode(OVER_SCROLL_NEVER);
                notifScroll.addView(notifList);
                LinearLayout box = vcol();
                box.addView(notifScroll, new LinearLayout.LayoutParams(MP, WC));
                setupClear();
                addClearTo(box);
                return box;
            }
            case "bright":
                if (wPx > hPx * 1.3f) {
                    return sliderRow(R.drawable.ic_brightness, 255,
                        () -> Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128), v -> setBrightness(v));
                }
                return vBright();
            case "volume": {
                if (wPx > hPx * 1.3f) {
                    return sliderRow(R.drawable.ic_volume, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                        () -> audio.getStreamVolume(AudioManager.STREAM_MUSIC), v -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0));
                }
                return vVol();
            }
            case "image":
            case "video":
                return mediaView(e);
            case "app":
                return appView(e, wPx, hPx);
            case "widget":
                return widgetView(e, wPx, hPx);
            case "text": {
                TextView t = new TextView(ctx);
                t.setText(e.a);
                t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Math.max((float) dp(10), hPx * 0.4f));
                t.setTextColor(e.b != 0 ? e.b : cText);
                t.setGravity(Gravity.CENTER);
                t.setMaxLines(3);
                return t;
            }
            default:
                return null;
        }
    }

    private LinearLayout canvasPage(List<Elem> all, int pg, int cell, int innerW, int padH) {
        int g = dp(sk.gap) / 2;
        int rows = 2;
        for (Elem e : all) {
            if (e.page == pg) rows = Math.max(rows, e.y + e.h);
        }
        FrameLayout fl = new FrameLayout(ctx);
        for (Elem e : all) {
            if (e.page != pg) continue;
            int wPx = Math.max(1, e.w * cell - 2 * g);
            int hPx = Math.max(1, e.h * cell - 2 * g);
            View v = elemView(e, wPx, hPx);
            if (v == null) continue;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(wPx, hPx);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = e.x * cell + g;
            lp.topMargin = e.y * cell + g;
            fl.addView(v, lp);
        }
        MaxScroll sc = new MaxScroll(ctx, (int) (dm.heightPixels * 0.8f));
        sc.setVerticalScrollBarEnabled(false);
        sc.setOverScrollMode(OVER_SCROLL_NEVER);
        sc.addView(fl, new FrameLayout.LayoutParams(innerW, rows * cell));
        if (pg == 0) cScroll0 = sc; else cScroll1 = sc;
        LinearLayout page = vcol();
        page.setPadding(padH, dp(4), padH, dp(6));
        page.addView(sc, new LinearLayout.LayoutParams(innerW, WC));
        return page;
    }

    private void buildCanvasPages(int innerW) {
        int padH = dp(sk.sidePad);
        int cols = Math.max(2, sk.canvasCols);
        int cell = innerW / cols;
        List<Elem> all = Elem.load(ctx);
        if (all.isEmpty()) all = Elem.example(cols);
        tmapCache = new java.util.HashMap<>();
        for (Tile t : allTiles()) tmapCache.put(t.key, t);
        boolean hasP1 = false;
        for (Elem e : all) {
            if (e.page == 1) hasP1 = true;
        }
        page0 = canvasPage(all, 0, cell, innerW, padH);
        page1 = canvasPage(all, 1, cell, innerW, padH);
        if (!hasP1) {
            tabPos = 0f;
            sheet.getChildAt(0).setVisibility(View.GONE);
        }
        pager = new FrameLayout(ctx);
        pager.addView(page0, new FrameLayout.LayoutParams(sheetW, WC));
        pager.addView(page1, new FrameLayout.LayoutParams(sheetW, WC));
        sheet.addView(pager, new LinearLayout.LayoutParams(sheetW, WC));
        if (sk.showGrab != 0) sheet.addView(grabBar(withA(cText, 35)), new LinearLayout.LayoutParams(MP, dp(Math.max(28, sk.grabH + 16))));
        applyTab();
        applyType(sheet);
        refreshNotes();
    }

    private boolean inNightHours() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        int from = sk.autoFrom;
        int to = sk.autoTo;
        return from <= to ? (hour >= from && hour < to) : (hour >= from || hour < to);
    }

    private boolean lowBat() {
        try {
            android.os.BatteryManager bm = (android.os.BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            return bm != null && bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) < 20;
        } catch (Exception e) { return false; }
    }

    private int tileOn(Tile t) {
        int c = Skin.get(ctx, "tOn_" + t.key);
        return c != 0 ? withA(c, sk.onAlpha) : cOn;
    }

    private int tileOff(Tile t) {
        int c = Skin.get(ctx, "tOff_" + t.key);
        return c != 0 ? withA(c, sk.offAlpha) : cOff;
    }

    private static int pi(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private List<Tile> customTileList() {
        List<Tile> out = new ArrayList<>();
        for (String[] f : Skin.customTiles(ctx)) {
            final String id = f[0];
            final int type = pi(f[1]);
            final String target = f[3];
            final boolean sw = type == 3;
            Tile t = new Tile(f[2].isEmpty() ? "Acceso" : f[2], 0, () -> sw && prefs.getBoolean("ctstate_" + id, false),
                sw ? "Activado" : "", sw ? "Desactivado" : "Tocar", () -> runCustom(id, type, target));
            t.key = "ct:" + id;
            out.add(t);
        }
        return out;
    }

    private void runCustom(String id, int type, String target) {
        switch (type) {
            case 0: {
                if (GameWatchdog.route(ctx, target.trim())) break;
                Intent li = ctx.getPackageManager().getLaunchIntentForPackage(target.trim());
                if (li != null) launch(li);
                else Toast.makeText(ctx, "No se encontró la app", Toast.LENGTH_SHORT).show();
                break;
            }
            case 1:
                launch(new Intent(Intent.ACTION_VIEW, Uri.parse(target.trim())));
                break;
            case 2:
                if (Privileged.available()) Privileged.run(target, () -> postDelayed(() -> refresh(), 400));
                else Toast.makeText(ctx, "Necesita Shizuku", Toast.LENGTH_SHORT).show();
                break;
            default: {
                if (!Privileged.available()) { Toast.makeText(ctx, "Necesita Shizuku", Toast.LENGTH_SHORT).show(); break; }
                boolean cur = prefs.getBoolean("ctstate_" + id, false);
                String[] pr = target.split("&&&", 2);
                String cmd = cur ? (pr.length > 1 ? pr[1] : "") : pr[0];
                if (!cmd.trim().isEmpty()) Privileged.run(cmd.trim());
                prefs.edit().putBoolean("ctstate_" + id, !cur).apply();
                refresh();
                break;
            }
        }
    }

    private void tapAnim(final View v) {
        final float b = v.getScaleX();
        switch (sk.tapAnim) {
            case 1:
                v.animate().scaleX(b * 0.85f).scaleY(b * 0.85f).setDuration(80).withEndAction(() ->
                    v.animate().scaleX(b).scaleY(b).setDuration(160).setInterpolator(new android.view.animation.OvershootInterpolator()).start()).start();
                break;
            case 2: v.animate().rotationBy(360f).setDuration(350).start(); break;
            case 3: v.animate().rotationYBy(360f).setDuration(400).start(); break;
            case 4: android.animation.ObjectAnimator.ofFloat(v, "translationX", 0f, -10f, 10f, -8f, 8f, -4f, 4f, 0f).setDuration(300).start(); break;
            case 5:
                v.animate().scaleX(b * 1.18f).scaleY(b * 1.18f).setDuration(110).withEndAction(() ->
                    v.animate().scaleX(b).scaleY(b).setDuration(180).start()).start();
                break;
            default: break;
        }
    }

    private void playEntry() {
        if (sk.entryAnim == 0) return;
        for (int i = 0; i < holders.size(); i++) {
            View bx = holders.get(i).box;
            bx.animate().cancel();
            bx.setTranslationX(0f);
            bx.setTranslationY(0f);
            paintTile(holders.get(i));
            bx.setAlpha(0f);
            android.view.ViewPropertyAnimator a = bx.animate().alpha(1f).setStartDelay(i * 25L).setDuration(260);
            if (sk.entryAnim == 1) { bx.setTranslationY((float) dp(24)); a.translationY(0f); }
            else if (sk.entryAnim == 2) { float s0 = bx.getScaleX(); bx.setScaleX(0.3f); bx.setScaleY(0.3f); a.scaleX(s0).scaleY(s0); }
            else { bx.setTranslationX((float) -dp(40)); a.translationX(0f); }
            a.start();
        }
    }

    private LayoutParams fabLp() {
        LayoutParams lp = new LayoutParams(dp(sk.fabSize), dp(sk.fabSize), Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, dp(24), dp(48));
        return lp;
    }

    private void applyFab() {
        float a = sk.fabMode == 2 ? 1f : Math.max(0f, (tabPos - 0.5f) * 2f);
        fab.setVisibility(sk.fabMode != 0 && editor == null && a > 0f ? View.VISIBLE : View.GONE);
        fab.setAlpha(a * Math.min(progress, 1f));
    }

    private void buildFab() {
        fab.setText(ss("txtFab", "✎"));
        fab.setTextSize(22f);
        fab.setTextColor(cOnAccent);
        fab.setGravity(Gravity.CENTER);
        GradientDrawable fg = new GradientDrawable();
        fg.setShape(GradientDrawable.OVAL);
        fg.setColor(cAccent);
        fab.setBackground(fg);
        fab.setElevation((float) dp(6));
        fab.setTranslationX((float) Skin.get(ctx, "fabDX"));
        fab.setTranslationY((float) Skin.get(ctx, "fabDY"));
        fab.setOnTouchListener(new View.OnTouchListener() {
            float x0 = 0f;
            float y0 = 0f;
            float tx0 = 0f;
            float ty0 = 0f;
            boolean moved = false;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                        x0 = e.getRawX(); y0 = e.getRawY(); tx0 = v.getTranslationX(); ty0 = v.getTranslationY(); moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - x0) > slop || Math.abs(e.getRawY() - y0) > slop) moved = true;
                        if (moved) { v.setTranslationX(tx0 + e.getRawX() - x0); v.setTranslationY(ty0 + e.getRawY() - y0); }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            Skin.set(ctx, "fabDX", (int) v.getTranslationX());
                            Skin.set(ctx, "fabDY", (int) v.getTranslationY());
                        } else showEditor();
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    // ======================= helpers de vistas =======================

    private LinearLayout hrow() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout vcol() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius((float) dp(r));
        return g;
    }

    private void setBrightness(int v) {
        if (Settings.System.canWrite(ctx)) {
            Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, v));
        } else if (!warned) { warned = true; open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true); }
    }

    // ======================= accesos (botones) =======================

    /** Arma un acceso con la forma, la disposición y los tamaños del Skin. */
    private TileView tileView(final Tile t, int cellW) { return tileView(t, cellW, 0); }

    private TileView tileView(final Tile t, int cellW, int forceH) {
        boolean pill = sk.layout == TileLayout.PILL;
        int sizePx = forceH > 0 ? (sk.layout == TileLayout.LABEL ? forceH - dp(sk.labelSize + 10) : forceH) : dp(sk.size);
        Shaped shape = new Shaped(sk.shape, cOff, sk.round);
        if (sk.tileStroke > 0) shape.setStroke(strokeCol(), dp(sk.tileStroke));
        LinearLayout box = new LinearLayout(ctx);
        box.setClickable(true);
        box.setRotation((float) sk.tileRot);
        box.setOnClickListener(v -> {
            if (sk.haptic != 0) v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            tapAnim(v);
            t.tap.run();
        });
        box.setOnLongClickListener(v -> { longPress(t); return true; });
        int d = pill ? (int) (sizePx * 0.72f) : Math.min(sizePx, cellW);
        int pad = Math.max((int) (d * (100 - sk.iconScale) / 200f), 0);
        ImageView img = new ImageView(ctx);
        setIcon(img, t);
        img.setBackground(shape);
        img.setPadding(pad, pad, pad, pad);
        TextView title = null;
        TextView sub = null;
        if (pill) {
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            int vpad = (int) ((sizePx - d) / 2f);
            box.setPadding(vpad, vpad, dp(12), vpad);
            box.addView(img, new LinearLayout.LayoutParams(d, d));
            LinearLayout col = vcol();
            col.setPadding(dp(10), 0, 0, 0);
            title = new TextView(ctx);
            title.setText(sk.labelCaps != 0 ? labelOf(t).toUpperCase() : labelOf(t));
            title.setTextSize((float) sk.labelSize);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(title);
            if (sk.showSub) {
                sub = new TextView(ctx);
                sub.setTextSize((float) Math.max(8, sk.labelSize - 2));
                sub.setMaxLines(1);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                col.addView(sub);
            }
            box.addView(col, new LinearLayout.LayoutParams(0, WC, 1f));
        } else {
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(forceH > 0 ? Gravity.CENTER : Gravity.CENTER_HORIZONTAL);
            box.addView(img, new LinearLayout.LayoutParams(d, d));
            if (sk.layout == TileLayout.LABEL) {
                title = new TextView(ctx);
                title.setText(sk.labelCaps != 0 ? labelOf(t).toUpperCase() : labelOf(t));
                title.setTextSize((float) sk.labelSize);
                title.setMaxLines(1);
                title.setGravity(Gravity.CENTER);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(MP, WC);
                tlp.topMargin = dp(4);
                box.addView(title, tlp);
            }
        }
        return new TileView(t, box, shape, img, title, sub, pill);
    }

    private void paintTile(TileView h) {
        boolean on;
        try { on = h.t.on.getAsBoolean(); } catch (Exception e) { on = false; }
        int fg = on ? (sk.onFg != 0 ? sk.onFg : cOnAccent) : cText;
        if (h.pill) {
            GradientDrawable pb = round(on ? tileOn(h.t) : tileOff(h.t), sk.size / 2 * sk.pillRound / 100);
            if (sk.tileStroke > 0) pb.setStroke(dp(sk.tileStroke), strokeCol());
            h.box.setBackground(pb);
            h.shape.setFill(on ? withA(cOnAccent, 22) : withA(cText, 14));
            if (h.title != null) h.title.setTextColor(sk.labelColor != 0 ? sk.labelColor : fg);
            if (h.sub != null) {
                h.sub.setTextColor(sk.subColor != 0 ? sk.subColor : (on ? cOnAccent : cSub));
                String s = h.t.subFn != null ? h.t.subFn.get() : null;
                h.sub.setText(s != null ? s : on ? h.t.onText : h.t.offText);
            }
        } else {
            h.shape.setFill(on ? tileOn(h.t) : tileOff(h.t));
            if (h.title != null) h.title.setTextColor(sk.labelColor != 0 ? sk.labelColor : cText);
        }
        h.img.setImageTintList(tint(fg));
        float sc = (on ? sk.onScale : sk.offScale) / 100f;
        h.box.setScaleX(sc);
        h.box.setScaleY(sc);
    }

    /** Cuadrícula de accesos del ancho [w]. */
    private View grid(List<Tile> list, int w) {
        int cols = Math.max(1, sk.layout == TileLayout.PILL ? Math.min(sk.cols, 3) : sk.cols);
        int m = dp(sk.gap) / 2;
        int inner = Math.max(dp(24), w / cols - 2 * m);
        LinearLayout col = vcol();
        LinearLayout line = hrow();
        int used = 0;
        for (Tile t : list) {
            int span = Math.max(1, Math.min(cols, Skin.get(ctx, "tSpan_" + t.key)));
            if (used > 0 && used + span > cols) { col.addView(line); line = hrow(); used = 0; }
            int cw = inner * span + 2 * m * (span - 1);
            TileView tv = tileView(t, cw);
            holders.add(tv);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cw, WC);
            lp.setMargins(m, m, m, m);
            line.addView(tv.box, lp);
            used += span;
        }
        if (used > 0) col.addView(line);
        return col;
    }

    // ======================= brillo y volumen =======================

    private View sliderRow(int icon, int max, IntSupplier get, final IntConsumer set) {
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.setBackground(cardBg());
        ImageView ic = new ImageView(ctx);
        ic.setImageResource(icon);
        ic.setImageTintList(tint(cSub));
        row.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));
        SeekBar sb = new SeekBar(ctx);
        sb.setMax(max);
        sb.setProgress(get.getAsInt());
        sb.setProgressTintList(tint(cAccent));
        sb.setThumbTintList(tint(cAccent));
        sb.setProgressBackgroundTintList(tint(cTrack));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { if (fromUser) set.accept(p); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        slp.leftMargin = dp(10);
        row.addView(sb, slp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MP, dp(sk.sliderThick));
        rlp.topMargin = dp(10);
        row.setLayoutParams(rlp);
        return row;
    }

    private View vBright() {
        VSlider s = new VSlider(ctx, R.drawable.ic_brightness, 255, cOff, cAccent,
            () -> Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128),
            v -> setBrightness(v),
            cOnAccent, cText);
        s.setCorner(sk.vCorner / 10f);
        vsliders.add(s);
        return s;
    }

    private View vVol() {
        VSlider s = new VSlider(ctx, R.drawable.ic_volume, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), cOff, cAccent,
            () -> audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            v -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0),
            cOnAccent, cText);
        s.setCorner(sk.vCorner / 10f);
        vsliders.add(s);
        return s;
    }

    // ======================= multimedia =======================

    private MediaController controller() {
        try {
            MediaSessionManager msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> l = msm.getActiveSessions(new ComponentName(ctx, MediaListener.class));
            return l.isEmpty() ? null : l.get(0);
        } catch (Exception e) { return null; }
    }

    private void mediaKey(int code) {
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        postDelayed(() -> refreshMedia(), 350);
    }

    private void refreshMedia() {
        MediaController c = controller();
        MediaMetadata md = c != null ? c.getMetadata() : null;
        String title = md != null ? md.getString(MediaMetadata.METADATA_KEY_TITLE) : null;
        mediaTitle.setText(title != null ? title : "Sin reproducción");
        String artist = md != null ? md.getString(MediaMetadata.METADATA_KEY_ARTIST) : null;
        mediaArtist.setText(artist != null ? artist : "");
        Bitmap bmp = null;
        if (md != null) {
            bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        if (bmp != null) {
            mediaArt.setPadding(0, 0, 0, 0); mediaArt.setImageTintList(null); mediaArt.setImageBitmap(bmp);
        } else {
            mediaArt.setPadding(dp(9), dp(9), dp(9), dp(9));
            mediaArt.setImageTintList(tint(cText));
            mediaArt.setImageResource(R.drawable.ic_music);
        }
        PlaybackState ps = c != null ? c.getPlaybackState() : null;
        mediaPlay.setImageResource(ps != null && ps.getState() == PlaybackState.STATE_PLAYING ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private ImageView cbtn(int ic, final int code) {
        ImageView v = new ImageView(ctx);
        v.setImageResource(ic);
        v.setImageTintList(tint(cText));
        v.setPadding(dp(8), dp(8), dp(8), dp(8));
        v.setOnClickListener(x -> mediaKey(code));
        return v;
    }

    private View mediaCard() {
        LinearLayout box = vcol();
        box.setPadding(dp(12), dp(10), dp(12), dp(6));
        box.setBackground(cardBg());
        mediaArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaArt.setBackground(round(withA(cText, 16), 10));
        mediaArt.setClipToOutline(true);
        mediaTitle.setTextSize(12f); mediaTitle.setTextColor(cText); mediaTitle.setMaxLines(1);
        mediaTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mediaArtist.setTextSize(10f); mediaArtist.setTextColor(cSub); mediaArtist.setMaxLines(1);
        mediaArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout top = hrow();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(free(mediaArt), new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout col = vcol();
        col.setPadding(dp(8), 0, 0, 0);
        col.addView(free(mediaTitle)); col.addView(free(mediaArtist));
        top.addView(col, new LinearLayout.LayoutParams(0, WC, 1f));
        box.addView(top);
        box.addView(new View(ctx), new LinearLayout.LayoutParams(1, 0, 1f));
        LinearLayout ctrl = hrow();
        ctrl.setGravity(Gravity.CENTER);
        ctrl.addView(cbtn(R.drawable.ic_prev, KeyEvent.KEYCODE_MEDIA_PREVIOUS), new LinearLayout.LayoutParams(0, dp(40), 1f));
        mediaPlay.setImageResource(R.drawable.ic_play);
        mediaPlay.setImageTintList(tint(cText));
        mediaPlay.setPadding(dp(8), dp(8), dp(8), dp(8));
        mediaPlay.setOnClickListener(x -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
        ctrl.addView(free(mediaPlay), new LinearLayout.LayoutParams(0, dp(40), 1f));
        ctrl.addView(cbtn(R.drawable.ic_next, KeyEvent.KEYCODE_MEDIA_NEXT), new LinearLayout.LayoutParams(0, dp(40), 1f));
        box.addView(ctrl, new LinearLayout.LayoutParams(MP, WC));
        return box;
    }

    // ======================= refresco =======================

    public void refresh() {
        updateClock();
        for (TileView h : holders) paintTile(h);
        refreshMedia();
        for (VSlider s : vsliders) s.sync();
    }

    // ======================= PANEL EN DOS ETAPAS =======================

    private static <T extends View> T free(T v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        return v;
    }

    private int statusBarH() {
        int id = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? ctx.getResources().getDimensionPixelSize(id) : dp(24);
    }

    private void setIcon(ImageView img, Tile t) {
        String gl = Skin.getStr(ctx, "glyph_" + t.key, null);
        if ((gl == null || gl.isEmpty()) && t.key.startsWith("ct:")) gl = "★";
        if (gl != null && !gl.isEmpty()) { img.setImageDrawable(new GlyphDrawable(gl)); return; }
        Drawable d = t.iconDrawable;
        if (d != null) {
            Drawable.ConstantState cs = d.getConstantState();
            img.setImageDrawable(cs != null ? cs.newDrawable() : d);
        } else img.setImageResource(t.icon);
    }

    /** 0 = cerrado, 1 = hoja con notificaciones, 2 = accesos expandidos (empujan las notificaciones hacia abajo). */
    public void setProgress(float v) {
        progress = Math.max(0f, Math.min(sk.layoutMode != 0 ? 1f : 2f, v));
        float p1 = Math.min(progress, 1f);
        float p2 = Math.max(progress - 1f, 0f);
        float sh = (float) dm.heightPixels;
        float h1 = stage1H > 0 ? (float) stage1H : sh;
        sheet.setTranslationY(-(1f - p1) * h1);
        if (qs != null) {
            int h = (int) (hC + (hF - hC) * p2);
            ViewGroup.LayoutParams lp = qs.getLayoutParams();
            if (lp.height != h) { lp.height = h; qs.setLayoutParams(lp); }
            qsCompact.setAlpha(1f - p2);
            qsCompact.setVisibility(p2 < 1f ? View.VISIBLE : View.INVISIBLE);
            qsFull.setAlpha(p2);
            qsFull.setVisibility(p2 > 0f ? View.VISIBLE : View.INVISIBLE);
        }
        blurImg.setAlpha(p1 * blurK);
        applyFab();
        scrim.setAlpha(p1);
        if (winBlur) {
            int r = p1 <= 0f ? 0 : (int) (Math.ceil(p1 * 4f) * (sk.blurR * 10f / 4f));
            if (r != lastBlur) {
                lastBlur = r;
                if (blurHook != null) blurHook.accept(r);
            }
        }
    }

    public void animateTo(float target) { animateTo(target, null); }

    public void animateTo(float target, final Runnable end) {
        lastAct = SystemClock.uptimeMillis();
        if (anim != null) anim.cancel();
        if (target >= 1f && progress < 0.9f) playEntry();
        final boolean[] cancelled = {false};
        ValueAnimator a = ValueAnimator.ofFloat(progress, target);
        a.setDuration((long) sk.animDur);
        a.setInterpolator(interp());
        a.addUpdateListener(an -> setProgress((Float) an.getAnimatedValue()));
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationCancel(Animator an) { cancelled[0] = true; }
            @Override public void onAnimationEnd(Animator an) { if (!cancelled[0] && end != null) end.run(); }
        });
        anim = a;
        a.start();
    }

    /** Al soltar: elige etapa según velocidad o cercanía. Cerrar delega en onClose (que anima hasta 0). */
    private void settle(float vy) {
        float t;
        if (vy > 1200f) t = progress < 1f ? 1f : 2f;
        else if (vy < -1200f) t = progress > 1f ? 1f : 0f;
        else if (progress < 0.5f) t = 0f;
        else if (progress < 1.5f) t = 1f;
        else t = 2f;
        if (t == 0f) onClose.run(); else animateTo(t);
    }

    public void dragFromHandle(float dy) {
        if (closing) return;
        lastAct = SystemClock.uptimeMillis();
        if (anim != null) anim.cancel();
        float hc = stage1H > 0 ? (float) stage1H : dm.heightPixels * 0.5f;
        setProgress(Math.max(0f, Math.min(1f, dy / hc)));
    }

    public void release(float vy) { settle(vy); }

    // ---- guardián: lo usa el servicio para cerrar sin depender de la animación ----

    /** Marca el panel como "en cierre": deja de procesar toques. */
    public void beginClose() { closing = true; drag = false; hdrag = false; }

    public void stopAnim() {
        if (anim != null) anim.cancel();
        if (tabAnim != null) tabAnim.cancel();
    }

    /** Abierto pero sin contenido visible ni actividad: es el estado que dejaba la pantalla "colgada". */
    public boolean isStuck() {
        return !closing && progress < 0.02f && !(anim != null && anim.isRunning()) && SystemClock.uptimeMillis() - lastAct > 1500;
    }

    /** Para la vista previa del constructor: etapa 1 (acotada) o 2 (completa) y pestaña 0/1. */
    public void previewState(int stage, int tab) {
        tabPos = (float) tab; applyTab();
        setProgress(stage <= 1 ? 1f : 2f);
    }

    private void dragStep(float delta) {
        float sh = (float) dm.heightPixels;
        float h1 = stage1H > 0 ? (float) stage1H : sh;
        float h2 = Math.max((float) (hF - hC), (float) dp(160));
        float span = (progress < 1f || (progress == 1f && delta < 0f)) ? h1 : h2;
        setProgress(progress + delta / span);
    }

    private boolean blockedByScroll(boolean down) {
        if (sk.layoutMode != 0) {
            View cs = tabPos > 0.5f ? cScroll1 : cScroll0;
            boolean inner = notifScroll != null && tabPos <= 0.5f && onNotifs && notifScroll.canScrollVertically(down ? -1 : 1);
            return inner || (cs != null && cs.canScrollVertically(down ? -1 : 1));
        }
        if (tabPos > 0.5f) return false;
        View s = progress >= 1.5f ? qsFull : notifScroll;
        return s != null && s.canScrollVertically(down ? -1 : 1);
    }

    private boolean touchOnNotifs(MotionEvent e) {
        if (tabPos >= 0.5f || notifScroll == null) return false;
        int[] loc = new int[2];
        notifScroll.getLocationOnScreen(loc);
        return e.getRawX() >= loc[0] && e.getRawX() <= loc[0] + notifScroll.getWidth() &&
            e.getRawY() >= loc[1] && e.getRawY() <= loc[1] + notifScroll.getHeight();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        // Si el servicio ya mandó cerrar este panel, no toca nada: así no se cancela la animación de salida.
        if (closing || editor != null) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastAct = SystemClock.uptimeMillis();
                dX = e.getRawX(); dY = e.getRawY(); lY = e.getRawY(); lX = e.getRawX(); drag = false; hdrag = false;
                onNotifs = touchOnNotifs(e);
                if (anim != null) anim.cancel();
                if (tabAnim != null) tabAnim.cancel();
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain(); vt.addMovement(e);
                break;
            case MotionEvent.ACTION_MOVE: {
                if (vt != null) vt.addMovement(e);
                float dy = e.getRawY() - dY;
                float dx = e.getRawX() - dX;
                if (!drag && !hdrag) {
                    if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) * 1.2f && !blockedByScroll(dy > 0f)) {
                        drag = true; lY = e.getRawY();
                        return true;
                    }
                    // Pestañas: deslizar a izquierda/derecha (no sobre las notificaciones, que se descartan así)
                    if (!onNotifs && progress >= 0.9f && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        hdrag = true; lX = e.getRawX();
                        return true;
                    }
                }
                break;
            }
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (closing) return false;
        if (editor != null) return true;
        lastAct = SystemClock.uptimeMillis();
        if (vt != null) vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (!drag && !hdrag && Math.abs(e.getRawY() - dY) > slop) { drag = true; lY = e.getRawY(); }
                if (drag) { dragStep(e.getRawY() - lY); lY = e.getRawY(); }
                if (hdrag) { tabStep(-(e.getRawX() - lX) / sheetW); lX = e.getRawX(); }
                break;
            case MotionEvent.ACTION_UP: {
                float vy = 0f;
                float vx = 0f;
                if (vt != null) {
                    vt.computeCurrentVelocity(1000);
                    vy = vt.getYVelocity();
                    vx = vt.getXVelocity();
                }
                if (hdrag) settleTab(vx);
                else if (drag) settle(vy);
                else if (sk.closeTap != 0 && Math.abs(e.getRawY() - dY) < slop && Math.abs(e.getRawX() - dX) < slop) onClose.run();
                drag = false; hdrag = false;
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                if (hdrag) settleTab(0f); else if (drag) settle(0f);
                drag = false; hdrag = false;
                break;
            default:
                break;
        }
        return true;
    }

    // La pantalla giró: el panel se armó para otra orientación, así que se cierra.
    @Override
    protected void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        onClose.run();
    }

    // ---- fondo detrás del panel: sólido / transparente / difuminado / imagen ----

    private int scrimColor() {
        return sk.bgMode == BgMode.TRANSPARENT ? 0x00000000 : withA(sk.bgColor, sk.dim);
    }

    private void loadImage() {
        java.io.File f = Skin.imageFile(ctx);
        if (!f.exists()) { scrim.setBackgroundColor(withA(sk.bgColor, Math.max(sk.dim, 60))); return; }
        String key = f.lastModified() + ":" + sk.imgBlur;
        Bitmap bmp = cacheKey.equals(key) ? cacheBmp : null;
        if (bmp == null) {
            try {
                Bitmap src = BitmapFactory.decodeFile(f.getPath());
                if (src != null && sk.imgBlur > 0) {
                    int w = 160;
                    int h = Math.max(1, src.getHeight() * w / src.getWidth());
                    Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
                    if (small != src) src.recycle();
                    bmp = Blur.apply(small, sk.imgBlur, 3);
                } else bmp = src;
            } catch (Throwable e) { bmp = null; }
            cacheKey = key; cacheBmp = bmp;
        }
        if (bmp != null) { blurImg.setImageBitmap(bmp); blurK = 1f; }
    }

    /** Captura difuminada de lo que hay detrás (solo en modo "Difuminado"). */
    public void setBackdrop(Bitmap b) {
        if (sk.bgMode != BgMode.BLUR) return;
        blurImg.setImageBitmap(b);
        ValueAnimator a = ValueAnimator.ofFloat(blurK, 1f);
        a.setDuration(160);
        a.addUpdateListener(an -> { blurK = (Float) an.getAnimatedValue(); setProgress(progress); });
        a.start();
    }

    public void useWindowBlur() {
        if (sk.bgMode != BgMode.BLUR) return;
        winBlur = true;
        setProgress(progress);
    }

    /** Sin ninguna forma de difuminar: oscurece más para que el texto siga leyéndose. */
    public void noBlur() {
        if (sk.bgMode == BgMode.BLUR) scrim.setBackgroundColor(withA(sk.bgColor, Math.max(sk.dim, 60)));
    }

    // ======================= ACCESOS: orden, activar/quitar, apps de terceros =======================

    private List<Tile> allTiles() {
        List<Tile> l = new ArrayList<>(tiles);
        l.addAll(customTileList());
        l.addAll(extra());
        return l;
    }

    private List<String> defaultOrder() {
        List<String> l = new ArrayList<>();
        for (Tile t : tiles) l.add(t.key);
        return l;
    }

    private List<String> enabledKeys() {
        String raw = prefs.getString("tiles_custom", null);
        if (raw == null) raw = prefs.getString("tiles_aosp", null);
        if (raw == null) return defaultOrder();
        List<String> all = new ArrayList<>();
        for (Tile t : allTiles()) all.add(t.key);
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\n")) {
            if (!s.isEmpty() && all.contains(s)) out.add(s);
        }
        return out;
    }

    private List<Tile> shown() {
        java.util.Map<String, Tile> m = new java.util.HashMap<>();
        for (Tile t : allTiles()) m.put(t.key, t);
        List<Tile> out = new ArrayList<>();
        for (String k : enabledKeys()) {
            Tile t = m.get(k);
            if (t != null) out.add(t);
        }
        return out;
    }

    /** Accesos de apps (TileService). Solo se pueden accionar vía Shizuku con `cmd statusbar click-tile`. */
    private static List<String[]> tpInfo = null;
    private static long tpTime = 0L;
    private static final java.util.Map<String, Drawable.ConstantState> tpIcons = new java.util.HashMap<>();

    public static List<String[]> thirdPartyInfo(Context c) {
        long now = SystemClock.uptimeMillis();
        if (tpInfo == null || now - tpTime > 120000L) {
            android.content.pm.PackageManager pm = c.getPackageManager();
            List<String[]> info = new ArrayList<>();
            tpIcons.clear();
            List<android.content.pm.ResolveInfo> list;
            try { list = pm.queryIntentServices(new Intent("android.service.quicksettings.action.QS_TILE"), 0); }
            catch (Exception e) { list = new ArrayList<>(); }
            for (android.content.pm.ResolveInfo ri : list) {
                if (ri.serviceInfo.packageName.equals(c.getPackageName())) continue;
                android.content.pm.ServiceInfo si = ri.serviceInfo;
                String comp = si.packageName + "/" + si.name;
                String label;
                try { label = si.loadLabel(pm).toString(); } catch (Exception e) { label = si.packageName; }
                info.add(new String[]{comp, label});
                try {
                    Drawable d = si.loadIcon(pm);
                    if (d != null && d.getConstantState() != null) tpIcons.put(comp, d.getConstantState());
                } catch (Exception e) {}
            }
            java.util.Collections.sort(info, (x, y) -> x[1].toLowerCase(Locale.ROOT).compareTo(y[1].toLowerCase(Locale.ROOT)));
            tpInfo = info;
            tpTime = now;
        }
        return tpInfo;
    }

    public static Drawable thirdPartyIcon(Context c, String comp) {
        thirdPartyInfo(c);
        Drawable.ConstantState cs = tpIcons.get(comp);
        return cs != null ? cs.newDrawable() : null;
    }

    private List<Tile> thirdParty() {
        List<Tile> out = new ArrayList<>();
        for (String[] f : thirdPartyInfo(ctx)) {
            final String comp = f[0];
            Tile t = new Tile(f[1], 0, () -> false, "", "App", null, () -> {
                if (Privileged.available()) {
                    Privileged.run("cmd statusbar add-tile " + comp + "; cmd statusbar click-tile " + comp);
                    onClose.run();
                } else Toast.makeText(ctx, "Los accesos de apps necesitan Shizuku", Toast.LENGTH_SHORT).show();
            });
            t.key = "tp:" + comp;
            t.iconDrawable = thirdPartyIcon(ctx, comp);
            out.add(t);
        }
        return out;
    }

    private View appView(final Elem e, int wPx, int hPx) {
        final android.content.pm.PackageManager pm = ctx.getPackageManager();
        Drawable ic;
        String label;
        try {
            ic = pm.getApplicationIcon(e.a);
            label = pm.getApplicationLabel(pm.getApplicationInfo(e.a, 0)).toString();
        } catch (Exception x) {
            return placeholder("App no encontrada");
        }
        LinearLayout box = vcol();
        box.setGravity(Gravity.CENTER);
        box.setClickable(true);
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(ic);
        int d = Math.min(wPx, hPx - (sk.layout == TileLayout.ICON ? 0 : dp(sk.labelSize + 8)));
        box.addView(iv, new LinearLayout.LayoutParams(Math.max(1, d), Math.max(1, d)));
        if (sk.layout != TileLayout.ICON) {
            TextView tv = new TextView(ctx);
            tv.setText(label);
            tv.setTextSize((float) sk.labelSize);
            tv.setTextColor(sk.labelColor != 0 ? sk.labelColor : cText);
            tv.setMaxLines(1);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setGravity(Gravity.CENTER);
            box.addView(tv, new LinearLayout.LayoutParams(MP, WC));
        }
        box.setOnClickListener(v -> {
            if (GameWatchdog.route(ctx, e.a)) return;
            Intent li = pm.getLaunchIntentForPackage(e.a);
            if (li != null) launch(li);
        });
        box.setOnLongClickListener(v -> {
            launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.a)));
            return true;
        });
        return box;
    }

    private TextView pillButton(String label, int tc, int bg, final Runnable action) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextSize(13f);
        t.setTextColor(tc);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setBackground(round(bg, 20));
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private TextView arrow(String txt, final int delta, final int i, final List<String> w, int tc) {
        TextView t = new TextView(ctx);
        t.setText(txt);
        t.setTextSize(18f);
        t.setTextColor(tc);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setOnClickListener(v -> {
            int j = i + delta;
            String k = w.get(i);
            w.set(i, w.get(j));
            w.set(j, k);
            post(() -> showEditor());
        });
        return t;
    }

    private View editRow(final Tile t, boolean on, int i, final List<String> w, int tc, int bg) {
        LinearLayout row = hrow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(8), dp(8));
        row.setBackground(round(bg, 16));
        row.setAlpha(on ? 1f : 0.6f);
        ImageView ic = new ImageView(ctx);
        setIcon(ic, t);
        ic.setImageTintList(tint(tc));
        row.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView name = new TextView(ctx);
        name.setText(labelOf(t));
        name.setTextSize(15f);
        name.setTextColor(tc);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(dp(12), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0, WC, 1f));
        if (on && i > 0) row.addView(arrow("▲", -1, i, w, tc));
        if (on && i < w.size() - 1) row.addView(arrow("▼", 1, i, w, tc));
        Switch sw = new Switch(ctx);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener((b, c) -> {
            if (c) w.add(t.key); else w.remove(t.key);
            post(() -> showEditor());
        });
        row.addView(sw);
        return row;
    }

    private void closeEditor() {
        if (editor != null) removeView(editor);
        editor = null;
        applyFab();
        sheet.setVisibility(View.VISIBLE);
    }

    /** Editor de accesos (mantener apretado un acceso). Es una capa que tapa la hoja; usa colores fijos para leerse siempre. */
    private void showEditor() {
        if (sk.layoutMode != 0) { launch(new Intent(ctx, LayoutEditorActivity.class)); return; }
        List<String> w0 = work;
        if (w0 == null) { w0 = new ArrayList<>(enabledKeys()); work = w0; }
        final List<String> w = w0;
        int tc = dark ? 0xFFF2EEF5 : 0xFF1D1B1E;
        int sc = withA(tc, 70);
        int rowBg = dark ? 0xFF2B2930 : 0xFFE6E0E3;
        int layerBg = dark ? 0xF0141218 : 0xF0F3EEF0;
        java.util.Map<String, Tile> map = new java.util.HashMap<>();
        for (Tile t : allTiles()) map.put(t.key, t);
        final int keep = editor != null ? editor.getScrollY() : 0;
        LinearLayout col = vcol();
        col.setPadding(dp(16), statusBarH() + dp(12), dp(16), dp(32));
        LinearLayout top = hrow();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView head = new TextView(ctx);
        head.setText("Editar accesos");
        head.setTextSize(20f);
        head.setTextColor(tc);
        top.addView(head, new LinearLayout.LayoutParams(0, WC, 1f));
        top.addView(pillButton("Listo", tc, rowBg, () -> {
            prefs.edit().putString("tiles_custom", String.join("\n", w)).apply();
            work = null;
            closeEditor();
            buildShade(); setProgress(progress); refresh();
        }));
        col.addView(top);
        TextView help = new TextView(ctx);
        help.setText("Activá o quitá accesos y ordenalos con las flechas. Los de apps necesitan Shizuku.");
        help.setTextSize(12f);
        help.setTextColor(sc);
        help.setPadding(0, dp(6), 0, dp(8));
        col.addView(help);
        for (int i = 0; i < w.size(); i++) {
            Tile t = map.get(w.get(i));
            if (t != null) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MP, WC);
                lp.topMargin = dp(6);
                col.addView(editRow(t, true, i, w, tc, rowBg), lp);
            }
        }
        for (Tile t : allTiles()) {
            if (w.contains(t.key)) continue;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MP, WC);
            lp.topMargin = dp(6);
            col.addView(editRow(t, false, -1, w, tc, rowBg), lp);
        }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(WC, WC);
        rlp.topMargin = dp(12);
        rlp.gravity = Gravity.CENTER_HORIZONTAL;
        col.addView(pillButton("Restablecer", sc, rowBg, () -> {
            prefs.edit().remove("tiles_custom").remove("tiles_aosp").apply();
            work = null;
            showEditor();
        }), rlp);
        final ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(OVER_SCROLL_NEVER);
        sv.setClickable(true);
        sv.setBackgroundColor(layerBg);
        applyType(col);
        sv.addView(col);
        if (editor != null) removeView(editor);
        editor = sv;
        applyFab();
        sheet.setVisibility(View.INVISIBLE);
        addView(sv, new LayoutParams(sheetW, MP, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        sv.post(() -> sv.scrollTo(0, keep));
    }

    // ======================= HOJA: pestañas, accesos que empujan, notificaciones =======================

    private int natH(View v, int w) {
        v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return v.getMeasuredHeight();
    }

    private void applyTab() {
        applyFab();
        if (page0 == null) return;
        page0.setTranslationX(-tabPos * sheetW);
        page1.setTranslationX((1f - tabPos) * sheetW);
        page0.setVisibility(tabPos >= 1f ? View.INVISIBLE : View.VISIBLE);
        page1.setVisibility(tabPos <= 0f ? View.INVISIBLE : View.VISIBLE);
        tabInd.setTranslationX(tabPos * (sheetW / 2f) + (sheetW / 4f - dp(14)));
        for (int i = 0; i < tabLabels.size(); i++) {
            tabLabels.get(i).setAlpha(i == 0 ? 1f - 0.45f * tabPos : 0.55f + 0.45f * tabPos);
        }
    }

    private void tabStep(float frac) { tabPos = Math.max(0f, Math.min(1f, tabPos + frac)); applyTab(); }

    private void animateTab(float target) {
        if (tabAnim != null) tabAnim.cancel();
        ValueAnimator a = ValueAnimator.ofFloat(tabPos, target);
        a.setDuration((long) sk.tabAnimDur);
        a.setInterpolator(interp());
        a.addUpdateListener(an -> { tabPos = (Float) an.getAnimatedValue(); applyTab(); });
        tabAnim = a;
        a.start();
    }

    private void settleTab(float vx) {
        animateTab(vx < -800f ? 1f : vx > 800f ? 0f : tabPos > 0.5f ? 1f : 0f);
    }

    private View buildTabBar() {
        FrameLayout bar = new FrameLayout(ctx);
        LinearLayout row = hrow();
        tabLabels.clear();
        String[] names = {ss("txtTab0", "Notificaciones"), ss("txtTab1", "Controles")};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView tv = new TextView(ctx);
            tv.setText(names[i]);
            tv.setTextSize(13f);
            tv.setTextColor(cText);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(8), 0, dp(10));
            tv.setOnClickListener(v -> animateTab((float) idx));
            tabLabels.add(tv);
            row.addView(tv, new LinearLayout.LayoutParams(0, WC, 1f));
        }
        bar.addView(row, new FrameLayout.LayoutParams(MP, WC));
        tabInd = new View(ctx);
        tabInd.setBackground(round(cAccent, 2));
        bar.addView(tabInd, new FrameLayout.LayoutParams(dp(28), dp(3), Gravity.BOTTOM | Gravity.START));
        if (sk.showTabs == 0) bar.setVisibility(View.GONE);
        return bar;
    }

    private View buildHeader() {
        free(cClock); free(cDate);
        int g = sk.clockAlign == 1 ? Gravity.CENTER_HORIZONTAL : sk.clockAlign == 2 ? Gravity.END : Gravity.START;
        cClock.setTextSize((float) sk.clockSize); cClock.setTextColor(sk.clockColor != 0 ? sk.clockColor : cText); cClock.setGravity(g);
        switch (sk.clockWeight) {
            case 0: cClock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL)); break;
            case 2: cClock.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); break;
            default: cClock.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); break;
        }
        if (sk.clockFont > 0) cClock.setTypeface(Typeface.create(FAMILIES[Math.min(FAMILIES.length - 1, sk.clockFont - 1)], sk.clockWeight == 2 ? Typeface.BOLD : Typeface.NORMAL));
        if (sk.clockItalic != 0) cClock.setTypeface(cClock.getTypeface(), Typeface.ITALIC);
        cDate.setTextSize((float) sk.dateSize); cDate.setTextColor(sk.dateColor != 0 ? sk.dateColor : cSub); cDate.setGravity(g);
        cDate.setVisibility(sk.showDate ? View.VISIBLE : View.GONE);
        cClock.setOnLongClickListener(v -> { showEditor(); return true; });
        LinearLayout col = vcol();
        col.addView(cClock, new LinearLayout.LayoutParams(MP, WC));
        col.addView(cDate, new LinearLayout.LayoutParams(MP, WC));
        LinearLayout wrap = hrow();
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(dp(6), dp(sk.clockPadV), dp(6), dp(sk.clockPadV + 2));
        wrap.addView(col, new LinearLayout.LayoutParams(0, WC, 1f));
        if (sk.showGear) {
            TextView gear = new TextView(ctx);
            gear.setText(ss("txtGear", "⚙"));
            gear.setTextSize(20f);
            gear.setTextColor(cText);
            gear.setGravity(Gravity.CENTER);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(cOff);
            gear.setBackground(gd);
            gear.setOnClickListener(v -> open(Settings.ACTION_SETTINGS));
            wrap.addView(gear, new LinearLayout.LayoutParams(dp(sk.gearSize), dp(sk.gearSize)));
        }
        if (sk.showEdit != 0) {
            TextView ed = new TextView(ctx);
            ed.setText(ss("txtEditBtn", "✎"));
            ed.setTextSize(20f);
            ed.setTextColor(cText);
            ed.setGravity(Gravity.CENTER);
            GradientDrawable edg = new GradientDrawable();
            edg.setShape(GradientDrawable.OVAL);
            edg.setColor(cOff);
            ed.setBackground(edg);
            ed.setOnClickListener(v -> showEditor());
            LinearLayout.LayoutParams edlp = new LinearLayout.LayoutParams(dp(sk.gearSize), dp(sk.gearSize));
            edlp.leftMargin = dp(8);
            wrap.addView(ed, edlp);
        }
        return wrap;
    }

    /** Franja de accesos de la primera etapa: los primeros N accesos activos. */
    private View buildQsCompact(int w) {
        List<Tile> all = shown();
        List<Tile> list = new ArrayList<>(all.subList(0, Math.max(0, Math.min(sk.compact, all.size()))));
        return list.isEmpty() ? new View(ctx) : grid(list, w);
    }

    private void addClearTo(LinearLayout parent) {
        free(clearBtn);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WC, WC);
        lp.gravity = Gravity.CENTER_HORIZONTAL; lp.topMargin = dp(8);
        parent.addView(clearBtn, lp);
    }

    private void buildShade() {
        sheet.removeAllViews();
        holders.clear(); vsliders.clear();
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        if (sk.sheetAlpha > 0) {
            float r = (float) dp(sk.sheetRadius);
            float rt = (float) dp(sk.sheetTopRadius);
            GradientDrawable sg;
            if (sk.sheetColor2 != 0) {
                GradientDrawable.Orientation[] os = {GradientDrawable.Orientation.TOP_BOTTOM, GradientDrawable.Orientation.LEFT_RIGHT,
                    GradientDrawable.Orientation.BOTTOM_TOP, GradientDrawable.Orientation.RIGHT_LEFT};
                sg = new GradientDrawable(os[Math.max(0, Math.min(3, sk.sheetAngle))], new int[]{cSheet, withA(sk.sheetColor2, sk.sheetAlpha)});
            } else {
                sg = new GradientDrawable();
                sg.setColor(cSheet);
            }
            sg.setCornerRadii(new float[]{rt, rt, rt, rt, r, r, r, r});
            if (sk.sheetStroke > 0) sg.setStroke(dp(sk.sheetStroke), sk.sheetStrokeColor != 0 ? sk.sheetStrokeColor : withA(cText, 25));
            sheet.setBackground(sg);
        } else sheet.setBackground(null);
        sheet.setPadding(0, statusBarH() + dp(sk.topPad), 0, 0);

        int padH = dp(sk.sidePad);
        int innerW = sheetW - padH * 2;
        u = innerW / 4;

        sheet.addView(buildTabBar(), new LinearLayout.LayoutParams(MP, WC));
        if (sk.layoutMode != 0) { buildCanvasPages(innerW); return; }

        // ---- pestaña 1: notificaciones (con los accesos que se expanden y empujan) ----
        page0 = vcol();
        page0.setPadding(padH, dp(4), padH, dp(6));
        page0.addView(buildHeader(), new LinearLayout.LayoutParams(MP, WC));
        qsCompact = buildQsCompact(innerW);
        View fullContent = grid(shown(), innerW);
        hC = natH(qsCompact, innerW);
        hF = Math.max(hC, Math.min(natH(fullContent, innerW), (int) (dm.heightPixels * 0.5f)));
        qsFull = new ScrollView(ctx);
        qsFull.setVerticalScrollBarEnabled(false);
        qsFull.setOverScrollMode(OVER_SCROLL_NEVER);
        qsFull.addView(fullContent);
        qs = new FrameLayout(ctx);
        qs.addView(qsCompact, new FrameLayout.LayoutParams(innerW, hC));
        qs.addView(qsFull, new FrameLayout.LayoutParams(innerW, hF));
        page0.addView(qs, new LinearLayout.LayoutParams(innerW, hC));

        free(notifList);
        notifList.setOrientation(LinearLayout.VERTICAL);
        notifScroll = new MaxScroll(ctx, (int) (dm.heightPixels * sk.notifMaxH / 100f));
        notifScroll.setVerticalScrollBarEnabled(false);
        notifScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        notifScroll.addView(notifList);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(MP, WC);
        nlp.topMargin = dp(8);
        page0.addView(notifScroll, nlp);
        clearBtn.setOnClickListener(v -> {
            MediaListener ml = MediaListener.instance;
            if (ml != null) ml.cancelAllNotifications();
            postDelayed(() -> refreshNotes(), 250);
        });
        clearBtn.setText(ss("txtClear", "Borrar todo")); clearBtn.setTextSize(13f);
        clearBtn.setTextColor(cText); clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        clearBtn.setBackground(round(cCard, 20));
        addClearTo(page0);

        // ---- pestaña 2: controles (multimedia, brillo, volumen) ----
        page1 = vcol();
        page1.setPadding(padH, dp(8), padH, dp(8));
        if (sk.sliderStyle == 0) {
            page1.addView(sliderRow(R.drawable.ic_brightness, 255,
                () -> Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128), v -> setBrightness(v)));
            int maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            page1.addView(sliderRow(R.drawable.ic_volume, maxVol, () -> audio.getStreamVolume(AudioManager.STREAM_MUSIC), v ->
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)));
            if (sk.showMedia) {
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(MP, dp(sk.mediaH));
                mlp.topMargin = dp(10);
                page1.addView(mediaCard(), mlp);
            }
        } else {
            int hh = dp(sk.sliderThick) * 3;
            int mg = dp(5);
            LinearLayout r = hrow();
            if (sk.showMedia) {
                LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, hh, 2f);
                l1.setMargins(mg, mg, mg, mg);
                r.addView(mediaCard(), l1);
            }
            LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, hh, 1f);
            l2.setMargins(mg, mg, mg, mg);
            r.addView(vBright(), l2);
            LinearLayout.LayoutParams l3 = new LinearLayout.LayoutParams(0, hh, 1f);
            l3.setMargins(mg, mg, mg, mg);
            r.addView(vVol(), l3);
            page1.addView(r);
        }

        if (sk.showEdit != 0) {
            TextView eb = pillButton(ss("txtEditTiles", "Editar accesos"), cText, cCard, () -> showEditor());
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(WC, WC);
            elp.topMargin = dp(14);
            elp.gravity = Gravity.CENTER_HORIZONTAL;
            page1.addView(eb, elp);
        }

        pager = new FrameLayout(ctx);
        pager.addView(page0, new FrameLayout.LayoutParams(sheetW, WC));
        pager.addView(page1, new FrameLayout.LayoutParams(sheetW, WC));
        sheet.addView(pager, new LinearLayout.LayoutParams(sheetW, WC));
        if (sk.showGrab != 0) sheet.addView(grabBar(withA(cText, 35)), new LinearLayout.LayoutParams(MP, dp(Math.max(28, sk.grabH + 16))));
        applyTab();
        applyType(sheet);
        refreshNotes();
    }

    // ---- notificaciones: agrupadas por app y desplegables ----
    private View noteRow(final MediaListener.Note n) {
        final boolean isOpen = expandedKeys.contains(n.key);
        boolean expandable = !n.text.isEmpty() || !n.actions.isEmpty();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(cardBg());
        row.setClickable(true);
        boolean useApp = sk.notifApp && n.appIcon != null;
        ImageView ic = new ImageView(ctx);
        if (useApp) {
            ic.setImageDrawable(n.appIcon);
            ic.setBackground(round(0x00000000, 8)); ic.setClipToOutline(true);
        } else {
            GradientDrawable og = new GradientDrawable();
            og.setShape(GradientDrawable.OVAL);
            og.setColor(n.color != 0 ? n.color : cAccent);
            ic.setBackground(og);
            ic.setPadding(dp(7), dp(7), dp(7), dp(7));
            if (n.icon != null) { ic.setImageDrawable(n.icon); ic.setImageTintList(tint(0xFFFFFFFF)); }
        }
        LinearLayout.LayoutParams iclp = new LinearLayout.LayoutParams(dp(sk.notifIconSize), dp(sk.notifIconSize));
        iclp.topMargin = dp(2);
        row.addView(ic, iclp);
        LinearLayout col = vcol();
        col.setPadding(dp(10), 0, 0, 0);
        LinearLayout head = hrow();
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView appT = new TextView(ctx);
        appT.setText(n.app); appT.setTextSize(11f); appT.setTextColor(cSub); appT.setMaxLines(1);
        head.addView(appT, new LinearLayout.LayoutParams(0, WC, 1f));
        TextView timeT = new TextView(ctx);
        timeT.setText(new SimpleDateFormat("H:mm", Locale.getDefault()).format(new Date(n.time)));
        timeT.setTextSize(11f); timeT.setTextColor(cSub);
        if (sk.notifShowTime != 0) head.addView(timeT);
        if (expandable) {
            TextView chev = new TextView(ctx);
            chev.setText(isOpen ? "▴" : "▾"); chev.setTextSize(14f); chev.setTextColor(cText); chev.setPadding(dp(10), dp(2), dp(4), dp(2));
            chev.setOnClickListener(v -> { if (isOpen) expandedKeys.remove(n.key); else expandedKeys.add(n.key); post(() -> refreshNotes()); });
            head.addView(chev);
        }
        col.addView(head);
        if (!n.title.isEmpty()) {
            TextView tt = new TextView(ctx);
            tt.setText(n.title); tt.setTextSize((float) sk.notifTitleSize); tt.setTextColor(cText); tt.setMaxLines(1);
            tt.setEllipsize(android.text.TextUtils.TruncateAt.END); tt.setTypeface(null, Typeface.BOLD);
            col.addView(tt);
        }
        if (!n.text.isEmpty()) {
            TextView bt = new TextView(ctx);
            bt.setText(n.text); bt.setTextSize((float) sk.notifTextSize); bt.setTextColor(cText); bt.setMaxLines(isOpen ? sk.notifOpenLines : sk.notifLines);
            bt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(bt);
        }
        if (isOpen && !n.actions.isEmpty()) {
            LinearLayout acts = hrow();
            acts.setPadding(0, dp(8), 0, 0);
            for (final MediaListener.Act a : n.actions.subList(0, Math.min(3, n.actions.size()))) {
                LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(WC, WC);
                alp.rightMargin = dp(8);
                acts.addView(pillButton(a.title, cText, withA(cText, 16), () -> {
                    try { if (a.intent != null) a.intent.send(); } catch (Exception e) {}
                    onClose.run();
                }), alp);
            }
            col.addView(acts);
        }
        row.addView(col, new LinearLayout.LayoutParams(0, WC, 1f));

        row.setOnTouchListener(new View.OnTouchListener() {
            float x0 = 0f;
            float y0 = 0f;
            boolean moved = false;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = e.getRawX(); y0 = e.getRawY(); moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - x0;
                        if (!moved && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(e.getRawY() - y0)) {
                            moved = true;
                            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        if (moved) {
                            v.setTranslationX(dx);
                            v.setAlpha(1f - Math.min(Math.abs(v.getTranslationX()) / v.getWidth(), 1f) * 0.7f);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        float dx = e.getRawX() - x0;
                        if (moved) {
                            if (Math.abs(dx) > v.getWidth() * 0.4f) {
                                float out = dx > 0 ? (float) v.getWidth() : -(float) v.getWidth();
                                // Se cancela YA: si el panel se refresca a mitad de animación, withEndAction nunca corre.
                                if (!n.clearable) MediaListener.hidden.add(n.key);
                                MediaListener.dismiss(n);
                                v.animate().translationX(out).alpha(0f).setDuration(120).withEndAction(() -> post(() -> refreshNotes())).start();
                            } else v.animate().translationX(0f).alpha(1f).setDuration(120).start();
                        } else if (Math.abs(dx) < slop && Math.abs(e.getRawY() - y0) < slop) openNote(n);
                        return true;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().translationX(0f).alpha(1f).setDuration(120).start();
                        return true;
                    default:
                        return true;
                }
            }
        });
        return row;
    }

    private void openNote(MediaListener.Note n) {
        if (!MediaListener.open(ctx, n)) {
            Toast.makeText(ctx, "No se pudo abrir la notificación", Toast.LENGTH_SHORT).show();
        }
        if (n.autoCancel && n.clearable) MediaListener.dismiss(n);
        onClose.run();
    }

    private void refreshNotes() {
        if (notifScroll == null) return;
        MediaListener l = MediaListener.instance;
        if (l == null) MediaListener.rebind(ctx);
        List<MediaListener.Note> notes = l != null ? l.notes() : new ArrayList<MediaListener.Note>();
        notifList.removeAllViews();
        if (notes.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText(l == null ? "Acceso a notificaciones no activo" : ss("txtEmpty", "Sin notificaciones"));
            empty.setTextSize(13f); empty.setGravity(Gravity.CENTER); empty.setTextColor(cSub);
            empty.setPadding(0, dp(16), 0, dp(8));
            notifList.addView(empty, new LinearLayout.LayoutParams(MP, WC));
        } else {
            java.util.LinkedHashMap<String, List<MediaListener.Note>> groups = new java.util.LinkedHashMap<>();
            for (MediaListener.Note n : notes) {
                List<MediaListener.Note> g = groups.get(n.pkg);
                if (g == null) { g = new ArrayList<>(); groups.put(n.pkg, g); }
                g.add(n);
            }
            for (java.util.Map.Entry<String, List<MediaListener.Note>> en : groups.entrySet()) {
                final String pkg = en.getKey();
                List<MediaListener.Note> list = en.getValue();
                LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(MP, WC);
                lpRow.topMargin = dp(sk.notifGap);
                if (list.size() == 1) { notifList.addView(noteRow(list.get(0)), lpRow); continue; }
                final boolean isOpen = expandedGroups.contains(pkg);
                LinearLayout box = vcol();
                List<MediaListener.Note> visible = isOpen ? list : list.subList(0, 1);
                for (MediaListener.Note it : visible) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MP, WC);
                    lp.topMargin = dp(4);
                    box.addView(noteRow(it), lp);
                }
                TextView more = new TextView(ctx);
                more.setText(isOpen ? "▴ Mostrar menos" : "▾ " + (list.size() - 1) + " más de " + list.get(0).app);
                more.setTextSize(12f); more.setTextColor(cSub); more.setGravity(Gravity.CENTER); more.setPadding(0, dp(6), 0, dp(6));
                more.setOnClickListener(v -> { if (isOpen) expandedGroups.remove(pkg); else expandedGroups.add(pkg); refreshNotes(); });
                box.addView(more);
                notifList.addView(box, lpRow);
            }
        }
        applyType(notifList);
        boolean anyClearable = false;
        for (MediaListener.Note n : notes) { if (n.clearable) { anyClearable = true; break; } }
        clearBtn.setVisibility(anyClearable && sk.showClear != 0 ? View.VISIBLE : View.GONE);
    }

    private View grabBar(int color) {
        FrameLayout g = new FrameLayout(ctx);
        g.setOnClickListener(v -> onClose.run());
        View bar = new View(ctx);
        bar.setBackground(round(color, 3));
        g.addView(bar, new FrameLayout.LayoutParams(dp(sk.grabW), dp(sk.grabH), Gravity.CENTER));
        return g;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        try { cam.registerTorchCallback(torchCb, null); } catch (Exception e) {}
        MediaListener.onChange = () -> post(() -> refreshNotes());
        removeCallbacks(tick);
        postDelayed(tick, 1000);
        try { if (sk.layoutMode != 0) widgetHost().startListening(); } catch (Throwable t) {}
        refreshNotes();
    }

    @Override
    protected void onDetachedFromWindow() {
        try { cam.unregisterTorchCallback(torchCb); } catch (Exception e) {}
        MediaListener.onChange = null;
        removeCallbacks(tick);
        try { if (sk.layoutMode != 0) widgetHost().stopListening(); } catch (Throwable t) {}
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
