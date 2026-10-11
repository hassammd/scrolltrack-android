package com.hatch.reeltracker;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Daily-goal progress ring (Figma 02): track + rounded purple progress arc. */
public class GoalRingView extends View {

    private float progress = 0f; // 0..1 used fraction of the daily goal
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GoalRingView(Context c) { super(c); }
    public GoalRingView(Context c, AttributeSet a) { super(c, a); }

    public void setProgress(float pr) {
        progress = Math.max(0f, Math.min(1f, pr));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float cx = w / 2f, cy = h / 2f;
        float R = Math.min(w, h) / 2f;
        float sw = 0.13f * R;
        RectF oval = new RectF(cx - R + sw / 2f, cy - R + sw / 2f,
                cx + R - sw / 2f, cy + R - sw / 2f);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setStrokeCap(Paint.Cap.ROUND);

        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        p.setColor(night ? 0xFF2A2A35 : 0xFFECECF1);
        cv.drawArc(oval, 0, 360, false, p);

        if (progress > 0.005f) {
            p.setColor(0xFF6257E8);
            cv.drawArc(oval, -90, 360 * progress, false, p);
        }
    }
}
