package com.blackfalcon.gnss;

/** One satellite as reported by the phone's GNSS receiver. */
public final class Sat {
    public int type, svid;
    public float cn0, az, el, freqMHz;
    public boolean used;

    /** Elevation/azimuth are 0 when the receiver has not located the satellite yet. */
    public boolean hasPosition() { return !(el == 0f && az == 0f && cn0 == 0f); }
}
