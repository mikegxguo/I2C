package com.mitac.i2c;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
//import android.nfc.tech.NfcA;
//import android.nfc.tech.NfcB;
//import android.nfc.tech.NfcF;
//import android.nfc.tech.NfcV;
//import android.os.Messenger;
//import android.os.SystemClock;
import android.media.AudioManager;
import android.media.SoundPool;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Timer;
import java.util.TimerTask;
import android.os.SystemVibrator;

import com.mitac.i2c.OnDataReceiveListener;
import com.mitac.i2c.SerialPortUtil;


public class I2CActivity extends Activity  implements OnDataReceiveListener{
    private final String TAG = "I2CActivity";
    // Definition for Sensor
    private SensorManager sensorMgr;
    private SensorEventListener lsn;
    private Sensor lightSensor, oriSensor, GSensor;
    private float x1, y1, z1;
    private float x2, y2, z2;
    private float x3;
    //
    // Definition for NFC function
    private static final String ACTION_NFC_CHANGE_STATUS = "android.action.NFC_CHANGE_STATUS";
    private static final String EXTRA_NFC_STATE = "android.nfc.extra.STATE";
    private static final int NFC_STATE_DISABLE = 1;
    private static final int NFC_STATE_ENABLE = 2;
    private static final int NFC_STATE_REINIT = 3;

    private static boolean scanned;
    private static String strUID;
    private static String strCardType;

    private NfcAdapter nfcAdapter;
    private String temp;

    //TMC function
    SerialPortUtil mSerialPortUtil;

    //Sound
    public static final int SOUND_FALL_BACK_RING = 0;
    public static final int SOUND_IN_CALL_ALARM = 1;
    Context mContext;
    int mFallbackRing;
    int mInCallAlarm;
    SoundPool mSoundPool; // playback synchronized on this

    //PMIC, Vibrator
    private boolean mHasVibrator = false;
    private boolean bValHigh = false;
    private int valHigh = 127;
    private int valMid = 64;
    private Timer timerVibrator;
    private Vibrator mVibrator;
    
    //NFC
    private TextView nfcPass;
    private TextView nfcFail;
    private static int cntNFCPass = 0;
    private static int cntNFCFail = 0;
    
    //Camera
    private Timer timerCamera;
    private TextView cameraPass;
    private TextView cameraFail;
    private static int cntCameraPass = 0;
    private static int cntCameraFail = 0;

    //Audio
    private Timer timerAudio;
    private TextView audioPass;
    private TextView audioFail;
    private static int cntAudioPass = 0;
    private static int cntAudioFail = 0;
    
    //Sensor
    private TextView sensorFail;
    private static int cntCompassFail = 0;
    private static int cntAcceleratorFail = 0;
    
    //Save FAIL information in the file
    protected static File logFile;

