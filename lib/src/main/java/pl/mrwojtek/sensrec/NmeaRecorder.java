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

import android.Manifest;
import android.location.GpsStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Recorder for NMEA GPS data
 */
public class NmeaRecorder implements Recorder, GpsStatus.NmeaListener {

    public static final String PREF_KEY = SensorsRecorder.PREF_SENSOR_ + "nmea";

    protected List<String> requiredPermissions;
    protected FrequencyMeasure measure = new FrequencyMeasure(5500, 10500, 100);
    protected SensorsRecorder sensorsRecorder;
    protected boolean started;

    public NmeaRecorder(SensorsRecorder sensorsRecorder) {
        this.sensorsRecorder = sensorsRecorder;
        this.requiredPermissions = new ArrayList<>();
        this.requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @Override
    public short getTypeId() {
        return SensorsRecorder.TYPE_GPS_NMEA;
    }

    @Override
    public short getDeviceId() {
        return 0;
    }

    @Override
    public String getPrefKey() {
        return PREF_KEY;
    }

    @Override
    public String getShortName() {
        return sensorsRecorder.getGpsNmeaName();
    }

    @Override
    public String getDescription() {
        return sensorsRecorder.getGpsNmeaDescription();
    }

    @Override
    public FrequencyMeasure getFrequencyMeasure() {
        return measure;
    }

    @Override
    public Collection<String> getRequiredPermissions() {
        return requiredPermissions;
    }

    @Override
    public void start() {
        if (!started) {
            try {
                sensorsRecorder.getLocationManager().addNmeaListener(this);
                started = true;
                measure.onStarted();
            } catch (SecurityException ex) {
                measure.onPermissionDenied();
            }
        }
    }

    @Override
    public void stop() {
        if (started) {
            sensorsRecorder.getLocationManager().removeNmeaListener(this);
            started = false;
        }
        measure.onStopped();
    }

    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        long millisecond = measure.onNewSample();

        int count = nmea.length();
        if (nmea.endsWith("\r\n")) {
            count -= 2;
        } else if (nmea.endsWith("\n")) {
            count -= 1;
        }

        sensorsRecorder.getOutput()
                .start(SensorsRecorder.TYPE_GPS_NMEA, getDeviceId())
                .write(millisecond)
                .write(timestamp)
                .write(nmea, 0, count)
                .save();
    }
}
