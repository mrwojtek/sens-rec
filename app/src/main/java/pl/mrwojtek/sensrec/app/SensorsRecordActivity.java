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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import pl.mrwojtek.sensrec.SensorsRecorder;

/**
 * Main activity for this application.
 */
public class SensorsRecordActivity extends AppCompatActivity implements
        SensorsRecorder.OnRecordingListener {

    private static final String TAG = "SensRec";

    protected TextView stopText;
    protected TextView pauseText;
    protected TextView startText;
    protected TextView restartText;
    protected View stopPauseLayout;

    protected SensorsRecorder recorder;

    protected boolean active;
    protected boolean paused;

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

        stopPauseLayout = findViewById(R.id.stop_pause_layout);

        stopText = (TextView) findViewById(R.id.stop_button);
        stopText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });

        pauseText = (TextView) findViewById(R.id.pause_button);
        pauseText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseRecording();
            }
        });

        restartText = (TextView) findViewById(R.id.restart_button);
        restartText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });

        startText = (TextView) findViewById(R.id.start_button);
        startText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        recorder.addOnRecordingListener(this, false);
        updateRecordingState(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        recorder.removeOnRecordingListener(this);
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

    @Override
    public void onStarted() {
        updateRecordingState(true);
    }

    @Override
    public void onStopped() {
        updateRecordingState(true);
    }

    @Override
    public void onPaused() {
        updateRecordingState(true);
    }

    @Override
    public void onOutput(boolean saving, boolean streaming) {
        // Ignore
    }

    protected void updateRecordingState(boolean animate) {
        if (recorder.isActive() && active) {
            updatePausedState(animate);
        } else if (recorder.isActive() && !active) {
            active = true;
            animateShow(stopPauseLayout, animate);
            animateHide(startText, animate);
            updatePausedState(false);
        } else if (!recorder.isActive() && active) {
            active = false;
            animateHide(stopPauseLayout, animate);
            animateShow(startText, animate);
        }
    }

    protected void updatePausedState(boolean animate) {
        if (recorder.isPaused() && !paused) {
            paused = true;
            animateShow(restartText, animate);
            animateHide(pauseText, animate);
        } else if (!recorder.isPaused() && paused){
            paused = false;
            animateHide(restartText, animate);
            animateShow(pauseText, animate);
        }
    }

    private void animateShow(View view, boolean animate) {
        view.setVisibility(View.VISIBLE);
        view.setAlpha(1.0f);
    }

    private void animateHide(final View view, boolean animate) {
        if (animate) {
            view.bringToFront();
            view.animate().alpha(0.0f).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(View.GONE);
                }
            });
        } else {
            view.setVisibility(View.GONE);
        }
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