    //Refresh
    private String mTmcBuf = null;
    private TextView dataTMC;
    private static final int MSG_REFRESH = 0x1234;
    private boolean mTakePicture = false;
    private Handler hRefresh = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_REFRESH:
                if (dataTMC != null) {
                    dataTMC.setText("TMC: " + mTmcBuf);
                }
                if(nfcPass != null) {
                    nfcPass.setText("Pass: "+ cntNFCPass);
                }
                if(nfcFail != null) {
                    nfcFail.setText("Fail: "+ cntNFCFail);
                }
                if(cameraPass != null) {
                    cameraPass.setText("Pass: "+ cntCameraPass);
                }
                if(cameraFail != null) {
                    cameraFail.setText("Fail: "+ cntCameraFail);
                }
                if(audioPass != null) {
                    audioPass.setText("Pass: "+ cntAudioPass);
                }
                if(audioFail != null) {
                    audioFail.setText("Fail: "+ cntAudioFail);
                }
                if(sensorFail != null) {
                    sensorFail.setText("Compass Fail: "+ cntCompassFail+"\nAccelerator Fail: "+cntAcceleratorFail);
                }
                break;
            default:
                break;
            }
        }
    };

    void initSoundPool() {
        synchronized(this) {
            if (mSoundPool == null) {
                mSoundPool = new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
                mFallbackRing = mSoundPool.load(mContext, R.raw.fallbackring, 1);
                mInCallAlarm = mSoundPool.load(mContext, R.raw.in_call_alarm, 1);
            }
        }
    }

    void releaseSoundPool() {
        synchronized(this) {
            if (mSoundPool != null) {
                mSoundPool.release();
                mSoundPool = null;
            }
        }
    }

    public int playSound(int sound) {
        synchronized (this) {
            int ret = 0;
            if (mSoundPool == null) {
                Log.w(TAG, "Not playing sound when NFC is disabled");
                return 0;
            }
            switch (sound) {
            case SOUND_FALL_BACK_RING:
                ret = mSoundPool.play(mFallbackRing, 1.0f, 1.0f, 0, 0, 1.0f);
                break;
            case SOUND_IN_CALL_ALARM:
                ret = mSoundPool.play(mInCallAlarm, 1.0f, 1.0f, 0, 0, 1.0f);
                break;
            }
            return ret;
        }
    }


    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                int capacity = intent.getIntExtra("level", 0);
                int voltage = intent.getIntExtra("voltage", 0);
                int temperature = intent.getIntExtra("temperature", 0);

                String Battery_info = "Capacity:    " + String.valueOf(capacity) + "\n"
                        + "Voltage:     " + String.valueOf(voltage) + "\n"
                        + "Temperature: " + String.valueOf(temperature) + "\n";
                Log.d(TAG, Battery_info);
            }
        }
    };


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        dataTMC = (TextView) findViewById(R.id.dataTMC);
        nfcPass = (TextView) findViewById(R.id.nfc_pass);
        nfcFail = (TextView) findViewById(R.id.nfc_fail);
        cameraPass = (TextView) findViewById(R.id.camera_pass);
        cameraFail = (TextView) findViewById(R.id.camera_fail);
        audioPass = (TextView) findViewById(R.id.audio_pass);
        audioFail = (TextView) findViewById(R.id.audio_fail);
        
        sensorFail = (TextView) findViewById(R.id.sensor_fail);

        mContext = getApplicationContext();

        sensorMgr = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        setMode(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        lsn = new SensorEventListener() {
            public void onSensorChanged(SensorEvent e) {
                if (e.sensor.getType() == Sensor.TYPE_ORIENTATION) {
                    x1 = e.values[SensorManager.DATA_X];
                    y1 = e.values[SensorManager.DATA_Y];
                    z1 = e.values[SensorManager.DATA_Z];
                    if(x1==0 && y1==0 && z1==0) {
                        cntCompassFail += 1;
                        hRefresh.sendEmptyMessage(MSG_REFRESH);
                        saveLog("E-Compass, FAIL to get the right value\n");
                    }                       
                } else if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    x2 = e.values[SensorManager.DATA_X];
                    y2 = e.values[SensorManager.DATA_Y];
                    z2 = e.values[SensorManager.DATA_Z];
                    //RAY: Put the device on the flat plane, calibrate it firstly,
                    //then the value of X/Y axis is approximately zero,
                    //the value of Z axis is about 9.8
                    if((x2<-0.1 || x2>0.1) ||(y2<-0.1 || y2>0.1) || ( z2<9.7 || z2>9.9)) {
                        cntAcceleratorFail += 1;
                        hRefresh.sendEmptyMessage(MSG_REFRESH);
                        saveLog("G-Sensor, FAIL to get the right value\n");
                    }                       
                } else if (e.sensor.getType() == Sensor.TYPE_LIGHT) {
                    x3 = e.values[SensorManager.DATA_X];
                }

                 Log.d(TAG, "Seneor Type: Compass\n" 
                     + "X:" + String.valueOf(x1) + "\n"
                     + "Y:" + String.valueOf(y1) + "\n"
                     + "Z:" + String.valueOf(z1) + "\n");

                 Log.d(TAG, "Seneor Type: G-Sensor\n"
                        + "X:" + String.valueOf(x2) + "\n"
                         + "Y:" + String.valueOf(y2) + "\n"
                        + "Z:" + String.valueOf(z2) + "\n");

                //                 Log.d(TAG, "Seneor Type: Light Sensor\n"
                //                        + "X:" + String.valueOf(x3) + "\n");

            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

        oriSensor = sensorMgr.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        sensorMgr.registerListener(lsn, oriSensor,
                SensorManager.SENSOR_DELAY_GAME); //20ms

        GSensor = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMgr.registerListener(lsn, GSensor,
                SensorManager.SENSOR_DELAY_GAME); //20ms

        lightSensor = sensorMgr.getDefaultSensor(Sensor.TYPE_LIGHT);
        sensorMgr.registerListener(lsn, lightSensor,
                SensorManager.SENSOR_DELAY_NORMAL); //200ms


        //Battery
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mBroadcastReceiver, filter);

        //Sound
        initSoundPool();

        //PMIC, Vibrator
        mHasVibrator = new SystemVibrator().hasVibrator();      
        mVibrator = new Vibrator();
        timerVibrator = new Timer();
        timerVibrator.schedule(new TimerTask() {           
            @Override
            public void run() {
                if (mHasVibrator) {
                    if (!bValHigh) {
                        bValHigh = true;
                        new SystemVibrator().change(valHigh);
                        String intensity = mVibrator.getIntensity();
                        if((intensity.compareTo("3.0V\n"))!=0) {
                            //saveLog("Vibrator: "+intensity);
                            saveLog("Vibrator, FAIL to set high intensity\n");
                            //Log.d(TAG, "PMIC, Vibrator, FAIL to set high value: "+intensity);
                       }
                    } else {
                        bValHigh = false;
                        new SystemVibrator().change(valMid);
                        String intensity = mVibrator.getIntensity();
                        if((intensity.compareTo("2.7V\n"))!=0) {
                            //saveLog("Vibrator: "+intensity);
                            saveLog("Vibrator, FAIL to set middle intensity\n");
                            //Log.d(TAG, "PMIC, Vibrator, FAIL to set middle value: "+intensity);
                        }
                    }
                }
            }
        }, 0, 10);

        //Save FAIL information into the file
        String temp = new SimpleDateFormat("HHmmss").format(System.currentTimeMillis());
        logFile = new File(Environment.getExternalStorageDirectory(), "FAIL_"+temp+".txt");
    }

    public static void saveLog(String log) {
        if (log == null || log.isEmpty()) {
            return;
        } else if (logFile == null) {
            return;
        } else if (!logFile.getParentFile().exists()) {
            if (!logFile.getParentFile().mkdirs())
                return;
        }
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(logFile, true);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(System.currentTimeMillis());
            fileWriter.append(timestamp+" "+log);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fileWriter != null) {
                    fileWriter.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void onInitNFC(View v) {
        //NFC function
        initNFC(this);
    }

    public void onScanNFC(View v) {
        //nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray);
        testBeginNFC();
        //nfcAdapter.disableForegroundDispatch(this);
    }

    public void onTakePicture(View v) {
        //Camera
        if(timerCamera == null) {
            timerCamera = new Timer();
            timerCamera.schedule(new TimerTask() {           
                @Override
                public void run() {
                    Intent intent = new Intent();
                    intent.setClass(mContext, CameraActivity.class);
                    intent.putExtra(CameraActivity.CAMERA_FACING, 0);
                    intent.putExtra(CameraActivity.CAMERA_SHOT_TIMES, 1);
                    intent.putExtra(CameraActivity.CAMERA_DELETE_FILE, true);
                    startActivity(intent);
                    mTakePicture = true;
                }
            }, 0, 15000);

        }
    }

    public void onPlaySound(View v) {
        //Audio
        if(timerAudio == null) {
            timerAudio = new Timer();
            timerAudio.schedule(new TimerTask() {           
                @Override
                public void run() {
                    if(playSound(SOUND_FALL_BACK_RING)!=0) {
                        cntAudioPass += 1;
                    } else {
                        cntAudioFail += 1;
                        saveLog("Audio, FAIL to play sound\n");
                    }
                    //playSound(SOUND_IN_CALL_ALARM);
                    hRefresh.sendEmptyMessage(MSG_REFRESH);
                }
            }, 0, 6000);
        }
    }


    public void onOpenTMC(View v) {
        //TMC function
        mSerialPortUtil = new SerialPortUtil();
        mSerialPortUtil.onCreate();
        mSerialPortUtil.setOnDataReceiveListener(this);
    }

    public void onCloseTMC(View v) {
        //TMC function
        if(mSerialPortUtil!=null) {
            mSerialPortUtil.closeSerialPort();
        }
    }

    public void onSendTMCCMD(View v) {
        //TMC function
        byte CMD_SET_FREQ[] = {(byte)0xFE,0x04,0x27,0x32, 0x42, (byte)0xFF}; //ShangHai, 91.4MHz
        //byte CMD_GET_LEVEL[] = {(byte)0xFE,0x05,0x30,0x35,(byte)0xFF};
        //byte CMD_GET_VERSION[] = {(byte)0xFE,0x02,0x30,0x32,(byte)0xFF};
        if(mSerialPortUtil!=null) {
            //mSerialPortUtil.sendBuffer_2(CMD_GET_VERSION);
            //SystemClock.sleep(2000);
            //mSerialPortUtil.sendBuffer_2(CMD_GET_LEVEL);
            //SystemClock.sleep(2000);
            mSerialPortUtil.sendBuffer_2(CMD_SET_FREQ);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if(mTakePicture) {
            if(CameraActivity.isCameraTestPassed) {
                cntCameraPass += 1;
            } else {
                cntCameraFail += 1;
            }
            mTakePicture = false;
        }
        hRefresh.sendEmptyMessage(MSG_REFRESH);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }
    
    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        //NFC function
        testCleanupNFC();
        //TMC function
        if(mSerialPortUtil!=null) {
            mSerialPortUtil.closeSerialPort();
        }
        //Sound
        releaseSoundPool();
        //PMIC, vibrator
        if(timerVibrator != null) {
            timerVibrator.cancel();
        }
        //Camera
        if(timerCamera != null) {
            timerCamera.cancel();
        }
        //Audio
        if(timerAudio != null) {
            timerAudio.cancel();
        }

        super.onDestroy();
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent");
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {

            Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tagFromIntent == null)
                return;
            // UID
            byte[] uid = tagFromIntent.getId();
            // Type
            String cardType = "";
            for (String tech : tagFromIntent.getTechList()) {
                cardType += tech.substring(tech.lastIndexOf(".") + 1);
                cardType += ", ";
            }
            setScanned(uid, cardType);          

            if (scanned) {
                temp = "Scanned card: [" + strCardType + "]\n UID: [" + strUID  + "]\n";
                cntNFCPass += 1;
            } else {
                temp = "Missing scanning this card!";
                cntNFCFail += 1;
            }
            Log.d(TAG, temp);
            Toast.makeText(getApplication(), temp, Toast.LENGTH_LONG).show();
            hRefresh.sendEmptyMessage(MSG_REFRESH);
        }
    }

    private void setMode(int mode) {
        Settings.System.putInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE, mode);
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
            stringBuilder.append(" ");
        }
        return stringBuilder.toString();
    }

    private void restartNFCReader() {
        Intent intent = new Intent(ACTION_NFC_CHANGE_STATUS);
        intent.putExtra(EXTRA_NFC_STATE, NFC_STATE_REINIT);
        sendBroadcast(intent);
    }

    private void enableNFCReader() {
        Intent intent = new Intent(ACTION_NFC_CHANGE_STATUS);
        intent.putExtra(EXTRA_NFC_STATE, NFC_STATE_ENABLE);
        sendBroadcast(intent);
    }

    private void disableNFCReader() {
        Intent intent = new Intent(ACTION_NFC_CHANGE_STATUS);
        intent.putExtra(EXTRA_NFC_STATE, NFC_STATE_DISABLE);
        sendBroadcast(intent);
    }

    public static void setScanned(byte[] uid, String type) {
        scanned = true;
        strUID = bytesToHexString(uid);
        strCardType = type;
    }


    public boolean initNFC(Context context) {
        nfcAdapter = NfcAdapter.getDefaultAdapter(context);
        if (nfcAdapter == null) {
            // There is no NFC module
            return false;
        }

        scanned = false;
        for (int i = 0; i < 60; i++) {
            // NFC module is not ON
            if (!nfcAdapter.isEnabled()) {
                if (i % 20 == 0) {
                    enableNFCReader();
                }
                SystemClock.sleep(1000);
            } else {
                disableNFCReader();
                enableNFCReader();
                for (int j = 0; j < 50; j++) {
                    SystemClock.sleep(100);
                    if (scanned)
                        return true;
                }
                return false;
            }
        }

        return false;
    }

    public boolean testBeginNFC() {
        scanned = false;
        Log.d(TAG, "Scanning card");

        disableNFCReader();
        enableNFCReader();
        SystemClock.sleep(5000);
        Log.d(TAG, "NFC enabled = " + nfcAdapter.isEnabled());
        return scanned;
    }

    public boolean testCleanupNFC() {
        disableNFCReader();
        return true;
    }


    public void onDataReceive(byte[] buffer, int size) {
        //TODO: 
        if(size == 0) {
            saveLog("TMC, TMC data is null\n");
            mTmcBuf = null;
        } else {
             mTmcBuf = bytesToHexString(buffer);
        }
        hRefresh.sendEmptyMessage(MSG_REFRESH);
    }
}
