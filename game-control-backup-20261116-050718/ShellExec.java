package com.lexus2026.cdc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

public final class ShellExec {
    private ShellExec() {}

    public static final class Res {
        public final int code;
        public final String out;

        Res(int c, String o) {
            code = c;
            out = o;
        }
    }

    public static Res exec(String cmd, long timeoutMs) {
        try {
            Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            final Process p = (Process) m.invoke(null, new String[]{"sh", "-c", "(" + cmd + ") 2>&1"}, null, null);
            final StringBuffer sb = new StringBuffer();
            Thread r = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String l;
                    while ((l = br.readLine()) != null) sb.append(l).append('\n');
                } catch (Throwable e) {
                }
            });
            r.start();
            boolean done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroy();
                r.join(500);
                return new Res(-2, sb.toString());
            }
            r.join(1500);
            return new Res(p.exitValue(), sb.toString());
        } catch (Throwable e) {
            return new Res(-1, "");
        }
    }
}
