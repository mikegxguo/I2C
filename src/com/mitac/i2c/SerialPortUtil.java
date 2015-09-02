package com.mitac.i2c;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import android.util.Log;

/**
 * 
 * @author mike
 * 
 */
public class SerialPortUtil {
    private String TAG = SerialPortUtil.class.getSimpleName();
    private SerialPort mSerialPort;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;
    private String path = "/dev/tmc_tty0"; // TMC serial port
    private int baudrate = 9600;
    private static SerialPortUtil portUtil;
    private OnDataReceiveListener onDataReceiveListener = null;
    private boolean isStop = false;
    private String strTest = "ABCDEFGHIJKLMNOPQRSTUVWXYZ123456";
    //TMC chip command
    private byte CMD_SET_FREQ[] = {(byte)0xFE,0x04,0x27,0x0,(byte)0xFF};
    private byte CMD_GET_LEVEL[] = {(byte)0xFE,0x05,0x30,0x35,(byte)0xFF};

    /*
     * public interface OnDataReceiveListener { public void onDataReceive(byte[]
     * buffer, int size); }
     */

    public void setOnDataReceiveListener(
            OnDataReceiveListener dataReceiveListener) {
        onDataReceiveListener = dataReceiveListener;
    }

    public static SerialPortUtil getInstance() {
        if (null == portUtil) {
            portUtil = new SerialPortUtil();
            portUtil.onCreate();
        }
        return portUtil;
    }

    public void onCreate() {
        try {
            mSerialPort = new SerialPort(new File(path), baudrate);
            mOutputStream = mSerialPort.getOutputStream();
            mInputStream = mSerialPort.getInputStream();

            mReadThread = new ReadThread();
            isStop = false;
            mReadThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 
     * @param cmd
     * @return
     */
    public boolean sendCmds(String cmd) {
        boolean result = true;
        byte[] mBuffer = (cmd + "\r\n").getBytes();
        try {
            if (mOutputStream != null) {
                mOutputStream.write(mBuffer);
                Log.d(TAG, "Send: " + cmd);
            } else {
                result = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            result = false;
        }
        return result;
    }

    private static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        char[] buffer = new char[2];
        for (int i = 0; i <= src.length - 1; i++) {
            buffer[0] = Character.forDigit((src[i] >>> 4) & 0x0F, 16);
            buffer[1] = Character.forDigit(src[i] & 0x0F, 16);
            stringBuilder.append(buffer);
         }
        return stringBuilder.toString();
    }

    public boolean sendBuffer(byte[] mBuffer) {
        boolean result = true;
        String tail = "\r\n";
        byte[] tailBuffer = tail.getBytes();
        byte[] mBufferTemp = new byte[mBuffer.length + tailBuffer.length];
        System.arraycopy(mBuffer, 0, mBufferTemp, 0, mBuffer.length);
        System.arraycopy(tailBuffer, 0, mBufferTemp, mBuffer.length, tailBuffer.length);
        try {
            if (mOutputStream != null) {
                mOutputStream.write(mBufferTemp);
                Log.d(TAG, "Send: " + bytesToHexString(mBufferTemp));
            } else {
                result = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            result = false;
        }
        return result;
    }

    public boolean sendBuffer_2(byte[] mBuffer) {
        boolean result = true;
        try {
            if (mOutputStream != null) {
                mOutputStream.write(mBuffer);
                Log.d(TAG, "Send: " + bytesToHexString(mBuffer));
            } else {
                result = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            result = false;
        }
        return result;
    }

    private class ReadThread extends Thread {

        @Override
        public void run() {
            super.run();
            while (!isStop && !isInterrupted()) {
                int size;
                try {
                    if (mInputStream == null)
                        return;
                    // for test
                    //sendCmds(strTest);
                    if (!isStop) {
                        Thread.sleep(2000);
                    }

                    byte[] buffer = new byte[64];
                    size = mInputStream.read(buffer);
                    if (size > 0) {
                        //Log.d(TAG, "Recv: " + new String(buffer, 0, size));
                        Log.d(TAG, "Recv: "+bytesToHexString(buffer));
                        if (null != onDataReceiveListener) {
                            onDataReceiveListener.onDataReceive(buffer, size);
                        }
                    } else if (size == 0) {
                        if (null != onDataReceiveListener) {
                            onDataReceiveListener.onDataReceive(buffer, 0);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    /**
     */
    public void closeSerialPort() {
        isStop = true;
        if (mReadThread != null) {
            mReadThread.interrupt();
            mReadThread = null;
        }
        if (mSerialPort != null) {
            mSerialPort.close();
            mSerialPort = null;
        }
    }

}