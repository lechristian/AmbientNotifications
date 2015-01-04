package com.cle.ambientnotifications;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import android.widget.SeekBar;

/**
 * Created by christian on 12/31/14.
 */
public class ControlActivity extends Activity {
    private final static String TAG = ControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private int[] RGBFrame = {0, 0, 0};
    private TextView mConnectionState;
    private SeekBar mRed,mGreen,mBlue;
    private String mDeviceName;
    private String mDeviceAddress;
    private BLEService mBluetoothLeService;
    private boolean mConnected = false;
    private boolean mFoundGatt = false;
    private BluetoothGattCharacteristic characteristicTx;
    private BluetoothGattService gattService;
    private NotificationReceiver notifReceiver;

    private SharedPreferences notificationColors;
    private String defaultColor;

    private String lastNotification = "";

    public final static UUID UUID_BLE_TX =
            UUID.fromString(BLEGattAttributes.BLE_SHIELD_TX);

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BLEService.LocalBinder) service).getService();

            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "This is action: " + action);
            if (BLEService.ACTION_GATT_CONNECTED.equals(action)) {
                appConnected();

                mConnected = true;
                updateConnectionState(R.string.connected);

                invalidateOptionsMenu();
            } else if (BLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);

                invalidateOptionsMenu();
            } else if (BLEService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                gattService = mBluetoothLeService.getSupportedGattService();
                if (gattService != null) {
                    characteristicTx = gattService.getCharacteristic(UUID_BLE_TX);
                    if (characteristicTx != null) {
                        mFoundGatt = true;
                        appConnected();
                    }
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.color_selector);

        final Intent intent = getIntent();

        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        mConnectionState = (TextView) findViewById(R.id.connection_state);

        notifReceiver = new NotificationReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.cle.ambientnotifications.NOTIFICATION_LISTENER");
        registerReceiver(notifReceiver, intentFilter);

        Intent i = new Intent("com.cle.ambientnotifications.NOTIFICATION_LISTENER_SERVICE");
        i.putExtra("command", "list");
        sendBroadcast(i);

        mRed = (SeekBar) findViewById(R.id.seekRed);
        mGreen = (SeekBar) findViewById(R.id.seekGreen);
        mBlue = (SeekBar) findViewById(R.id.seekBlue);

        readSeek(mRed, 0);
        readSeek(mGreen, 1);
        readSeek(mBlue, 2);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        Intent gattServiceIntent = new Intent(this, BLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        notificationColors = getSharedPreferences(getApplicationContext().getPackageName(), 0);
        boolean firstStart = notificationColors.getBoolean("firstStart", true);
        defaultColor = notificationColors.getString("defaultColor", "150,150,0");

        if (firstStart) {
            SharedPreferences.Editor editor = notificationColors.edit();
            editor.putBoolean("firstStart", false)
                    .putString("defaultColor", "150,150,0")
                    .putString("com.motorola.vzw.settings.extensions", "0,0,0")
                    .putString("com.google.android.apps.inbox", "66,133,244")
                    .putString("com.google.android.gm", "219,68,55")
                    .putString("com.android.vending", "0,0,0")
                    .putString("com.android.providers.downloads", "0,0,0");
            editor.commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.cle.ambientnotifications.NOTIFICATION_LISTENER");
        registerReceiver(notifReceiver, intentFilter);

        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(notifReceiver);
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                turnOffLight();
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction("notification_event");

        return intentFilter;
    }

    private void readSeek(SeekBar seekBar,final int pos) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                RGBFrame[pos] = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                makeChange();
            }
        });
    }

    public void onSetFavorite(View v) {
        int[] colors = new int[] {
                RGBFrame[0],
                RGBFrame[1],
                RGBFrame[2]
        };

        String strColor = Arrays.toString(colors);
        String color = strColor.substring(1, strColor.length() - 1).replace(" ", "");

        SharedPreferences.Editor editor = notificationColors.edit();
        editor.putString("defaultColor", color);
        editor.apply();
    }

    private void makeChange() {
        if (!mFoundGatt && gattService != null) {
            characteristicTx = gattService.getCharacteristic(UUID_BLE_TX);

            if (characteristicTx != null) {
                mFoundGatt = true;
            }
        }

        int sync = 0xa5;
        int red = RGBFrame[0];
        int green = RGBFrame[1];
        int blue = RGBFrame[2];
        int checksum = red ^ green ^ blue;

        final byte[] sendTx = new byte[] {
                (byte) sync,
                (byte) red,
                (byte) green,
                (byte) blue,
                (byte) checksum
        };

        // Log Bytes
        for (int i = 0; i < 5; i += 1) {
            Log.d(TAG, "Byte " + i + ": " + sendTx[i]);
        }

        if (mConnected && characteristicTx != null) {
            characteristicTx.setValue(sendTx);
            mBluetoothLeService.writeCharacteristic(characteristicTx);
        }
    }

    private void setColor(String pckt) {
        if (!mFoundGatt && gattService != null) {
            characteristicTx = gattService.getCharacteristic(UUID_BLE_TX);

            if (characteristicTx != null) {
                mFoundGatt = true;
            }
        }

        String color;

        if (notificationColors.contains(pckt)) {
            color = notificationColors.getString(pckt, "100,100,100");
        } else {
            color = notificationColors.getString(pckt, getApplicationColor(pckt));
        }

        String[] colors = color.split(",");

        int sync = 0xa5;
        int red = Integer.parseInt(colors[0]);
        int green = Integer.parseInt(colors[1]);
        int blue = Integer.parseInt(colors[2]);
        int checksum = red ^ green ^ blue;

        final byte[] sendTx = new byte[] {
                (byte) sync,
                (byte) red,
                (byte) green,
                (byte) blue,
                (byte) checksum
        };

        // Log Bytes
        for (int i = 0; i < 5; i += 1) {
            Log.d(TAG, "Byte " + i + ": " + sendTx[i]);
        }

        if (mConnected && characteristicTx != null) {
            characteristicTx.setValue(sendTx);
            mBluetoothLeService.writeCharacteristic(characteristicTx);
        }
    }

    private void appConnected() {
        if (!mFoundGatt && gattService != null) {
            characteristicTx = gattService.getCharacteristic(UUID_BLE_TX);

            if (characteristicTx != null) {
                mFoundGatt = true;
            }
        }

        int sync = 0xa5;
        int red = 0x00;
        int green = 0x33;
        int blue = 0x00;
        int checksum = red ^ green ^ blue;

        final byte[] sendTx = new byte[] {
                (byte) sync,
                (byte) red,
                (byte) green,
                (byte) blue,
                (byte) checksum
        };

        // Log Byte
        for (int i = 0; i < 5; i += 1) {
            Log.d(TAG, "Byte " + i + ": " + sendTx[i]);
        }

        if (mConnected && characteristicTx != null) {
            characteristicTx.setValue(sendTx);
            mBluetoothLeService.writeCharacteristic(characteristicTx);
        }
    }

    private void turnOffLight() {
        if (!mFoundGatt && gattService != null) {
            characteristicTx = gattService.getCharacteristic(UUID_BLE_TX);

            if (characteristicTx != null) {
                mFoundGatt = true;
            }
        }

        int sync = 0xa5;
        int off = 0x00;

        final byte[] sendTx = new byte[] {
                (byte) sync,
                (byte) off,
                (byte) off,
                (byte) off,
                (byte) off
        };

        if (mConnected && characteristicTx != null) {
            characteristicTx.setValue(sendTx);
            mBluetoothLeService.writeCharacteristic(characteristicTx);
        }
    }

    public String getApplicationColor(String pckt) {
        String color;

        try {
            Drawable icon = getPackageManager().getApplicationIcon(pckt);
            Bitmap bIcon = drawableToBitmap(icon);
            int iWidth = bIcon.getWidth();
            int iHeight = bIcon.getHeight();

            int[] pixels = new int[iWidth * iHeight];
            bIcon.getPixels(pixels, 0, iWidth, 0, 0, iWidth, iHeight);

            int[] colors = getDominantColor(pixels);

            String strColor = Arrays.toString(colors);
            color = strColor.substring(1, strColor.length() - 1).replace(" ", "");

            if (!notificationColors.contains(pckt)) {
                SharedPreferences.Editor editor = notificationColors.edit();
                editor.putString(pckt, color);
                editor.apply();
            }
        } catch (PackageManager.NameNotFoundException e) {
            color = defaultColor;
        }

        return color;
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable)drawable).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        int bWidth = bitmap.getWidth();
        int bHeight = bitmap.getHeight();

        if (bWidth * bHeight > 10000) {
            bitmap = Bitmap.createScaledBitmap(bitmap, 100, (100 * bHeight) / bWidth, false);
        }

        return bitmap;
    }

    public int[] getDominantColor(int[] pixels) {
        HashMap<Integer, Integer> colorMap = new HashMap();

        for (int pixel : pixels) {
            if (!isGrayScalePixel(pixel)) {
                Integer counter = colorMap.get(pixel);

                if (counter == null) {
                    counter = 1;
                } else {
                    counter += 1;
                }

                colorMap.put(pixel, counter);
            }
        }

        boolean init = true;
        int maxColor = 0;

        for (int color : colorMap.keySet()) {
            if (init) {
                maxColor = color;
                init = false;
            } else if (colorMap.get(color) > colorMap.get(maxColor)) {
                maxColor = color;
            }
        }

        int[] colorToUse = {
                Color.red(maxColor),
                Color.green(maxColor),
                Color.blue(maxColor)
        };

        return colorToUse;
    }

    boolean isGrayScalePixel(int pixel){
        int red = Color.red(pixel);
        int green = Color.green(pixel);
        int blue = Color.blue(pixel);

        if (red == green && green == blue) {
            return true;
        } else {
            return false;
        }
    }

    class NotificationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String event = intent.getStringExtra("notification_event");
            String pckt = intent.getStringExtra("notification_package");

            if (event.equals("NotificationPosted")) {
                lastNotification = pckt;
                setColor(pckt);
            } else if (event.equals("NotificationRemoved")) {
                if (lastNotification.equals(pckt)) {
                    lastNotification = "";
                    turnOffLight();
                }
            }
        }
    }
}
