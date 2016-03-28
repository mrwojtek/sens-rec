/*
 * (C) Copyright 2013, 2015-2106 Wojciech Mruczkiewicz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Wojciech Mruczkiewicz
 */

package pl.mrwojtek.sensrec.ble;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import pl.mrwojtek.sensrec.FrequencyMeasure;
import pl.mrwojtek.sensrec.Recorder;
import pl.mrwojtek.sensrec.SensorsRecorder;

/**
 * Recorder for Bluetooth Low Energy devices. Current implementation supports
 * only the heart rate sensor devices.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BleRecorder extends BluetoothGattCallback implements Recorder {

    private static final String TAG = "Ble";

    protected FrequencyMeasure measure = new FrequencyMeasure();
    protected SensorsRecorder sensorsRecorder;
    protected String address;
    protected String name;
    protected short deviceId;
    protected boolean started;
    protected BluetoothGatt bluetoothGatt;
    protected BluetoothDevice bluetoothDevice;
    protected Handler handler;

    protected Queue<BluetoothGattCharacteristic> forRetrieval = new LinkedList<>();
    protected List<BluetoothGattCharacteristic> forSubscription = new ArrayList<>();
    protected boolean discovered;

    protected Runnable connectRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized(BleRecorder.this) {
                if (bluetoothDevice == null) {
                    return;
                }
                if (bluetoothGatt != null) {
                    Log.v(TAG, "GATT disconnecting");
                    bluetoothGatt.disconnect();
                }
                if (started) {
                    if (bluetoothGatt == null) {
                        // Establish a new, direct BLE connection (autoConnect == false)
                        Log.v(TAG, "GATT establishing new connection");
                        bluetoothGatt = bluetoothDevice.connectGatt(
                                sensorsRecorder.getContext(), false, BleRecorder.this);
                        if (bluetoothGatt == null) {
                            Log.e(TAG, "GATT establishing connection failure");
                        }
                    } else {
                        // Use a background BLE connection (autoConnect == true)
                        Log.v(TAG, "GATT connecting");
                        bluetoothGatt.connect();
                    }
                }
            }
        }
    };

    public BleRecorder(SensorsRecorder sensorsRecorder, int id, String address, String name) {
        this.sensorsRecorder = sensorsRecorder;
        this.deviceId = (short) id;
        this.address = address;
        this.name = name;
        this.handler = sensorsRecorder.getUiHandler();
    }

    public String getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    @Override
    public short getTypeId() {
        return SensorsRecorder.TYPE_BLE;
    }

    @Override
    public short getDeviceId() {
        return deviceId;
    }

    @Override
    public String getPrefKey() {
        return null;
    }

    @Override
    public String getShortName() {
        return name;
    }

    @Override
    public String getDescription() {
        return address;
    }

    @Override
    public FrequencyMeasure getFrequencyMeasure() {
        return measure;
    }

    @Override
    public void start() {
        if (address == null || started) {
            return;
        }

        discovered = false;

        final BluetoothAdapter bluetoothAdapter = sensorsRecorder.getBluetoothAdapter();
        if (bluetoothAdapter != null) {
            Log.v(TAG, "Starting BleRecorder for " + address);
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
            started = true;
            measure.onStarted();
            connectRunnable.run();
        } else {
            Log.e(TAG, "Starting BleRecorder for " + address +
                    " error, BluetoothAdapter unavailable");
        }
    }

    @Override
    public void stop() {
        BluetoothGatt bluetoothGattForClose = null;
        synchronized(this) {
            if (started) {
                sensorsRecorder.getUiHandler().removeCallbacks(connectRunnable);
                measure.onStopped();
                forRetrieval.clear();
                forSubscription.clear();
                started = false;
            }

            if (bluetoothGatt != null) {
                Log.i(TAG, "Closing BleRecorder for " + address);
                bluetoothGattForClose = bluetoothGatt;
                bluetoothGatt = null;
                bluetoothDevice = null;
            }
        }

        if (bluetoothGattForClose != null) {
            bluetoothGattForClose.close();
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.w(TAG, "GATT server connection state " + newState + " failed " + status);
            handler.post(connectRunnable);
            return;
        }

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.i(TAG, "GATT server connected");
            synchronized(this) {
                if (bluetoothGatt == null) {
                    return;
                }
                if (!discovered) {
                    if (!bluetoothGatt.discoverServices()) {
                        Log.e(TAG, "Unable to discover services, aborting BLE recording");
                    }
                } else if (!scheduleRead()) {
                    subscribeForNotifications();
                }
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.i(TAG, "GATT server disconnected");
            handler.post(connectRunnable);
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        synchronized(this) {
            if (bluetoothGatt == null) {
                return;
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                resolveServices(gatt.getServices());
                if (!scheduleRead()) {
                    subscribeForNotifications();
                }
                discovered = true;
            } else {
                Log.w(TAG, "Services discovery failure: " + status);
                bluetoothGatt.discoverServices();
            }
        }
    }

    private void resolveServices(List<BluetoothGattService> services) {
        for (BluetoothGattService service : services) {
            Log.v(TAG, "Found service: " + service.getUuid());
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                Log.v(TAG, "Found characteristic: " + characteristic.getUuid());
                int flags = GattResolver.resolve(characteristic.getUuid());
                if ((flags & GattResolver.FLAG_RETRIEVE) != 0) {
                    forRetrieval.add(characteristic);
                }
                if ((flags & GattResolver.FLAG_SUBSCRIBE) != 0) {
                    forSubscription.add(characteristic);
                }
            }
            resolveServices(service.getIncludedServices());
        }
    }

    private boolean scheduleRead() {
        while (!forRetrieval.isEmpty()) {
            BluetoothGattCharacteristic characteristic = forRetrieval.peek();
            if (bluetoothGatt.readCharacteristic(characteristic)) {
                return true;
            } else {
                Log.w(TAG, "Characteristic " + characteristic.getUuid() + " read unavailable");
                forRetrieval.poll();
            }
        }
        return false;
    }

    private void subscribeForNotifications() {
        for (BluetoothGattCharacteristic characteristic : forSubscription) {
            if (bluetoothGatt.setCharacteristicNotification(characteristic, true)) {
                // Additional action specific to the Heart Rate Measurement.
                if (GattResolver.HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
                    configureHeartRateMeasurementNotification(characteristic);
                }
            } else {
                Log.e(TAG, "Unable to set characteristic " + characteristic.getUuid() +
                        " notification");
            }
        }
    }

    private void configureHeartRateMeasurementNotification(
            BluetoothGattCharacteristic characteristic) {
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                GattResolver.CLIENT_CHARACTERISTIC_CONFIG);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!bluetoothGatt.writeDescriptor(descriptor)) {
            Log.e(TAG, "Unable to configure heart rate measurement notification");
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic characteristic,
                                     int status) {
        if (status == BluetoothGatt.GATT_CONNECTION_CONGESTED) {
            Log.w(TAG, "Characteristic " + characteristic.getUuid() + " read error " + status);
            synchronized(this) {
                if (bluetoothGatt != null) {
                    bluetoothGatt.readCharacteristic(forRetrieval.peek());
                }
            }
        } else {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onCharacteristicValue(characteristic);
            } else {
                Log.e(TAG, "Characteristic " + characteristic.getUuid() + " read error " + status);
            }
            synchronized(this) {
                if (bluetoothGatt != null) {
                    forRetrieval.poll();
                    if (!scheduleRead()) {
                        subscribeForNotifications();
                    }
                }
            }
        }
    }

    @Override
    public synchronized void onCharacteristicChanged(BluetoothGatt gatt,
                                                     BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt != null) {
            onCharacteristicValue(characteristic);
        }
    }

    private void onCharacteristicValue(final BluetoothGattCharacteristic characteristic) {
        if (GattResolver.HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            final int flag = characteristic.getProperties();
            final int format = (flag & 0x01) != 0 ? BluetoothGattCharacteristic.FORMAT_UINT16 :
                    BluetoothGattCharacteristic.FORMAT_UINT8;
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, "Received " + characteristic.getUuid() + " (heartRate=" + heartRate + ")");
        } else {
            Log.d(TAG, "Received " + characteristic.getUuid());
        }

        if (started) {
            long millisecond = measure.onNewSample();
            sensorsRecorder.getOutput()
                    .start(getTypeId(), getDeviceId())
                    .write(millisecond)
                    .write(characteristic.getUuid().getMostSignificantBits())
                    .write(characteristic.getUuid().getLeastSignificantBits())
                    .write(characteristic.getValue(), 0, characteristic.getValue().length)
                    .save();
        }
    }
}
