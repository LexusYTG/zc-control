package com.lexus2026.cdc;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mide CPU/RAM/GPU/FPS del juego y la temperatura del SoC usando solo comandos de shell (Shizuku).
 * No depende de leer /proc/PID/smaps (el shell no puede): la RAM sale de "dumpsys meminfo".
 */
public final class GameMeter {
    public static final class Stats {
        public int cpu = 0;
        public long memKb = 0L;
        public int gpu = -1;
        public boolean gpuEst = false;
        public int fps = 0;
        public float temp = -1f;
    }

    public interface Listener {
        void onStats(Stats s);
    }

    private static final Pattern NUM = Pattern.compile("-?\\d+");
    private static final Pattern PSS1 = Pattern.compile("TOTAL PSS:\\s*([\\d,.]+)");
    private static final Pattern PSS2 = Pattern.compile("^\\s*TOTAL\\s+([\\d,.]+)");
    private static final Pattern THS = Pattern.compile("mValue=(-?[\\d.]+),\\s*mType=(\\d+)");
    private static final Pattern TS_NAME = Pattern.compile("layerName\\s*[=:]\\s*(.+)");
    private static final Pattern TS_FRM = Pattern.compile("totalFrames\\s*[=:]\\s*(\\d+)");
    private static final Pattern LNAME = Pattern.compile("(?:RequestedLayerState\\{|name=)[0-9a-fA-F]+\\s+(\\S+#\\d+)");
    private static final Pattern TOP = Pattern.compile("\\bu\\d+\\s+([A-Za-z0-9_.]+)/");

    private static final String GPU_SH =
        "echo @GPU; "
        + "for f in /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage /sys/class/kgsl/kgsl-3d0/devfreq/gpu_load "
        + "/sys/class/devfreq/*kgsl-3d0/gpu_load /sys/class/kgsl/kgsl-3d0/gpubusy "
        + "/sys/class/misc/mali0/device/utilization /sys/devices/platform/*mali*/utilization "
        + "/sys/devices/platform/*/*mali*/utilization /sys/kernel/gpu/gpu_busy /sys/kernel/ged/hal/gpu_utilization "
        + "/sys/module/ged/parameters/gpu_loading /proc/mali/utilization "
        + "/sys/class/devfreq/*gpu*/load /sys/class/devfreq/*mali*/load; do "
        + "if [ -r \"$f\" ]; then l=; read l < \"$f\"; echo \"G|$f|$l\"; fi; done; "
        + "echo @FRQ; "
        + "if [ -r /sys/class/kgsl/kgsl-3d0/gpuclk ] && [ -r /sys/class/kgsl/kgsl-3d0/max_gpuclk ]; then "
        + "read c < /sys/class/kgsl/kgsl-3d0/gpuclk; read m < /sys/class/kgsl/kgsl-3d0/max_gpuclk; echo \"F|$c|$m\"; fi; "
        + "for d in /sys/class/devfreq/*gpu* /sys/class/devfreq/*mali* /sys/class/devfreq/*kgsl*; do "
        + "if [ -r \"$d/cur_freq\" ] && [ -r \"$d/max_freq\" ]; then read c < \"$d/cur_freq\"; read m < \"$d/max_freq\"; echo \"F|$c|$m\"; fi; done; ";

    private static final String TMP_SH =
        "echo @TMP; for z in /sys/class/thermal/thermal_zone*; do "
        + "if [ -r \"$z/temp\" ] && [ -r \"$z/type\" ]; then t=; v=; read t < \"$z/type\"; read v < \"$z/temp\"; echo \"T|$t|$v\"; fi; done; ";

    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private volatile int generation = 0;
    private volatile Listener listener = null;
    private Thread thread = null;
    private String pkg = "";

    private int tickN = 0;
    private long prevProc = -1L;
    private long prevTot = -1L;
    private long memKb = 0L;
    private boolean pssOk = false;
    private int gpu = -1;
    private boolean gpuEst = false;
    private float temp = -1f;
    private int noTemp = 0;
    private boolean needThs = false;

    /** Archivo de diagnóstico de FPS (lo fija GamePanel). */
    public static volatile File logFile = null;

    private static final int M_NONE = 0, M_LAT = 1, M_GFX = 2, M_TS = 3;
    private int mode = M_NONE;
    private String layer = null;
    private String tsLayer = null;
    private Map<String, Long> prevTs = null;
    private long prevTsT = 0L;
    private long lastMax = -1L;
    private int zero = 0;
    private int tsEmpty = 0;
    private int nextScan = 0;

