package com.hatch.reeltracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Stylized flame hero for the streak celebration (Figma 05):
 * soft circle backdrop, layered gradient flame, sparkle, dot.
 */
public class FlameView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FlameView(Context c) { super(c); }
    public FlameView(Context c, AttributeSet a) { super(c, a); }

    private Path teardrop(float cx, float tipY, float r, float bottomY) {
        Path p = new Path();
        p.moveTo(cx, tipY);
        p.cubicTo(cx + r, tipY + r * 1.15f, cx + r, bottomY - r * 0.55f, cx, bottomY);
        p.cubicTo(cx - r, bottomY - r * 0.55f, cx - r, tipY + r * 1.15f, cx, tipY);
        p.close();
        return p;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float cx = w * 0.5f, cy = h * 0.52f;
        float s = Math.min(w, h) / 260f; // design unit

        boolean dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        // soft backdrop circle
        paint.setShader(null);
        paint.setColor(dark ? 0xFF26224A : 0xFFF1F0FF);
        canvas.drawCircle(cx, cy, 118 * s, paint);

        // left lick
        paint.setColor(0xFFFF8A3D);
        canvas.drawPath(teardrop(cx - 34 * s, 78 * s, 22 * s, 178 * s), paint);

        // main flame body
        paint.setShader(new LinearGradient(0, 40 * s, 0, 200 * s,
                0xFFFFB03D, 0xFFFF5E62, Shader.TileMode.CLAMP));
        canvas.drawPath(teardrop(cx + 6 * s, 44 * s, 44 * s, 198 * s), paint);
        paint.setShader(null);

        // inner flame
        paint.setShader(new LinearGradient(0, 100 * s, 0, 200 * s,
                0xFFFFE29A, 0xFFFFA94D, Shader.TileMode.CLAMP));
        canvas.drawPath(teardrop(cx + 4 * s, 104 * s, 24 * s, 192 * s), paint);
        paint.setShader(null);

        // core glow
        paint.setColor(0xFFFFF6DE);
        canvas.drawPath(teardrop(cx + 4 * s, 138 * s, 12 * s, 186 * s), paint);

        // sparkle top-left
        paint.setColor(dark ? 0xFFB7A8FF : 0xFF7C6FF0);
        paint.setStrokeWidth(3.2f * s);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float sx = cx - 108 * s, sy = cy - 84 * s, r = 9 * s;
        canvas.drawLine(sx - r, sy, sx + r, sy, paint);
        canvas.drawLine(sx, sy - r, sx, sy + r, paint);
        canvas.drawLine(sx - r * 0.55f, sy - r * 0.55f, sx + r * 0.55f, sy + r * 0.55f, paint);
        canvas.drawLine(sx - r * 0.55f, sy + r * 0.55f, sx + r * 0.55f, sy - r * 0.55f, paint);

        // small dot right
        canvas.drawCircle(cx + 112 * s, cy + 6 * s, 5 * s, paint);
    }
}
