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

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import pl.mrwojtek.sensrec.SensorsRecorder;

/**
 * Main activity for this application.
 */
public class SensorsRecordActivity extends AppCompatActivity {

    private static final String TAG = "SensRec";

    protected FloatingActionButton stopFloat;
    protected FloatingActionButton recordFloat;
    protected ColorStateList recordFloatColor;
    protected ColorStateList recordFloatBackgroundColor;
    protected SensorsRecorder recorder;

    public SensorsRecorder getRecorder() {
        return recorder;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sensors_record_activity);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    //.add(R.id.container, new RecordingFragment()).commit();
                    .add(new Records(), Records.FRAGMENT_TAG)
                    .add(R.id.container, new RecordsFragment(), RecordsFragment.FRAGMENT_TAG)
                    .commit();
        }

        recorder = RecordingService.getRecorder(this);

        stopFloat = (FloatingActionButton) findViewById(R.id.stop_float);
        stopFloat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });

        recordFloatColor = ContextCompat.getColorStateList(this, R.color.record_float);
        recordFloatBackgroundColor =
                ContextCompat.getColorStateList(this, R.color.record_float_background);
        recordFloat = (FloatingActionButton) findViewById(R.id.record_float);
        recordFloat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (recorder.isActive()) {
                    if (recorder.isPaused()) {
                        startRecording();
                    } else {
                        pauseRecording();
                    }
                } else {
                    startRecording();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.sensors_record_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void updateRecordingState() {
        int[] state;

        if (recorder.isActive()) {
            stopFloat.setVisibility(View.VISIBLE);
            if (recorder.isPaused()) {
                state = new int[]{R.attr.recording_paused};
            } else {
                state = new int[]{R.attr.recording_active};
            }
        } else {
            stopFloat.setVisibility(View.GONE);
            state = new int[]{};
        }

        recordFloat.setImageState(state, false);
        recordFloat.setBackgroundTintList(ColorStateList.valueOf(
                recordFloatBackgroundColor.getColorForState(state,
                recordFloatBackgroundColor.getDefaultColor())));
        recordFloat.setColorFilter(recordFloatColor.getColorForState(state,
                recordFloatColor.getDefaultColor()));
    }

    protected void startRecording() {
        recorder.start();
        RecordingService.startService(this, RecordingService.ACTION_START_RECORDING);
    }

    protected void stopRecording() {
        recorder.stop();
        RecordingService.startService(this, RecordingService.ACTION_STOP_RECORDING);
    }

    protected void pauseRecording() {
        recorder.pause();
    }

}