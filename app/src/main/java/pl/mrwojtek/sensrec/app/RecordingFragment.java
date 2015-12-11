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

import android.content.Context;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import pl.mrwojtek.sensrec.FrequencyMeasure;
import pl.mrwojtek.sensrec.Recorder;
import pl.mrwojtek.sensrec.SensorsRecorder;
import pl.mrwojtek.sensrec.app.util.TintableImageView;

/**
 * Preview of the currently running recording.
 */
public class RecordingFragment extends Fragment implements SensorsRecorder.OnRecordingListener {

    private static final String TAG = "SensRec";

    private static final int DOT_TICK = 500;
    private static final int MINIMUM_DELAY = 300;
    private static final int SECOND = 1000;
    private static final int MINUTE = 60;
    private static final int HOUR = 60;

    protected SensorsRecordActivity activity;
    protected Handler uiHandler;
    protected Runnable recordingRunnable;

    protected TintableImageView recordingClockImage;
    protected TextView recordingClockText;
    protected boolean recordingClockRed;
    protected List<RecordingView> recordings = new ArrayList<>();

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = (SensorsRecordActivity) context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.record_fragment, container, false);
        ViewGroup sensorsLayout = (ViewGroup) view.findViewById(R.id.recordings_layout);

        uiHandler = new Handler(activity.getMainLooper());

        recordingClockText = (TextView) view.findViewById(R.id.recording_clock_text);
        recordingClockImage = (TintableImageView) view.findViewById(R.id.recording_clock_image);
        recordingClockImage.setTintMode(PorterDuff.Mode.SRC_IN);

        for (Recorder recorder : activity.getRecorder().getAll()) {
            RecordingView recordingView = new RecordingView();
            sensorsLayout.addView(recordingView.bind(recorder, inflater, sensorsLayout));
            recordings.add(recordingView);
        }

        recordingRunnable = new Runnable() {
            @Override
            public void run() {
                long duration = activity.getRecorder().getDuration(SystemClock.elapsedRealtime());
                long delay = (DOT_TICK - (duration % DOT_TICK)) % DOT_TICK;
                uiHandler.postDelayed(recordingRunnable,
                        (delay < MINIMUM_DELAY ? DOT_TICK : 0) + delay + 1);
                boolean active = updateRecordingClock(duration);
                for (RecordingView recordingView : recordings) {
                    active = recordingView.updateValueText() || active;
                }
                if (!active) {
                    uiHandler.removeCallbacks(recordingRunnable);
                }
            }
        };

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        activity.getRecorder().addOnRecordingListener(this, true);
    }

    @Override
    public void onStop() {
        activity.getRecorder().removeOnRecordingListener(this);
        uiHandler.removeCallbacks(recordingRunnable);
        super.onStop();
    }

    @Override
    public void onStarted() {
        recordingClockRed = true;
        activity.updateRecordingState();
        uiHandler.removeCallbacks(recordingRunnable);
        recordingRunnable.run();
    }

    @Override
    public void onStopped() {
        activity.updateRecordingState();
        updateRecordingClock();
    }

    @Override
    public void onPaused() {
        activity.updateRecordingState();
        updateRecordingClock();
    }

    protected void updateRecordingClock() {
        updateRecordingClock(activity.getRecorder().getDuration(SystemClock.elapsedRealtime()));
    }

    protected boolean updateRecordingClock(long duration) {
        Log.i(TAG, "update " + duration);
        duration /= SECOND;
        long seconds = duration % MINUTE;
        duration /= MINUTE;
        long minutes = duration % HOUR;
        long hours = duration / HOUR;
        recordingClockText.setText(getString(R.string.record_clock, hours, minutes, seconds));

        int[] state = null;
        if (activity.getRecorder().isActive()) {
            if (activity.getRecorder().isPaused()) {
                state = new int[]{R.attr.recording_paused};
            } else {
                if (recordingClockRed) {
                    state = new int[]{R.attr.recording_active};
                }
                recordingClockRed = !recordingClockRed;
            }

        }
        if (state == null) {
            state = new int[]{};
        }

        recordingClockImage.setImageState(state, false);

        return activity.getRecorder().isActive() && !activity.getRecorder().isPaused();
    }

    protected class RecordingView {

        protected Recorder recorder;
        protected TextView valueText;

        protected View bind(Recorder recorder, LayoutInflater inflater, ViewGroup root) {
            this.recorder = recorder;

            View view = inflater.inflate(R.layout.record_recording, root, false);
            TextView nameText = (TextView) view.findViewById(R.id.name_text);
            nameText.setText(recorder.getShortName());

            valueText = (TextView) view.findViewById(R.id.value_text);
            updateValueText();

            return view;
        }

        protected boolean updateValueText() {
            FrequencyMeasure measure = recorder.getFrequencyMeasure();
            measure.resolveNow();
            if (measure.getMeasure() == FrequencyMeasure.MEASURE_VALUE) {
                valueText.setText(getString(R.string.measure_frequency, measure.getValue()));
                return true;
            } else if (measure.getMeasure() == FrequencyMeasure.MEASURE_QUIET) {
                valueText.setText(getString(R.string.measure_quiet));
                return false;
            } else if (measure.getMeasure() == FrequencyMeasure.MEASURE_AMBIGUOUS) {
                valueText.setText(getString(R.string.measure_ambiguous));
                return false;
            } else if (measure.getMeasure() == FrequencyMeasure.MEASURE_MISSING) {
                valueText.setText(getString(R.string.measure_missing));
                return false;
            }
            return false;
        }
    }

}
