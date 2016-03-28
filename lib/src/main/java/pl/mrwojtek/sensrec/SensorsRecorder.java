/*
 * (C) Copyright 2013, 2015 Wojciech Mruczkiewicz
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

package pl.mrwojtek.sensrec;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import pl.mrwojtek.sensrec.ble.BleRecorder;

/**
 * Provides functionality to manage recordings lifecycle.
 */
public class SensorsRecorder implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String PREF_FILE_SAVE = "pref_file_save";
    public static final String PREF_NETWORK_SAVE = "pref_network_save";
    public static final String PREF_NETWORK_HOST = "pref_network_host";
    public static final String PREF_NETWORK_PROTOCOL = "pref_network_protocol";
    public static final String PREF_NETWORK_PORT = "pref_network_port";
    public static final String PREF_SAVE_BINARY = "pref_save_binary";
    public static final String PREF_SAMPLING_PERIOD = "pref_sampling_period";
    public static final String PREF_SENSOR_= "sensor_";
    public static final String PREF_BLE_DEVICES = "ble_devices";
    public static final String PREF_BLE_NAME_ = "ble_name_";
    public static final String PREF_BLE_ID_ = "ble_id_";
    public static final String PREF_LAST_FILE_INDEX = "file_index";

    public static final int PROTOCOL_TCP = 0;
    public static final int PROTOCOL_UDP = 1;

    public static final int DEFAULT_PORT = 44335;
    public static final int DEFAULT_PROTOCOL = PROTOCOL_TCP;
    public static final String DEFAULT_HOST = "";
    public static final boolean DEFAULT_NETWORK_SAVE = false;
    public static final boolean DEFAULT_FILE_SAVE = true;
    public static final boolean DEFAULT_SAVE_BINARY = true;
    public static final long DEFAULT_SAMPLING_PERIOD = SensorManager.SENSOR_DELAY_NORMAL;

    public static final short TYPE_START = -1;
    public static final short TYPE_PAUSE = -2;
    public static final short TYPE_END = -3;
    public static final short TYPE_DEVICE = -4;
    public static final short TYPE_BATTERY_VOLTAGE = -5;
    public static final short TYPE_GPS = -6;
    public static final short TYPE_GPS_NMEA = -7;
    public static final short TYPE_BLE = -8;

    protected static final int LOG_VERSION = 1301;

    protected static final String SEPARATOR = "\t";
    protected static final String NEW_LINE = "\n";
    protected static final String PREFIX_UNKNOWN = "unknown";
    protected static final String BINARY_FILE_NAME = "Recording %d.bin";
    protected static final String TEXT_FILE_NAME = "Recording %d.txt";
    protected static final String MAGIC_WORD = "SensorsRecord";

    protected static final String TAG = "SensRec";

    protected Context context;
    protected Handler uiHandler;
    protected SensorManager sensorManager;
    protected LocationManager locationManager;
    protected BluetoothManager bluetoothManager;
    protected BluetoothAdapter bluetoothAdapter;
    protected SharedPreferences prefs;
    protected List<OnRecordingListener> onRecordingListeners = new ArrayList<>();
    protected List<OnBleRecordersChangedListener> onBleRecordersListeners = new ArrayList<>();
    protected PhysicalRecorderComparator physicalComparator;
    protected List<Recorder> recorders;
    protected SortedMap<Integer, BleRecorder> bleRecorders;
    protected RecorderOutput output;

    protected long lastDuration;
    protected long lastTime;
    protected boolean active;
    protected boolean paused;

    public static short getSensorTypeId(int type) {
        return (short) (type * 2);
    }

    public static short getSensorAccuracyId(int type) {
        return (short) (type * 2 + 1);
    }

    public SensorsRecorder(Context context) {
        this.context = context.getApplicationContext();
        uiHandler = new Handler(context.getMainLooper());
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        output = new RecorderOutput(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(this);
        initialize();
    }

    private void reinitializeBle() {
        Set<String> devices = prefs.getStringSet(PREF_BLE_DEVICES, null);

        SortedMap<Integer, BleRecorder> newRecorders = new TreeMap<>();

        if (bleRecorders != null) {
            for (Map.Entry<Integer, BleRecorder> entry : bleRecorders.entrySet()) {
                BleRecorder recorder = entry.getValue();
                if (devices != null && devices.contains(recorder.getAddress()) &&
                        prefs.getInt(PREF_BLE_ID_ + recorder.getAddress(), 0) == entry.getKey()) {
                    newRecorders.put(entry.getKey(), recorder);
                } else if (isRecording()) {
                    recorder.stop();
                }
            }
        }

        if (devices != null) {
            for (String address : devices) {
                int id = prefs.getInt(PREF_BLE_ID_ + address, 0);
                if (!newRecorders.containsKey(id)) {
                    String name = prefs.getString(PREF_BLE_NAME_ + address, null);
                    BleRecorder recorder = new BleRecorder(this, id, address, name);
                    newRecorders.put(id, recorder);
                    if (isRecording()) {
                        recorder.start();
                    }
                }
            }
        }

        bleRecorders = newRecorders;

        notifyBleRecordersChanged();
    }

    public void initialize() {
        reinitializeBle();

        recorders = new ArrayList<>();
        recorders.add(new LocationRecorder(this));
        recorders.add(new NmeaRecorder(this));

        Map<Integer, List<Sensor>> sensors = new HashMap<>();
        Map<Integer, Sensor> defaultSensor = new HashMap<>();

        for (Sensor sensor : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
            Integer type = sensor.getType();
            List<Sensor> list = sensors.get(type);
            if (list == null) {
                list = new ArrayList<>();
                sensors.put(type, list);
                defaultSensor.put(type, sensorManager.getDefaultSensor(type));
            }
            list.add(sensor);
        }

        for (Map.Entry<Integer, List<Sensor>> entry : sensors.entrySet()) {
            Sensor def = defaultSensor.get(entry.getKey());
            List<Sensor> list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                Integer number = list.size() > 1 ? i : null;
                Sensor sensor = list.get(i);
                recorders.add(new SensorRecorder(this, sensor, i, getShortName(sensor, number),
                        sensor == def));
            }
        }

        recorders.add(new BatteryRecorder(this));

        physicalComparator = new PhysicalRecorderComparator();
        Collections.sort(recorders, physicalComparator);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_FILE_SAVE.equals(key)) {
            if (isSaving()) {
                output.getFileOutput().start();
            } else {
                output.getFileOutput().stop();
            }
            notifyOutput();
        } else if (PREF_NETWORK_SAVE.equals(key)
                || PREF_NETWORK_PROTOCOL.equals(key)
                || PREF_NETWORK_HOST.equals(key)
                || PREF_NETWORK_PORT.equals(key)) {
            if (isStreaming()) {
                output.getSocketOutput().start();
            } else {
                output.getSocketOutput().stop();
            }
        } else if (PREF_BLE_DEVICES.equals(key)) {
            reinitializeBle();
        } else if (isRecording() && key != null && key.startsWith(PREF_SENSOR_)) {
            for (Recorder recorder : recorders) {
                if (isEnabled(recorder)) {
                    recorder.start();
                } else {
                    recorder.stop();
                }
            }
        }
    }

    public void addOnRecordingListener(OnRecordingListener onRecordingListener, boolean notify) {
        onRecordingListeners.add(onRecordingListener);

        if (notify) {
            if (active) {
                if (paused) {
                    notifyPaused();
                } else {
                    notifyStarted();
                }
            } else {
                notifyStopped();
            }
        }
    }

    public boolean removeOnRecordingListener(OnRecordingListener onRecordingListener) {
        onRecordingListeners.remove(onRecordingListener);
        return onRecordingListeners.isEmpty();
    }

    public void addOnBleRecordersChangedListener(OnBleRecordersChangedListener listener) {
        onBleRecordersListeners.add(listener);
    }

    public void removeOnBleRecordersChangedListener(OnBleRecordersChangedListener listener) {
        onBleRecordersListeners.remove(listener);
    }

    protected SensorManager getSensorManager() {
        return sensorManager;
    }

    public LocationManager getLocationManager() {
        return locationManager;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public Handler getUiHandler() {
        return uiHandler;
    }

    public Context getContext() {
        return context;
    }

    public SortedMap<Integer, BleRecorder> getBleRecorders() {
        return bleRecorders;
    }

    public List<Recorder> getFixedRecorders() {
        return recorders;
    }

    public PhysicalRecorderComparator getPhysicalComparator() {
        return physicalComparator;
    }

    public RecorderOutput getOutput() {
        return output;
    }

    public long getDuration(long millisecond) {
        if (active && !paused) {
            return lastDuration + millisecond - lastTime;
        } else {
            return lastDuration;
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isRecording() {
        return active && !paused;
    }

    public void start() {
        if (!active) {
            lastTime = SystemClock.elapsedRealtime();
            lastDuration = 0;
            active = true;
            paused = false;
            startOutput();
            startBluetooth();
            startSensors();
            notifyStarted();
        } else if (paused) {
            lastTime = SystemClock.elapsedRealtime();
            paused = false;
            startBluetooth();
            startSensors();
            notifyStarted();
        }
    }

    public void pause() {
        if (!paused) {
            lastDuration += SystemClock.elapsedRealtime() - lastTime;
            paused = true;
            stopSensors();
            stopBluetooth();
            notifyPaused();
        }
    }

    public void stop() {
        if (active) {
            if (!paused) {
                lastDuration += SystemClock.elapsedRealtime() - lastTime;
            }
            active = false;
            stopSensors();
            stopBluetooth();
            stopOutput();
            notifyStopped();
        }
    }

    protected void startOutput() {
        output.start(prefs.getBoolean(PREF_SAVE_BINARY, DEFAULT_SAVE_BINARY));
    }

    protected void stopOutput() {
        output.stop();
    }

    protected boolean startBluetooth() {
        if (bluetoothManager != null && Build.VERSION.SDK_INT >= 18) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter == null) {
                Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
                return false;
            }
            return true;
        }
        return false;
    }

    protected void stopBluetooth() {
        bluetoothAdapter = null;
    }

    protected void startSensors() {
        for (Map.Entry<Integer, BleRecorder> recorder : bleRecorders.entrySet()) {
            recorder.getValue().start();
        }
        for (Recorder recorder : recorders) {
            if (isEnabled(recorder)) {
                recorder.start();
            }
        }
    }

    protected void stopSensors() {
        for (Recorder recorder : recorders) {
            recorder.stop();
        }
        for (Map.Entry<Integer, BleRecorder> recorder : bleRecorders.entrySet()) {
            recorder.getValue().stop();
        }
    }

    protected boolean isEnabled(Recorder recorder) {
        return prefs.getBoolean(recorder.getPrefKey(),
                physicalComparator.isDefaultEnabled(recorder));
    }

    public void recordStart(Output.Record record) {
        long time = SystemClock.elapsedRealtime();
        long wallTime = System.currentTimeMillis();
        record.start(TYPE_START, (short) 0)                 // 4B
                .write(MAGIC_WORD, 0, MAGIC_WORD.length())  // 4B + len(MAGIC_WORD)
                .write(LOG_VERSION)                         // 4B
                .write(time)                                // 8B
                .write(wallTime)                            // 8B
                .save();
    }

    public void recordStop(Output.Record record) {
        long time = SystemClock.elapsedRealtime();
        long wallTime = System.currentTimeMillis();
        long duration = getDuration(SystemClock.elapsedRealtime());
        record.start(TYPE_END, (short) 0)                   // 4B
                .write(MAGIC_WORD, 0, MAGIC_WORD.length())  // 4B + len(MAGIC_WORD)
                .write(LOG_VERSION)                         // 4B
                .write(time)                                // 8B
                .write(wallTime)                            // 8B
                .write(duration)                            // 8B
                .write(0l)                                  // 8B, moving time
                .write(-1.0)                                // 8B, move distance
                .save();
    }

    public boolean isSaving() {
        return prefs.getBoolean(PREF_FILE_SAVE, DEFAULT_FILE_SAVE);
    }

    public String getOutputFileName(boolean binary) {
        if (binary) {
            return BINARY_FILE_NAME;
        } else {
            return TEXT_FILE_NAME;
        }
    }

    public boolean isStreaming() {
        return prefs.getBoolean(PREF_NETWORK_SAVE, DEFAULT_NETWORK_SAVE);
    }

    public String getOutputHost(boolean binary) {
        return prefs.getString(PREF_NETWORK_HOST, DEFAULT_HOST);
    }

    public int getOutputProtocol(boolean binary) {
        return prefs.getInt(PREF_NETWORK_PROTOCOL, DEFAULT_PROTOCOL);
    }

    public int getOutputPort(boolean binary) {
        return prefs.getInt(PREF_NETWORK_PORT, DEFAULT_PORT);
    }

    public int getLastFileIndex() {
        return prefs.getInt(PREF_LAST_FILE_INDEX, 1);
    }

    public void setLastFileIndex(int fileIndex) {
        prefs.edit().putInt(PREF_LAST_FILE_INDEX, fileIndex).apply();
    }

    public String getTextSeparator() {
        return SEPARATOR;
    }

    public String getTextNewLine() {
        return NEW_LINE;
    }

    public String getTypePrefix(short typeId, short deviceId) {
        if (typeId < 0) {
            return getOtherTypePrefix(typeId);
        } else {
            String prefix = getSensorTypePrefix((short) (typeId / 2));
            if (!PREFIX_UNKNOWN.equals(prefix)) {
                if (typeId < 0 || typeId % 2 == 0) {
                    return String.format("%s_%d", prefix, deviceId);
                } else {
                    return String.format("%s_%d_acc", prefix, deviceId);
                }
            } else {
                return prefix;
            }
        }
    }

    public String getOtherTypePrefix(short typeId) {
        switch (typeId) {
            case TYPE_START:
                return "start";
            case TYPE_PAUSE:
                return "pause";
            case TYPE_END:
                return "end";
            case TYPE_DEVICE:
                return "device";
            case TYPE_BATTERY_VOLTAGE:
                return "bat";
            case TYPE_GPS:
                return "gps";
            case TYPE_GPS_NMEA:
                return "nmea";
            case TYPE_BLE:
                return "ble";
            default:
                return PREFIX_UNKNOWN;
        }
    }

    public String getSensorTypePrefix(short typeId) {
        switch (typeId) {
            case Sensor.TYPE_ACCELEROMETER:
                return "accel";
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                return "amb";
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                return "rotg";
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                return "rotgeo";
            case Sensor.TYPE_GRAVITY:
                return "grav";
            case Sensor.TYPE_GYROSCOPE:
                return "gyro";
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                return "ugyro";
            case Sensor.TYPE_HEART_RATE:
                return "heart";
            case Sensor.TYPE_LIGHT:
                return "light";
            case Sensor.TYPE_LINEAR_ACCELERATION:
                return "lacc";
            case Sensor.TYPE_MAGNETIC_FIELD:
                return "magn";
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                return "umagn";
            case Sensor.TYPE_ORIENTATION:
                return "orient";
            case Sensor.TYPE_PRESSURE:
                return "press";
            case Sensor.TYPE_PROXIMITY:
                return "prox";
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                return "humi";
            case Sensor.TYPE_ROTATION_VECTOR:
                return "rotv";
            case Sensor.TYPE_SIGNIFICANT_MOTION:
                return "motion";
            case Sensor.TYPE_STEP_COUNTER:
                return "stepc";
            case Sensor.TYPE_STEP_DETECTOR:
                return "stepd";
            case Sensor.TYPE_TEMPERATURE:
                return "temp";
            default:
                return PREFIX_UNKNOWN;
        }
    }

    public String getBatteryVoltageName() {
        return context.getString(R.string.sensor_battery_voltage);
    }

    public String getBatteryVoltageDescription() {
        return context.getString(R.string.sensor_battery_voltage_description);
    }

    public String getGpsName() {
        return context.getString(R.string.sensor_gps);
    }

    public String getGpsDescription() {
        return context.getString(R.string.sensor_gps_description);
    }

    public String getGpsNmeaName() {
        return context.getString(R.string.sensor_gps_nmea);
    }

    public String getGpsNmeaDescription() {
        return context.getString(R.string.sensor_gps_nmea_description);
    }

    public String getShortName(Sensor sensor, Integer number) {
        int stringId;
        switch (sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                stringId = R.string.sensor_accelerometer;
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                stringId = R.string.sensor_ambient_temperature;
                break;
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                stringId = R.string.sensor_game_rotation_vector;
                break;
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                stringId = R.string.sensor_geomagnetic_rotation_vector;
                break;
            case Sensor.TYPE_GRAVITY:
                stringId = R.string.sensor_gravity;
                break;
            case Sensor.TYPE_GYROSCOPE:
                stringId = R.string.sensor_gyroscope;
                break;
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                stringId = R.string.sensor_gyroscope_uncalibrated;
                break;
            case Sensor.TYPE_HEART_RATE:
                stringId = R.string.sensor_heart_rate;
                break;
            case Sensor.TYPE_LIGHT:
                stringId = R.string.sensor_light;
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                stringId = R.string.sensor_linear_acceleration;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                stringId = R.string.sensor_magnetic_field;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                stringId = R.string.sensor_magnetic_field_uncalibrated;
                break;
            case Sensor.TYPE_ORIENTATION:
                stringId = R.string.sensor_orientation;
                break;
            case Sensor.TYPE_PRESSURE:
                stringId = R.string.sensor_pressure;
                break;
            case Sensor.TYPE_PROXIMITY:
                stringId = R.string.sensor_proximity;
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                stringId = R.string.sensor_relative_humidity;
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                stringId = R.string.sensor_rotation_vector;
                break;
            case Sensor.TYPE_SIGNIFICANT_MOTION:
                stringId = R.string.sensor_significant_motion;
                break;
            case Sensor.TYPE_STEP_COUNTER:
                stringId = R.string.sensor_step_counter;
                break;
            case Sensor.TYPE_STEP_DETECTOR:
                stringId = R.string.sensor_step_detector;
                break;
            case Sensor.TYPE_TEMPERATURE:
                stringId = R.string.sensor_temperature;
                break;
            default:
                stringId = R.string.sensor_unknown;
        }

        String name = context.getResources().getString(stringId);
        if (number != null) {
            return context.getResources().getString(R.string.sensor_many, name, number);
        } else {
            return name;
        }
    }

    public String getDescription(Sensor sensor, boolean sensorDefault) {
        int id = sensorDefault ? R.string.sensor_full_name_default : R.string.sensor_full_name;
        return context.getString(id, sensor.getName(), sensor.getVersion(), sensor.getPower());
    }

    protected void notifyStarted() {
        for (OnRecordingListener listener : onRecordingListeners) {
            listener.onStarted();
        }
    }

    protected void notifyStopped() {
        for (OnRecordingListener listener : onRecordingListeners) {
            listener.onStopped();
        }
    }

    protected void notifyPaused() {
        for (OnRecordingListener listener : onRecordingListeners) {
            listener.onPaused();
        }
    }

    protected void notifyOutput() {
        boolean saving = isSaving();
        boolean streaming = isStreaming();
        for (OnRecordingListener listener : onRecordingListeners) {
            listener.onOutput(saving, streaming);
        }
    }

    protected void notifyBleRecordersChanged() {
        for (OnBleRecordersChangedListener listener : onBleRecordersListeners) {
            listener.onBleRecordersChanged();
        }
    }

    public interface OnRecordingListener {
        void onStarted();
        void onStopped();
        void onPaused();
        void onOutput(boolean saving, boolean streaming);
    }

    public interface OnBleRecordersChangedListener {
        void onBleRecordersChanged();
    }
}
