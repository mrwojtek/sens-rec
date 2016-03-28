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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import pl.mrwojtek.sensrec.ble.BleRecorder;

/**
 * Compares recorders according to source of measurements. The recorder is set
 * up in such a way that raw measurements directly from physical sensors have
 * precedence over derived measurements.
 */
public class PhysicalRecorderComparator implements Comparator<Recorder> {

    protected Map<Short, Integer> orders = new HashMap<>();
    protected int defaultDisabledIndex;

    public PhysicalRecorderComparator() {
        int index = 0;
        orders.put(SensorsRecorder.TYPE_BLE, index++);
        orders.put(SensorsRecorder.TYPE_GPS, index++);
        orders.put(SensorsRecorder.TYPE_GPS_NMEA, index++);
        orders.put(SensorsRecorder.TYPE_BATTERY_VOLTAGE, index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_ACCELEROMETER), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_GYROSCOPE), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_GYROSCOPE_UNCALIBRATED), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_MAGNETIC_FIELD), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED),
                index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_PRESSURE), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_RELATIVE_HUMIDITY), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_AMBIENT_TEMPERATURE), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_TEMPERATURE), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_LIGHT), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_HEART_RATE), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_PROXIMITY), index++);

        defaultDisabledIndex = index;
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_GRAVITY), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_LINEAR_ACCELERATION), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_ROTATION_VECTOR), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_GAME_ROTATION_VECTOR), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR),
                index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_STEP_COUNTER), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_STEP_DETECTOR), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_SIGNIFICANT_MOTION), index++);
        orders.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_ORIENTATION), index++);
    }

    public boolean isDefaultEnabled(Recorder recorder) {
        Integer order = orders.get(recorder.getTypeId());
        return order != null && order < defaultDisabledIndex || recorder instanceof BleRecorder;
    }

    @Override
    public int compare(Recorder lhs, Recorder rhs) {
        int c;

        Integer lhsOrder = orders.get(lhs.getTypeId());
        Integer rhsOrder = orders.get(rhs.getTypeId());
        if (lhsOrder != null && rhsOrder != null) {
            c = !lhsOrder.equals(rhsOrder) ? (lhsOrder < rhsOrder ? -1 : 1) : 0;
        } else {
            c = (lhsOrder == null ? 1 : 0) - (rhsOrder == null ? 1 : 0);
            if (c == 0) {
                c = lhs.getTypeId() != rhs.getTypeId() ?
                        (lhs.getTypeId() < rhs.getTypeId() ? -1 : 1) : 0;
            }
        }

        if (c == 0) {
            c = lhs.getDeviceId() != rhs.getDeviceId() ?
                    (lhs.getDeviceId() < rhs.getDeviceId() ? -1 : 1) : 0;
        }

        return c;
    }

}
