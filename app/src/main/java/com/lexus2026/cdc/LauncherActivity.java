package com.lexus2026.cdc;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.window.OnBackInvokedDispatcher;

/** Actividad de inicio (HOME). Toda la lógica vive en LauncherEngine. */
public class LauncherActivity extends Activity {
    private LauncherEngine engine;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        engine = new LauncherEngine(this);
        setContentView(engine.view());
        // Con targetSdk alto el botón Atrás ya no llega a onBackPressed: se registra el callback moderno.
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> engine.onBack());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        // Modo escritorio: teléfono en horizontal; si no, vertical como siempre.
        boolean desk = new LauncherSettings(this).get(LauncherSettings.DESKTOP) == 1;
        int want = desk ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        if (getRequestedOrientation() != want) setRequestedOrientation(want);
        engine.onResume();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (Intent.ACTION_MAIN.equals(i.getAction())) engine.onHome(); // se pulsó Home
    }

    @Override public void onBackPressed() { engine.onBack(); } // un launcher nunca se cierra con Atrás

    @Override protected void onDestroy() { super.onDestroy(); engine.onDestroy(); }
}
