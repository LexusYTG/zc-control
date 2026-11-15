package com.lexus2026.cdc;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;

public class VideoCard extends TextureView implements TextureView.SurfaceTextureListener {
    private final Uri uri;
    private final boolean fit;
    private MediaPlayer mp = null;
    private int vw = 0;
    private int vh = 0;

    public VideoCard(Context c, Uri uri, boolean fit) {
        super(c);
        this.uri = uri;
        this.fit = fit;
        setSurfaceTextureListener(this);
    }

    private void release() {
        if (mp != null) {
            try { mp.release(); } catch (Exception e) {}
            mp = null;
        }
    }

    private void fitVideo() {
        if (vw <= 0 || vh <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
        float sx = (float) getWidth() / vw;
        float sy = (float) getHeight() / vh;
        float s = fit ? Math.min(sx, sy) : Math.max(sx, sy);
        Matrix m = new Matrix();
        m.setScale(vw * s / getWidth(), vh * s / getHeight(), getWidth() / 2f, getHeight() / 2f);
        setTransform(m);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        release();
        try {
            mp = new MediaPlayer();
            mp.setDataSource(getContext(), uri);
            mp.setSurface(new Surface(st));
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mp.setOnVideoSizeChangedListener((m, w2, h2) -> { vw = w2; vh = h2; fitVideo(); });
            mp.setOnPreparedListener(m -> m.start());
            mp.prepareAsync();
        } catch (Exception e) {
            release();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { fitVideo(); }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {}
}
