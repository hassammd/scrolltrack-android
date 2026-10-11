package com.hatch.reeltracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Flexible bar chart. Each bar can have stacked segments.
 * If dimOthers is true, every bar except highlightIndex is drawn in a
 * light tone and only the highlighted bar uses the segment colors.
 */
public class BarsView extends View {

    private String[] labels = new String[0];
    private String[] sublabels = null;
    private long[][] segs = new long[0][];
    private int[] segColors = new int[]{0xFF6257E8};
    private int highlight = -1;
    private boolean dimOthers = false;
    private boolean showValues = false;
    private boolean yAxis = false;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int DIM = 0xFFE4E2FA;
    private static final int STUB = 0xFFE9EAF2;

    public BarsView(Context c, AttributeSet a) {
        super(c, a);
        gridPaint.setColor(0xFFE9EAF2);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{8f, 8f}, 0));
        textPaint.setColor(0xFF777B8B);
        textPaint.setTextSize(24f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        valPaint.setColor(0xFF191B2A);
        valPaint.setTextSize(24f);
        valPaint.setTextAlign(Paint.Align.CENTER);
        valPaint.setFakeBoldText(true);
    }

    public void setData(String[] labels, long[][] segs, int[] segColors,
                        int highlightIndex, boolean dimOthers, boolean showValues) {
        setData(labels, null, segs, segColors, highlightIndex, dimOthers, showValues);
    }

    public void setData(String[] labels, String[] sublabels, long[][] segs, int[] segColors,
                        int highlightIndex, boolean dimOthers, boolean showValues) {
        this.labels = labels != null ? labels : new String[0];
        this.sublabels = sublabels;
        this.segs = segs != null ? segs : new long[0][];
        if (segColors != null && segColors.length > 0) this.segColors = segColors;
        this.highlight = highlightIndex;
        this.dimOthers = dimOthers;
        this.showValues = showValues;
        invalidate();
    }

    /** Draw time labels (2h/1h/0) on the gridlines, like the Insights Figma. */
    public void setYAxis(boolean b) {
        yAxis = b;
        invalidate();
    }

    private boolean night() {
        return (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static String fmtY(long secs) {
        if (secs >= 3600) return (secs / 3600) + "h";
        if (secs >= 60) return (secs / 60) + "m";
        return "0";
    }

    private long total(int i) {
        long t = 0;
        if (i < segs.length && segs[i] != null)
            for (long v : segs[i]) t += v;
        return t;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int n = Math.max(labels.length, segs.length);
        if (n == 0) return;
        long max = 1;
        for (int i = 0; i < n; i++) max = Math.max(max, total(i));

        float w = getWidth(), h = getHeight();
        float labelH = (sublabels != null) ? 62f : 40f;
        float topPad = showValues ? 34f : 12f;
        float plotH = h - labelH - topPad;
        float baseY = topPad + plotH;
        float leftPad = yAxis ? 64f : 0f;
        boolean nt = night();

        gridPaint.setColor(nt ? 0xFF2A2A35 : 0xFFE9EAF2);
        textPaint.setColor(nt ? 0xFFA7A9B8 : 0xFF777B8B);

        // gridlines (+ optional y-axis time labels)
        for (int g = 0; g <= 3; g++) {
            float y = topPad + plotH * g / 3f;
            c.drawLine(leftPad, y, w, y, gridPaint);
            if (yAxis) {
                long v = max * (3 - g) / 3;
                c.drawText(fmtY(v), 32f, y + 8f, textPaint);
            }
        }

        float slot = (w - leftPad) / n;
        float barW = Math.min(slot * 0.62f, 60f);
        float radius = Math.min(16f, barW / 2f);

        for (int i = 0; i < n; i++) {
            float cx = leftPad + slot * i + slot / 2f;
            long t = total(i);
            float left = cx - barW / 2f, right = cx + barW / 2f;

            if (t == 0) {
                c.drawRoundRect(new RectF(left, baseY - 8f, right, baseY), 6f, 6f, stubPaint());
                drawLabel(c, i, cx, h);
                continue;
            }
            float bh = plotH * t / max;
            float top = baseY - bh;
            boolean dimmed = dimOthers && highlight >= 0 && i != highlight;

            float y = baseY;
            int sCount = (i < segs.length && segs[i] != null) ? segs[i].length : 0;
            for (int s = 0; s < Math.max(sCount, 1); s++) {
                long sv = sCount > 0 ? segs[i][s] : t;
                if (sv <= 0) continue;
                float sh = bh * sv / t;
                int color = dimmed ? DIM : segColors[s % segColors.length];
                barPaint.setColor(color);
                c.save();
                c.clipRect(left, y - sh, right, y);
                c.drawRoundRect(new RectF(left, top, right, baseY), radius, radius, barPaint);
                c.restore();
                y -= sh;
            }
            if (showValues) c.drawText(String.valueOf(t), cx, top - 8f, valPaint);
            drawLabel(c, i, cx, h);
        }
    }

    private final Paint stubP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint stubPaint() {
        stubP.setColor(STUB);
        return stubP;
    }

    private void drawLabel(Canvas c, int i, float cx, float h) {
        String lab = (i < labels.length && labels[i] != null) ? labels[i] : "";
        boolean hl = (i == highlight);
        if (sublabels != null && i < sublabels.length && sublabels[i] != null
                && !sublabels[i].isEmpty()) {
            // two-line: number on top, day below (like the reference)
            if (!lab.isEmpty()) {
                valPaint.setColor(0xFF777B8B);
                valPaint.setFakeBoldText(false);
                c.drawText(lab, cx, h - 32f, valPaint);
                valPaint.setColor(0xFF191B2A);
                valPaint.setFakeBoldText(true);
            }
            textPaint.setTextSize(20f);
            c.drawText(sublabels[i], cx, h - 10f, textPaint);
            textPaint.setTextSize(24f);
        } else if (!lab.isEmpty()) {
            if (hl) {
                int prev = textPaint.getColor();
                textPaint.setColor(0xFF6257E8);
                textPaint.setFakeBoldText(true);
                c.drawText(lab, cx, h - 10f, textPaint);
                textPaint.setFakeBoldText(false);
                textPaint.setColor(prev);
            } else {
                c.drawText(lab, cx, h - 10f, textPaint);
            }
        }
    }
}
