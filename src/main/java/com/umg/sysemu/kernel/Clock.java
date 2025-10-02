package com.umg.sysemu.kernel;

public class Clock {
    private static int INTERNAL_CLOCK = 0;

    public static int forward() { return INTERNAL_CLOCK++; }
    public static void reset() { INTERNAL_CLOCK = 0; }
    public static int time() { return INTERNAL_CLOCK; }
}
