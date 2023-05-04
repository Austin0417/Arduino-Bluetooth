package com.example.myapp2;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.IntentFilter;

import android.os.Message;

import android.os.Handler;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private interface MessageConstants {
        public static final int MESSAGE_READ = 0;
        public static final int MESSAGE_WRITE = 1;
        public static final int MESSAGE_TOAST = 2;
    }

    public static final int REQUEST_BLUETOOTH_SCAN = 3;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ENABLE_BLUETOOTH_ADMIN = 2;
    private static final String CHARACTERISTIC_UUID = "BEB5483E-36E1-4688-B7F5-EA07361B26A8";
    private static final String SERVICE_UUID = "00001101-0000-1000-8000-00805F9B34FB";


    BluetoothManager manager;
    BluetoothGattServer server;
    BluetoothAdapter adapter;
    Button initializeBluetooth;
    Button scanForBluetooth;
    Button startBtn;
    InputStream input;
    OutputStream output;
    Handler handler;
    byte[] inputBuffer;
    ArrayList<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
    Map<String, Parcelable[]> uuidMapping = new HashMap<String, Parcelable[]>();
    BluetoothLeAdvertiser bluetoothLeAdvertiser;
    @SuppressLint("MissingPermission")
    BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("Device connected", "Device address: " + device.getAddress());
                if (device != null) {
                    devices.add(device);
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("Device disconnected", "Device address: " + device.getAddress());
            }
        }
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (device != null && characteristic.getUuid().equals(CHARACTERISTIC_UUID)) {
                byte[] value = characteristic.getValue();
                String message = new String(value, StandardCharsets.UTF_8);
                Log.i("Received message", message);
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            if (device != null && characteristic.getUuid().equals(UUID.fromString(CHARACTERISTIC_UUID))) {
                String message = new String(value, StandardCharsets.UTF_8);
                Log.i("Received message" , message);
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }

    };
    AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.i("Advertise success", "Successfully started advertising.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.i("Advertise failure", "Couldn't start advertising. Error code: " + errorCode);

        }
    };

    private void showExplanation(String title, String message, String permission, int permissionRequestCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title).setMessage(message).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                requestPermissions(new String[]{permission}, permissionRequestCode);
            }
        });
        builder.create().show();
    }
    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i("Permission", "Bluetooth permission granted");
                adapter.enable();
            } else {
                Log.i("Permission", "Bluetooth permission denied");
            }
        } else if (requestCode == REQUEST_BLUETOOTH_SCAN) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i("Permission", "Bluetooth scan permission granted");
                    //adapter.startDiscovery();
                } else {
                    Log.i("Permission", "Bluetooth scan permission denied");
                }
        }
    }

    ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    MainActivity.super.onActivityResult(REQUEST_ENABLE_BT, result.getResultCode(), result.getData());
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_ENABLE_BT);
                        } else {
                            Intent data = result.getData();
                            adapter.enable();
                        }
                    }
                }
            }
    );
    private void initializeAdapters() {
        //manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (adapter == null) {
            Toast.makeText(this, "Device does not support Bluetooth", Toast.LENGTH_LONG).show();
            return;
        }
        if (adapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is enabled", Toast.LENGTH_SHORT).show();
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            someActivityResultLauncher.launch(enableBtIntent);
        }
        scanForBluetooth.setVisibility(View.VISIBLE);
    }
    private void scanForBluetooth() {
        BluetoothDevice device = null;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                showExplanation("Permission Needed", "Rationale", Manifest.permission.BLUETOOTH_SCAN, REQUEST_BLUETOOTH_SCAN);
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BLUETOOTH_SCAN);
            }
        } else {
            startBtn.setVisibility(View.VISIBLE);
        }
    }
    @SuppressLint("MissingPermission")
    private void start() {
        server = manager.openGattServer(this, gattServerCallback);
        BluetoothGattService service = new BluetoothGattService(UUID.fromString(SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString(CHARACTERISTIC_UUID), BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        service.addCharacteristic(characteristic);
        server.addService(service);
        AdvertiseSettings settings = new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setConnectable(true).setTimeout(0).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH).build();
        AdvertiseData data = new AdvertiseData.Builder().setIncludeDeviceName(true).setIncludeTxPowerLevel(true).addServiceUuid(new ParcelUuid(UUID.fromString(SERVICE_UUID))).build();
        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();
        bluetoothLeAdvertiser = adapter.getBluetoothLeAdvertiser();

        initializeBluetooth = findViewById(R.id.initializeBluetooth);
        scanForBluetooth = findViewById(R.id.scanBluetooth);
        startBtn = findViewById(R.id.startBtn);
        scanForBluetooth.setVisibility(View.INVISIBLE);
        startBtn.setVisibility(View.INVISIBLE);
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        initializeBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initializeAdapters();
            }
        });
        scanForBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanForBluetooth();
            }
        });
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                start();
            }
        });
//        initializeAdapters();
//        scanForBluetooth();
        //run();
    }

    private void run(String deviceAddress) {
        BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);
        inputBuffer = new byte[1024];
        int numBytes;

        while (true) {
            try {
                numBytes = input.read(inputBuffer);
                Message data = handler.obtainMessage(MessageConstants.MESSAGE_READ, numBytes, -1, inputBuffer);
                data.sendToTarget();
            } catch(IOException e) {
                Log.e("Read input stream", "Input stream was disconnected", e);
                break;
            }
        }
    }
}
