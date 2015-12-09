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

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

/**
 * Recorder for GPS location updates.
 */
public class LocationRecorder implements Recorder, LocationListener {

    protected FrequencyMeasure measure = new FrequencyMeasure();
    protected SensorsRecorder sensorsRecorder;

    public LocationRecorder(SensorsRecorder sensorsRecorder) {
        this.sensorsRecorder = sensorsRecorder;
    }

    @Override
    public short getTypeId() {
        return SensorsRecorder.TYPE_GPS;
    }

    @Override
    public short getDeviceId() {
        return 0;
    }

    @Override
    public String getShortName() {
        return sensorsRecorder.getGpsName();
    }

    @Override
    public String getDescription() {
        return sensorsRecorder.getGpsDescription();
    }

    @Override
    public FrequencyMeasure getFrequencyMeasure() {
        return measure;
    }

    @Override
    public void start() {
        sensorsRecorder.getLocationManager()
                .requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }

    @Override
    public void stop() {
        sensorsRecorder.getLocationManager().removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        measure.onNewSample();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
