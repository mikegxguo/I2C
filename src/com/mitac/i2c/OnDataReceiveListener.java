package com.mitac.i2c;

public interface OnDataReceiveListener {
    public void onDataReceive(byte[] buffer, int size);
}
