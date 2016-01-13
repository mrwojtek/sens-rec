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
import android.net.Uri;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pl.mrwojtek.sensrec.FileOutput;
import pl.mrwojtek.sensrec.RecordReader;
import pl.mrwojtek.sensrec.SensorsRecorder;

/**
 * Maintains a list of records.
 */
public class Records extends Fragment {

    private static final String TAG = "SensRec";

    public static final String FRAGMENT_TAG = "FilesFragment";

    private static final String PARAM_ACTIVATED = "activated";

    private RecordsObserver recordsObserver;
    private File recordsDirectory;
    private Handler uiHandler;
    private int activatedCount;
    private int lastRecordId;
    private SensorsRecorder recorder;
    private FileListener fileListener;

    private List<RecordEntry> records = new ArrayList<>();
    private Map<String, RecordEntry> recordByName = new HashMap<>();

    private OnItemListener onItemListener;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        Set<String> activated = null;
        if (savedInstanceState != null) {
            activated = new HashSet<>(savedInstanceState.getStringArrayList(PARAM_ACTIVATED));
        }

        // TODO: Add option to select storage directory
        //File[] directories = ContextCompat.getExternalFilesDirs(context, null);

        uiHandler = new Handler(getActivity().getMainLooper());

        recorder = RecordingService.getRecorder(getContext());

        recordsDirectory = getActivity().getExternalFilesDir(null);
        recordsObserver = new RecordsObserver(recordsDirectory.getPath());
        recordsObserver.startWatching();

        fileListener = new FileListener();
        ((SensorsRecordActivity) getActivity()).getRecorder().getOutput().getFileOutput()
                .addOnFileListener(fileListener);

        for (File d : recordsDirectory.listFiles()) {
            RecordEntry recordEntry = new RecordEntry(d);
            if (activated != null && activated.contains(recordEntry.getFile().getPath())) {
                recordEntry.toggleActivated();
            }
            records.add(recordEntry);
            recordByName.put(recordEntry.getName(), recordEntry);
        }

