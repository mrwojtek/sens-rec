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

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/**
 * Recorder for phone battery voltage.
 */
public class BatteryRecorder implements Recorder, Runnable {

    public static final String PREF_KEY = SensorsRecorder.PREF_SENSOR_PREFIX + "battery";

    protected static final int BATTERY_INTERVAL = 5000;

    protected FrequencyMeasure measure = new FrequencyMeasure(10000, 20000, 3);
    protected SensorsRecorder sensorsRecorder;
    protected boolean started;

    public BatteryRecorder(SensorsRecorder sensorsRecorder) {
        this.sensorsRecorder = sensorsRecorder;
    }

    @Override
    public short getTypeId() {
        return SensorsRecorder.TYPE_BATTERY_VOLTAGE;
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
        return sensorsRecorder.getBatteryVoltageName();
    }

    @Override
    public String getDescription() {
        return sensorsRecorder.getBatteryVoltageDescription();
    }

    @Override
    public FrequencyMeasure getFrequencyMeasure() {
        return measure;
    }

    @Override
    public void start() {
        if (!started) {
            sensorsRecorder.getUiHandler().post(this);
            started = true;
        }
    }

    @Override
    public void stop() {
        if (started) {
            sensorsRecorder.getUiHandler().removeCallbacks(this);
            started = false;
        }
    }

    @Override
    public void run() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = sensorsRecorder.getContext().registerReceiver(null, filter);

        long millisecond = measure.onNewSample();
        int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level / (float)scale;

        sensorsRecorder.getOutput()
                .start(SensorsRecorder.TYPE_BATTERY_VOLTAGE, getDeviceId())
                .write(millisecond)
                .write(batteryPct)
                .write(voltage)
                .write(temperature)
                .save();

        sensorsRecorder.getUiHandler().postDelayed(this, BATTERY_INTERVAL);
    }
}
