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

package pl.mrwojtek.sensrec.app;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.Collections;
import java.util.List;

import pl.mrwojtek.sensrec.PhysicalRecorderComparator;
import pl.mrwojtek.sensrec.Recorder;
import pl.mrwojtek.sensrec.SensorsRecorder;

/**
 * Activity for configuring recording.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String KEY_SENSORS = "pref_sensors";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings);

            PreferenceCategory sensors =
                    (PreferenceCategory) getPreferenceScreen().findPreference(KEY_SENSORS);

            SensorsRecorder recorder = new SensorsRecorder(getActivity());
            List<Recorder> all = recorder.getAll();
            Collections.sort(all, new PhysicalRecorderComparator());
            for (Recorder r : all) {
                Preference pref = new SwitchPreference(getActivity());
                pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        Log.i("SensRec", "Clicked");
                        return false;
                    }
                });
                pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        Log.i("SensRec", "Changed");
                        return true;
                    }
                });
                pref.setTitle(r.getShortName());
                pref.setSummary(r.getDescription());
                sensors.addPreference(pref);
            }
        }

    }

}
