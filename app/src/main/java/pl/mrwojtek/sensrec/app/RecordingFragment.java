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
import android.support.v7.widget.GridLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import pl.mrwojtek.sensrec.FileOutput;
import pl.mrwojtek.sensrec.FrequencyMeasure;
import pl.mrwojtek.sensrec.Recorder;
import pl.mrwojtek.sensrec.SensorsRecorder;
import pl.mrwojtek.sensrec.SocketOutput;
import pl.mrwojtek.sensrec.app.util.MaterialUtils;
import pl.mrwojtek.sensrec.app.util.TintableImageView;

/**
 * Preview of the currently running recording.
 */
public class RecordingFragment extends Fragment implements SensorsRecorder.OnRecordingListener {

    private static final String TAG = "SensRec";

    public static final String FRAGMENT_TAG = "RecordingFragment";

    private static final String ARG_FREEZE_ON_STOP = "freezeOnStop";

    private static final int DOT_TICK = 500;
    private static final int MINIMUM_DELAY = 300;

    protected SensorsRecordActivity activity;
    protected Handler uiHandler;
    protected Runnable recordingRunnable;

    protected boolean dual;
    protected boolean freezeOnStop;

    protected ViewGroup recordProgressLayout;
    protected ViewGroup fragmentLayout;

    protected TintableImageView recordingClockImage;
    protected TextView recordingClockText;
    protected boolean recordingClockRed;

    protected TextView fileCaption;
    protected TextView fileText;
    protected TextView fileStatusText;

    protected TextView networkCaption;
    protected TextView networkText;
    protected TextView networkStatusText;

    protected List<RecordingView> recordings = new ArrayList<>();
    protected FileOutputListener onFileListener = new FileOutputListener();
    protected SocketOutputListener onSocketListener = new SocketOutputListener();

    public static RecordingFragment newInstance(boolean freezeOnStop) {
        Bundle args = new Bundle();
        args.putBoolean(ARG_FREEZE_ON_STOP, freezeOnStop);
        RecordingFragment fragment = new RecordingFragment();
        fragment.setArguments(args);
        return fragment;
    }

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

        uiHandler = new Handler(activity.getMainLooper());

        dual = getResources().getBoolean(R.bool.recording_dual_fragments);
        freezeOnStop = getArguments().getBoolean(ARG_FREEZE_ON_STOP);

        recordProgressLayout = (ViewGroup) view.findViewById(R.id.record_progress_layout);
        fragmentLayout = (ViewGroup) view.findViewById(R.id.fragment_layout);

        recordingClockText = (TextView) view.findViewById(R.id.recording_clock_text);
        recordingClockImage = (TintableImageView) view.findViewById(R.id.recording_clock_image);
        recordingClockImage.setTintMode(PorterDuff.Mode.SRC_IN);

        fileCaption = (TextView) view.findViewById(R.id.file_caption);
        fileText = (TextView) view.findViewById(R.id.file_text);
        fileStatusText = (TextView) view.findViewById(R.id.file_status_text);
        fileStatusText.setTypeface(MaterialUtils.getRobotoMedium(activity));

        networkCaption = (TextView) view.findViewById(R.id.network_caption);
        networkText = (TextView) view.findViewById(R.id.network_text);
        networkStatusText = (TextView) view.findViewById(R.id.network_status_text);
        networkStatusText.setTypeface(MaterialUtils.getRobotoMedium(activity));

