package com.example.camilo.smartwalkerapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothMonitor extends AppCompatActivity{
    private final static String TAG = BluetoothMonitor.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private String deviceName;
    private String deviceAddress;
    private int mConnectionState = STATE_DISCONNECTED;

    private TextView delayView;
    private BluetoothGattCharacteristic serial;
    private Date messageTime, responseTime;
    private final static UUID SERIAL = UUID.fromString("0000dfb1-0000-1000-8000-00805f9b34fb");
    private final static UUID SERIALSERVICE = UUID.fromString("0000dfb0-0000-1000-8000-00805f9b34fb");
    private boolean alarm =false, noAlert=true;
    private Timer timer;
    private long sent, delay;

    private String phoneNumber;
    private EditText phone;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
//    public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bluetooth_monitor);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        deviceName = getIntent().getStringExtra("name");
        deviceAddress= getIntent().getStringExtra("address");
        delayView = (TextView) findViewById(R.id.delay);

        TextView view = (TextView) findViewById(R.id.devnameV);
        view.setText(deviceName);
        view = (TextView) findViewById(R.id.addressV);
        view.setText(deviceAddress);
        phone = (EditText) findViewById(R.id.newNumber);
        phone.addTextChangedListener(new PhoneNumberFormattingTextWatcher());


        if (!initialize()) {
            Log.e(TAG, "Unable to initialize Bluetooth");
            finish();
        }
        // Automatically connects to the device upon successful start-up initialization.
        connect(deviceAddress);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothGatt.close();
        timer.cancel();
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
//                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
//                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                sendSMS(false);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        delayView.setText("Disconnected");
                        updateFuture.cancel(true);
                    }
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                serial=mBluetoothGatt.getService(SERIALSERVICE).getCharacteristic(SERIAL);
                mBluetoothGatt.setCharacteristicNotification(serial, true);
                timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        serial.setValue(new byte[]{0});
                        sent = System.nanoTime();
                        mBluetoothGatt.writeCharacteristic(serial);
                    }
                }, 0, 400);
//                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            if(characteristic.getUuid().equals(SERIAL)) {
                if (characteristic.getValue()[0]==48) {
                    delay = (System.nanoTime() - sent)/1000000;
                    if(delay>500 && noAlert){
                        noAlert=false;
                        sendSMS(true);
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                noAlert=true;
                            }
                        },100000);
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            delayView.setText(Long.toString(delay));
                            updateFuture.cancel(true);
                        }
                    });
                }
            }
        }
    };

    private ExecutorService threadPoolExecutor = Executors.newSingleThreadExecutor();
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            Toast.makeText(BluetoothMonitor.this,"",Toast.LENGTH_SHORT).show();
            updateFuture.cancel(true);
        }
    };
    // submit task to threadpool:
    private Future updateFuture = threadPoolExecutor.submit(runnable);


    public void updateCaretaker(View v){
        if(phone.getText().toString().length()==14){
            phoneNumber = phone.getText().toString();
            TextView view = (TextView) findViewById(R.id.currentNumber);
            view.setText(phoneNumber);
            phone.setText("");
            phoneNumber = phoneNumber.replaceAll("[^\\d]", "");
        }
        else
            Toast.makeText(this, "Make sure you enter a 10-digit number",Toast.LENGTH_SHORT).show();
    }

    private void sendSMS(boolean stillConnected){
        String message;
        if(stillConnected) message = "Patient is at edge of monitored area.";
        else message = "Smart Walker has been disconnected.";
        if(phoneNumber!=null){
            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
                Toast.makeText(getApplicationContext(), "SMS Sent!",
                        Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(BluetoothMonitor.this,
                        "SMS failed, please try again later!",
                        Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }







    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
//        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
//            int flag = characteristic.getProperties();
//            int format = -1;
//            if ((flag & 0x01) != 0) {
//                format = BluetoothGattCharacteristic.FORMAT_UINT16;
//                Log.d(TAG, "Heart rate format UINT16.");
//            } else {
//                format = BluetoothGattCharacteristic.FORMAT_UINT8;
//                Log.d(TAG, "Heart rate format UINT8.");
//            }
//            final int heartRate = characteristic.getIntValue(format, 1);
//            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
//            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
//        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
//            }
        }
        sendBroadcast(intent);
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (deviceAddress != null && address.equals(deviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, true, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null)
            return null;
        return mBluetoothGatt.getServices();
    }
}
