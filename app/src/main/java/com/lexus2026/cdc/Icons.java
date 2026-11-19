package com.lexus2026.cdc;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Íconos vectoriales dibujados en Java (reemplazan a los drawables XML). Cuadrícula de 24x24. */
public final class Icons {
    public static final int AIRPLANE = 1, BATTERY = 2, BRIGHTNESS = 3, BT = 4, DARK = 5, DATA = 6, DATASAVER = 7, DIM = 8, DND = 9, INVERT = 10, LOCATION = 11, LOCK = 12, MUSIC = 13, NEXT = 14, NFC = 15, NIGHT = 16, PADLOCK = 17, PAUSE = 18, PLAY = 19, POWER = 20, PREV = 21, ROTATE = 22, SCREEN = 23, SCREENSHOT = 24, SOUND = 25, TORCH = 26, VOLUME = 27, WIFI = 28;

    public static final int COUNT = 28;

    /** Por ícono: secuencia de [op, coords...]. op 0 = M(x,y), 1 = L(x,y), 2 = C(x1,y1,x2,y2,x,y), 3 = Z. */
    private static final float[][] D = {
        {0f, 21f, 16f, 1f, 21f, 14f, 1f, 13f, 9f, 1f, 13f, 3.5f, 2f, 13f, 2.67f, 12.33f, 2f, 11.5f, 2f, 2f, 10.67f, 2f, 10f, 2.67f, 10f, 3.5f, 1f, 10f, 9f, 1f, 2f, 14f, 1f, 2f, 16f, 1f, 10f, 13.5f, 1f, 10f, 19f, 1f, 8f, 20.5f, 1f, 8f, 22f, 1f, 11.5f, 21f, 1f, 15f, 22f, 1f, 15f, 20.5f, 1f, 13f, 19f, 1f, 13f, 13.5f, 1f, 21f, 16f, 3f}, // airplane
        {0f, 15.67f, 4f, 1f, 14f, 4f, 1f, 14f, 2f, 1f, 10f, 2f, 1f, 10f, 4f, 1f, 8.33f, 4f, 2f, 7.6f, 4f, 7f, 4.6f, 7f, 5.33f, 1f, 7f, 20.66f, 2f, 7f, 21.4f, 7.6f, 22f, 8.33f, 22f, 1f, 15.66f, 22f, 2f, 16.4f, 22f, 17f, 21.4f, 17f, 20.67f, 1f, 17f, 5.33f, 2f, 17f, 4.6f, 16.4f, 4f, 15.67f, 4f, 3f}, // battery
        {0f, 12f, 7f, 2f, 9.239f, 7f, 7f, 9.239f, 7f, 12f, 2f, 7f, 14.761f, 9.239f, 17f, 12f, 17f, 2f, 14.761f, 17f, 17f, 14.761f, 17f, 12f, 2f, 17f, 9.239f, 14.761f, 7f, 12f, 7f, 3f, 0f, 2f, 13f, 1f, 4f, 13f, 1f, 4f, 11f, 1f, 2f, 11f, 1f, 2f, 13f, 3f, 0f, 20f, 13f, 1f, 22f, 13f, 1f, 22f, 11f, 1f, 20f, 11f, 1f, 20f, 13f, 3f, 0f, 11f, 2f, 1f, 11f, 4f, 1f, 13f, 4f, 1f, 13f, 2f, 1f, 11f, 2f, 3f, 0f, 11f, 20f, 1f, 11f, 22f, 1f, 13f, 22f, 1f, 13f, 20f, 1f, 11f, 20f, 3f, 0f, 5.99f, 4.58f, 1f, 4.58f, 5.99f, 1f, 5.99f, 7.41f, 1f, 7.41f, 5.99f, 1f, 5.99f, 4.58f, 3f, 0f, 18.01f, 16.59f, 1f, 16.59f, 18.01f, 1f, 18.01f, 19.42f, 1f, 19.42f, 18.01f, 1f, 18.01f, 16.59f, 3f, 0f, 18.01f, 7.4f, 1f, 19.42f, 5.99f, 1f, 18.01f, 4.58f, 1f, 16.59f, 5.99f, 1f, 18.01f, 7.4f, 3f, 0f, 5.99f, 19.42f, 1f, 7.41f, 18.01f, 1f, 5.99f, 16.59f, 1f, 4.58f, 18.01f, 1f, 5.99f, 19.42f, 3f}, // brightness
        {0f, 17.71f, 7.71f, 1f, 12f, 2f, 1f, 11f, 2f, 1f, 11f, 9.59f, 1f, 6.41f, 5f, 1f, 5f, 6.41f, 1f, 10.59f, 12f, 1f, 5f, 17.59f, 1f, 6.41f, 19f, 1f, 11f, 14.41f, 1f, 11f, 22f, 1f, 12f, 22f, 1f, 17.71f, 16.29f, 1f, 13.41f, 12f, 1f, 17.71f, 7.71f, 3f, 0f, 13f, 5.83f, 1f, 14.88f, 7.71f, 1f, 13f, 9.59f, 1f, 13f, 5.83f, 3f, 0f, 14.88f, 16.29f, 1f, 13f, 18.17f, 1f, 13f, 14.41f, 1f, 14.88f, 16.29f, 3f}, // bt
        {0f, 12f, 3f, 2f, 7.029f, 3f, 3f, 7.029f, 3f, 12f, 2f, 3f, 16.971f, 7.029f, 21f, 12f, 21f, 2f, 16.971f, 21f, 21f, 16.971f, 21f, 12f, 2f, 21f, 11.54f, 20.96f, 11.08f, 20.9f, 10.64f, 2f, 19.886f, 12.062f, 18.246f, 12.904f, 16.5f, 12.9f, 2f, 14.151f, 12.9f, 12.071f, 11.382f, 11.355f, 9.146f, 2f, 10.638f, 6.909f, 11.448f, 4.465f, 13.36f, 3.1f, 2f, 12.92f, 3.04f, 12.46f, 3f, 12f, 3f, 3f}, // dark
        {0f, 16f, 17.01f, 1f, 16f, 10f, 1f, 14f, 10f, 1f, 14f, 17.01f, 1f, 11f, 17.01f, 1f, 15f, 21f, 1f, 19f, 17.01f, 1f, 16f, 17.01f, 3f, 0f, 9f, 3f, 1f, 5f, 6.99f, 1f, 8f, 6.99f, 1f, 8f, 14f, 1f, 10f, 14f, 1f, 10f, 6.99f, 1f, 13f, 6.99f, 1f, 9f, 3f, 3f}, // data
        {0f, 12f, 2f, 1f, 4f, 5f, 1f, 4f, 11f, 2f, 4f, 16f, 7.4f, 20.7f, 12f, 22f, 2f, 16.6f, 20.7f, 20f, 16f, 20f, 11f, 1f, 20f, 5f, 1f, 12f, 2f, 3f}, // datasaver
        {0f, 12f, 4f, 2f, 7.582f, 4f, 4f, 7.582f, 4f, 12f, 2f, 4f, 16.418f, 7.582f, 20f, 12f, 20f, 2f, 16.418f, 20f, 20f, 16.418f, 20f, 12f, 2f, 20f, 7.582f, 16.418f, 4f, 12f, 4f, 3f, 0f, 12f, 7f, 2f, 14.761f, 7f, 17f, 9.239f, 17f, 12f, 2f, 17f, 14.761f, 14.761f, 17f, 12f, 17f, 2f, 9.239f, 17f, 7f, 14.761f, 7f, 12f, 2f, 7f, 9.239f, 9.239f, 7f, 12f, 7f, 3f}, // dim
        {0f, 12f, 2f, 2f, 6.48f, 2f, 2f, 6.48f, 2f, 12f, 2f, 2f, 17.52f, 6.48f, 22f, 12f, 22f, 2f, 17.52f, 22f, 22f, 17.52f, 22f, 12f, 2f, 22f, 6.48f, 17.52f, 2f, 12f, 2f, 3f, 0f, 17f, 13f, 1f, 7f, 13f, 1f, 7f, 11f, 1f, 17f, 11f, 1f, 17f, 13f, 3f}, // dnd
        {0f, 17.66f, 7.93f, 1f, 12f, 2.27f, 1f, 6.34f, 7.93f, 2f, 3.22f, 11.05f, 3.22f, 16.12f, 6.34f, 19.24f, 2f, 7.9f, 20.8f, 9.95f, 21.58f, 12f, 21.58f, 2f, 14.05f, 21.58f, 16.1f, 20.8f, 17.66f, 19.24f, 2f, 20.78f, 16.12f, 20.78f, 11.05f, 17.66f, 7.93f, 3f, 0f, 12f, 19.59f, 2f, 10.4f, 19.59f, 8.89f, 18.97f, 7.76f, 17.83f, 2f, 6.62f, 16.69f, 6f, 15.19f, 6f, 13.59f, 2f, 6f, 11.99f, 6.62f, 10.48f, 7.76f, 9.35f, 1f, 12f, 5.1f, 1f, 12f, 19.59f, 3f}, // invert
        {0f, 12f, 2f, 2f, 8.13f, 2f, 5f, 5.13f, 5f, 9f, 2f, 5f, 14.25f, 12f, 22f, 12f, 22f, 2f, 12f, 22f, 19f, 14.25f, 19f, 9f, 2f, 19f, 5.13f, 15.87f, 2f, 12f, 2f, 3f, 0f, 12f, 11.5f, 2f, 10.619f, 11.5f, 9.5f, 10.381f, 9.5f, 9f, 2f, 9.5f, 7.619f, 10.619f, 6.5f, 12f, 6.5f, 2f, 13.381f, 6.5f, 14.5f, 7.619f, 14.5f, 9f, 2f, 14.5f, 10.381f, 13.381f, 11.5f, 12f, 11.5f, 3f}, // location
        {0f, 18f, 8f, 1f, 17f, 8f, 1f, 17f, 6f, 2f, 17f, 3.24f, 14.76f, 1f, 12f, 1f, 2f, 9.24f, 1f, 7f, 3.24f, 7f, 6f, 1f, 7f, 8f, 1f, 6f, 8f, 2f, 4.9f, 8f, 4f, 8.9f, 4f, 10f, 1f, 4f, 20f, 2f, 4f, 21.1f, 4.9f, 22f, 6f, 22f, 1f, 18f, 22f, 2f, 19.1f, 22f, 20f, 21.1f, 20f, 20f, 1f, 20f, 10f, 2f, 20f, 8.9f, 19.1f, 8f, 18f, 8f, 3f, 0f, 12f, 17f, 2f, 10.9f, 17f, 10f, 16.1f, 10f, 15f, 2f, 10f, 13.9f, 10.9f, 13f, 12f, 13f, 2f, 13.1f, 13f, 14f, 13.9f, 14f, 15f, 2f, 14f, 16.1f, 13.1f, 17f, 12f, 17f, 3f, 0f, 15.1f, 8f, 1f, 8.9f, 8f, 1f, 8.9f, 6f, 2f, 8.9f, 4.29f, 10.29f, 2.9f, 12f, 2.9f, 2f, 13.71f, 2.9f, 15.1f, 4.29f, 15.1f, 6f, 1f, 15.1f, 8f, 3f}, // lock
        {0f, 12f, 3f, 1f, 12f, 13.55f, 2f, 11.41f, 13.21f, 10.73f, 13f, 10f, 13f, 2f, 7.79f, 13f, 6f, 14.79f, 6f, 17f, 2f, 6f, 19.21f, 7.79f, 21f, 10f, 21f, 2f, 12.21f, 21f, 14f, 19.21f, 14f, 17f, 1f, 14f, 7f, 1f, 18f, 7f, 1f, 18f, 3f, 1f, 12f, 3f, 3f}, // music
        {0f, 6f, 18f, 1f, 14.5f, 12f, 1f, 6f, 6f, 1f, 6f, 18f, 3f, 0f, 16f, 6f, 1f, 16f, 18f, 1f, 18f, 18f, 1f, 18f, 6f, 1f, 16f, 6f, 3f}, // next
        {0f, 20f, 2f, 1f, 4f, 2f, 2f, 2.9f, 2f, 2f, 2.9f, 2f, 4f, 1f, 2f, 20f, 2f, 2f, 21.1f, 2.9f, 22f, 4f, 22f, 1f, 20f, 22f, 2f, 21.1f, 22f, 22f, 21.1f, 22f, 20f, 1f, 22f, 4f, 2f, 22f, 2.9f, 21.1f, 2f, 20f, 2f, 3f, 0f, 20f, 20f, 1f, 4f, 20f, 1f, 4f, 4f, 1f, 20f, 4f, 1f, 20f, 20f, 3f, 0f, 18f, 6f, 1f, 13f, 6f, 2f, 11.9f, 6f, 11f, 6.9f, 11f, 8f, 1f, 11f, 10.28f, 2f, 10.4f, 10.63f, 10f, 11.26f, 10f, 12f, 2f, 10f, 13.1f, 10.9f, 14f, 12f, 14f, 2f, 13.1f, 14f, 14f, 13.1f, 14f, 12f, 2f, 14f, 11.26f, 13.6f, 10.62f, 13f, 10.28f, 1f, 13f, 8f, 1f, 16f, 8f, 1f, 16f, 16f, 1f, 8f, 16f, 1f, 8f, 8f, 1f, 10f, 8f, 1f, 10f, 6f, 1f, 6f, 6f, 1f, 6f, 18f, 1f, 18f, 18f, 1f, 18f, 6f, 3f}, // nfc
        {0f, 12f, 3f, 2f, 7.029f, 3f, 3f, 7.029f, 3f, 12f, 2f, 3f, 16.971f, 7.029f, 21f, 12f, 21f, 2f, 16.971f, 21f, 21f, 16.971f, 21f, 12f, 2f, 21f, 11.54f, 20.96f, 11.08f, 20.9f, 10.64f, 2f, 19.886f, 12.062f, 18.246f, 12.904f, 16.5f, 12.9f, 2f, 14.151f, 12.9f, 12.071f, 11.382f, 11.355f, 9.146f, 2f, 10.638f, 6.909f, 11.448f, 4.465f, 13.36f, 3.1f, 2f, 12.92f, 3.04f, 12.46f, 3f, 12f, 3f, 3f, 0f, 19f, 2f, 1f, 19.8f, 3.7f, 1f, 21.5f, 4.5f, 1f, 19.8f, 5.3f, 1f, 19f, 7f, 1f, 18.2f, 5.3f, 1f, 16.5f, 4.5f, 1f, 18.2f, 3.7f, 3f}, // night
        {0f, 18f, 8f, 1f, 17f, 8f, 1f, 17f, 6f, 2f, 17f, 3.24f, 14.76f, 1f, 12f, 1f, 2f, 9.24f, 1f, 7f, 3.24f, 7f, 6f, 1f, 7f, 8f, 1f, 6f, 8f, 2f, 4.9f, 8f, 4f, 8.9f, 4f, 10f, 1f, 4f, 20f, 2f, 4f, 21.1f, 4.9f, 22f, 6f, 22f, 1f, 18f, 22f, 2f, 19.1f, 22f, 20f, 21.1f, 20f, 20f, 1f, 20f, 10f, 2f, 20f, 8.9f, 19.1f, 8f, 18f, 8f, 3f, 0f, 9f, 6f, 2f, 9f, 4.34f, 10.34f, 3f, 12f, 3f, 2f, 13.66f, 3f, 15f, 4.34f, 15f, 6f, 1f, 15f, 8f, 1f, 9f, 8f, 1f, 9f, 6f, 3f}, // padlock
        {0f, 6f, 19f, 1f, 10f, 19f, 1f, 10f, 5f, 1f, 6f, 5f, 1f, 6f, 19f, 3f, 0f, 14f, 5f, 1f, 14f, 19f, 1f, 18f, 19f, 1f, 18f, 5f, 1f, 14f, 5f, 3f}, // pause
        {0f, 8f, 5f, 1f, 8f, 19f, 1f, 19f, 12f, 3f}, // play
        {0f, 13f, 3f, 1f, 11f, 3f, 1f, 11f, 13f, 1f, 13f, 13f, 1f, 13f, 3f, 3f, 0f, 17.83f, 5.17f, 1f, 16.41f, 6.59f, 2f, 17.99f, 7.86f, 19f, 9.81f, 19f, 12f, 2f, 19f, 15.87f, 15.87f, 19f, 12f, 19f, 2f, 8.13f, 19f, 5f, 15.87f, 5f, 12f, 2f, 5f, 9.81f, 6.01f, 7.86f, 7.58f, 6.58f, 1f, 6.17f, 5.17f, 2f, 4.23f, 6.82f, 3f, 9.26f, 3f, 12f, 2f, 3f, 16.97f, 7.03f, 21f, 12f, 21f, 2f, 16.97f, 21f, 21f, 16.97f, 21f, 12f, 2f, 21f, 9.26f, 19.77f, 6.82f, 17.83f, 5.17f, 3f}, // power
        {0f, 6f, 6f, 1f, 8f, 6f, 1f, 8f, 18f, 1f, 6f, 18f, 3f, 0f, 9.5f, 12f, 1f, 18f, 18f, 1f, 18f, 6f, 3f}, // prev
        {0f, 7.34f, 6.41f, 1f, 0.86f, 12.9f, 1f, 7.35f, 19.38f, 1f, 13.84f, 12.9f, 1f, 7.34f, 6.41f, 3f, 0f, 3.69f, 12.9f, 1f, 7.35f, 9.24f, 1f, 11f, 12.9f, 1f, 7.34f, 16.56f, 1f, 3.69f, 12.9f, 3f, 0f, 19.36f, 6.64f, 2f, 17.61f, 4.88f, 15.3f, 4f, 13f, 4f, 1f, 13f, 0.76f, 1f, 8.76f, 5f, 1f, 13f, 9.24f, 1f, 13f, 6f, 2f, 14.79f, 6f, 16.58f, 6.68f, 17.95f, 8.05f, 2f, 20.68f, 10.78f, 20.68f, 15.22f, 17.95f, 17.95f, 2f, 16.58f, 19.32f, 14.79f, 20f, 13f, 20f, 2f, 12.03f, 20f, 11.06f, 19.79f, 10.16f, 19.39f, 1f, 8.67f, 20.88f, 2f, 10.02f, 21.62f, 11.51f, 22f, 13f, 22f, 2f, 15.3f, 22f, 17.61f, 21.12f, 19.36f, 19.36f, 2f, 22.88f, 15.85f, 22.88f, 10.15f, 19.36f, 6.64f, 3f}, // rotate
        {0f, 17f, 1.01f, 1f, 7f, 1f, 2f, 5.9f, 1f, 5f, 1.9f, 5f, 3f, 1f, 5f, 21f, 2f, 5f, 22.1f, 5.9f, 23f, 7f, 23f, 1f, 17f, 23f, 2f, 18.1f, 23f, 19f, 22.1f, 19f, 21f, 1f, 19f, 3f, 2f, 19f, 1.9f, 18.1f, 1.01f, 17f, 1.01f, 3f, 0f, 17f, 19f, 1f, 7f, 19f, 1f, 7f, 5f, 1f, 17f, 5f, 1f, 17f, 19f, 3f}, // screen
        {0f, 17f, 15f, 1f, 19f, 15f, 1f, 19f, 7f, 2f, 19f, 5.9f, 18.1f, 5f, 17f, 5f, 1f, 9f, 5f, 1f, 9f, 7f, 1f, 17f, 7f, 1f, 17f, 15f, 3f, 0f, 7f, 17f, 1f, 7f, 1f, 1f, 5f, 1f, 1f, 5f, 5f, 1f, 1f, 5f, 1f, 1f, 7f, 1f, 5f, 7f, 1f, 5f, 17f, 2f, 5f, 18.1f, 5.9f, 19f, 7f, 19f, 1f, 17f, 19f, 1f, 17f, 23f, 1f, 19f, 23f, 1f, 19f, 19f, 1f, 23f, 19f, 1f, 23f, 17f, 1f, 7f, 17f, 3f}, // screenshot
        {0f, 12f, 22f, 2f, 13.1f, 22f, 14f, 21.1f, 14f, 20f, 1f, 10f, 20f, 2f, 10f, 21.1f, 10.9f, 22f, 12f, 22f, 3f, 0f, 18f, 16f, 1f, 18f, 11f, 2f, 18f, 7.93f, 16.37f, 5.36f, 13.5f, 4.68f, 1f, 13.5f, 4f, 2f, 13.5f, 3.17f, 12.83f, 2.5f, 12f, 2.5f, 2f, 11.17f, 2.5f, 10.5f, 3.17f, 10.5f, 4f, 1f, 10.5f, 4.68f, 2f, 7.64f, 5.36f, 6f, 7.92f, 6f, 11f, 1f, 6f, 16f, 1f, 4f, 18f, 1f, 4f, 19f, 1f, 20f, 19f, 1f, 20f, 18f, 1f, 18f, 16f, 3f}, // sound
        {0f, 6f, 2f, 1f, 6f, 8f, 1f, 8f, 11f, 1f, 8f, 22f, 1f, 16f, 22f, 1f, 16f, 11f, 1f, 18f, 8f, 1f, 18f, 2f, 1f, 6f, 2f, 3f, 0f, 12f, 12.5f, 2f, 11.17f, 12.5f, 10.5f, 11.83f, 10.5f, 11f, 2f, 10.5f, 10.17f, 11.17f, 9.5f, 12f, 9.5f, 2f, 12.83f, 9.5f, 13.5f, 10.17f, 13.5f, 11f, 2f, 13.5f, 11.83f, 12.83f, 12.5f, 12f, 12.5f, 3f}, // torch
        {0f, 3f, 9f, 1f, 3f, 15f, 1f, 7f, 15f, 1f, 12f, 20f, 1f, 12f, 4f, 1f, 7f, 9f, 1f, 3f, 9f, 3f, 0f, 16.5f, 12f, 2f, 16.488f, 10.302f, 15.521f, 8.755f, 14f, 8f, 1f, 14f, 16f, 2f, 15.526f, 15.251f, 16.495f, 13.7f, 16.5f, 12f, 3f}, // volume
        {0f, 1f, 9f, 1f, 3f, 11f, 2f, 7.97f, 6.03f, 16.03f, 6.03f, 21f, 11f, 1f, 23f, 9f, 2f, 16.93f, 2.93f, 7.08f, 2.93f, 1f, 9f, 3f, 0f, 9f, 17f, 1f, 12f, 20f, 1f, 15f, 17f, 2f, 13.35f, 15.34f, 10.66f, 15.34f, 9f, 17f, 3f, 0f, 5f, 13f, 1f, 7f, 15f, 2f, 9.76f, 12.24f, 14.24f, 12.24f, 17f, 15f, 1f, 19f, 13f, 2f, 15.14f, 9.14f, 8.87f, 9.14f, 5f, 13f, 3f}, // wifi
    };