        updateRecordsOrder(records);
    }

    @Override
    public void onDestroy() {
        ((SensorsRecordActivity) getActivity()).getRecorder().getOutput().getFileOutput()
                .removeOnFileListener(fileListener);

        recordsObserver.stopWatching();
        uiHandler.removeCallbacks(recordsObserver);
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        ArrayList<String> activated = new ArrayList<>();
        for (RecordEntry recordEntry : records) {
            if (recordEntry.isActivated()) {
                activated.add(recordEntry.getFile().getPath());
            }
        }
        outState.putStringArrayList(PARAM_ACTIVATED, activated);
    }

    public void setOnItemListener(OnItemListener listener) {
        onItemListener = listener;
        if (listener != null) {
            listener.onCountsChanged();
            listener.onDataSetChanged();
        }
    }

    public RecordEntry get(int position) {
        return records.get(position);
    }

    public int size() {
        return records.size();
    }

    public int getActivatedCount() {
        return activatedCount;
    }

    public void activateAll(boolean activate) {
        boolean modified = false;
        for (RecordEntry recordEntry : records) {
            if (recordEntry.isActivated() != activate) {
                recordEntry.toggleActivated();
                modified = true;
            }
        }
        if (modified) {
            notifyDataSetChanged();
        }
    }

    public boolean shareActivated() {
        ArrayList<Uri> files = new ArrayList<>();
        for (RecordEntry recordEntry : records) {
            if (recordEntry.isActivated()) {
                files.add(Uri.fromFile(recordEntry.getFile()));
            }
        }

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Sensors Record recordings.");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);

        if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
            getActivity().startActivity(intent);
            return true;
        } else {
            return false;
        }
    }

    public boolean deleteActivated() {
        boolean successful = true;
        for (RecordEntry recordEntry : records) {
            if (recordEntry.isActivated()) {
                successful &= recordEntry.getFile().delete();
            }
        }

        return successful;
    }

    private void updateRecordsOrder(List<RecordEntry> newRecords) {
        Collections.sort(newRecords);

        int index = 0;
        for (RecordEntry record : newRecords) {
            record.setPosition(index++);
        }

        records = newRecords;
    }

    public void updateRecords(Set<String> added, Set<String> deleted, Set<String> modified) {
        boolean changed = false;
        if (!added.isEmpty() || !deleted.isEmpty()) {
            List<RecordEntry> newRecords;
            if (!deleted.isEmpty()) {
                newRecords = new ArrayList<>();
                for (RecordEntry entry : records) {
                    String name = entry.getName();
                    if (added.contains(name)) {
                        added.remove(name);
                    }
                    if (!deleted.contains(name)) {
                        newRecords.add(entry);
                    } else {
                        if (entry.isActivated()) {
                            --activatedCount;
                        }
                        recordByName.remove(name);
                        changed = true;
                    }
                }
            } else {
                newRecords = records;
            }

            for (String name : added) {
                if (!deleted.contains(name)) {
                    RecordEntry record = new RecordEntry(new File(recordsDirectory, name));
                    newRecords.add(record);
                    recordByName.put(name, record);
                    changed = true;
                }
            }

            if (changed) {
                updateRecordsOrder(newRecords);
                notifyCountsChanged();
            }
        }

        for (String name : modified) {
            RecordEntry record = recordByName.get(name);
            if (record != null) {
                record.onModified();
                if (!changed) {
                    notifyItemChanged(record.getPosition());
                }
            }
        }

        if (changed) {
            notifyDataSetChanged();
        }
    }

    private void updateTabu(String tabuPreviousPath, String tabuPath) {
        RecordEntry record = recordByName.get(tabuPreviousPath);
        if (record != null) {
            record.setTabu(false);
            record.onModified();
            notifyItemChanged(record.getPosition());
        }

        record = recordByName.get(tabuPath);
        if (record != null) {
            record.setTabu(true);
            record.onModified();
            notifyItemChanged(record.getPosition());
        }
    }

    void notifyCountsChanged() {
        if (onItemListener != null) {
            onItemListener.onCountsChanged();
        }
    }

    void notifyDataSetChanged() {
        if (onItemListener != null) {
            onItemListener.onDataSetChanged();
        }
    }

    void notifyItemChanged(int position) {
        if (onItemListener != null) {
            onItemListener.onItemChanged(position);
        }
    }

    public interface OnDataSetChangedListener {
        void onDataSetChanged();
        void onCountsChanged();
    }

    public interface OnItemListener extends OnDataSetChangedListener {
        void onItemChanged(int position);
    }

    private class RecordsObserver extends FileObserver implements Runnable {

        private static final long MODIFICATION_INTERVAL = 500;

        private List<String> added = new ArrayList<>();
        private List<String> deleted = new ArrayList<>();
        private Set<String> modified = new HashSet<>();
        private long lastUpdateTime;
        private long scheduledTime;
        private boolean scheduled;
        private String tabuPath;
        private String tabuPreviousPath;
        private boolean tabuUpdate;

        public RecordsObserver(String path) {
            super(path, FileObserver.MODIFY | FileObserver.CREATE | FileObserver.MOVED_TO |
                    FileObserver.DELETE | FileObserver.MOVED_FROM);
            lastUpdateTime = SystemClock.elapsedRealtime() - MODIFICATION_INTERVAL;
        }

        public synchronized void setTabuPath(String tabuPath) {
            if (!this.tabuUpdate) {
                this.tabuPreviousPath = this.tabuPath;
            }

            this.tabuPath = tabuPath;
            this.tabuUpdate = true;

            schedule(0);
        }

        @Override
        public synchronized void onEvent(int event, String path) {
            if (path.equals(tabuPath)) {
                return;
            }

            long delay = 0;
            switch (event) {
                case FileObserver.MODIFY:
                    delay = Math.max(0,
                            lastUpdateTime + MODIFICATION_INTERVAL - SystemClock.elapsedRealtime());
                    modified.add(path);
                    break;
                case FileObserver.CREATE:
                case FileObserver.MOVED_TO:
                    added.add(path);
                    break;
                case FileObserver.DELETE:
                case FileObserver.MOVED_FROM:
                    deleted.add(path);
                    break;
            }

            schedule(delay);
        }

        private void schedule(long delay) {
            long nextTime = SystemClock.uptimeMillis() + delay;
            if (scheduled && nextTime - scheduledTime < 0) {
                uiHandler.removeCallbacks(this);
                scheduled = false;
            }

            if (!scheduled) {
                uiHandler.postAtTime(this, nextTime);
                scheduledTime = nextTime;
                scheduled = true;
            }
        }

        @Override
        public void run() {
            Set<String> added;
            Set<String> deleted;
            Set<String> modified;
            boolean tabuUpdate;
            String tabuPath;
            String tabuPreviousPath;

            synchronized (this) {
                scheduled = false;
                lastUpdateTime = SystemClock.elapsedRealtime();
                added = new HashSet<>(this.added);
                this.added.clear();
                deleted = new HashSet<>(this.deleted);
                this.deleted.clear();
                modified = new HashSet<>(this.modified);
                this.modified.clear();
                tabuUpdate = this.tabuUpdate;
                this.tabuUpdate = false;
                tabuPath = this.tabuPath;
                tabuPreviousPath = this.tabuPreviousPath;
            }

            updateRecords(added, deleted, modified);

            if (tabuUpdate) {
                updateTabu(tabuPreviousPath, tabuPath);
            }
        }
    }

    public class RecordEntry implements Comparable<RecordEntry> {

        private Date date;
        private Date endDate;
        private Long duration;
        private Double distance;
        private File file;
        private boolean activated;
        private int id;
        private int position;
        private boolean tabu;

        public RecordEntry(File file) {
            this.file = file;
            id = ++lastRecordId;
            onModified();
        }

        public int getId() {
            return id;
        }

        public Date getDate() {
            return date;
        }

        public Date getEndDate() {
            return endDate;
        }

        public Long getDuration() {
            return duration;
        }

        public Double getDistance() {
            return distance;
        }

        public String getName() {
            return file.getName();
        }

        public File getFile() {
            return file;
        }

        public long getSize() {
            return file.length();
        }

        public boolean isTabu() {
            return tabu;
        }

        public void setTabu(boolean tabu) {
            this.tabu = tabu;
        }

        public boolean isActivated() {
            return activated;
        }

        public boolean toggleActivated() {
            activated = !activated;
            activatedCount += activated ? 1 : -1;
            return activated;
        }

        public void onModified() {
            RecordReader reader = new RecordReader(recorder);
            if (reader.readStartEnd(file)) {
                date = reader.getStartDate();
                endDate = reader.getEndDate();
                duration = reader.getDuration();
            } else {
                date = null;
                endDate = null;
                duration = null;
            }

            if (date == null) {
                date = new Date(file.lastModified());
            }
        }

        @Override
        public int compareTo(@NonNull RecordEntry another) {
            return -date.compareTo(another.date);
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public int getPosition() {
            return position;
        }
    }

    private class FileListener implements FileOutput.OnFileListener {

        @Override
        public void onError(int error) {
            recordsObserver.setTabuPath(null);
        }

        @Override
        public void onStart(String fileName) {
            recordsObserver.setTabuPath(fileName);
        }

        @Override
        public void onStop() {
            recordsObserver.setTabuPath(null);
        }
    }
}
