package com.lexus2026.cdc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context c, Intent i) {
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                GameWatchdog.recover(c.getApplicationContext());
            } finally {
                pr.finish();
            }
        }).start();
    }
}
