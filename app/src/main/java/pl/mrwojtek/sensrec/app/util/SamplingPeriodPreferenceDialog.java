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

package pl.mrwojtek.sensrec.app.util;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import pl.mrwojtek.sensrec.SensorsRecorder;
import pl.mrwojtek.sensrec.app.R;

/**
 * Allows to configure sampling period for sensors.
 */
public class SamplingPeriodPreferenceDialog extends DialogFragment implements
        DialogInterface.OnClickListener, DialogInterface.OnShowListener {

    public static final String DIALOG_TAG = "SamplingPeriodPreferenceDialog";
    public static final String TAG = "SensRec";

    public static final int POSITION_NORMAL = 0;
    public static final int POSITION_UI = 1;
    public static final int POSITION_GAME = 2;
    public static final int POSITION_FASTEST = 3;
    public static final int POSITION_CUSTOM = 4;

    protected boolean initializing;
    protected Spinner samplingSpinner;
    protected EditText millisecondsEdit;
    protected Button okButton;

    public static int getSamplingPosition(long delay) {
        if (delay == SensorManager.SENSOR_DELAY_NORMAL) {
            return POSITION_NORMAL;
        } else if (delay == SensorManager.SENSOR_DELAY_UI) {
            return POSITION_UI;
        } else if (delay == SensorManager.SENSOR_DELAY_GAME) {
            return POSITION_GAME;
        } else if (delay == SensorManager.SENSOR_DELAY_FASTEST) {
            return POSITION_FASTEST;
        } else {
            return POSITION_CUSTOM;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ContextThemeWrapper context = new ContextThemeWrapper(getActivity(), R.style.DialogTheme);
        LayoutInflater inflater = getActivity().getLayoutInflater().cloneInContext(context);
        View view = inflater.inflate(R.layout.sampling_period_dialog, null);

        initializeSamplingSpinner(view);
        initializeMillisecondsEdit(view);

        if (savedInstanceState == null) {
            setFromPreferences();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DialogTheme);
        builder.setTitle(R.string.sampling_period_title);
        builder.setView(view);
        builder.setNegativeButton(R.string.action_cancel, null);
        builder.setPositiveButton(R.string.action_ok, this);
        return MaterialUtils.fixTitle(context, builder.create(), this);
    }

    @Override
    public void onShow(DialogInterface dialog) {
        okButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
        updateOkButton();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        int position = samplingSpinner.getSelectedItemPosition();
        if (position == POSITION_CUSTOM) {
            try {
                long delay = Long.valueOf(millisecondsEdit.getText().toString());
                prefs.edit().putLong(SensorsRecorder.PREF_SAMPLING_PERIOD, delay).apply();
            } catch (NumberFormatException ex) {
                Log.i(TAG, "Unable to save sampling delay: " + ex.getMessage());
            }
        } else if (position == POSITION_NORMAL) {
            prefs.edit().putLong(SensorsRecorder.PREF_SAMPLING_PERIOD,
                    SensorManager.SENSOR_DELAY_NORMAL).apply();
        } else if (position == POSITION_UI) {
            prefs.edit().putLong(SensorsRecorder.PREF_SAMPLING_PERIOD,
                    SensorManager.SENSOR_DELAY_UI).apply();
        } else if (position == POSITION_GAME) {
            prefs.edit().putLong(SensorsRecorder.PREF_SAMPLING_PERIOD,
                    SensorManager.SENSOR_DELAY_GAME).apply();
        } else if (position == POSITION_FASTEST) {
            prefs.edit().putLong(SensorsRecorder.PREF_SAMPLING_PERIOD,
                    SensorManager.SENSOR_DELAY_FASTEST).apply();
        }
    }

    private void setFromPreferences() {
        // Mark as initializing to disable focus override on start
        initializing = true;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        long delay = prefs.getLong(SensorsRecorder.PREF_SAMPLING_PERIOD,
                SensorsRecorder.DEFAULT_SAMPLING_PERIOD);
        int position = getSamplingPosition(delay);
        samplingSpinner.setSelection(position);

        // If set to custom then set edit text value
        if (position == POSITION_CUSTOM) {
            millisecondsEdit.setText(String.format("%d", delay));
        } else {
            millisecondsEdit.setText("");
        }
    }

    private void updateOkButton() {
        if (okButton != null) {
            okButton.setEnabled(isOkEnabled());
        }
    }

    private boolean isOkEnabled() {
        // Any of the default constants is valid
        if (samplingSpinner.getSelectedItemPosition() != POSITION_CUSTOM) {
            return true;
        }

        // In other case check the milliseconds value formatting
        try {
            long delay = Long.parseLong(millisecondsEdit.getText().toString());
            if (getSamplingPosition(delay) == POSITION_CUSTOM) {
                return true;
            }
        } catch (NumberFormatException ex) {
            // Do nothing
        }

        return false;
    }

    private void initializeSamplingSpinner(View view) {
        TextView samplingCaption = (TextView) view.findViewById(R.id.sampling_caption);
        MaterialUtils.transformForSpinner(samplingCaption);

        samplingSpinner = (Spinner) view.findViewById(R.id.sampling_spinner);
        samplingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                InputMethodManager inputMethodManager = (InputMethodManager) getActivity()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (position != POSITION_CUSTOM) {
                    millisecondsEdit.setText("");
                    if (!initializing) {
                        millisecondsEdit.clearFocus();
                        inputMethodManager
                                .hideSoftInputFromWindow(millisecondsEdit.getWindowToken(), 0);
                    } else {
                        initializing = false;
                    }
                } else {
                    if (!initializing) {
                        millisecondsEdit.requestFocus();
                        inputMethodManager
                                .showSoftInput(millisecondsEdit, InputMethodManager.SHOW_IMPLICIT);
                    } else {
                        initializing = false;
                    }
                }
                updateOkButton();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    private void initializeMillisecondsEdit(View view) {
        millisecondsEdit = (EditText) view.findViewById(R.id.milliseconds_edit);
        millisecondsEdit.setMinimumWidth(MaterialUtils.calculateWidth(millisecondsEdit,
                new String[]{getString(R.string.sampling_period_milliseconds_text)}));
        millisecondsEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Ignore
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Ignore
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() != 0
                        && samplingSpinner.getSelectedItemPosition() != POSITION_CUSTOM) {
                    samplingSpinner.setSelection(POSITION_CUSTOM);
                }
                updateOkButton();
            }
        });
    }

}
