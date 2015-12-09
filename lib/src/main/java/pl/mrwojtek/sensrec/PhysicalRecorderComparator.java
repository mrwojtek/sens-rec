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

/**
 * Compares recorders according to source of measurements. The recorder is set
 * up in such a way that raw measurements directly from physical sensors have
 * precedence over derived measurements.
 */
public class PhysicalRecorderComparator implements Comparator<Recorder> {

    protected Map<Short, Integer> order = new HashMap<>();

    public PhysicalRecorderComparator() {
        int index = 0;
        order.put(SensorsRecorder.TYPE_GPS, index++);
        order.put(SensorsRecorder.TYPE_GPS_NMEA, index++);
        order.put(SensorsRecorder.TYPE_BATTERY_VOLTAGE, index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_ACCELEROMETER), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_GYROSCOPE), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_GYROSCOPE_UNCALIBRATED), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_MAGNETIC_FIELD), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED),
                index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_PRESSURE), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_RELATIVE_HUMIDITY), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_AMBIENT_TEMPERATURE), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_TEMPERATURE), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_LIGHT), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_HEART_RATE), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_PROXIMITY), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_GRAVITY), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_LINEAR_ACCELERATION), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_ROTATION_VECTOR), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_GAME_ROTATION_VECTOR), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR),
                index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_STEP_COUNTER), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_STEP_DETECTOR), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_SIGNIFICANT_MOTION), index++);
        order.put(SensorsRecorder.getSensorTypeId(Sensor.TYPE_ORIENTATION), index++);
    }

    @Override
    public int compare(Recorder lhs, Recorder rhs) {
        int c;

        Integer lhsOrder = order.get(lhs.getTypeId());
        Integer rhsOrder = order.get(rhs.getTypeId());
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
