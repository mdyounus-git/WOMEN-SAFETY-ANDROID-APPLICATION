package com.example.womensafety.circle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws a QR code with rounded dots and soft finder "eyes" in the brand colour, instead of the
 * usual hard black squares. Still scans like any normal QR code.
 */
public final class QrRenderer {

    private QrRenderer() {
    }

    @NonNull
    public static Bitmap render(@NonNull String content, int sizePx, @ColorInt int ink,
                                @ColorInt int background) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 0);
        BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints);

        int modules = m.getWidth();
        int quiet = 2; // modules of padding
        float cell = (float) sizePx / (modules + quiet * 2);
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(background);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(ink);
        RectF r = new RectF();
        float dotInset = cell * 0.08f;
        for (int y = 0; y < modules; y++) {
            for (int x = 0; x < modules; x++) {
                if (!m.get(x, y) || isInFinder(x, y, modules)) continue;
                float left = (x + quiet) * cell + dotInset;
                float top = (y + quiet) * cell + dotInset;
                r.set(left, top, left + cell - 2 * dotInset, top + cell - 2 * dotInset);
                c.drawRoundRect(r, cell * 0.38f, cell * 0.38f, p);
            }
        }
        drawEye(c, p, quiet, quiet, cell, ink, background);
        drawEye(c, p, quiet + modules - 7, quiet, cell, ink, background);
        drawEye(c, p, quiet, quiet + modules - 7, cell, ink, background);
        return bmp;
    }

    private static boolean isInFinder(int x, int y, int n) {
        return (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7);
    }

    private static void drawEye(Canvas c, Paint p, int mx, int my, float cell, int ink, int bg) {
        RectF outer = new RectF(mx * cell, my * cell, (mx + 7) * cell, (my + 7) * cell);
        p.setColor(ink);
        c.drawRoundRect(outer, cell * 2.2f, cell * 2.2f, p);
        RectF gap = new RectF(outer.left + cell, outer.top + cell, outer.right - cell, outer.bottom - cell);
        p.setColor(bg);
        c.drawRoundRect(gap, cell * 1.6f, cell * 1.6f, p);
        RectF core = new RectF(outer.left + 2 * cell, outer.top + 2 * cell, outer.right - 2 * cell,
                outer.bottom - 2 * cell);
        p.setColor(ink);
        c.drawRoundRect(core, cell * 1.1f, cell * 1.1f, p);
        p.setColor(Color.BLACK);
    }
}
