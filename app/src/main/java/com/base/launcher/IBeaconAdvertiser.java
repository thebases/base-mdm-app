package com.base.launcher;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public class IBeaconAdvertiser {

    private static final String TAG = "IBeaconAdvertiser";

    private static final UUID BEACON_UUID = UUID.fromString(UUID.randomUUID().toString());
//            UUID.fromString("fda50693-a4e2-4fb1-afcf-c6eb07647825");
    private static final int MAJOR = 1;
    private static final int MINOR = 1;
    private static final int MEASURED_POWER_DBM = -59;

    private static BluetoothLeAdvertiser advertiser;
    private static AdvertiseCallback advertiseCallback;

    // ========= PUBLIC: get full frame hex ====================

    /**
     * Returns the full iBeacon frame as hex:
     * 4C00 0215 <UUID16> <MAJOR> <MINOR> <POWER>
     */
    public static String getFullFrameHex() {
        byte[] payload = buildIBeaconPayload(); // 02 15 ...
        return "4C00" + bytesToHex(payload);    // prepend Apple company ID
    }

    // ========= Start / Stop advertising ======================

    public static void start(Context context) {
        try {
            BluetoothManager btManager =
                    (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (btManager == null) {
                Log.e(TAG, "BluetoothManager is null");
                return;
            }

            BluetoothAdapter adapter = btManager.getAdapter();
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
//                return;
            }
            adapter.setName("Base");
            if (adapter == null || !adapter.isEnabled()) {
                Log.e(TAG, "Bluetooth disabled or not available");
                return;
            }

            if (!adapter.isMultipleAdvertisementSupported()) {
                Log.e(TAG, "BLE advertising not supported");
                return;
            }

            advertiser = adapter.getBluetoothLeAdvertiser();
            if (advertiser == null) {
                Log.e(TAG, "BluetoothLeAdvertiser is null");
                return;
            }

            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "BLUETOOTH_ADVERTISE permission not granted");
                return;
            }

            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(false)
                    .build();

            byte[] payload = buildIBeaconPayload();  // 02 15 ...
            AdvertiseData data = new AdvertiseData.Builder()
                    .addManufacturerData(0x004C, payload) // Android adds 4C 00
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .build();

            // Log hex so you can see it in Logcat
            Log.i(TAG, "iBeacon payload (no company ID): " + bytesToHex(payload));
            Log.i(TAG, "FULL frame (with 4C00): 4C00" + bytesToHex(payload));

            advertiseCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    Log.i(TAG, "iBeacon advertising started");
                }

                @Override
                public void onStartFailure(int errorCode) {
                    Log.e(TAG, "iBeacon advertising failed: " + errorCode);
                }
            };

            advertiser.startAdvertising(settings, data, advertiseCallback);
        } catch (Throwable t) {
            Log.e(TAG, "Error starting iBeacon advertising", t);
        }
    }

    public static void stop(Context context) {
        try {
            if (advertiser != null && advertiseCallback != null) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                advertiser.stopAdvertising(advertiseCallback);
                Log.i(TAG, "iBeacon advertising stopped");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error stopping iBeacon advertising", t);
        }
    }

    // ========= Internal helpers ==============================

    // Builds: 02 15 <UUID16> <MAJOR> <MINOR> <POWER>
    private static byte[] buildIBeaconPayload() {
        ByteBuffer bb = ByteBuffer.allocate(2 + 16 + 2 + 2 + 1);
        bb.order(ByteOrder.BIG_ENDIAN);

        bb.put((byte) 0x02);   // type
        bb.put((byte) 0x15);   // length = 21
        bb.putLong(BEACON_UUID.getMostSignificantBits());
        bb.putLong(BEACON_UUID.getLeastSignificantBits());
        bb.putShort((short) MAJOR);
        bb.putShort((short) MINOR);
        bb.put((byte) MEASURED_POWER_DBM);

        return bb.array();     // 02 15 ...
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
