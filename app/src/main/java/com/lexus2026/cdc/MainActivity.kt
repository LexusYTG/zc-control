package com.lexus2026.cdc

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val ui = Handler(Looper.getMainLooper())
    private var asked = false
    private var granting = false
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) autoGrant()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (48 * d).toInt(), (24 * d).toInt(), (24 * d).toInt())
        }
        root.addView(TextView(this).apply { text = "Centro de control"; textSize = 28f })
        status = TextView(this).apply { textSize = 15f; setPadding(0, (16 * d).toInt(), 0, (16 * d).toInt()) }
        root.addView(status)
        fun btn(label: String, onClick: () -> Unit) = root.addView(Button(this).apply {
            text = label; isAllCaps = false; setOnClickListener { onClick() }
        })
        btn("🎨 Make your action bar") { startActivity(Intent(this, BuilderActivity::class.java)) }
        btn("⚡ Activar todo con Shizuku") {
            if (Privileged.available()) autoGrant()
            else try { Shizuku.requestPermission(1) } catch (e: Throwable) {
                Toast.makeText(this, "Abrí Shizuku e iniciá el servicio primero", Toast.LENGTH_LONG).show()
            }
        }
        btn("▶ Probar centro de control ahora") {
            val svc = ControlService.instance
            if (svc != null) svc.open()
            else Toast.makeText(this, "El servicio todavía no está activo", Toast.LENGTH_LONG).show()
        }
        root.addView(TextView(this).apply {
            text = "Sin Shizuku, permisos manuales:"; textSize = 13f; setPadding(0, (20 * d).toInt(), 0, 0)
        })
        btn("Accesibilidad") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("Modificar ajustes (brillo, giro)") {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        }
        btn("Acceso a No molestar") { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        root.addView(TextView(this).apply {
            text = "Uso: arrastrá hacia abajo desde la barrita blanca del borde superior. Primera bajada: notificaciones; bajá de nuevo para expandir los accesos. Deslizá a los costados para cambiar de pestaña. Mantené apretado un acceso para editar."
            textSize = 14f; gravity = Gravity.START; setPadding(0, (16 * d).toInt(), 0, 0)
        })
        fun lock(on: Boolean) {
            if (!Privileged.available()) {
                Toast.makeText(this, "Necesita Shizuku activo", Toast.LENGTH_LONG).show(); return
            }
            Privileged.run("cmd statusbar send-disable-flag " + (if (on) "statusbar-expansion" else "none")) {
                getSharedPreferences("cc", MODE_PRIVATE).edit().putBoolean("lock", on).apply()
                Toast.makeText(this, if (on) "Panel del sistema reemplazado" else "Panel del sistema restablecido",
                    Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(TextView(this).apply {
            text = "Panel del sistema:"; textSize = 13f; setPadding(0, (20 * d).toInt(), 0, 0)
        })
        btn("Suplantar sistema") { lock(true) }
        btn("Restablecer sistema") { lock(false) }
        setContentView(ScrollView(this).apply { addView(root) })
        try { Shizuku.addRequestPermissionResultListener(shizukuListener) } catch (e: Throwable) {}
    }

    /** Concede por Shizuku todos los permisos, incluida la accesibilidad. */
    private fun autoGrant() {
        if (granting) return
        granting = true
        Privileged.grantAll(this) {
            ui.postDelayed({
                granting = false
                showStatus()
                // Primera vez: si nuestro panel ya está vivo, se bloquea el panel del sistema.
                val prefs = getSharedPreferences("cc", MODE_PRIVATE)
                if (ControlService.instance != null && !prefs.contains("lock")) {
                    Privileged.run("cmd statusbar send-disable-flag statusbar-expansion")
                    prefs.edit().putBoolean("lock", true).apply()
                }
            }, 1500)
        }
    }

    private fun allDone(): Boolean {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return ControlService.instance != null && Settings.System.canWrite(this) &&
            nm.isNotificationPolicyAccessGranted && listeners.contains("$packageName/com.lexus2026.cdc.MediaListener")
    }

    override fun onResume() {
        super.onResume()
        showStatus()
        // Con Shizuku corriendo, todo se configura solo.
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    if (!allDone()) autoGrant()
                } else if (!asked) { asked = true; Shizuku.requestPermission(1) }
            }
        } catch (e: Throwable) {}
    }

    private fun showStatus() {
        val acc = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        fun m(ok: Boolean) = if (ok) "listo" else "pendiente"
        status.text = "Servicio corriendo: ${if (ControlService.instance != null) "sí" else "no"}\n" +
            "Accesibilidad: ${m(acc)}\nAjustes: ${m(Settings.System.canWrite(this))}\n" +
            "No molestar: ${m(nm.isNotificationPolicyAccessGranted)}\nShizuku: ${m(Privileged.available())}"
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuListener) } catch (e: Throwable) {}
        super.onDestroy()
    }
}
