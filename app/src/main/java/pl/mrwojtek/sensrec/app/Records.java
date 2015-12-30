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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private List<RecordEntry> records = new ArrayList<>();
    private Map<String, RecordEntry> recordByName = new HashMap<>();

    private List<OnDataSetChangedListener> onDataSetChangedListeners = new ArrayList<>();
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

        recordsDirectory = getActivity().getExternalFilesDir(null);
        recordsObserver = new RecordsObserver(recordsDirectory.getPath());
        recordsObserver.startWatching();

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

    public void addOnDataSetChangedListener(OnDataSetChangedListener listener) {
        onDataSetChangedListeners.add(listener);
        listener.onCountsChanged();
        listener.onDataSetChanged();
    }

    public void removeOnDataSetChangedListener(OnDataSetChangedListener listener) {
        onDataSetChangedListeners.remove(listener);
    }

    public void setOnItemListener(OnItemListener onItemListener) {
        if (this.onItemListener != null) {
            removeOnDataSetChangedListener(this.onItemListener);
        }

        this.onItemListener = onItemListener;

        if (onItemListener != null) {
            addOnDataSetChangedListener(onItemListener);
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

    void notifyCountsChanged() {
        for (OnDataSetChangedListener listener : onDataSetChangedListeners) {
            listener.onCountsChanged();
        }
    }

    void notifyDataSetChanged() {
        for (OnDataSetChangedListener listener : onDataSetChangedListeners) {
            listener.onDataSetChanged();
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

        public RecordsObserver(String path) {
            super(path, FileObserver.MODIFY | FileObserver.CREATE | FileObserver.MOVED_TO |
                    FileObserver.DELETE | FileObserver.MOVED_FROM);
            lastUpdateTime = SystemClock.elapsedRealtime() - MODIFICATION_INTERVAL;
        }

        @Override
        public synchronized void onEvent(int event, String path) {
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
            synchronized (this) {
                scheduled = false;
                lastUpdateTime = SystemClock.elapsedRealtime();
                added = new HashSet<>(this.added);
                this.added.clear();
                deleted = new HashSet<>(this.deleted);
                this.deleted.clear();
                modified = new HashSet<>(this.modified);
                this.modified.clear();
            }
            updateRecords(added, deleted, modified);
        }
    }

    public class RecordEntry implements Comparable<RecordEntry> {

        private Date date;
        private File file;
        private boolean activated;
        private int id;
        private int position;

        public RecordEntry(File file) {
            this.file = file;
            this.date = new Date(file.lastModified());
            this.id = ++lastRecordId;
        }

        public int getId() {
            return id;
        }

        public Date getDate() {
            return date;
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

        public boolean isActivated() {
            return activated;
        }

        public boolean toggleActivated() {
            activated = !activated;
            activatedCount += activated ? 1 : -1;
            return activated;
        }

        public void onModified() {
            date = new Date(file.lastModified());
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
}
