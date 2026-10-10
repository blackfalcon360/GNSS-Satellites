package com.blackfalcon.gnss;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class MainActivity extends Activity implements SensorEventListener {

    private static final int GREEN = 0xFF2ECC71, GRAY = 0xFF9E9E9E, CARD = 0xFF0C0C1E, EDGE = 0xFF3A3A60, AMBER = 0xFFFFB300;

    private LocationManager lm;
    private SensorManager sm;
    private Sensor rotSensor;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<Sat> sats = new ArrayList<>();
    private final Set<Integer> hidden = new HashSet<>();
    private Location loc;
    private int ttffMs = -1;
    private boolean gpsOn = true, running = false, compassOn = false;
    private float heading = 0f, declination = 0f;
    private int tab = 0;

    private final float[] rotMat = new float[9], remapped = new float[9], orient = new float[3];
    private float sinAvg = 0f, cosAvg = 1f;
    private boolean firstSample = true;

    private SkyView sky;
    private TextView detailTv, statusTv, compassBtn, fixTv, hwTv;
    private LinearLayout chipRow, skyPage, infoPage;
    private ListView listView;
    private SatAdapter adapter;
    private final TextView[] tabs = new TextView[3];
    private final List<Sat> shown = new ArrayList<>();

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ------------------------------------------------------------ callbacks
    private final GnssStatus.Callback gnssCb = new GnssStatus.Callback() {
        @Override
        public void onSatelliteStatusChanged(GnssStatus st) {
            List<Sat> out = new ArrayList<>();
            for (int i = 0; i < st.getSatelliteCount(); i++) {
                Sat s = new Sat();
                s.type = st.getConstellationType(i);
                s.svid = st.getSvid(i);
                s.cn0 = st.getCn0DbHz(i);
                s.az = st.getAzimuthDegrees(i);
                s.el = st.getElevationDegrees(i);
                s.used = st.usedInFix(i);
                if (Build.VERSION.SDK_INT >= 26 && st.hasCarrierFrequencyHz(i)) s.freqMHz = st.getCarrierFrequencyHz(i) / 1e6f;
                out.add(s);
            }
            sats = out;
            refresh();
        }

        @Override public void onFirstFix(int ttffMillis) { ttffMs = ttffMillis; refresh(); }
    };

    private final LocationListener locListener = new LocationListener() {
        @Override public void onLocationChanged(Location l) {
            loc = l;
            declination = new GeomagneticField((float) l.getLatitude(), (float) l.getLongitude(), (float) l.getAltitude(), System.currentTimeMillis()).getDeclination();
            refresh();
        }
        @Override public void onProviderEnabled(String p) { checkGps(); refresh(); }
        @Override public void onProviderDisabled(String p) { checkGps(); refresh(); }
        @Override public void onStatusChanged(String p, int s, Bundle e) { }
    };

    // ------------------------------------------------------------- lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotSensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        buildUi();
        if (!hasPerm()) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }

    @Override protected void onResume() { super.onResume(); start(); }
    @Override protected void onPause() { super.onPause(); stop(); }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        start();
        refresh();
    }

    private boolean hasPerm() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkGps() {
        try { gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception e) { gpsOn = false; }
    }

    private void start() {
        if (running || !hasPerm()) { refresh(); return; }
        checkGps();
        try {
            lm.registerGnssStatusCallback(gnssCb, handler);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locListener);
            running = true;
        } catch (SecurityException ignored) { }
        if (compassOn && rotSensor != null) sm.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_GAME);
        refresh();
    }

    private void stop() {
        if (running) {
            try {
                lm.unregisterGnssStatusCallback(gnssCb);
                lm.removeUpdates(locListener);
            } catch (SecurityException ignored) { }
            running = false;
        }
        sm.unregisterListener(this);
    }

    // -------------------------------------------------------------------- UI
    private TextView tv(int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(r));
        return g;
    }

    private LinearLayout card(int stroke) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(round(CARD, stroke, 16));
        return c;
    }

    private LinearLayout.LayoutParams lp(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(top), 0, 0);
        return p;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF02030A);

        // header
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(10), dp(10), dp(2));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = tv(21, Color.WHITE, true);
        title.setText("\uD83D\uDEF0 GNSS Satellites");
        statusTv = tv(12, GRAY, false);
        titles.addView(title);
        titles.addView(statusTv);
        compassBtn = tv(22, Color.WHITE, false);
        compassBtn.setText("\uD83E\uDDED");
        compassBtn.setGravity(Gravity.CENTER);
        compassBtn.setClickable(true);
        compassBtn.setOnClickListener(v -> toggleCompass());
        head.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(compassBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        styleCompassBtn();

        // constellation chips (tap to hide / show one system)
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        chipRow.setPadding(dp(8), dp(6), dp(8), dp(4));
        hs.addView(chipRow);

        // tabs
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding(dp(10), dp(2), dp(10), dp(6));
        String[] names = {"\uD83C\uDF10 Sky", "\uD83D\uDCCB List", "\u2139\uFE0F Info"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            tabs[i] = tv(14, GREEN, true);
            tabs[i].setText(names[i]);
            tabs[i].setGravity(Gravity.CENTER);
            tabs[i].setPadding(dp(4), dp(9), dp(4), dp(9));
            tabs[i].setClickable(true);
            tabs[i].setOnClickListener(v -> setTab(idx));
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            l.setMargins(dp(3), 0, dp(3), 0);
            tabRow.addView(tabs[i], l);
        }

        // sky page
        ScrollView skyScroll = new ScrollView(this);
        skyPage = new LinearLayout(this);
        skyPage.setOrientation(LinearLayout.VERTICAL);
        skyPage.setPadding(dp(10), 0, dp(10), dp(10));
        sky = new SkyView(this);
        sky.setListener(s -> showDetail(s));
        int side = getResources().getDisplayMetrics().widthPixels - dp(20);
        skyPage.addView(sky, new LinearLayout.LayoutParams(side, side));
        LinearLayout dc = card(EDGE);
        detailTv = tv(14, Color.WHITE, false);
        detailTv.setLineSpacing(0, 1.15f);
        dc.addView(detailTv);
        skyPage.addView(dc, lp(6));
        skyScroll.addView(skyPage);

        // list page
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setPadding(dp(10), 0, dp(10), dp(6));
        listView.setClipToPadding(false);
        adapter = new SatAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((p, v, pos, id) -> { showDetail(shown.get(pos)); setTab(0); });

        // info page
        ScrollView infoScroll = new ScrollView(this);
        infoPage = buildInfo();
        infoScroll.addView(infoPage);

        // the three pages share one frame
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(skyScroll);
        frame.addView(listView);
        frame.addView(infoScroll);
        skyScroll.setTag("sky");
        listView.setTag("list");
        infoScroll.setTag("info");

        TextView credit = tv(12, Color.WHITE, true);
        credit.setText("By: Black Falcon \uD83E\uDD85");
        credit.setGravity(Gravity.RIGHT);
        credit.setPadding(dp(14), dp(4), dp(14), dp(6));

        root.addView(head);
        root.addView(hs);
        root.addView(tabRow);
        root.addView(frame, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(credit);
        setContentView(root);
        pages[0] = skyScroll; pages[1] = listView; pages[2] = infoScroll;
        setTab(0);
        showDetail(null);
    }

    private final View[] pages = new View[3];

    private void setTab(int t) {
        tab = t;
        for (int i = 0; i < 3; i++) {
            pages[i].setVisibility(i == t ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(i == t ? Color.BLACK : GREEN);
            tabs[i].setBackground(round(i == t ? GREEN : 0xFF101028, GREEN, 18));
        }
    }

    private void styleCompassBtn() {
        compassBtn.setBackground(round(compassOn ? GREEN : 0xFF101028, GREEN, 24));
    }

    private void toggleCompass() {
        compassOn = !compassOn;
        styleCompassBtn();
        sky.setCompass(compassOn);
        if (compassOn) {
            if (rotSensor == null) { detailTv.setText("This phone has no compass sensor, so the plot cannot turn."); }
            else sm.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            sm.unregisterListener(this);
        }
        showDetail(null);
    }

    // ------------------------------------------------------------ info page
    private LinearLayout buildInfo() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(4), dp(12), dp(12));

        LinearLayout fix = card(GREEN);
        TextView ft = tv(16, GREEN, true);
        ft.setText("\uD83D\uDCCD Your position fix");
        fixTv = tv(14, Color.WHITE, false);
        fixTv.setLineSpacing(0, 1.15f);
        fixTv.setPadding(0, dp(6), 0, 0);
        hwTv = tv(12, GRAY, false);
        hwTv.setPadding(0, dp(8), 0, 0);
        fix.addView(ft);
        fix.addView(fixTv);
        fix.addView(hwTv);
        box.addView(fix);

        TextView h = tv(16, Color.WHITE, true);
        h.setText("The satellite systems");
        h.setPadding(dp(2), dp(14), 0, dp(2));
        box.addView(h);

        String[][] sys = {
                {String.valueOf(Gnss.GPS), "Operated by the U.S. Space Force. Medium Earth orbit, about 20,200 km up. Signals: L1 1575.42, L2 1227.60, L5 1176.45 MHz."},
                {String.valueOf(Gnss.GLONASS), "Operated by Russia's Roscosmos. About 19,100 km up. Signals: G1 near 1602 MHz and G2 near 1246 MHz (each satellite has its own channel)."},
                {String.valueOf(Gnss.GALILEO), "The European Union's system (EUSPA with ESA). About 23,222 km up. Signals: E1 1575.42, E5a 1176.45, E5b 1207.14 MHz."},
                {String.valueOf(Gnss.BEIDOU), "China's system. A mix of medium-orbit (about 21,500 km), inclined geosynchronous and geostationary satellites. Works worldwide since 2020 (BDS-3). Signals: B1I 1561.098, B1C 1575.42, B2a 1176.45, B3 1268.52 MHz."},
                {String.valueOf(Gnss.QZSS), "Japan's regional system \"Michibiki\". Its orbits keep satellites high above Japan, East Asia and Oceania. Sends GPS-compatible L1, L2 and L5 signals."},
                {String.valueOf(Gnss.IRNSS), "NavIC (IRNSS), India's regional system run by ISRO. Geostationary and inclined geosynchronous satellites covering India and about 1,500 km around it. Signals: L5 1176.45 and S-band 2492.028 MHz."},
                {String.valueOf(Gnss.SBAS), "Satellite-based augmentation: WAAS \uD83C\uDDFA\uD83C\uDDF8, EGNOS \uD83C\uDDEA\uD83C\uDDFA, MSAS \uD83C\uDDEF\uD83C\uDDF5, GAGAN \uD83C\uDDEE\uD83C\uDDF3, SDCM \uD83C\uDDF7\uD83C\uDDFA, BDSBAS \uD83C\uDDE8\uD83C\uDDF3. Geostationary satellites that send corrections to make positions more accurate. The system name of an SBAS satellite is guessed from its number."}};
        for (String[] s : sys) {
            int t = Integer.parseInt(s[0]);
            LinearLayout c = card(Gnss.color(t));
            TextView n = tv(15, Gnss.color(t), true);
            n.setText(Gnss.flag(t) + "  " + Gnss.name(t) + (t == Gnss.SBAS ? "" : " \u2014 " + Gnss.country(t)));
            TextView d = tv(13, Color.WHITE, false);
            d.setText(s[1]);
            d.setPadding(0, dp(4), 0, 0);
            c.addView(n);
            c.addView(d);
            box.addView(c, lp(8));
        }

        TextView tips = tv(12, GRAY, false);
        tips.setText("Tips: go outside or next to a window; a fix needs at least 4 satellites used. "
                + "A signal above about 35 dB-Hz is good, below 20 is weak. "
                + "Which systems appear depends on your phone's GNSS chip. "
                + "Filled circles on the sky plot are used for your position; empty ones are seen but not used.");
        tips.setPadding(dp(4), dp(12), dp(4), dp(8));
        box.addView(tips);
        return box;
    }

    // ---------------------------------------------------------------- update
    private int countUsed(List<Sat> l) { int n = 0; for (Sat s : l) if (s.used) n++; return n; }

    private void refresh() {
        // status line
        if (!hasPerm()) {
            statusTv.setText("Allow the Location permission to read the satellites");
            statusTv.setTextColor(AMBER);
        } else if (!gpsOn) {
            statusTv.setText("Location (GPS) is switched off \u2014 turn it on in your phone settings");
            statusTv.setTextColor(AMBER);
        } else {
            statusTv.setText(sats.size() + " satellites in view \u2022 " + countUsed(sats) + " used"
                    + (loc != null && loc.hasAccuracy() ? String.format(Locale.US, " \u2022 accuracy \u00B1%.0f m", loc.getAccuracy()) : " \u2022 waiting for a fix\u2026"));
            statusTv.setTextColor(GRAY);
        }

        // visible list (respecting the chips)
        shown.clear();
        for (Sat s : sats) if (!hidden.contains(s.type)) shown.add(s);
        Collections.sort(shown, new Comparator<Sat>() {
            @Override public int compare(Sat a, Sat b) {
                int ia = orderIndex(a.type), ib = orderIndex(b.type);
                return ia != ib ? Integer.compare(ia, ib) : Integer.compare(a.svid, b.svid);
            }
        });
        sky.setSats(sats);
        sky.setHidden(hidden);
        adapter.notifyDataSetChanged();
        rebuildChips();
        updateFix();
    }

    private int orderIndex(int type) {
        for (int i = 0; i < Gnss.ORDER.length; i++) if (Gnss.ORDER[i] == type) return i;
        return 99;
    }

    private void rebuildChips() {
        chipRow.removeAllViews();
        int[] types = {Gnss.GPS, Gnss.GLONASS, Gnss.GALILEO, Gnss.BEIDOU, Gnss.QZSS, Gnss.IRNSS, Gnss.SBAS};
        for (final int t : types) {
            int seen = 0, used = 0;
            for (Sat s : sats) if (s.type == t) { seen++; if (s.used) used++; }
            boolean off = hidden.contains(t);
            int color = Gnss.color(t);
            TextView b = tv(13, off ? 0xFF707070 : (seen > 0 ? color : 0xFF707070), true);
            b.setText(Gnss.flag(t) + " " + Gnss.name(t) + " " + used + "/" + seen);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(12), dp(7), dp(12), dp(7));
            b.setBackground(round(off ? 0xFF0A0A0A : 0xFF101018, off ? 0xFF404040 : color, 16));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                if (hidden.contains(t)) hidden.remove(t); else hidden.add(t);
                refresh();
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            l.setMargins(dp(3), 0, dp(3), 0);
            chipRow.addView(b, l);
        }
    }

    private void updateFix() {
        StringBuilder sb = new StringBuilder();
        if (loc == null) {
            sb.append("No fix yet. Go outside or next to a window and wait a little.");
        } else {
            sb.append(String.format(Locale.US, "Latitude  %.6f\u00B0\nLongitude %.6f\u00B0\n", loc.getLatitude(), loc.getLongitude()));
            if (loc.hasAltitude()) sb.append(String.format(Locale.US, "Altitude  %.0f m\n", loc.getAltitude()));
            if (loc.hasAccuracy()) sb.append(String.format(Locale.US, "Accuracy  \u00B1%.0f m\n", loc.getAccuracy()));
            if (loc.hasSpeed()) sb.append(String.format(Locale.US, "Speed     %.1f km/h\n", loc.getSpeed() * 3.6f));
            if (loc.hasBearing()) sb.append(String.format(Locale.US, "Bearing   %.0f\u00B0 %s\n", loc.getBearing(), Gnss.cardinal(loc.getBearing())));
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            sb.append("GPS time  ").append(f.format(new Date(loc.getTime()))).append(" UTC\n");
        }
        sb.append("Satellites used: ").append(countUsed(sats)).append(" of ").append(sats.size());
        if (ttffMs >= 0) sb.append(String.format(Locale.US, "\nTime to first fix: %.1f s", ttffMs / 1000.0));
        fixTv.setText(sb.toString());

        String hw = "";
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                int year = lm.getGnssYearOfHardware();
                String model = lm.getGnssHardwareModelName();
                if (year > 0) hw += "GNSS hardware year: " + year + "\n";
                if (model != null && !model.isEmpty()) hw += "GNSS hardware: " + model;
            } catch (Exception ignored) { }
        }
        hwTv.setText(hw.trim());
    }

    private void showDetail(Sat s) {
        StringBuilder sb = new StringBuilder();
        if (s != null) {
            String sys = Gnss.nameOf(s.type, s.svid);
            sb.append(Gnss.flagOf(s.type, s.svid)).append("  ").append(sys).append("  ").append(Gnss.code(s.type, s.svid));
            if (s.type != Gnss.SBAS) sb.append("  \u2014  ").append(Gnss.country(s.type));
            sb.append("\n");
            sb.append(String.format(Locale.US, "Signal: %.0f dB-Hz", s.cn0));
            String b = Gnss.band(s.type, s.freqMHz);
            if (s.freqMHz > 0) sb.append(String.format(Locale.US, "  \u2022  %s %.2f MHz", b.isEmpty() ? "" : b, s.freqMHz));
            sb.append("\n");
            if (s.hasPosition()) sb.append(String.format(Locale.US, "Elevation %.0f\u00B0  \u2022  Azimuth %.0f\u00B0 (%s)\n", s.el, s.az, Gnss.cardinal(s.az)));
            sb.append(s.used ? "\u2705 Used in your position fix" : "\u25CB Seen, but not used in the fix");
        } else {
            sb.append("Tap a satellite for details. Filled = used for your position, empty = seen but not used. A bigger circle means a stronger signal; the flag is the satellite's country. ");
            if (compassOn) sb.append(String.format(Locale.US, "\n\uD83E\uDDED Compass on \u2014 the plot turns with the phone. Facing %.0f\u00B0 %s. Hold the phone flat.", heading, Gnss.cardinal(heading)));
            else sb.append("\nTap \uD83E\uDDED to turn the plot with your phone's compass.");
        }
        detailTv.setText(sb.toString());
    }

    // ------------------------------------------------------------------ list
    private int barColor(float cn0) {
        if (cn0 >= 40) return 0xFF2ECC71;
        if (cn0 >= 30) return 0xFFC0CA33;
        if (cn0 >= 20) return 0xFFFFB300;
        return 0xFFFF5252;
    }

    private class SatAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            if (convert == null) {
                LinearLayout wrap = new LinearLayout(MainActivity.this);
                wrap.setPadding(0, dp(3), 0, dp(3));
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(8), dp(12), dp(8));
                row.setBackground(round(CARD, 0xFF2A2A44, 12));
                TextView flag = tv(24, Color.WHITE, false);
                LinearLayout mid = new LinearLayout(MainActivity.this);
                mid.setOrientation(LinearLayout.VERTICAL);
                mid.setPadding(dp(10), 0, dp(10), 0);
                TextView name = tv(15, Color.WHITE, true);
                LinearLayout barRow = new LinearLayout(MainActivity.this);
                barRow.setGravity(Gravity.CENTER_VERTICAL);
                View on = new View(MainActivity.this);
                View off = new View(MainActivity.this);
                off.setBackgroundColor(0xFF1C1C2C);
                TextView cn = tv(12, Color.WHITE, true);
                cn.setPadding(dp(8), 0, 0, 0);
                barRow.addView(on, new LinearLayout.LayoutParams(0, dp(8), 1f));
                barRow.addView(off, new LinearLayout.LayoutParams(0, dp(8), 1f));
                barRow.addView(cn);
                mid.addView(name);
                mid.addView(barRow);
                LinearLayout right = new LinearLayout(MainActivity.this);
                right.setOrientation(LinearLayout.VERTICAL);
                right.setGravity(Gravity.END);
                TextView pos2 = tv(11, GRAY, false);
                TextView used = tv(12, GREEN, true);
                right.addView(pos2);
                right.addView(used);
                row.addView(flag);
                row.addView(mid, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(right);
                wrap.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                wrap.setTag(new View[]{flag, name, on, off, cn, pos2, used});
                convert = wrap;
            }
            View[] v = (View[]) convert.getTag();
            Sat s = shown.get(pos);
            ((TextView) v[0]).setText(Gnss.flagOf(s.type, s.svid));
            String b = Gnss.band(s.type, s.freqMHz);
            TextView name = (TextView) v[1];
            name.setText(Gnss.nameOf(s.type, s.svid) + "  " + Gnss.code(s.type, s.svid) + (b.isEmpty() ? "" : "  \u00B7 " + b));
            name.setTextColor(Gnss.color(s.type));
            float f = Math.max(0.02f, Math.min(1f, s.cn0 / 50f));
            ((LinearLayout.LayoutParams) v[2].getLayoutParams()).weight = f;
            ((LinearLayout.LayoutParams) v[3].getLayoutParams()).weight = 1f - f;
            v[2].setBackgroundColor(barColor(s.cn0));
            v[2].requestLayout();
            ((TextView) v[4]).setText(String.format(Locale.US, "%.0f dB-Hz", s.cn0));
            ((TextView) v[5]).setText(s.hasPosition() ? String.format(Locale.US, "%.0f\u00B0 / %.0f\u00B0 %s", s.el, s.az, Gnss.cardinal(s.az)) : "\u2014");
            TextView used = (TextView) v[6];
            used.setText(s.used ? "\u2713 used" : "\u2013");
            used.setTextColor(s.used ? GREEN : GRAY);
            return convert;
        }
    }

    // --------------------------------------------------------------- compass
    @SuppressWarnings("deprecation")
    private int displayRotation() { return getWindowManager().getDefaultDisplay().getRotation(); }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        SensorManager.getRotationMatrixFromVector(rotMat, event.values);
        int xAxis, yAxis;
        switch (displayRotation()) {
            case Surface.ROTATION_90: xAxis = SensorManager.AXIS_Y; yAxis = SensorManager.AXIS_MINUS_X; break;
            case Surface.ROTATION_180: xAxis = SensorManager.AXIS_MINUS_X; yAxis = SensorManager.AXIS_MINUS_Y; break;
            case Surface.ROTATION_270: xAxis = SensorManager.AXIS_MINUS_Y; yAxis = SensorManager.AXIS_X; break;
            default: xAxis = SensorManager.AXIS_X; yAxis = SensorManager.AXIS_Y; break;
        }
        SensorManager.remapCoordinateSystem(rotMat, xAxis, yAxis, remapped);
        SensorManager.getOrientation(remapped, orient);
        double rad = Math.toRadians(Math.toDegrees(orient[0]) + declination);
        float sn = (float) Math.sin(rad), cs = (float) Math.cos(rad);
        if (firstSample) { sinAvg = sn; cosAvg = cs; firstSample = false; }
        else { sinAvg += 0.15f * (sn - sinAvg); cosAvg += 0.15f * (cs - cosAvg); }
        heading = (float) Math.toDegrees(Math.atan2(sinAvg, cosAvg));
        if (heading < 0) heading += 360f;
        sky.setHeading(heading);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