    public void start(String p, Listener l) {
        stop();
        pkg = p;
        listener = l;
        tickN = 0;
        prevProc = -1L;
        prevTot = -1L;
        memKb = 0L;
        pssOk = false;
        gpu = -1;
        gpuEst = false;
        temp = -1f;
        noTemp = 0;
        needThs = false;
        layer = null;
        mode = M_NONE;
        tsLayer = null;
        prevTs = null;
        lastMax = -1L;
        zero = 0;
        nextScan = 0;
        running = true;
        final int gen = ++generation;
        thread = new Thread(() -> {
            while (running && gen == generation) {
                long t0 = SystemClock.uptimeMillis();
                try {
                    tick(gen);
                } catch (Throwable ignored) {
                }
                long dt = 1000L - (SystemClock.uptimeMillis() - t0);
                if (dt > 0) {
                    try {
                        Thread.sleep(dt);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        if (mode == M_TS) Privileged.run("dumpsys SurfaceFlinger --timestats -disable");
        running = false;
        generation++;
        listener = null;
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private static long[] nums(String line) {
        List<Long> l = new ArrayList<>();
        Matcher m = NUM.matcher(line);
        while (m.find()) {
            try {
                l.add(Long.parseLong(m.group()));
            } catch (NumberFormatException ignored) {
            }
        }
        long[] out = new long[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }

    private static long parseKb(String s) {
        try {
            return (long) Double.parseDouble(s.replace(",", ""));
        } catch (Exception e) {
            return 0L;
        }
    }

    // ---------------- FPS ----------------

    /** Agrega a ts el instante de presentación de cada fila de "SurfaceFlinger --latency". */
    private static void addLatency(List<Long> ts, String line) {
        long[] n = nums(line);
        if (n.length < 3) return;
        long t = n[1];
        if (t > 0L && t < Long.MAX_VALUE / 2) ts.add(t);
    }

    /** Agrega FrameCompleted de una fila de "gfxinfo framestats" (flags 0 = frame normal). */
    private static void addGfx(List<Long> ts, String line) {
        String[] f = line.split(",");
        if (f.length < 14 || !f[0].trim().equals("0")) return;
        try {
            long t = Long.parseLong(f[13].trim());
            if (t > 0L && t < Long.MAX_VALUE / 2) ts.add(t);
        } catch (NumberFormatException ignored) {
        }
    }

    private static long maxOf(List<Long> ts) {
        long m = 0L;
        for (long t : ts) if (t > m) m = t;
        return m;
    }

    /** Frames por segundo en el último segundo de la lista, medido por el intervalo real entre frames. */
    private static int rate(List<Long> ts) {
        if (ts.isEmpty()) return 0;
        long max = maxOf(ts);
        List<Long> w = new ArrayList<>();
        for (long t : ts) if (t > max - 1000000000L) w.add(t);
        Collections.sort(w);
        int n = w.size();
        if (n < 2) return n;
        long span = w.get(n - 1) - w.get(0);
        if (span <= 0L) return n;
        return (int) Math.min(240L, Math.round((n - 1) * 1e9 / (double) span));
    }

    private static int windowCount(List<Long> ts) {
        long max = maxOf(ts);
        int c = 0;
        for (long t : ts) if (t > max - 1000000000L) c++;
        return c;
    }

    /** Nombre real de la capa a partir de una línea de "--list" (Android 15 imprime RequestedLayerState{...}). */
    private static String layerName(String line) {
        String t = line.trim();
        if (t.startsWith("RequestedLayerState{")) {
            t = t.substring("RequestedLayerState{".length());
            int pi = t.indexOf(" parentId=");
            if (pi >= 0) t = t.substring(0, pi);
            else if (t.endsWith("}")) t = t.substring(0, t.length() - 1);
            t = t.replaceFirst("^[0-9a-fA-F]{7}\\s+", "");
            if (t.startsWith("Surface(name=")) return null;
        }
        t = t.trim();
        return t.isEmpty() ? null : t;
    }

    /** Busca la capa del juego con datos de latencia; si no hay, pasa a TimeStats. Devuelve los FPS de este tick. */
    private int scan() {
        String q = pkg.replace("'", "");
        ShellExec.Res r = ShellExec.exec("dumpsys SurfaceFlinger --list 2>/dev/null | grep -F '" + q + "'", 5000);
        java.util.LinkedHashSet<String> pri = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> sec = new java.util.LinkedHashSet<>();
        for (String raw : r.out.split("\n")) {
            String l = raw.trim();
            if (l.isEmpty() || !l.contains(pkg)) continue;
            String low = l.toLowerCase();
            if (low.contains("bounds") || low.contains("background") || low.contains("animation-leash")) continue;
            String nm = layerName(l);
            if (nm == null || nm.contains("'") || nm.startsWith("ActivityRecord") || nm.contains("InputSink")) continue;
            if (low.contains("surfaceview") || low.contains("blast")) pri.add(nm);
            else sec.add(nm);
        }
        pri.addAll(sec);
        List<String> all = new ArrayList<>(pri);
        List<String> cand = new ArrayList<>(all.subList(0, Math.min(5, all.size())));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cand.size(); i++) {
            sb.append("echo '@L").append(i).append("'; dumpsys SurfaceFlinger --latency '").append(cand.get(i)).append("' 2>&1 | head -140; ");
        }
        List<List<Long>> lists = new ArrayList<>();
        List<String> rawHead = new ArrayList<>();
        for (int i = 0; i < cand.size(); i++) {
            lists.add(new ArrayList<Long>());
            rawHead.add("");
        }
        if (!cand.isEmpty()) {
            ShellExec.Res o = ShellExec.exec(sb.toString(), 9000);
            int cur = -1;
            int shown = 0;
            for (String raw : o.out.split("\n")) {
                String line = raw.trim();
                if (line.startsWith("@L")) {
                    try {
                        cur = Integer.parseInt(line.substring(2));
                    } catch (NumberFormatException e) {
                        cur = -1;
                    }
                    shown = 0;
                    continue;
                }
                if (cur < 0 || cur >= lists.size()) continue;
                if (shown++ < 4) rawHead.set(cur, rawHead.get(cur) + "    " + line + "\n");
                addLatency(lists.get(cur), line);
            }
        }
        long overall = 0L;
        for (List<Long> l : lists) overall = Math.max(overall, maxOf(l));
        // Los candidatos vienen con las SurfaceView primero: se toma la primera con frames recientes
        // (la capa del juego); solo si ninguna tiene, la de más actividad entre las demás.
        int best = -1;
        for (int i = 0; i < lists.size() && best < 0; i++) {
            List<Long> l = lists.get(i);
            if (l.isEmpty() || maxOf(l) < overall - 1000000000L) continue;
            String nm = cand.get(i).toLowerCase();
            if (windowCount(l) >= 2 && (nm.contains("surfaceview") || nm.contains("blast"))) best = i;
        }
        int bestN = 0;
        for (int i = 0; i < lists.size() && best < 0; i++) {
            List<Long> l = lists.get(i);
            if (l.isEmpty() || maxOf(l) < overall - 1000000000L) continue;
            int n = windowCount(l);
            if (n > bestN) {
                bestN = n;
                best = i;
            }
        }
        layer = null;
        tsLayer = null;
        prevTs = null;
        tsEmpty = 0;
        mode = M_NONE;
        StringBuilder dbg = new StringBuilder();
        dbg.append("pkg=").append(pkg).append(" sdk=").append(android.os.Build.VERSION.SDK_INT).append("\n--list:\n").append(head(r.out, 25));
        for (int i = 0; i < cand.size(); i++) {
            dbg.append("\ncand ").append(i).append(" '").append(cand.get(i)).append("' frames=").append(lists.get(i).size()).append("\n").append(rawHead.get(i));
        }
        ShellExec.Res gp = ShellExec.exec(
            "echo '[kgsl]'; ls /sys/class/kgsl 2>&1 | head -5; echo '[devfreq]'; ls /sys/class/devfreq 2>&1 | head -30; "
            + "echo '[platform gpu/mali]'; ls -d /sys/devices/platform/*mali* /sys/devices/platform/*gpu* /sys/devices/platform/*/*mali* 2>&1 | head -10; "
            + "echo '[misc]'; ls /sys/class/misc 2>&1 | grep -i -E 'mali|gpu' | head -5; "
            + GPU_SH.replace("echo @GPU; ", "echo '[lecturas G|ruta|valor, F|cur|max]'; ").replace("echo @FRQ; ", "")
            + "echo '[nodos legibles R|ruta|valor]'; for d in /sys/devices/platform/*mali* /sys/class/devfreq/*mali* /sys/devices/platform/gpufreq* "
            + "/sys/kernel/ged/hal /sys/kernel/gpu /sys/kernel/gpufreq /proc/mali /proc/gpufreq /sys/module/ged/parameters; do "
            + "for f in $d/*; do if [ -f \"$f\" ] && [ -r \"$f\" ]; then l=; read l < \"$f\"; echo \"R|$f|$l\"; fi; done; done 2>/dev/null | head -150", 8000);
        dbg.append("\n--gpu:\n").append(head(gp.out, 200));
        if (best >= 0) {
            mode = M_LAT;
            layer = cand.get(best);
            lastMax = maxOf(lists.get(best));
            dbg.append("\n-> mode 1 (latency)\n");
            log(dbg.toString());
            boolean fresh = System.nanoTime() - lastMax < 2500000000L;
            return fresh ? rate(lists.get(best)) : 0;
        }
        // Sin datos de latencia: TimeStats (contador de frames por capa de SurfaceFlinger).
        ShellExec.Res en = ShellExec.exec("dumpsys SurfaceFlinger --timestats -enable 2>&1", 4000);
        ShellExec.Res ts = ShellExec.exec("dumpsys SurfaceFlinger --timestats -dump -maxlayers 200 2>&1 | head -80", 6000);
        dbg.append("\n--timestats -enable: ").append(head(en.out, 3));
        dbg.append("--timestats -dump:\n").append(head(ts.out, 60));
        dbg.append("\n-> mode 3 (timestats)\n");
        log(dbg.toString());
        mode = M_TS;
        return 0;
    }

    private static String head(String s, int lines) {
        StringBuilder b = new StringBuilder();
        int c = 0;
        for (String l : s.split("\n")) {
            if (c++ >= lines) break;
            b.append(l).append('\n');
        }
        return b.toString();
    }

    private static void log(String s) {
        File f = logFile;
        if (f == null) return;
        try (FileOutputStream o = new FileOutputStream(f, false)) {
            o.write(s.getBytes("UTF-8"));
        } catch (Throwable ignored) {
        }
    }

    // ---------------- script de cada tick ----------------

    private String script(boolean doMem, boolean doTemp, boolean doThs) {
        StringBuilder s = new StringBuilder();
        s.append("P=$(pidof ").append(pkg).append(" | cut -d' ' -f1); ");
        s.append("if [ -n \"$P\" ]; then echo @CPU; sed 's/.*) //' /proc/$P/stat 2>/dev/null | cut -d' ' -f12,13; ");
        s.append("echo @RSS; if [ -r /proc/$P/statm ]; then read a b c < /proc/$P/statm; echo $((b*4)); fi; ");
        if (doMem) s.append("echo @MEM; dumpsys meminfo $P 2>/dev/null | grep TOTAL | head -6; ");
        s.append("fi; echo @TOT; head -1 /proc/stat; ");
        s.append(GPU_SH);
        if (doTemp) s.append(TMP_SH);
        if (doThs) s.append("echo @THS; dumpsys thermalservice 2>/dev/null | grep -E 'mType=(0|1),' | head -24; ");
        if (mode == M_TS) {
            s.append("echo @TS; dumpsys SurfaceFlinger --timestats -dump -maxlayers 200 2>/dev/null | grep -E 'layerName|totalFrames'; ");
        } else if (mode == M_LAT && layer != null) {
            s.append("echo @LAT; dumpsys SurfaceFlinger --latency '").append(layer).append("' 2>/dev/null; ");
        } else if (mode == M_GFX) {
            s.append("echo @GFX; dumpsys gfxinfo ").append(pkg).append(" framestats 2>/dev/null | grep -E '^[0-9]+,[0-9]+,'; ");
        }
        return s.toString();
    }

    private static boolean dieSensor(String type) {
        String t = type.toLowerCase();
        String[] bad = {"batt", "bat", "usb", "charg", "skin", "xo", "pa-", "pa_", "pmic", "pm8", "pm6", "wifi", "wlan", "modem",
            "mdm", "quiet", "camera", "flash", "display", "lcd", "bms", "case", "board", "msm_therm", "conn", "ufs", "emmc",
            "nvme", "vts", "cam", "audio", "sdm", "ambient", "front", "back", "rear", "shell", "wireless"};
        for (String b : bad) if (t.contains(b)) return false;
        String[] good = {"cpu", "gpu", "soc", "big", "mid", "little", "g3d", "mali", "kryo", "apc", "cluster", "tsens", "ap_", "nsp"};
        for (String g : good) if (t.contains(g)) return true;
        return false;
    }

    private static float celsius(long v) {
        if (v <= 0L) return -1f;
        float c = v >= 1000L ? v / 1000f : v >= 200L ? v / 10f : v;
        return c >= 10f && c <= 130f ? c : -1f;
    }

    private void tick(final int gen) {
        final int n = tickN++;
        if (!pkg.matches("[A-Za-z0-9_.]+")) return;
        int fpsScan = -1;
        boolean rescan = mode == M_NONE && n >= nextScan;
        if (rescan) fpsScan = scan();
        boolean doMem = n % 3 == 0;
        boolean doTemp = n % 2 == 0;
        boolean doThs = needThs && n % 3 == 0;
        ShellExec.Res r = ShellExec.exec(script(doMem, doTemp, doThs), 6000);

        long proc = -1L;
        long tot = -1L;
        long rss = 0L;
        long pss = 0L;
        int gpuBusy = -1;
        int gpuFrq = -1;
        float tmax = -1f;
        float ths = -1f;
        boolean tempSeen = false;
        List<Long> ts = new ArrayList<>();
        Map<String, Long> tsNow = new HashMap<>();
        String curName = null;
        String sec = "";
        for (String raw : r.out.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("@")) {
                sec = line;
                continue;
            }
            switch (sec) {
                case "@CPU":
                    if (line.matches("\\d+ \\d+")) {
                        long[] x = nums(line);
                        proc = x[0] + x[1];
                    }
                    break;
                case "@RSS":
                    if (line.matches("\\d+")) rss = parseKb(line);
                    break;
                case "@MEM": {
                    if (pss > 0L) break;
                    Matcher m = PSS1.matcher(line);
                    if (m.find()) {
                        pss = parseKb(m.group(1));
                        break;
                    }
                    m = PSS2.matcher(line);
                    if (m.find()) pss = parseKb(m.group(1));
                    break;
                }
                case "@TOT":
                    if (line.startsWith("cpu")) {
                        long[] x = nums(line);
                        long sum = 0L;
                        for (int i = 0; i < Math.min(8, x.length); i++) sum += x[i];
                        tot = sum;
                    }
                    break;
                case "@GPU":
                    if (gpuBusy < 0 && line.startsWith("G|")) gpuBusy = parseGpu(line);
                    break;
                case "@FRQ":
                    if (gpuFrq < 0 && line.startsWith("F|")) {
                        String[] f = line.split("\\|", -1);
                        if (f.length >= 3) {
                            try {
                                double c = Double.parseDouble(f[1].trim());
                                double m = Double.parseDouble(f[2].trim());
                                if (c > 0 && m > 0 && c <= m * 1.02) gpuFrq = (int) Math.max(0, Math.min(100, Math.round(c * 100.0 / m)));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    break;
                case "@TMP":
                    if (line.startsWith("T|")) {
                        String[] f = line.split("\\|", -1);
                        if (f.length >= 3 && dieSensor(f[1])) {
                            try {
                                float c = celsius(Long.parseLong(f[2].trim()));
                                if (c > 0f) {
                                    tempSeen = true;
                                    if (c > tmax) tmax = c;
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    break;
                case "@THS": {
                    Matcher m = THS.matcher(line);
                    if (m.find()) {
                        try {
                            float c = Float.parseFloat(m.group(1));
                            if (c >= 10f && c <= 130f && c > ths) ths = c;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    break;
                }
                case "@TS": {
                    Matcher mn = TS_NAME.matcher(line);
                    if (mn.find()) {
                        curName = mn.group(1).trim();
                        break;
                    }
                    Matcher mf = TS_FRM.matcher(line);
                    if (mf.find() && curName != null) {
                        if (curName.contains(pkg)) tsNow.put(curName, parseKb(mf.group(1)));
                        curName = null;
                    }
                    break;
                }
                case "@LAT":
                    addLatency(ts, line);
                    break;
                case "@GFX":
                    addGfx(ts, line);
                    break;
                default:
                    break;
            }
        }

        // CPU del proceso respecto al total del dispositivo.
        int cpu = 0;
        if (proc >= 0L && tot > 0L && prevProc >= 0L && tot > prevTot) {
            cpu = (int) Math.max(0L, Math.min(100L, (proc - prevProc) * 100L / (tot - prevTot)));
        }
        if (proc >= 0L) {
            prevProc = proc;
            prevTot = tot;
        }

        // RAM: PSS de dumpsys; hasta que llegue, el RSS de statm.
        if (pss > 0L) {
            memKb = pss;
            pssOk = true;
        } else if (!pssOk && rss > 0L) {
            memKb = rss;
        }

        // GPU: carga real si existe; si no, estimación por frecuencia.
        if (gpuBusy >= 0) {
            gpu = gpuBusy;
            gpuEst = false;
        } else if (gpuFrq >= 0) {
            gpu = gpuFrq;
            gpuEst = true;
        }

        // Temperatura del SoC.
        if (doTemp) {
            if (tempSeen) {
                temp = tmax;
                noTemp = 0;
                needThs = false;
            } else if (++noTemp >= 2) {
                needThs = true;
            }
        }
        if (doThs && ths > 0f) temp = ths;

        // FPS.
        int fps = 0;
        if (rescan && mode != M_TS) {
            fps = Math.max(0, fpsScan);
        } else if (mode == M_TS) {
            long now = SystemClock.uptimeMillis();
            if (prevTs != null && now > prevTsT) {
                double dt = (now - prevTsT) / 1000.0;
                if (tsLayer == null) {
                    long bd = 0L;
                    for (Map.Entry<String, Long> e : tsNow.entrySet()) {
                        Long pv = prevTs.get(e.getKey());
                        long dl = pv == null ? 0L : e.getValue() - pv;
                        if (dl > bd) {
                            bd = dl;
                            tsLayer = e.getKey();
                        }
                    }
                }
                if (tsLayer != null) {
                    Long a = tsNow.get(tsLayer);
                    Long b = prevTs.get(tsLayer);
                    if (a != null && b != null && a >= b) fps = (int) Math.min(240L, Math.round((a - b) / dt));
                }
            }
            if (!tsNow.isEmpty()) {
                prevTs = tsNow;
                prevTsT = now;
                tsEmpty = 0;
            } else if (++tsEmpty >= 3) {
                mode = M_GFX;
                lastMax = -1L;
            }
        } else if (!ts.isEmpty()) {
            long max = maxOf(ts);
            if (max != lastMax) fps = rate(ts);
            lastMax = max;
        }
        if (mode != M_NONE) {
            if (fps == 0) {
                if (++zero >= 4) {
                    zero = 0;
                    layer = null;
                    tsLayer = null;
                    prevTs = null;
                    mode = M_NONE;
                    nextScan = tickN;
                }
            } else {
                zero = 0;
            }
        }

        final Stats st = new Stats();
        st.cpu = cpu;
        st.memKb = memKb;
        st.gpu = gpu;
        st.gpuEst = gpuEst;
        st.fps = fps;
        st.temp = temp;
        h.post(() -> {
            Listener l = listener;
            if (running && gen == generation && l != null) l.onStats(st);
        });
    }

    /** Interpreta una línea "G|ruta|contenido" y devuelve 0-100, o -1 si no sirve. */
    private static int parseGpu(String line) {
        String[] f = line.split("\\|", 3);
        if (f.length < 3) return -1;
        long[] n = nums(f[2]);
        if (n.length == 0) return -1;
        long v;
        if (f[1].endsWith("gpubusy")) {
            if (n.length < 2 || n[1] <= 0L) return -1;
            v = n[0] * 100L / n[1];
        } else {
            v = n[0];
            if (v > 100L && v <= 255L) v = v * 100L / 255L;
        }
        if (v < 0L) return -1;
        return (int) Math.min(100L, v);
    }

    /** Paquete de la actividad realmente en primer plano (o null si no se pudo leer). */
    public static String topPackage() {
        ShellExec.Res r = ShellExec.exec("dumpsys activity activities 2>/dev/null | grep -E 'topResumedActivity|ResumedActivity' | head -4", 5000);
        String found = null;
        for (String line : r.out.split("\n")) {
            Matcher m = TOP.matcher(line);
            if (!m.find()) continue;
            if (line.contains("topResumedActivity")) return m.group(1);
            if (found == null) found = m.group(1);
        }
        return found;
    }
}
