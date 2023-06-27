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
import android.bluetooth.BluetoothGattDescriptor;
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

import android.os.Looper;
import android.os.Message;

import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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
    private static final String DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";
    private final Semaphore connectionSemaphore = new Semaphore(1);
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

    Button initializeBluetooth;
    Button scanForBluetooth;
    Button startBtn;
    Button stopBtn;
    TextView textView;
    TextView lastDetection;
    Context context;

    ArrayList<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
    Map<String, Parcelable[]> uuidMapping = new HashMap<String, Parcelable[]>();
    BluetoothLeScanner bluetoothScanner = adapter.getBluetoothLeScanner();
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
//            super.onScanResult(callbackType, result);
            devices.add(result.getDevice());
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.i("Scan failed", "Could not complete scan for nearby BLE devices");
            return;
        }
    };
    @SuppressLint("MissingPermission")
    BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("Device info", "Discovering bluetooth services of target device...");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "Connected to ESP32 successfully", Toast.LENGTH_SHORT).show();
                    }
                });
                  gatt.requestMtu(512);
//                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("Device info", "Disconnecting bluetooth device...");
                gatt.disconnect();
                gatt.close();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("MTU Request", "MTU request success");
                gatt.discoverServices();
            } else {
                Log.i("MTU Request", "MTU request failed");
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // Read the updated characteristic value
            byte[] message = characteristic.getValue();
            String messageString = new String(message, StandardCharsets.UTF_8);
            Log.i("Unparsed JSON string: ", messageString);
            String status = "";
            int lastDetected = 0;
            boolean motionDetected, proximityDetected, lightDetected, vibrationDetected;
            try {
                JSONObject jsonObject = new JSONObject(messageString);
                status = jsonObject.getString("status");
                lastDetected = jsonObject.getInt("lastDetected");
                motionDetected = jsonObject.getBoolean("motion");
                proximityDetected = jsonObject.getBoolean("proximity");
                lightDetected = jsonObject.getBoolean("light");
                vibrationDetected = jsonObject.getBoolean("vibration");
                //float lightIntensity = (float) jsonObject.getDouble("lightIntensity");

                String finalStatus = status;
                int finalLastDetected = lastDetected;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText("Status: " + finalStatus + "\nMotion: " + motionDetected + "\nProximity: " + proximityDetected + "\nLight: " + lightDetected + "\nVibration: " + vibrationDetected);
                        lastDetection.setText("Last detected: " + finalLastDetected + "m ago");
                    }
                });
            } catch (JSONException e) {
                Log.i("Error", "Could not parse JSON string");
            }
            Log.i("Notification", "Updated status: " + status);
            Log.i("Notification", "Last detected: " + lastDetected);
            // Do something with the updated characteristic value
        }

        ;

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
                Log.i("Device info", "Successfully discovered services of target device");
                if (service != null) {
                    Log.i("Service status", "Service is not null.");
                    BluetoothGattCharacteristic discoveredCharacteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
                    if (discoveredCharacteristic != null) {
                            gatt.readCharacteristic(discoveredCharacteristic);
                            if (gatt.setCharacteristicNotification(discoveredCharacteristic, true)) {
                                Log.i("Set characteristic notification", "Success!");
                                Log.i("Characteristic property flags" , String.valueOf(discoveredCharacteristic.getProperties()));
                                BluetoothGattDescriptor desc = discoveredCharacteristic.getDescriptor(UUID.fromString(DESCRIPTOR_UUID));
                                desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(desc);
                                //gatt.requestMtu(512);
                            } else {
                                Log.i("Set characteristic notification", "Failure!");
                            }
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
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic discoveredCharacteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte data[] = discoveredCharacteristic.getValue();
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                showExplanation("Permission Needed", "Rationale", Manifest.permission.BLUETOOTH_SCAN, REQUEST_BLUETOOTH_SCAN);
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BLUETOOTH_SCAN);
            }
        } else {
            bluetoothScanner.startScan(scanCallback);
            startBtn.setVisibility(View.VISIBLE);
        }
    }

    @SuppressLint("MissingPermission")
    private void startProcess() {
        BluetoothDevice device = null;
        String targetDeviceAddress = "";

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        List<BluetoothDevice> connectedDevices = manager.getConnectedDevices(BluetoothProfile.GATT);
        while (connectedDevices.isEmpty()) {
            if (!devices.isEmpty()) {
                Log.i("Number of found devices", "# of devices: " + devices.size());
                Log.i("Found Devices", "Devices: ");
                for (int i = 0; i < devices.size(); i++) {
                    if (devices.get(i) != null && devices.get(i).getName() != null) {
                        Log.i("Devices", "Device #" + String.valueOf(i) + " name: " + devices.get(i).getName());
                        if (devices.get(i).getName().equals("ESP32")) {
                            targetDeviceAddress = devices.get(i).getAddress();
                            device = devices.get(i);
                            Log.i("Device Found", "Found target device: " + device.getName());
                            Log.i("Device Address", "Device address is: " + targetDeviceAddress);
                            bluetoothScanner.stopScan(scanCallback);
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
                connectedDevices = manager.getConnectedDevices(BluetoothProfile.GATT);

        }
        initializeBluetooth.setVisibility(View.INVISIBLE);
        scanForBluetooth.setVisibility(View.INVISIBLE);
        startBtn.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeBluetooth = findViewById(R.id.initializeBluetooth);
        scanForBluetooth = findViewById(R.id.scanBluetooth);
        textView = findViewById(R.id.statusText);
        lastDetection = findViewById(R.id.lastDetection);
        startBtn = findViewById(R.id.startBtn);

        scanForBluetooth.setVisibility(View.INVISIBLE);
        startBtn.setVisibility(View.INVISIBLE);
        context = this;

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
                startProcess();
            }
        });
    }
}
