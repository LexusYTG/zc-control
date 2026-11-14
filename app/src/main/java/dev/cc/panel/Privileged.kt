package dev.cc.panel

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku

/** Ejecuta comandos de shell con los permisos de Shizuku (sin root). */
object Privileged {
    fun available(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Throwable) { false }

    fun run(cmd: String, done: (() -> Unit)? = null) {
        Thread {
            try {
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
                )
                m.isAccessible = true
                val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                p.waitFor()
            } catch (e: Throwable) { /* sin permiso o Shizuku detenido */ }
            if (done != null) Handler(Looper.getMainLooper()).post(done)
        }.start()
    }
}

/** Concede por shell todos los permisos que la app necesitaba pedir a mano. */
fun Privileged.grantAll(ctx: android.content.Context, done: (() -> Unit)? = null) {
    val pkg = ctx.packageName
    val svc = "$pkg/dev.cc.panel.ControlService"
    val cur = android.provider.Settings.Secure.getString(
        ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
    val list = if (cur.contains(svc)) cur else if (cur.isEmpty()) svc else "$cur:$svc"
    val cmd = listOf(
        "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
        "appops set $pkg ACCESS_RESTRICTED_SETTINGS allow",
        "appops set $pkg WRITE_SETTINGS allow",
        "cmd notification allow_dnd $pkg",
        "cmd notification allow_listener $pkg/dev.cc.panel.MediaListener",
        "dumpsys deviceidle whitelist +$pkg",
        "settings put secure enabled_accessibility_services '$list'",
        "settings put secure accessibility_enabled 1"
    ).joinToString("; ")
    run(cmd, done)
}
