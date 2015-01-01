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
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.HashMap;
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
    private TextView txtView;
    private String defaultColor = "150,150,0";
    private String facebookColor = "59,89,152";
    private String snapchatColor = "255,252,0";
    private String messagingColor = "149,184,42";
    private String phoneColor = "6,77,176";

    private static final HashMap<String, String> notificationColors;
    static {
        notificationColors = new HashMap<>();
        notificationColors.put("com.motorola.vzw.settings.extensions", "102,51,153");
        notificationColors.put("com.facebook.orca", "59,89,152");
        notificationColors.put("com.facebook.katana", "109,132,180");
        notificationColors.put("com.snapchat.android", "255,252,0");
        notificationColors.put("com.google.android.apps.inbox", "66,133,244");
        notificationColors.put("com.google.android.gm", "219,68,55");
        notificationColors.put("com.mailboxapp", "81,185,219");
        notificationColors.put("com.google.android.talk", "27,162,97");
        notificationColors.put("com.android.providers.telephony", "6,77,176");
        notificationColors.put("com.android.vending", "0,0,0");
        notificationColors.put("com.android.providers.downloads", "0,0,0");
    }

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

        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();

        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        mConnectionState = (TextView) findViewById(R.id.connection_state);

        txtView = (TextView) findViewById(R.id.notif_text_view);

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
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                RGBFrame[pos] = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                makeChange();
            }
        });
    }

    private void makeChange() {
        if (!mFoundGatt && gattService != null) {
            characteristicTx = gattService.getCharacteristic(UUID_BLE_TX);

            if (characteristicTx != null) {
                mFoundGatt = true;
            }
        }

        int sync = 0xa5;
        int red = 255 - RGBFrame[0];
        int green = 255 - RGBFrame[1];
        int blue = 255 - RGBFrame[2];
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

        String color = notificationColors.get(pckt);
        if (color == null) {
            if (pckt.toLowerCase().contains("facebook")) {
                color = facebookColor;
            } else if (pckt.toLowerCase().contains("snapchat")) {
                color = snapchatColor;
            } else if (pckt.toLowerCase().contains("phon")) {
                color = phoneColor;
            } else if (pckt.toLowerCase().contains("messag")) {
                color = messagingColor;
            } else {
                color = defaultColor;
            }
        }
        String[] colors = color.split(",");

        int sync = 0xa5;
        int red = 255 - Integer.parseInt(colors[0]);
        int green = 255 - Integer.parseInt(colors[1]);
        int blue = 255 - Integer.parseInt(colors[2]);
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
        int red = 0xff;
        int green = 0xdd;
        int blue = 0xff;
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
        int off = 0xFF;

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

    class NotificationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "gets here");
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

            String temp = event + ":\n" + pckt;
            txtView.setText(temp);
        }
    }
}
