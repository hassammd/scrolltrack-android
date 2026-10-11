package com.hatch.reeltracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Segmented progress ring like the reference design: one arc per app
 * (platform colors), platform dots floating at each segment, total time
 * in the center.
 */
public class RingView extends View {

    private float[] fractions = new float[0];
    private int[] colors = new int[0];
    private String top = "0m";
    private String bottom = "Today time";

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint segPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotOuter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotInner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint botPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public RingView(Context c, AttributeSet a) {
        super(c, a);
        trackPaint.setColor(0xFFEEECFF);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(30f);
        segPaint.setStyle(Paint.Style.STROKE);
        segPaint.setStrokeWidth(30f);
        segPaint.setStrokeCap(Paint.Cap.ROUND);
        dotOuter.setColor(0xFFFFFFFF);
        dotInner.setStyle(Paint.Style.FILL);
        topPaint.setColor(0xFF6257E8);
        topPaint.setTextSize(76f);
        topPaint.setTextAlign(Paint.Align.CENTER);
        topPaint.setFakeBoldText(true);
        botPaint.setColor(0xFF777B8B);
        botPaint.setTextSize(30f);
        botPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setSegments(float[] fractions, int[] colors, String top, String bottom) {
        this.fractions = fractions != null ? fractions : new float[0];
        this.colors = colors != null ? colors : new int[0];
        this.top = top;
        this.bottom = bottom;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - 58f;
        if (r <= 0) return;
        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(oval, 0, 360, false, trackPaint);

        float start = -90f;
        for (int i = 0; i < fractions.length; i++) {
            float f = Math.max(0f, Math.min(1f, fractions[i]));
            if (f <= 0.005f) continue;
            float sweep = 360f * f;
            segPaint.setColor(colors[i % colors.length]);
            // small gap between segments
            c.drawArc(oval, start + 2f, Math.max(1f, sweep - 4f), false, segPaint);
            // dot at segment middle, floating outside the ring
            float mid = (float) Math.toRadians(start + sweep / 2f);
            float dx = cx + (float) ((r + 44f) * Math.cos(mid));
            float dy = cy + (float) ((r + 44f) * Math.sin(mid));
            dotInner.setColor(colors[i % colors.length]);
            c.drawCircle(dx, dy, 21f, dotOuter);
            c.drawCircle(dx, dy, 15f, dotInner);
            start += sweep;
        }

        c.drawText(top, cx, cy + 8f, topPaint);
        c.drawText(bottom, cx, cy + 52f, botPaint);
    }
}
