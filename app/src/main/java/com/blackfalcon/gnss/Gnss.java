package com.blackfalcon.gnss;

/** Names, flags, colours and signal bands of the satellite systems. Pure Java (testable). */
public final class Gnss {
    private Gnss() { }

    // same numbers as android.location.GnssStatus.CONSTELLATION_*
    public static final int GPS = 1, SBAS = 2, GLONASS = 3, QZSS = 4, BEIDOU = 5, GALILEO = 6, IRNSS = 7;
    public static final int[] ORDER = {GPS, GLONASS, GALILEO, BEIDOU, QZSS, IRNSS, SBAS, 0};

    public static String name(int t) {
        switch (t) {
            case GPS: return "GPS";
            case GLONASS: return "GLONASS";
            case GALILEO: return "Galileo";
            case BEIDOU: return "BeiDou";
            case QZSS: return "QZSS";
            case IRNSS: return "NavIC";
            case SBAS: return "SBAS";
            default: return "Unknown";
        }
    }

    public static String country(int t) {
        switch (t) {
            case GPS: return "USA";
            case GLONASS: return "Russia";
            case GALILEO: return "European Union";
            case BEIDOU: return "China";
            case QZSS: return "Japan";
            case IRNSS: return "India";
            case SBAS: return "various";
            default: return "";
        }
    }

    public static String flag(int t) {
        switch (t) {
            case GPS: return "\uD83C\uDDFA\uD83C\uDDF8";
            case GLONASS: return "\uD83C\uDDF7\uD83C\uDDFA";
            case GALILEO: return "\uD83C\uDDEA\uD83C\uDDFA";
            case BEIDOU: return "\uD83C\uDDE8\uD83C\uDDF3";
            case QZSS: return "\uD83C\uDDEF\uD83C\uDDF5";
            case IRNSS: return "\uD83C\uDDEE\uD83C\uDDF3";
            default: return "\uD83D\uDEF0";
        }
    }

    public static int color(int t) {
        switch (t) {
            case GPS: return 0xFF4FC3F7;
            case GLONASS: return 0xFFEF5350;
            case GALILEO: return 0xFFFFD54F;
            case BEIDOU: return 0xFFFF8A65;
            case QZSS: return 0xFFF48FB1;
            case IRNSS: return 0xFF81C784;
            case SBAS: return 0xFFB0BEC5;
            default: return 0xFF9E9E9E;
        }
    }

    private static String prefix(int t) {
        switch (t) {
            case GPS: return "G";
            case GLONASS: return "R";
            case GALILEO: return "E";
            case BEIDOU: return "C";
            case QZSS: return "J";
            case IRNSS: return "I";
            case SBAS: return "S";
            default: return "?";
        }
    }

    public static String code(int t, int svid) { return prefix(t) + svid; }

    /** SBAS satellites: {system name, flag} guessed from the PRN number (approximate). */
    public static String[] sbasSystem(int prn) {
        int[] waas = {131, 133, 135, 138}, egnos = {120, 123, 126, 136}, msas = {129, 137}, gagan = {127, 128, 132},
                sdcm = {125, 140, 141}, bds = {130, 143, 144};
        if (in(prn, waas)) return new String[]{"WAAS", "\uD83C\uDDFA\uD83C\uDDF8"};
        if (in(prn, egnos)) return new String[]{"EGNOS", "\uD83C\uDDEA\uD83C\uDDFA"};
        if (in(prn, msas)) return new String[]{"MSAS", "\uD83C\uDDEF\uD83C\uDDF5"};
        if (in(prn, gagan)) return new String[]{"GAGAN", "\uD83C\uDDEE\uD83C\uDDF3"};
        if (in(prn, sdcm)) return new String[]{"SDCM", "\uD83C\uDDF7\uD83C\uDDFA"};
        if (in(prn, bds)) return new String[]{"BDSBAS", "\uD83C\uDDE8\uD83C\uDDF3"};
        return new String[]{"SBAS", "\uD83D\uDEF0"};
    }

    private static boolean in(int v, int[] a) { for (int x : a) if (x == v) return true; return false; }

    /** Flag of one satellite (SBAS satellites get the flag of their own system). */
    public static String flagOf(int t, int svid) { return t == SBAS ? sbasSystem(svid)[1] : flag(t); }

    public static String nameOf(int t, int svid) { return t == SBAS ? sbasSystem(svid)[0] : name(t); }

    private static boolean near(float f, double v, double tol) { return Math.abs(f - v) <= tol; }

    /** Signal band from the carrier frequency, e.g. "L1", "E5a", "B1C". Empty if unknown. */
    public static String band(int t, float f) {
        if (f <= 0) return "";
        if (t == GLONASS) {
            if (f > 1590 && f < 1610) return "G1";
            if (f > 1238 && f < 1255) return "G2";
            if (near(f, 1202.025, 3)) return "G3";
        }
        if (near(f, 1575.42, 1)) return t == GALILEO ? "E1" : (t == BEIDOU ? "B1C" : "L1");
        if (near(f, 1176.45, 1)) return t == GALILEO ? "E5a" : (t == BEIDOU ? "B2a" : "L5");
        if (near(f, 1227.60, 1)) return "L2";
        if (near(f, 1561.098, 1)) return "B1I";
        if (near(f, 1207.14, 1)) return t == GALILEO ? "E5b" : "B2b";
        if (near(f, 1191.795, 1)) return "E5";
        if (near(f, 1268.52, 1)) return "B3";
        if (near(f, 1278.75, 1)) return t == GALILEO ? "E6" : "L6";
        if (near(f, 2492.028, 2)) return "S-band";
        return "";
    }

    public static String cardinal(float az) {
        String[] n = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return n[Math.round(az / 45f) % 8];
    }
}
