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
                disconnectApp();
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

    private void disconnectApp() {
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
}