        GridLayout sensorsLayout = (GridLayout) view.findViewById(R.id.recordings_layout);
        int cw = getResources().getDimensionPixelSize(R.dimen.recording_column_width);
        int sw = getResources().getDisplayMetrics().widthPixels;
        sensorsLayout.setColumnCount(Math.max(1, (dual ? sw / (2 * cw) : sw / cw)));
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
                updateFileStatus();
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
        onFileListener.setVisibility(View.GONE);
        onSocketListener.setVisibility(View.GONE);
        activity.getRecorder().addOnRecordingListener(this, true);
        activity.getRecorder().getOutput().getFileOutput().setOnFileListener(onFileListener);
        activity.getRecorder().getOutput().getSocketOutput().setOnSocketListener(onSocketListener);
    }

    @Override
    public void onStop() {
        activity.getRecorder().getOutput().getSocketOutput().setOnSocketListener(null);
        activity.getRecorder().getOutput().getFileOutput().setOnFileListener(null);
        activity.getRecorder().removeOnRecordingListener(this);
        uiHandler.removeCallbacks(recordingRunnable);
        super.onStop();
    }

    @Override
    public void onStarted() {
        if (dual) {
            recordProgressLayout.setVisibility(View.VISIBLE);
        }

        recordingClockRed = true;
        uiHandler.removeCallbacks(recordingRunnable);
        recordingRunnable.run();
    }

    @Override
    public void onStopped() {
        if (dual) {
            recordProgressLayout.setVisibility(View.GONE);
        }

        updateRecordingClock();
    }

    @Override
    public void onPaused() {
        if (dual) {
            recordProgressLayout.setVisibility(View.VISIBLE);
        }

        updateRecordingClock();
    }

    @Override
    public void onOutput(boolean saving, boolean streaming) {
        // Ignore
    }

    protected void updateRecordingClock() {
        updateRecordingClock(activity.getRecorder().getDuration(SystemClock.elapsedRealtime()));
    }

    protected boolean updateRecordingClock(long duration) {
        Log.d(TAG, "fragment update " + duration);
        recordingClockText.setText(
                RecordingService.getTimeText(getContext(), R.string.record_clock, duration));

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

    protected void updateFileStatus() {
        FileOutput fileOutput = activity.getRecorder().getOutput().getFileOutput();
        if (fileOutput.isStarted()) {
            fileStatusText.setText(MaterialUtils.formatBytesWritten(fileOutput.getBytesWritten()));
            fileStatusText.setVisibility(View.VISIBLE);
        } else {
            fileStatusText.setVisibility(View.GONE);
        }
    }

    protected class FileOutputListener implements FileOutput.OnFileListener {

        @Override
        public void onError(int error) {
            fileText.setText(getString(R.string.record_file_error, error));
            setVisibility(View.VISIBLE);
            updateFileStatus();
        }

        @Override
        public void onStart(String fileName) {
            fileText.setText(fileName);
            setVisibility(View.VISIBLE);
            updateFileStatus();
        }

        @Override
        public void onStop() {
            if (!freezeOnStop || activity.getRecorder().isActive()) {
                setVisibility(View.GONE);
            }
        }

        public void setVisibility(int visibility) {
            fileCaption.setVisibility(visibility);
            fileText.setVisibility(visibility);
            fileStatusText.setVisibility(visibility);
        }
    }

    protected class SocketOutputListener implements SocketOutput.OnSocketListener {

        private Runnable socketRunnable;

        private final Runnable uiRunnable = new Runnable() {
            @Override
            public void run() {
                Runnable runnable;
                synchronized (uiRunnable) {
                    runnable = socketRunnable;
                    socketRunnable = null;
                }
                runnable.run();
            }
        };

        @Override
        public void onError(final int protocol, final String host, final int port,
                            final int errorId) {
            schedule(new Runnable() {
                @Override
                public void run() {
                    networkStatusText.setText(getString(R.string.record_network_error, errorId));
                    setText(protocol, host, port);
                    setVisibility(View.VISIBLE);
                }
            });
        }

        @Override
        public void onConnecting(final int protocol, final String host, final int port) {
            schedule(new Runnable() {
                @Override
                public void run() {
                    networkStatusText.setText(R.string.record_network_connecting);
                    setText(protocol, host, port);
                    setVisibility(View.VISIBLE);
                }
            });
        }

        @Override
        public void onConnected(final int protocol, final String host, final int port) {
            schedule(new Runnable() {
                @Override
                public void run() {
                    networkStatusText.setText(R.string.record_network_connected);
                    setText(protocol, host, port);
                    setVisibility(View.VISIBLE);
                }
            });
        }

        @Override
        public void onStop() {
            schedule(new Runnable() {
                @Override
                public void run() {
                    if (!freezeOnStop || activity.getRecorder().isActive()) {
                        setVisibility(View.GONE);
                    }
                }
            });
        }

        private void setText(int protocol, String host, int port) {
            String[] protocols = getResources().getStringArray(R.array.network_protocol_values);
            networkText.setText(getString(R.string.record_network_text, protocols[protocol], port,
                    host));
        }

        public void setVisibility(int visibility) {
            networkCaption.setVisibility(visibility);
            networkText.setVisibility(visibility);
            networkStatusText.setVisibility(visibility);
        }

        private void schedule(Runnable runnable) {
            synchronized (uiRunnable) {
                if (socketRunnable != null) {
                    socketRunnable = runnable;
                    return;
                }
            }

            socketRunnable = runnable;
            uiHandler.post(uiRunnable);
        }
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
            } else if (measure.getMeasure() == FrequencyMeasure.MEASURE_DISABLED) {
                valueText.setText(getString(R.string.measure_disabled));
                return false;
            }
            return false;
        }
    }

}
