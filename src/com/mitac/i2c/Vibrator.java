package com.mitac.i2c;

public class Vibrator {
    
    public int HIGH = 127;
    public int MIDDLE = 64;
        
    public Vibrator() {
        // do nothing;
    }
    public native int setIntensity(int level);
    public native String getIntensity();

    static {
        System.loadLibrary("Vibrator");
    }

}
