package com.blackfalcon.gnss;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Sky plot: the satellites where they are in the sky. In compass mode the plot turns with the phone. */
public class SkyView extends View {

    public interface Listener { void onSelect(Sat s); }

    private static final int BG = 0xFF02030A, LINE = 0xFF3A3A60, GREEN = 0xFF2ECC71, RED = 0xFFFF3B30;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Sat> sats = new ArrayList<>();
    private Set<Integer> hidden = new HashSet<>();
    private float heading = 0f;
    private boolean compass = false;
    private int selType = -1, selSvid = -1;
    private Listener listener;

    private float cx, cy, R;
    private final float[] hx = new float[256], hy = new float[256];
    private final Sat[] hs = new Sat[256];
    private int hn = 0;

    public SkyView(Context c) { super(c); tp.setTextAlign(Paint.Align.CENTER); }

    public void setListener(Listener l) { listener = l; }
    public void setSats(List<Sat> s) { sats = s; invalidate(); }
    public void setHidden(Set<Integer> h) { hidden = h; invalidate(); }
    public void setHeading(float h) { heading = h; if (compass) invalidate(); }
    public void setCompass(boolean on) { compass = on; invalidate(); }
    public void setSelected(int type, int svid) { selType = type; selSvid = svid; invalidate(); }

    private float px(float r, double deg) { return cx + r * (float) Math.sin(Math.toRadians(deg)); }
    private float py(float r, double deg) { return cy - r * (float) Math.cos(Math.toRadians(deg)); }

    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        tp.setTextSize(size);
        tp.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(size * 0.2f);
        tp.setColor(BG);
        c.drawText(s, x, y, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(color);
        c.drawText(s, x, y, tp);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight(), unit = Math.min(w, h);
        cx = w / 2f; cy = h / 2f; R = unit * 0.38f;
        c.drawColor(BG);
        float rot = compass ? -heading : 0f;

        // elevation rings: 0 (horizon), 30, 60 degrees
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(unit * 0.006f);
        p.setColor(Color.WHITE);
        c.drawCircle(cx, cy, R, p);
        p.setStrokeWidth(unit * 0.003f);
        p.setColor(LINE);
        c.drawCircle(cx, cy, R * 2f / 3f, p);
        c.drawCircle(cx, cy, R / 3f, p);
        for (int d = 0; d < 360; d += 45) {
            c.drawLine(cx, cy, px(R, d + rot), py(R, d + rot), p);
        }
        text(c, "30\u00B0", cx + unit * 0.012f, cy - R * 2f / 3f + unit * 0.03f, unit * 0.024f, 0xFF8A8AA8, false);
        text(c, "60\u00B0", cx + unit * 0.012f, cy - R / 3f + unit * 0.03f, unit * 0.024f, 0xFF8A8AA8, false);

        // compass letters (they turn with the plot)
        String[] card = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            double a = i * 90 + rot;
            float size = i == 0 ? R * 0.17f : R * 0.12f;
            text(c, card[i], px(R * 1.13f, a), py(R * 1.13f, a) + size * 0.35f, size, i == 0 ? RED : Color.WHITE, true);
        }

        hn = 0;
        // weak ones first so strong ones sit on top
        for (int pass = 0; pass < 2; pass++) {
            for (Sat s : sats) {
                if (hidden.contains(s.type) || !s.hasPosition() || s.el < 0) continue;
                if ((pass == 0) == s.used) continue;
                float r = R * (1f - Math.max(0f, Math.min(90f, s.el)) / 90f);
                double ang = s.az + rot;
                float x = px(r, ang), y = py(r, ang);
                float rad = unit * (0.026f + 0.020f * Math.min(1f, s.cn0 / 50f));
                int col = Gnss.color(s.type);
                p.setStyle(Paint.Style.FILL);
                p.setColor(s.used ? col : 0xFF0D0D1A);
                c.drawCircle(x, y, rad, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(unit * 0.005f);
                p.setColor(col);
                c.drawCircle(x, y, rad, p);
                if (s.type == selType && s.svid == selSvid) {
                    p.setStrokeWidth(unit * 0.006f);
                    p.setColor(Color.WHITE);
                    c.drawCircle(x, y, rad + unit * 0.012f, p);
                }
                p.setStyle(Paint.Style.FILL);
                tp.setTextAlign(Paint.Align.CENTER);
                text(c, String.valueOf(s.svid), x, y + unit * 0.01f, unit * 0.026f, s.used ? Color.BLACK : Color.WHITE, true);
                // the flag of the satellite's country, next to it
                tp.setStyle(Paint.Style.FILL);
                tp.setTypeface(Typeface.DEFAULT);
                tp.setTextSize(unit * 0.034f);
                tp.setColor(Color.WHITE);
                c.drawText(Gnss.flagOf(s.type, s.svid), x + rad * 0.95f, y - rad * 0.55f, tp);
                if (hn < hx.length) { hx[hn] = x; hy[hn] = y; hs[hn] = s; hn++; }
            }
        }

        // you (the centre = straight up)
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        c.drawCircle(cx, cy, unit * 0.008f, p);

        // where the phone points (compass mode)
        if (compass) {
            Path tri = new Path();
            float tw = unit * 0.028f, ty = cy - R * 1.38f;
            tri.moveTo(cx, ty + tw * 1.5f);
            tri.lineTo(cx - tw, ty);
            tri.lineTo(cx + tw, ty);
            tri.close();
            p.setColor(GREEN);
            c.drawPath(tri, p);
            text(c, "\u2191 phone points here", cx, unit * 0.03f, unit * 0.026f, GREEN, false);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float unit = Math.min(getWidth(), getHeight());
            Sat best = null;
            float bestD = unit * 0.07f;
            for (int i = 0; i < hn; i++) {
                float d = (float) Math.hypot(e.getX() - hx[i], e.getY() - hy[i]);
                if (d < bestD) { bestD = d; best = hs[i]; }
            }
            if (best != null) {
                selType = best.type; selSvid = best.svid;
                invalidate();
                if (listener != null) listener.onSelect(best);
            }
            return true;
        }
        return true;
    }
}