    private Icons() {}

    /** id 0 = sin ícono (devuelve null, igual que el recurso 0 de antes). */
    public static Drawable get(int id) {
        if (id < 1 || id > COUNT) return null;
        return new Vec(D[id - 1], id == DIM);
    }

    private static final class Vec extends Drawable {
        private final Path path = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color = 0xFFFFFFFF;
        private int alpha = 255;

        Vec(float[] d, boolean evenOdd) {
            int i = 0;
            while (i < d.length) {
                switch ((int) d[i++]) {
                    case 0: path.moveTo(d[i], d[i + 1]); i += 2; break;
                    case 1: path.lineTo(d[i], d[i + 1]); i += 2; break;
                    case 2: path.cubicTo(d[i], d[i + 1], d[i + 2], d[i + 3], d[i + 4], d[i + 5]); i += 6; break;
                    default: path.close();
                }
            }
            if (evenOdd) path.setFillType(Path.FillType.EVEN_ODD);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
        }

        private void paintColor() {
            int a = ((color >>> 24) * alpha) / 255;
            paint.setColor((color & 0x00FFFFFF) | (a << 24));
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            int s = c.save();
            c.translate(b.left, b.top);
            c.scale(b.width() / 24f, b.height() / 24f);
            c.drawPath(path, paint);
            c.restoreToCount(s);
        }

        @Override
        public void setTintList(ColorStateList t) {
            color = t != null ? t.getDefaultColor() : 0xFFFFFFFF;
            paintColor();
            invalidateSelf();
        }

        @Override public void setAlpha(int a) { alpha = a; paintColor(); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); invalidateSelf(); }
        @SuppressWarnings("deprecation")
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return Math.round(24f * Resources.getSystem().getDisplayMetrics().density); }
        @Override public int getIntrinsicHeight() { return getIntrinsicWidth(); }
    }
}
