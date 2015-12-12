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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import pl.mrwojtek.sensrec.SensorsRecorder;

/**
 * Android service to keep the app running.
 */
public class RecordingService extends Service implements SensorsRecorder.OnRecordingListener {

    private static final String ACTION_PAUSE_RECORDING = "pl.mrwojtek.sensrec.PauseRecording";
    private static final String ACTION_START_RECORDING = "pl.mrwojtek.sensrec.StartRecording";
    private static final String ACTION_STOP_RECORDING = "pl.mrwojtek.sensrec.StopRecording";

    private static final String TAG = "SensRec";
    private static final int NOTIFICATION_ID = 1;
    private static final int MINIMUM_DELAY = 300;
    private static final int SECOND = 1000;
    private static final int MINUTE = 60;
    private static final int HOUR = 60;

    protected static SensorsRecorder recorder;

    protected PendingIntent contentIntent;
    protected PendingIntent startIntent;
    protected PendingIntent stopIntent;
    protected PendingIntent pauseIntent;
    protected NotificationCompat.Builder notificationBuilder;

    protected Handler uiHandler;
    protected Runnable recordingRunnable;

    protected PowerManager.WakeLock wakeLock;
    protected int lastStartId;

    public static SensorsRecorder getRecorder(Context context) {
        if (recorder == null) {
            recorder = new SensorsRecorder(context);
        }
        return recorder;
    }

