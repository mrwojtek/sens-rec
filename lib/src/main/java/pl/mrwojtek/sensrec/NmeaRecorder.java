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

import android.location.GpsStatus;

/**
 * Recorder for NMEA GPS data
 */
public class NmeaRecorder implements Recorder, GpsStatus.NmeaListener {

    protected FrequencyMeasure measure = new FrequencyMeasure(1000, 5000, 100);
    protected SensorsRecorder sensorsRecorder;

    public NmeaRecorder(SensorsRecorder sensorsRecorder) {
        this.sensorsRecorder = sensorsRecorder;
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
    public void start() {
        sensorsRecorder.getLocationManager().addNmeaListener(this);
    }

    @Override
    public void stop() {
        sensorsRecorder.getLocationManager().removeNmeaListener(this);
    }

    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        measure.onNewSample();
    }
}
