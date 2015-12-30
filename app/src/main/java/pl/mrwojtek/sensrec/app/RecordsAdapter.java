package pl.mrwojtek.sensrec.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pl.mrwojtek.sensrec.app.util.MaterialUtils;

/**
 * Recycler adapter for displaying list of records.
 */
public class RecordsAdapter extends RecyclerView.Adapter<RecordsAdapter.RecordViewHolder> {

    private static final String TAG = "SensRec";

    private static final String PARAM_ACTIVATED = "activated";

    private RecordsFragment fragment;
    private RecordsObserver recordsObserver;
    private File recordsDirectory;
    private Handler uiHandler;
    private int activatedCount;
    private List<RecordEntry> records = new ArrayList<>();
    private Map<String, RecordEntry> recordByName = new HashMap<>();
    private int lastRecordId;

    public RecordsAdapter(RecordsFragment fragment, Handler uiHandler) {
        setHasStableIds(true);
        // TODO: Add option to select storage directory
        //File[] directories = ContextCompat.getExternalFilesDirs(context, null);
        recordsDirectory = fragment.getActivity().getExternalFilesDir(null);

        this.fragment = fragment;
        this.uiHandler = uiHandler;
        this.recordsObserver = new RecordsObserver(recordsDirectory.getPath());
        recordsObserver.startWatching();

        for (File d : recordsDirectory.listFiles()) {
            RecordEntry recordEntry = new RecordEntry(d);
            records.add(recordEntry);
            recordByName.put(recordEntry.getName(), recordEntry);
        }

        updateRecordsOrder(records);
    }

    private void updateRecordsOrder(List<RecordEntry> newRecords) {
        Collections.sort(newRecords);

        int index = 0;
        for (RecordEntry record : newRecords) {
            record.setPosition(index++);
        }

        records = newRecords;
    }

    public void onDestroy() {
        recordsObserver.stopWatching();
        uiHandler.removeCallbacks(recordsObserver);
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }

        Set<String> activated =
                new HashSet<>(savedInstanceState.getStringArrayList(PARAM_ACTIVATED));
        for (RecordEntry recordEntry : records) {
            if (activated.contains(recordEntry.getFile().getPath())) {
                recordEntry.toggleActivated();
            }
        }

        fragment.resolveActionMode();
    }

    public void onSaveInstanceState(Bundle outState) {
        ArrayList<String> activated = new ArrayList<>();
        for (RecordEntry recordEntry : records) {
            if (recordEntry.isActivated()) {
                activated.add(recordEntry.getFile().getPath());
            }
        }
        outState.putStringArrayList(PARAM_ACTIVATED, activated);
    }

    @Override
    public RecordViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return RecordViewHolder.newHolder(parent);
    }

    @Override
    public void onBindViewHolder(RecordViewHolder holder, int position) {
        holder.bind(records.get(position));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    @Override
    public long getItemId(int position) {
        return records.get(position).getId();
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
            fragment.resolveActionMode();
            notifyDataSetChanged();
        }
    }

    public void shareActivated() {
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

        if (intent.resolveActivity(fragment.getActivity().getPackageManager()) != null) {
            fragment.getActivity().startActivity(intent);
        } else {
            Snackbar.make(fragment.getRecycler(),
                    R.string.records_share_failed,
                    Snackbar.LENGTH_LONG).show();
        }
    }

    public void deleteActivated() {
        boolean successful = true;
        for (RecordEntry recordEntry : records) {
            if (recordEntry.isActivated()) {
                successful &= recordEntry.getFile().delete();
            }
        }

        if (!successful) {
            Snackbar.make(fragment.getRecycler(),
                    R.string.records_delete_failed,
                    Snackbar.LENGTH_LONG).show();
        }
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
                fragment.resolveVisibility();
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
            fragment.resolveActionMode();
            notifyDataSetChanged();
        }
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

    private class RecordEntry implements Comparable<RecordEntry> {

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

        public RecordsFragment getFragment() {
            return fragment;
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

    public static class RecordViewHolder extends RecyclerView.ViewHolder implements
            View.OnClickListener {

        private TextView nameText;
        private TextView dateText;
        private TextView timeText;
        private TextView detailsText;

        private RecordEntry record;

        public static RecordViewHolder newHolder(ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            return new RecordViewHolder(inflater.inflate(R.layout.records_record, parent, false));
        }

        public RecordViewHolder(View itemView) {
            super(itemView);
            nameText = (TextView) itemView.findViewById(R.id.name_text);
            dateText = (TextView) itemView.findViewById(R.id.date_text);
            timeText = (TextView) itemView.findViewById(R.id.time_text);
            detailsText = (TextView) itemView.findViewById(R.id.details_text);
            itemView.setOnClickListener(this);
        }

        public void bind(RecordEntry record) {
            this.record = record;

            nameText.setText(record.getName());

            Date date = record.getDate();
            dateText.setText(DateFormat.getDateInstance().format(date));
            timeText.setText(DateFormat.getTimeInstance().format(date));

            String written = MaterialUtils.formatBytesWritten(record.getSize());
            detailsText.setText(detailsText.getContext().getString(R.string.records_size, written));

            itemView.setActivated(record.isActivated());
        }

        @Override
        public void onClick(View v) {
            itemView.setActivated(record.toggleActivated());
            record.getFragment().resolveActionMode();
        }
    }
}
