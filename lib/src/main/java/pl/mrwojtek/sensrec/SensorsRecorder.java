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

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public static final String PREF_SENSOR_PREFIX = "sensor_";

    public static final int DEFAULT_PORT = 44335;
    public static final int DEFAULT_PROTOCOL = 0;
    public static final String DEFAULT_HOST = "";
    public static final boolean DEFAULT_NETWORK_SAVE = false;
    public static final boolean DEFAULT_FILE_SAVE = true;
    public static final long DEFAULT_SAMPLING_PERIOD = SensorManager.SENSOR_DELAY_NORMAL;

    public static final short TYPE_START = -1;
    public static final short TYPE_END = -2;
    public static final short TYPE_DEVICE = -3;
    public static final short TYPE_BATTERY_VOLTAGE = -4;
    public static final short TYPE_GPS = -5;
    public static final short TYPE_GPS_NMEA = -6;

    private static final String TAG = "SensRec";

    protected Context context;
    protected Handler uiHandler;
    protected SensorManager sensorManager;
    protected LocationManager locationManager;
    protected SharedPreferences prefs;
    protected PhysicalRecorderComparator physicalComparator;

    protected List<OnRecordingListener> onRecordingListeners = new ArrayList<>();
    protected List<Recorder> recorders;

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
        initialize();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    public void initialize() {
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
            notifyOutput();
        } else if (isRecording() && key != null && key.startsWith(PREF_SENSOR_PREFIX)) {
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

    protected SensorManager getSensorManager() {
        return sensorManager;
    }

    public LocationManager getLocationManager() {
        return locationManager;
    }

    protected Handler getUiHandler() {
        return uiHandler;
    }

    protected Context getContext() {
        return context;
    }

    public List<Recorder> getAll() {
        return recorders;
    }

    public PhysicalRecorderComparator getPhysicalComparator() {
        return physicalComparator;
    }

    public long getDuration(long millisecond) {
        if (active && !paused) {
            return lastDuration + millisecond - lastTime;
        } else {
            return lastDuration;
        }
    }

    public boolean isSaving() {
        return prefs.getBoolean(PREF_FILE_SAVE, true);
    }

    public boolean isStreaming() {
        return false;
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
            startSensors();
            notifyStarted();
        } else if (paused) {
            lastTime = SystemClock.elapsedRealtime();
            paused = false;
            startSensors();
            notifyStarted();
        }
    }

    public void pause() {
        if (!paused) {
            lastDuration += SystemClock.elapsedRealtime() - lastTime;
            paused = true;
            stopSensors();
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
            notifyStopped();
        }
    }

    protected void startSensors() {
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
    }

    protected boolean isEnabled(Recorder recorder) {
        return prefs.getBoolean(recorder.getPrefKey(),
                physicalComparator.isDefaultEnabled(recorder));
    }

    protected String getBatteryVoltageName() {
        return context.getString(R.string.sensor_battery_voltage);
    }

    protected String getBatteryVoltageDescription() {
        return context.getString(R.string.sensor_battery_voltage_description);
    }

    protected String getGpsName() {
        return context.getString(R.string.sensor_gps);
    }

    protected String getGpsDescription() {
        return context.getString(R.string.sensor_gps_description);
    }

    protected String getGpsNmeaName() {
        return context.getString(R.string.sensor_gps_nmea);
    }

    protected String getGpsNmeaDescription() {
        return context.getString(R.string.sensor_gps_nmea_description);
    }

    protected String getShortName(Sensor sensor, Integer number) {
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

    protected String getDescription(Sensor sensor, boolean sensorDefault) {
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

    public interface OnRecordingListener {
        void onStarted();
        void onStopped();
        void onPaused();
        void onOutput(boolean saving, boolean streaming);
    }

}
