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

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import pl.mrwojtek.sensrec.SensorsRecorder;

/**
 * Main activity for this application.
 */
public class RecordsActivity extends AppCompatActivity implements
        SensorsRecorder.OnRecordingListener {

    private static final String TAG = "SensRec";

    private static final int PERMISSIONS_REQUEST = 1;

    protected Toolbar toolbar;
    protected TextView stopText;
    protected TextView pauseText;
    protected TextView startText;
    protected TextView restartText;
    protected View stopPauseLayout;

    protected SensorsRecorder recorder;

    protected boolean dual;
    protected boolean active;
    protected boolean paused;

    protected int actionActiveColor;
    protected int statusInactiveColor;
    protected ValueAnimator actionColorAnimation;

    protected int actionInactiveColor;
    protected int statusActiveColor;
    protected ValueAnimator statusColorAnimation;

    public SensorsRecorder getRecorder() {
        return recorder;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sensors_record_activity);

        recorder = RecordingService.getRecorder(this);

        dual = getResources().getBoolean(R.bool.recording_dual_fragments);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setupBarsColors();
        setupControlButtons();

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            FragmentTransaction ft = fm.beginTransaction();
            ft.add(new Records(), Records.FRAGMENT_TAG);
            ft.add(R.id.recording_container, RecordingFragment.newInstance(true),
                    RecordingFragment.FRAGMENT_TAG);
            ft.add(R.id.records_container, new RecordsFragment(), RecordsFragment.FRAGMENT_TAG);
            ft.commit();
            fm.executePendingTransactions();
        }

        updateRecordingState(false);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            recorder.onPermissionsGranted(permissions, grantResults);
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    protected void checkPermissions(Collection<String> requiredPermissions) {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[missingPermissions.size()]),
                    PERMISSIONS_REQUEST);
        }
    }

    protected void updateRecordingState(boolean animate) {
        boolean recordingActive = recorder.isActive();
        boolean recordingPaused = recorder.isPaused();

        if ((active && !paused) != (recordingActive && !recordingPaused)) {
            animateBars(recordingActive && !recordingPaused, animate);
        }

        if (recordingActive && active) {
            updatePausedState(animate);
        } else {
            if (recordingActive && !active) {
                active = true;
                animateShow(stopPauseLayout, animate);
                animateHide(startText, animate);
                updatePausedState(false);
            } else if (!recordingActive && active) {
                active = false;
                animateHide(stopPauseLayout, animate);
                animateShow(startText, animate);
            }
        }

        if (!dual) {
            FragmentManager fm = getSupportFragmentManager();
            Fragment recordingFragment = fm.findFragmentByTag(RecordingFragment.FRAGMENT_TAG);
            Fragment recordsFragment = fm.findFragmentByTag(RecordsFragment.FRAGMENT_TAG);

            if (recordingActive && (!recordsFragment.isHidden() || recordingFragment.isHidden())) {
                FragmentTransaction ft = fm.beginTransaction();
                if (animate)
                    ft.setCustomAnimations(R.anim.slide_in_bottom, R.anim.slide_out_top);
                ft.show(recordingFragment);
                ft.hide(recordsFragment);
                ft.commit();
            }

            if (!recordingActive && (recordsFragment.isHidden() || !recordingFragment.isHidden())) {
                FragmentTransaction ft = fm.beginTransaction();
                if (animate)
                    ft.setCustomAnimations(R.anim.slide_in_bottom, R.anim.slide_out_top);
                ft.show(recordsFragment);
                ft.hide(recordingFragment);
                ft.commit();
            }
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

    private void animateBars(boolean active, boolean animate) {
        if (!animate) {
            toolbar.setBackgroundColor(active ? actionActiveColor : actionInactiveColor);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                getWindow().setStatusBarColor(active ? statusActiveColor : statusInactiveColor);
            }
        } else {
            if (active) {
                actionColorAnimation.start();
                if (statusColorAnimation != null)
                    statusColorAnimation.start();
            } else {
                actionColorAnimation.reverse();
                if (statusColorAnimation != null)
                    statusColorAnimation.reverse();
            }
        }
    }

    private void setupBarsColors() {
        int animationDuration = getResources().getInteger(R.integer.color_animation_duration);

        actionActiveColor = ContextCompat.getColor(this, R.color.material_red_500);
        actionInactiveColor = ContextCompat.getColor(this, R.color.colorPrimary);
        actionColorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(),
                actionInactiveColor, actionActiveColor);
        actionColorAnimation.setDuration(animationDuration);
        actionColorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                toolbar.setBackgroundColor((Integer) animator.getAnimatedValue());
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            statusActiveColor = ContextCompat.getColor(this, R.color.material_red_700);
            statusInactiveColor = ContextCompat.getColor(this, R.color.colorPrimaryDark);
            statusColorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(),
                    statusInactiveColor, statusActiveColor);
            statusColorAnimation.setDuration(animationDuration);
            statusColorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @SuppressLint("NewApi")
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    getWindow().setStatusBarColor((Integer) animator.getAnimatedValue());
                }
            });
        }
    }

    private void setupControlButtons() {
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

    protected void startRecording() {
        checkPermissions(recorder.collectPermissions());
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