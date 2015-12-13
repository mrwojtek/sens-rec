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

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Recorder for various phone sensors.
 */
public class SensorRecorder implements Recorder, SensorEventListener {

    private static final String PREF_KEY = SensorsRecorder.PREF_SENSOR_PREFIX + "%d_%d";

    protected FrequencyMeasure measure = new FrequencyMeasure();
    protected SensorsRecorder sensorsRecorder;
    protected Sensor sensor;

    protected boolean sensorDefault;
    protected String shortName;
    protected String prefKey;
    protected short typeId;
    protected short accuracyId;
    protected short deviceId;

    protected boolean started;

    /*protected Output.Record record;
    protected Output.Record accuracyRecord;*/

    public SensorRecorder(SensorsRecorder sensorsRecorder, Sensor sensor, int number,
                          String shortName, boolean sensorDefault) {
        this.sensorsRecorder = sensorsRecorder;
        this.sensor = sensor;
        this.sensorDefault = sensorDefault;
        this.shortName = shortName;
        this.typeId = SensorsRecorder.getSensorTypeId(sensor.getType());
        this.accuracyId = SensorsRecorder.getSensorAccuracyId(sensor.getType());
        this.deviceId = (short) number;
        this.prefKey = String.format(PREF_KEY, sensor.getType(), number);
    }

    @Override
    public short getTypeId() {
        return typeId;
    }

    @Override
    public short getDeviceId() {
        return deviceId;
    }

    @Override
    public String getPrefKey() {
        return prefKey;
    }

    @Override
    public String getShortName() {
        return shortName;
    }

    @Override
    public String getDescription() {
        return sensorsRecorder.getDescription(sensor, sensorDefault);
    }

    @Override
    public FrequencyMeasure getFrequencyMeasure() {
        return measure;
    }

    @Override
    public void start() {
        if (!started) {
            sensorsRecorder.getSensorManager()
                    .registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
            started = true;
        }
    }

    @Override
    public void stop() {
        if (started) {
            sensorsRecorder.getSensorManager().unregisterListener(this, sensor);
            started = false;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        measure.onNewSample();

        /*record.start();
        try {
            record.write(System.currentTimeMillis());
            record.write(event.timestamp);
            record.write(event.values.length);
            for (float value : event.values) {
                record.write(value);
            }
        } finally {
            record.over();
        }*/
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        /*accuracyRecord.start();
        try {
            record.write(System.currentTimeMillis());
            record.write(accuracy);
        } finally {
            accuracyRecord.over();
        }*/
    }

}
