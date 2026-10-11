package com.hatch.reeltracker;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Onboarding hero illustration (Figma 01): faint halo, gradient ring with
 * pause medallion in the center, floating orange/green dots and a sparkle.
 */
public class HeroRingView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HeroRingView(Context c) { super(c); }
    public HeroRingView(Context c, AttributeSet a) { super(c, a); }

    private boolean night() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float cx = w / 2f, cy = h / 2f;
        float R = Math.min(w, h) / 2f;
        boolean n = night();

        p.setShader(null);
        p.setStyle(Paint.Style.FILL);

        // soft ground shadow
        p.setColor(n ? 0x40000000 : 0x14000000);
        cv.drawOval(new RectF(cx - 0.42f * R, cy + 0.66f * R,
                cx + 0.42f * R, cy + 0.76f * R), p);

        // faint halo behind the ring
        p.setColor(n ? 0xFF26224A : 0xFFEDE9FF);
        cv.drawCircle(cx, cy, 0.94f * R, p);

        // inner disc
        p.setColor(n ? 0xFF17171F : 0xFFFFFFFF);
        cv.drawCircle(cx, cy, 0.50f * R, p);

        // gradient ring
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.22f * R);
        p.setShader(new LinearGradient(0, cy - R, 0, cy + R,
                n ? 0xFF9A86FF : 0xFFB3A4FF,
                n ? 0xFF5B4BD6 : 0xFF6A5AE0,
                Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, 0.59f * R, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);

        // center medallion (chip with soft shadow)
        float ch = 0.19f * R;
        RectF chip = new RectF(cx - ch, cy - ch, cx + ch, cy + ch);
        RectF sh = new RectF(chip);
        sh.offset(0, 0.03f * R);
        p.setColor(n ? 0x30000000 : 0x18000000);
        cv.drawRoundRect(sh, 0.09f * R, 0.09f * R, p);
        p.setColor(n ? 0xFFF0EDFF : 0xFFFFFFFF);
        cv.drawRoundRect(chip, 0.09f * R, 0.09f * R, p);

        // pause bars
        p.setColor(0xFF6A5AE0);
        p.setStrokeWidth(0.055f * R);
        p.setStrokeCap(Paint.Cap.ROUND);
        float bx = 0.055f * R, bh = 0.10f * R;
        cv.drawLine(cx - bx, cy - bh, cx - bx, cy + bh, p);
        cv.drawLine(cx + bx, cy - bh, cx + bx, cy + bh, p);
        p.setStyle(Paint.Style.FILL);

        // floating accents
        dot(cv, cx + 0.60f * R, cy - 0.55f * R, 0.078f * R, 0xFFFFA94D); // orange
        dot(cv, cx - 0.62f * R, cy + 0.45f * R, 0.066f * R, 0xFF45D483); // green
        dot(cv, cx + 0.85f * R, cy + 0.10f * R, 0.024f * R, 0xFF8B7CFF); // tiny dot
        sparkle(cv, cx - 0.80f * R, cy - 0.55f * R, 0.055f * R, 0xFF8B7CFF);
    }

    private void dot(Canvas cv, float x, float y, float r, int c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(c);
        cv.drawCircle(x, y, r, p);
    }

    private void sparkle(Canvas cv, float x, float y, float s, int c) {
        Path path = new Path();
        float inner = s * 0.32f;
        for (int i = 0; i < 8; i++) {
            float ang = (float) (Math.PI / 4 * i - Math.PI / 2);
            float r = (i % 2 == 0) ? s : inner;
            float px = x + (float) Math.cos(ang) * r;
            float py = y + (float) Math.sin(ang) * r;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(c);
        cv.drawPath(path, p);
    }
}
