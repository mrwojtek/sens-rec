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
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import pl.mrwojtek.sensrec.SensorsRecorder;
import pl.mrwojtek.sensrec.app.R;

/**
 * Dialog for setting network preferences.
 */
public class NetworkPreferenceDialog extends DialogFragment implements
        DialogInterface.OnClickListener {

    public static final String DIALOG_TAG = "NetworkPreferenceDialog";
    public static final String MAXIMUM_PORT = "65535";

    private Switch networkSwitch;
    private EditText hostEdit;
    private Spinner protocolSpinner;
    private EditText portEdit;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ContextThemeWrapper context = new ContextThemeWrapper(getActivity(), R.style.DialogTheme);
        LayoutInflater inflater = getActivity().getLayoutInflater().cloneInContext(context);
        View view = inflater.inflate(R.layout.network_dialog, null);

        initializePortEdit(view);
        networkSwitch = (Switch) view.findViewById(R.id.network_switch);
        protocolSpinner = (Spinner) view.findViewById(R.id.protocol_spinner);
        hostEdit = (EditText) view.findViewById(R.id.host_edit);
        portEdit = (EditText) view.findViewById(R.id.port_edit);

        TextView protocolCaption = (TextView) view.findViewById(R.id.protocol_caption);
        MaterialUtils.transformForSpinner(protocolCaption);

        if (savedInstanceState == null) {
            setFromPreferences();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.DialogTheme);
        builder.setTitle(R.string.network_title);
        builder.setView(view);
        builder.setNegativeButton(R.string.action_cancel, null);
        builder.setPositiveButton(R.string.action_ok, this);
        return MaterialUtils.fixTitle(context, builder.create(), null);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        int port;
        try {
            port = Integer.parseInt(portEdit.getText().toString());
        } catch (NumberFormatException ex) {
            port = 0;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.edit()
                .putString(SensorsRecorder.PREF_NETWORK_HOST, hostEdit.getText().toString())
                .putInt(SensorsRecorder.PREF_NETWORK_PROTOCOL,
                        protocolSpinner.getSelectedItemPosition())
                .putInt(SensorsRecorder.PREF_NETWORK_PORT, port)
                .putBoolean(SensorsRecorder.PREF_NETWORK_SAVE, networkSwitch.isChecked())
                .apply();
    }

    private void setFromPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        networkSwitch.setChecked(prefs.getBoolean(SensorsRecorder.PREF_NETWORK_SAVE,
                SensorsRecorder.DEFAULT_NETWORK_SAVE));
        hostEdit.setText(prefs.getString(SensorsRecorder.PREF_NETWORK_HOST,
                SensorsRecorder.DEFAULT_HOST));
        protocolSpinner.setSelection(prefs.getInt(SensorsRecorder.PREF_NETWORK_PROTOCOL,
                SensorsRecorder.DEFAULT_PROTOCOL));
        portEdit.setText(String.format("%d", prefs.getInt(SensorsRecorder.PREF_NETWORK_PORT,
                SensorsRecorder.DEFAULT_PORT)));
    }

    private void initializePortEdit(View view) {
        portEdit = (EditText) view.findViewById(R.id.port_edit);
        portEdit.setMinimumWidth(MaterialUtils.calculateWidth(portEdit,
                new String[]{MAXIMUM_PORT, getString(R.string.network_port)}));
    }

}
