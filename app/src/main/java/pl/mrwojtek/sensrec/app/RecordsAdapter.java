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

import android.content.ContextWrapper;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

import pl.mrwojtek.sensrec.app.util.MaterialUtils;

/**
 * Recycler adapter for displaying list of records.
 */
public class RecordsAdapter extends RecyclerView.Adapter<RecordsAdapter.RecordViewHolder> {

    private static final String TAG = "SensRec";

    private Records records;
    private RecordsFragment recordsFragment;

    public RecordsAdapter(RecordsFragment recordsFragment, Records records) {
        setHasStableIds(true);
        this.recordsFragment = recordsFragment;
        this.records = records;
    }

    @Override
    public RecordViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return RecordViewHolder.newHolder(recordsFragment, parent);
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

    public static class RecordViewHolder extends RecyclerView.ViewHolder implements
            View.OnClickListener {

        private TextView nameText;
        private TextView dateText;
        private TextView timeText;
        private TextView detailsText;

        private RecordsFragment recordsFragment;
        private Records.RecordEntry record;

        private int textColorPrimary;
        private int textColorSecondary;
        private int textColorTertiary;

        public static RecordViewHolder newHolder(RecordsFragment recordsFragment,
                                                 ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            return new RecordViewHolder(recordsFragment,
                    inflater.inflate(R.layout.records_record, parent, false));
        }

        public RecordViewHolder(RecordsFragment recordsFragment, View itemView) {
            super(itemView);
            this.recordsFragment = recordsFragment;
            nameText = (TextView) itemView.findViewById(R.id.name_text);
            dateText = (TextView) itemView.findViewById(R.id.date_text);
            timeText = (TextView) itemView.findViewById(R.id.time_text);
            detailsText = (TextView) itemView.findViewById(R.id.details_text);
            itemView.setOnClickListener(this);

            textColorPrimary = ContextCompat.getColor(recordsFragment.getContext(),
                    R.color.colorTextPrimary);
            textColorSecondary = ContextCompat.getColor(recordsFragment.getContext(),
                    R.color.colorTextSecondary);
            textColorTertiary = ContextCompat.getColor(recordsFragment.getContext(),
                    R.color.colorTextHint);
        }

        public void bind(Records.RecordEntry record) {
            this.record = record;

            nameText.setText(record.getName());

            Date startDate = record.getDate();
            Date endDate = record.getEndDate();

            String startDateString = DateFormat.getDateInstance().format(startDate);
            dateText.setText(startDateString);

            if (recordsFragment.isRecordsAdapterWide() && endDate != null) {
                timeText.setText(timeText.getContext().getString(R.string.records_dates_wide,
                        DateFormat.getTimeInstance().format(startDate),
                        DateFormat.getTimeInstance().format(endDate)));
            } else {
                timeText.setText(DateFormat.getTimeInstance().format(startDate));
            }

            if (record.isDateFallback()) {
                dateText.setTextColor(textColorTertiary);
                timeText.setTextColor(textColorTertiary);
            } else {
                dateText.setTextColor(textColorSecondary);
                timeText.setTextColor(textColorSecondary);
            }

            if (!record.isTabu()) {
                String written = MaterialUtils.formatBytesWritten(record.getSize());
                if (record.getDuration() != null) {
                    String timeText = RecordingService.getTimeText(
                            detailsText.getContext(), R.string.record_clock, record.getDuration());
                    detailsText.setText(detailsText.getContext()
                            .getString(R.string.records_size_time, written, timeText));
                } else {
                    detailsText.setText(detailsText.getContext()
                            .getString(R.string.records_size, written));
                }
                detailsText.setTextColor(ContextCompat.getColor(detailsText.getContext(),
                        R.color.colorTextSecondary));
            } else {
                detailsText.setText(R.string.records_recording);
                detailsText.setTextColor(textColorPrimary);
            }

            itemView.setActivated(record.isActivated());
        }

        @Override
        public void onClick(View v) {
            itemView.setActivated(record.toggleActivated());
            recordsFragment.resolveActionMode();
        }
    }
}
