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
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
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
    private static final String CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
    private static final String SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";

    Context context;
    BluetoothManager manager;
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothServerSocket server;
    BluetoothSocket socket;
    Button initializeBluetooth;
    Button scanForBluetooth;
    InputStream input;
    OutputStream output;
    Handler handler;
    byte[] inputBuffer;
    ArrayList<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
    Map<String, Parcelable[]> uuidMapping = new HashMap<String, Parcelable[]>();
    BluetoothLeScanner bluetoothScanner = adapter.getBluetoothLeScanner();
    @SuppressLint("MissingPermission")
    BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("Device info", "Discovering bluetooth services of target device...");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("Device info", "Disconnecting bluetooth device...");
                gatt.disconnect();
                gatt.close();
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            // Read the updated characteristic value
            byte[] message = characteristic.getValue();
            String messageString = new String(message, StandardCharsets.UTF_8);
            Log.i("Read Data:", "Received data: " + messageString);
            // Do something with the updated characteristic value
        };
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
                Log.i("Device info", "Successfully discovered services of target device");
                if (service != null) {
                    Log.i("Service status", "Service is not null.");
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
                    if (characteristic != null) {
                            gatt.readCharacteristic(characteristic);
                    } else {
                        Log.i("Characteristic info", "Characteristic not found!");
                    }
                } else {
                    Log.i("Service info", "Service not found!");
                }
            } else {
                Log.i("Service Discovery", "Service discovery failed");
            }
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                    byte data[] = characteristic.getValue();
                    String value = new String(data, StandardCharsets.UTF_8);
                    Log.i("Read data", "Received data: " + value);


            }
        }
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

            }
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
            bluetoothScanner.startScan(new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    devices.add(result.getDevice());
                }
                @Override
                public void onScanFailed(int errorCode) {
                    Log.i("Scan failed", "Could not complete scan for nearby BLE devices");
                    return;
                }
            });
        }

        String targetDeviceAddress = "";
        if (!devices.isEmpty()) {
            Log.i ("Number of found devices", "# of devices: " + devices.size());
            Log.i("Found Devices", "Devices: ");
            for (int i = 0; i < devices.size(); i++) {
                //bluetoothScanner.stopScan(scanCallback);
                if (devices.get(i) != null && devices.get(i).getName() != null) {
                    Log.i("Devices", "Device #" + String.valueOf(i) + " name: " + devices.get(i).getName());
                    if (devices.get(i).getName().equals("ESP32")) {
                        targetDeviceAddress = devices.get(i).getAddress();
                        device = devices.get(i);
                        Log.i("Device Found", "Found target device: " + device.getName());
                        Log.i("Device Address", "Device address is: " + targetDeviceAddress);
                        break;
                    }
                }
            }
            if (device == null) {
                Log.i("Devices", "Target device was not found");
                return;
            }
        } else {
            Log.i("Devices", "No devices were found");
            return;
        }
        BluetoothGatt gatt = device.connectGatt(this, false, gattCallback);
        gatt.discoverServices();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeBluetooth = findViewById(R.id.initializeBluetooth);
        scanForBluetooth = findViewById(R.id.scanBluetooth);
        scanForBluetooth.setVisibility(View.INVISIBLE);
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
//        registerReceiver(bluetoothReceiver, intentFilter);
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