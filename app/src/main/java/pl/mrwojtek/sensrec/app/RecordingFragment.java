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

    protected TextView fileCaption;
    protected TextView fileText;
    protected TextView fileStatusText;

    protected TextView networkCaption;
    protected TextView networkText;
    protected TextView networkStatusText;

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

        fileCaption = (TextView) view.findViewById(R.id.file_caption);
        fileText = (TextView) view.findViewById(R.id.file_text);
        fileStatusText = (TextView) view.findViewById(R.id.file_status_text);
        fileStatusText.setTypeface(MaterialUtils.getRobotoMedium(activity));

        networkCaption = (TextView) view.findViewById(R.id.network_caption);
        networkText = (TextView) view.findViewById(R.id.network_text);
        networkStatusText = (TextView) view.findViewById(R.id.network_status_text);
        networkStatusText.setTypeface(MaterialUtils.getRobotoMedium(activity));

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

    @Override
    public void onOutput(boolean saving, boolean streaming) {
        // Ignore
    }

    protected void updateRecordingClock() {
        updateRecordingClock(activity.getRecorder().getDuration(SystemClock.elapsedRealtime()));
    }

    protected boolean updateRecordingClock(long duration) {
        Log.d(TAG, "fragment update " + duration);
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

    protected void updateFileStatus() {
        FileOutput fileOutput = activity.getRecorder().getOutput().getFileOutput();
        if (fileOutput.isStarted()) {
            fileStatusText.setText(formatBytesWritten(fileOutput.getBytesWritten()));
            fileStatusText.setVisibility(View.VISIBLE);
        } else {
            fileStatusText.setVisibility(View.GONE);
        }
    }

    protected String formatBytesWritten(int written) {
        final String[] formats = new String[]{"%.0fB", "%.0fkB", "%.1fMB", "%.2fGB", "%.3fTB",
                "%.3fPB", "%.3fEB" };
        int remainder = 0;
        int i = 0;
        for (; i + 1 < formats.length && written > 999; ++i) {
            remainder = written % 1000;
            written /= 1000;
        }
        return String.format(formats[i], written + remainder / 1000.0f);
    }

    protected FileOutput.OnFileListener onFileListener = new FileOutput.OnFileListener() {
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
            setVisibility(View.GONE);
        }

        private void setVisibility(int visibility) {
            fileCaption.setVisibility(visibility);
            fileText.setVisibility(visibility);
            fileStatusText.setVisibility(visibility);
        }

    };

    protected SocketOutput.OnSocketListener onSocketListener = new SocketOutput.OnSocketListener() {

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
                    setVisibility(View.GONE);
                }
            });
        }

        private void setText(int protocol, String host, int port) {
            String[] protocols = getResources().getStringArray(R.array.network_protocol_values);
            networkText.setText(getString(R.string.record_network_text, protocols[protocol], port,
                    host));
        }

        private void setVisibility(int visibility) {
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

    };

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
