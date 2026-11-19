package com.lexus2026.cdc;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Lang.init(this);
        Heal.start(this);
    }
}