    public static void startService(Context context) {
        Intent intent = new Intent(context, RecordingService.class);
        intent.setAction(ACTION_START_RECORDING);
        context.startService(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lastStartId = startId;
        if (intent != null) {
            if (ACTION_START_RECORDING.equals(intent.getAction())) {
                Log.i(TAG, "onStartCommand " + lastStartId + " START");
                recorder.start();
                onStarted();
                return START_REDELIVER_INTENT;
            } else if (ACTION_STOP_RECORDING.equals(intent.getAction())) {
                Log.i(TAG, "onStopCommand " + lastStartId + " STOP");
                recorder.stop();
                return START_REDELIVER_INTENT;
            } else if (ACTION_PAUSE_RECORDING.equals(intent.getAction())) {
                Log.i(TAG, "onPauseCommand " + lastStartId + " PAUSE");
                recorder.pause();
                return START_REDELIVER_INTENT;
            }
        }
        if (!recorder.isActive()) {
            Log.i(TAG, "onStartCommand " + lastStartId + " OTHER");
            stopSelf(lastStartId);
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        getRecorder(this).addOnRecordingListener(this, false);
    }

    @Override
    public void onDestroy() {
        recorder.removeOnRecordingListener(this);
        super.onDestroy();
    }

    @Override
    public void onStarted() {
        Log.i(TAG, "onStarted " + lastStartId);
        takeWakeLock();
        notificationBuilder = null;
        startForeground(NOTIFICATION_ID, getNotification());
        startNotificationClock();
    }

    @Override
    public void onStopped() {
        Log.i(TAG, "onStopped " + lastStartId);
        stopNotificationClock();
        stopForeground(true);
        notificationBuilder = null;
        stopSelf(lastStartId);
        releaseWakeLock();
        updateNotification();
    }

    @Override
    public void onPaused() {
        Log.i(TAG, "onPaused " + lastStartId);
        stopNotificationClock();
        stopForeground(false);
        notificationBuilder = null;
        stopSelf(lastStartId);
        releaseWakeLock();
        updateNotification();
    }

    @Override
    public void onOutput(boolean saving, boolean streaming) {
        if (notificationBuilder != null) {
            notificationBuilder.setContentText(getNotificationText());
            updateNotification();
        }
    }

    public void takeWakeLock() {
        if (wakeLock == null) {
            Log.i(TAG, "takeWakeLock " + lastStartId);
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            wakeLock.acquire();
        }
    }

    public void releaseWakeLock() {
        if (wakeLock != null) {
            Log.i(TAG, "releaseWakeLock " + lastStartId);
            wakeLock.release();
            wakeLock = null;
        }
    }

    private void startNotificationClock() {
        if (uiHandler == null) {
            recordingRunnable = new Runnable() {
                @Override
                public void run() {
                    long duration = scheduleUpdate();
                    updateNotification(duration);
                }
            };
            uiHandler = new Handler(getMainLooper());
            scheduleUpdate();
        }
    }

    private long scheduleUpdate() {
        long duration = recorder.getDuration(SystemClock.elapsedRealtime());
        long delay = (SECOND - (duration % SECOND)) % SECOND;
        uiHandler.postDelayed(recordingRunnable,
                (delay < MINIMUM_DELAY ? SECOND : 0) + delay + 1);
        return duration;
    }

    private void stopNotificationClock() {
        if (uiHandler != null) {
            uiHandler.removeCallbacks(recordingRunnable);
            uiHandler = null;
        }
    }

    private void updateNotification() {
        updateNotification(recorder.getDuration(SystemClock.elapsedRealtime()));
    }

    private void updateNotification(long duration) {
        Log.i(TAG, "updateNotification " + duration);
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (recorder.isActive()) {
            notificationManager.notify(NOTIFICATION_ID, getNotification(duration));
        } else {
            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private Notification getNotification() {
        return getNotification(recorder.getDuration(SystemClock.elapsedRealtime()));
    }

    private Notification getNotification(long duration) {
        if (notificationBuilder == null) {
            notificationBuilder = new NotificationCompat.Builder(this);
            notificationBuilder.setSmallIcon(R.drawable.ic_stat_sens_rec);
            notificationBuilder.setContentText(getNotificationText());
            notificationBuilder.setContentIntent(getContentIntent());

            // and FLAG_NO_CLEAR
            if (recorder.isActive()) {
                notificationBuilder.addAction(R.drawable.ic_stop_white_24dp,
                        getString(R.string.record_stop), getStopIntent());
            }

            if (recorder.isPaused()) {
                notificationBuilder.addAction(R.drawable.ic_fiber_manual_record_white_24dp,
                        getString(R.string.record_restart), getStartIntent());
            } else if (recorder.isActive()) {
                notificationBuilder.addAction(R.drawable.ic_pause_white_24dp,
                        getString(R.string.record_pause), getPauseIntent());
            }
        }
        notificationBuilder.setContentTitle(getTitleText(duration));
        return notificationBuilder.build();
    }

    private String getTitleText(long duration) {
        duration /= SECOND;
        long seconds = duration % MINUTE;
        duration /= MINUTE;
        long minutes = duration % HOUR;
        long hours = duration / HOUR;
        return getString(R.string.record_notification_clock, hours, minutes, seconds);
    }

    private String getNotificationText() {
        if (recorder.isSaving() && recorder.isStreaming()) {
            return getString(R.string.output_saving_streaming);
        } else if (recorder.isSaving()) {
            return getString(R.string.output_saving);
        } else if (recorder.isStreaming()) {
            return getString(R.string.output_streaming);
        } else {
            return getString(R.string.output_disabled);
        }
    }

    public PendingIntent getContentIntent() {
        if (contentIntent == null) {
            Intent intent = new Intent(this, SensorsRecordActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            contentIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return contentIntent;
    }

    public PendingIntent getStartIntent() {
        if (startIntent == null) {
            Intent intent = new Intent(this, RecordingService.class);
            intent.setAction(ACTION_START_RECORDING);
            startIntent = PendingIntent.getService(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return startIntent;
    }

    public PendingIntent getStopIntent() {
        if (stopIntent == null) {
            Intent intent = new Intent(this, RecordingService.class);
            intent.setAction(ACTION_STOP_RECORDING);
            stopIntent = PendingIntent.getService(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return stopIntent;
    }

    public PendingIntent getPauseIntent() {
        if (pauseIntent == null) {
            Intent intent = new Intent(this, RecordingService.class);
            intent.setAction(ACTION_PAUSE_RECORDING);
            pauseIntent = PendingIntent.getService(
                    this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        return pauseIntent;
    }

}
